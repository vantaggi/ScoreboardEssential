package it.vantaggi.scoreboardessential.padelelite

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Il client REST contro un server finto: accesso, rinnovo, gruppi, stato e ogni errore del
 * contratto (`docs/dashboard/SCOREBOARD_FORMAT.md` par. 6). Nessuna chiamata al Supabase vero.
 */
@RunWith(AndroidJUnit4::class)
class PadelEliteApiTest {
    private var finto: ServerFinto? = null

    @After
    fun chiudi() {
        finto?.chiudi()
    }

    private fun api(risposte: (okhttp3.mockwebserver.RecordedRequest, Int) -> okhttp3.mockwebserver.MockResponse): PadelEliteApi {
        finto?.chiudi()
        val s = ServerFinto(risposte).also { finto = it }
        return PadelEliteApi(s.config(), clock = { 1_000L })
    }

    @Test
    fun `l'accesso riuscito legge la sessione e manda chiave e credenziali`() =
        runBlocking {
            val api = api { _, _ -> json(200, rispostaDiSessione("A1", "R1", 5_000L)) }

            val esito = api.signIn("mattia@example.com", "segreta")

            val sessione = (esito as AuthResult.Ok).session
            assertEquals(PadelEliteSession("A1", "R1", 5_000L, "u-1", "mattia@example.com"), sessione)
            val richiesta = finto!!.richieste.single()
            assertEquals("/auth/v1/token?grant_type=password", richiesta.path)
            assertEquals(CHIAVE_DI_PROVA, richiesta.getHeader("apikey"))
            assertEquals("Bearer $CHIAVE_DI_PROVA", richiesta.getHeader("Authorization"))
            val corpo = richiesta.body.readUtf8()
            assertTrue(corpo, corpo.contains("\"email\":\"mattia@example.com\"") && corpo.contains("\"password\":\"segreta\""))
        }

    @Test
    fun `la scadenza si ricava da expires_in se expires_at manca`() =
        runBlocking {
            val api =
                api {
                    _,
                    _,
                    ->
                    json(200, """{"access_token":"A","refresh_token":"R","expires_in":600,"user":{"id":"u","email":"e"}}""")
                }
            assertEquals(1_600L, (api.signIn("e", "p") as AuthResult.Ok).session.expiresAtSec)
        }

    @Test
    fun `una password sbagliata e' rifiutata con il motivo giusto`() =
        runBlocking {
            val api = api { _, _ -> json(400, """{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}""") }
            assertEquals(AuthResult.Rejected(AuthFailure.INVALID_CREDENTIALS), api.signIn("a@b.it", "x"))
        }

    @Test
    fun `l'email non confermata e i troppi tentativi hanno il loro motivo`() =
        runBlocking {
            val nonConfermata = api { _, _ -> json(400, """{"error_code":"email_not_confirmed","msg":"Email not confirmed"}""") }
            assertEquals(AuthResult.Rejected(AuthFailure.EMAIL_NOT_CONFIRMED), nonConfermata.signIn("a@b.it", "x"))
            val troppi = api { _, _ -> json(429, """{"error_code":"over_request_rate_limit","msg":"rate limit"}""") }
            assertEquals(AuthResult.Rejected(AuthFailure.RATE_LIMITED), troppi.signIn("a@b.it", "x"))
        }

    @Test
    fun `un 5xx o un server che non risponde e' un errore di rete, non un rifiuto`() =
        runBlocking {
            assertEquals(AuthResult.Network, api { _, _ -> json(503, "") }.signIn("a@b.it", "x"))
            val spento = api { _, _ -> json(200, "") }
            finto!!.chiudi()
            assertEquals(AuthResult.Network, spento.signIn("a@b.it", "x"))
        }

    @Test
    fun `il rinnovo manda il refresh token e rifiuta quello scaduto`() =
        runBlocking {
            val buono = api { _, _ -> json(200, rispostaDiSessione("A2", "R2", 9_000L)) }
            assertEquals("A2", (buono.refresh("R1") as AuthResult.Ok).session.accessToken)
            val richiesta = finto!!.richieste.single()
            assertEquals("/auth/v1/token?grant_type=refresh_token", richiesta.path)
            assertTrue(richiesta.body.readUtf8().contains("\"refresh_token\":\"R1\""))

            val scaduto =
                api { _, _ -> json(400, """{"error":"invalid_grant","error_description":"Invalid Refresh Token: Already Used"}""") }
            assertEquals(AuthResult.Rejected(AuthFailure.SESSION_EXPIRED), scaduto.refresh("R1"))
        }

    @Test
    fun `l'elenco dei gruppi filtra per utente e legge nome e ruolo`() =
        runBlocking {
            val api =
                api { _, _ ->
                    json(
                        200,
                        """[{"group_id":"g-1","role":"admin","groups":{"name":"Padel del giovedi"}},""" +
                            """{"group_id":"g-2222222222","role":"member","groups":null}]""",
                    )
                }

            val gruppi = (api.fetchGroups("A1", "u-1") as GroupsResult.Ok).groups

            assertEquals(
                listOf(PadelEliteGroup("g-1", "Padel del giovedi", "admin"), PadelEliteGroup("g-2222222222", "g-222222", "member")),
                gruppi,
            )
            val richiesta = finto!!.richieste.single()
            assertTrue(
                richiesta.path!!,
                richiesta.path!!.startsWith("/rest/v1/group_members?select=group_id,role,groups(name)&user_id=eq.u-1"),
            )
            assertEquals("Bearer A1", richiesta.getHeader("Authorization"))
            assertEquals(CHIAVE_DI_PROVA, richiesta.getHeader("apikey"))
        }

    @Test
    fun `i gruppi con un token rifiutato dicono di rinnovare`() =
        runBlocking {
            assertEquals(GroupsResult.NotAuthenticated, api { _, _ -> json(401, """{"message":"JWT expired"}""") }.fetchGroups("A", "u"))
        }

    @Test
    fun `la rosa del gruppo filtra per gruppo, ordina per nome e legge id, nome e account`() =
        runBlocking {
            val api =
                api { _, _ ->
                    json(
                        200,
                        """[{"id":3,"name":"Anna Bianchi"},""" +
                            """{"id":12,"name":"  Marco Rossi "},""" +
                            """{"id":0,"name":"Rotto"},""" +
                            """{"id":15,"name":"   "}]""",
                    )
                }

            val rosa = (api.fetchRoster("A1", "g-1") as RosterResult.Ok).players

            // Le righe senza id positivo o senza nome non si possono collegare: restano fuori.
            assertEquals(listOf(RemotePlayer(3, "Anna Bianchi"), RemotePlayer(12, "Marco Rossi")), rosa)
            val richiesta = finto!!.richieste.single()
            assertEquals("/rest/v1/v2_players?group_id=eq.g-1&select=id,name&order=name", richiesta.path)
            assertEquals("Bearer A1", richiesta.getHeader("Authorization"))
            assertEquals(CHIAVE_DI_PROVA, richiesta.getHeader("apikey"))
        }

    @Test
    fun `un gruppo senza giocatori e' una rosa vuota, non un errore`() =
        runBlocking {
            assertEquals(RosterResult.Ok(emptyList()), api { _, _ -> json(200, "[]") }.fetchRoster("A1", "g-1"))
        }

    @Test
    fun `la rosa con un token rifiutato dice di rinnovare`() =
        runBlocking {
            assertEquals(RosterResult.NotAuthenticated, api { _, _ -> json(401, """{"message":"JWT expired"}""") }.fetchRoster("A", "g"))
        }

    @Test
    fun `un 403 sulla rosa non e' un errore di rete, non si e' piu' nel gruppo`() =
        runBlocking {
            assertEquals(
                RosterResult.NotAuthorized,
                api {
                    _,
                    _,
                    ->
                    json(403, """{"message":"permission denied"}""")
                }.fetchRoster("A1", "g-1"),
            )
        }

    @Test
    fun `la rosa chiede solo id e nome, niente dell'account dei giocatori`() =
        runBlocking {
            api { _, _ -> json(200, "[]") }.fetchRoster("A1", "g-1")

            assertTrue(
                finto!!
                    .richieste
                    .single()
                    .path!!
                    .contains("select=id,name&"),
            )
            assertTrue(
                !finto!!
                    .richieste
                    .single()
                    .path!!
                    .contains("linked_user_id"),
            )
        }

    @Test
    fun `la rosa senza rete, con un 5xx o con un corpo che non e' una lista e' un errore di rete`() =
        runBlocking {
            assertEquals(RosterResult.Network, api { _, _ -> json(503, "") }.fetchRoster("A1", "g-1"))
            assertEquals(RosterResult.Network, api { _, _ -> json(200, """{"message":"non una lista"}""") }.fetchRoster("A1", "g-1"))
            val spento = api { _, _ -> json(200, "[]") }
            finto!!.chiudi()
            assertEquals(RosterResult.Network, spento.fetchRoster("A1", "g-1"))
        }

    @Test
    fun `l'invio riuscito manda il file intatto con gruppo e token`() =
        runBlocking {
            val api = api { _, _ -> rispostaDiInvio() }

            val esito = api.submit("A1", "g-1", FILE_V2)

            val accettata = esito as SubmitResult.Accepted
            assertEquals("pending", accettata.item.status)
            assertEquals(false, accettata.alreadySubmitted)
            val richiesta = finto!!.richieste.single()
            assertEquals("/rest/v1/rpc/submit_scoreboard_match", richiesta.path)
            assertEquals("Bearer A1", richiesta.getHeader("Authorization"))
            // Il file non si rilegge e non si riscrive: dentro il corpo c'e' lo stesso testo.
            assertEquals("""{"p_group":"g-1","p_payload":$FILE_V2}""", richiesta.body.readUtf8())
        }

    @Test
    fun `already_submitted e' una consegna riuscita, non un errore`() =
        runBlocking {
            val esito = api { _, _ -> rispostaDiInvio("imported", giaInviata = true) }.submit("A1", "g-1", FILE_V2)
            assertEquals(true, (esito as SubmitResult.Accepted).alreadySubmitted)
            assertEquals("imported", esito.item.status)
        }

    @Test
    fun `la chiave updated si legge, e se manca vale false`() =
        runBlocking {
            fun accettata(risposta: okhttp3.mockwebserver.MockResponse) =
                runBlocking { api { _, _ -> risposta }.submit("A1", "g-1", FILE_V2) } as SubmitResult.Accepted

            assertEquals(true, accettata(rispostaDiInvio(giaInviata = true, aggiornata = true)).updated)
            assertEquals(false, accettata(rispostaDiInvio(giaInviata = true, aggiornata = false)).updated)
            // Server vecchio: nessuna chiave.
            assertEquals(false, accettata(rispostaDiInvio(giaInviata = true)).updated)
            assertEquals(false, accettata(rispostaDiInvio()).updated)
        }

    @Test
    fun `ogni errore della RPC e' letto dal messaggio e non dallo stato HTTP`() =
        runBlocking {
            fun esito(risposta: okhttp3.mockwebserver.MockResponse) = runBlocking { api { _, _ -> risposta }.submit("A1", "g-1", FILE_V2) }

            assertEquals(SubmitResult.NotAuthenticated, esito(erroreRpc(403, "28000", "not_authenticated")))
            assertEquals(SubmitResult.NotAuthorized, esito(erroreRpc(403, "42501", "not_authorized")))
            assertEquals(SubmitResult.InvalidPayload("matchId"), esito(erroreRpc(400, "22023", "invalid_payload", "matchId")))
            assertEquals(SubmitResult.PayloadTooLarge, esito(erroreRpc(500, "54000", "payload_too_large")))
            assertEquals(SubmitResult.InboxFull("user_pending"), esito(erroreRpc(500, "54000", "inbox_full", "user_pending")))
        }

    @Test
    fun `un token scaduto, un 5xx e una RPC che non c'e' ancora hanno ciascuno il suo esito`() =
        runBlocking {
            fun esito(risposta: okhttp3.mockwebserver.MockResponse) = runBlocking { api { _, _ -> risposta }.submit("A1", "g-1", FILE_V2) }

            assertEquals(SubmitResult.NotAuthenticated, esito(json(401, """{"code":"PGRST301","message":"JWT expired"}""")))
            assertEquals(SubmitResult.Network, esito(json(503, "")))
            assertEquals(SubmitResult.Network, esito(json(429, "")))
            assertEquals(SubmitResult.Unexpected(404), esito(json(404, """{"code":"PGRST202","message":"Could not find the function"}""")))
        }

    @Test
    fun `lo stato della voce si legge dalla casella per external_id`() =
        runBlocking {
            val api = api { _, _ -> json(200, """[{"status":"imported","match_id":"m-9"}]""") }
            assertEquals(StatusResult.Found("imported", "m-9"), api.fetchStatus("A1", UUID_PARTITA))
            assertTrue(
                finto!!
                    .richieste
                    .single()
                    .path!!
                    .contains("external_id=eq.match%3A$UUID_PARTITA"),
            )

            assertEquals(StatusResult.NotFound, api { _, _ -> json(200, "[]") }.fetchStatus("A1", UUID_PARTITA))
        }
}
