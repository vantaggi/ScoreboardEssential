package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * L'account: sessione cifrata, rinnovo automatico del token scaduto (una volta sola, anche con
 * chiamate in parallelo), esci, gruppi. Con un server finto e un orologio finto.
 */
@RunWith(AndroidJUnit4::class)
class PadelEliteAccountTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var finto: ServerFinto? = null
    private var adesso = 1_000L

    @After
    fun chiudi() {
        finto?.chiudi()
    }

    private fun account(
        risposte: (okhttp3.mockwebserver.RecordedRequest, Int) -> okhttp3.mockwebserver.MockResponse,
        prefs: String = "sessione_di_prova",
    ): PadelEliteAccount {
        val s = ServerFinto(risposte).also { finto = it }
        val store = SessionStore(preferenze(context, prefs), SoftwareSecretBox())
        return PadelEliteAccount(s.config(), PadelEliteApi(s.config(), clock = { adesso }), store, clock = { adesso })
    }

    private fun accessoRiuscito() = json(200, rispostaDiSessione("A1", "R1", adesso + 3_600))

    @Test
    fun `dopo l'accesso la sessione c'e' ed e' cifrata su disco`() =
        runBlocking {
            val account = account({ _, _ -> accessoRiuscito() })

            account.signIn("mattia@example.com", "segreta")

            assertEquals("A1", account.session()?.accessToken)
            val grezzo =
                context
                    .getSharedPreferences("sessione_di_prova", Context.MODE_PRIVATE)
                    .all.values
                    .joinToString()
            // Falsificazione: se la sessione fosse in chiaro, il token comparirebbe nel file.
            assertFalse(grezzo, grezzo.contains("mattia@example.com"))
            assertFalse(grezzo, grezzo.contains("\"a\":\"A1\""))
            assertFalse(grezzo, grezzo.contains("segreta"))
        }

    @Test
    fun `una sessione che non si decifra vale come nessuna sessione`() {
        val prefs = preferenze(context, "sessione_rotta")
        SessionStore(prefs, SoftwareSecretBox()).save(PadelEliteSession("A", "R", 1L, "u", "e"))
        // Un'altra chiave (per esempio il Keystore ripristinato altrove) non apre quel dato.
        assertNull(SessionStore(prefs, SoftwareSecretBox()).load())
    }

    @Test
    fun `esci cancella sessione e gruppo`() =
        runBlocking {
            val account = account({ _, _ -> accessoRiuscito() })
            account.signIn("a@b.it", "x")
            account.selectGroup(PadelEliteGroup("g-1", "Padel", "member"))

            account.signOut()

            assertNull(account.session())
            assertNull(account.selectedGroup())
        }

    @Test
    fun `un altro utente non eredita il gruppo del precedente`() =
        runBlocking {
            val account =
                account({ _, n ->
                    json(
                        200,
                        rispostaDiSessione(
                            "A$n",
                            "R$n",
                            adesso + 3_600,
                            userId =
                                if (n ==
                                    0
                                ) {
                                    "u-1"
                                } else {
                                    "u-2"
                                },
                        ),
                    )
                })
            account.signIn("uno@b.it", "x")
            account.selectGroup(PadelEliteGroup("g-1", "Padel", "member"))

            account.signIn("due@b.it", "x")

            assertNull(account.selectedGroup())
        }

    @Test
    fun `un token ancora valido non chiede nessun rinnovo`() =
        runBlocking {
            val account = account({ _, _ -> accessoRiuscito() })
            account.signIn("a@b.it", "x")

            val token = account.accessToken()

            assertEquals("A1", (token as TokenResult.Ok).session.accessToken)
            assertEquals(0, finto!!.quante("/auth/v1/token?grant_type=refresh_token"))
        }

    @Test
    fun `un token scaduto si rinnova da solo una volta e il nuovo resta salvato`() =
        runBlocking {
            val account =
                account({ richiesta, _ ->
                    if (richiesta.path!!.contains(
                            "refresh_token",
                        )
                    ) {
                        json(200, rispostaDiSessione("A2", "R2", adesso + 3_600))
                    } else {
                        accessoRiuscito()
                    }
                })
            account.signIn("a@b.it", "x")
            adesso += 4_000 // oltre la scadenza

            val primo = account.accessToken()
            val secondo = account.accessToken()

            assertEquals("A2", (primo as TokenResult.Ok).session.accessToken)
            assertEquals("A2", (secondo as TokenResult.Ok).session.accessToken)
            assertEquals("R2", account.session()?.refreshToken)
            assertEquals(1, finto!!.quante("/auth/v1/token?grant_type=refresh_token"))
        }

    @Test
    fun `un token che scade entro un minuto si rinnova prima di partire`() =
        runBlocking {
            val account =
                account({ richiesta, _ ->
                    if (richiesta.path!!.contains(
                            "refresh_token",
                        )
                    ) {
                        json(200, rispostaDiSessione("A2", "R2", adesso + 3_600))
                    } else {
                        accessoRiuscito()
                    }
                })
            account.signIn("a@b.it", "x")
            adesso += 3_600 - 30 // restano 30 secondi

            assertEquals("A2", (account.accessToken() as TokenResult.Ok).session.accessToken)
        }

    @Test
    fun `chiamate in parallelo con il token scaduto bruciano un solo refresh token`() =
        runBlocking {
            val account =
                account({ richiesta, _ ->
                    if (richiesta.path!!.contains(
                            "refresh_token",
                        )
                    ) {
                        json(200, rispostaDiSessione("A2", "R2", adesso + 3_600))
                    } else {
                        accessoRiuscito()
                    }
                })
            account.signIn("a@b.it", "x")
            adesso += 4_000

            val esiti = (1..5).map { async { account.accessToken() } }.awaitAll()

            assertTrue(esiti.all { it is TokenResult.Ok && it.session.accessToken == "A2" })
            assertEquals(1, finto!!.quante("/auth/v1/token?grant_type=refresh_token"))
        }

    @Test
    fun `un rinnovo rifiutato cancella la sessione e chiede l'accesso`() =
        runBlocking {
            val account =
                account({ richiesta, _ ->
                    if (richiesta.path!!.contains("refresh_token")) json(400, """{"error":"invalid_grant"}""") else accessoRiuscito()
                })
            account.signIn("a@b.it", "x")
            adesso += 4_000

            assertEquals(TokenResult.NeedLogin, account.accessToken())
            assertNull(account.session())
        }

    @Test
    fun `senza rete il rinnovo non cancella la sessione`() =
        runBlocking {
            val account =
                account({ richiesta, _ ->
                    if (richiesta.path!!.contains("refresh_token")) json(503, "") else accessoRiuscito()
                })
            account.signIn("a@b.it", "x")
            adesso += 4_000

            assertEquals(TokenResult.Network, account.accessToken())
            assertNotNull(account.session())
        }

    @Test
    fun `un invio rifiutato per token scaduto rinnova e ripete una volta sola`() =
        runBlocking {
            val account =
                account({ richiesta, n ->
                    when {
                        richiesta.path!!.contains("refresh_token") -> {
                            json(200, rispostaDiSessione("A2", "R2", adesso + 3_600))
                        }

                        richiesta.path!!.contains("submit_scoreboard_match") -> {
                            if (richiesta.getHeader("Authorization") ==
                                "Bearer A2"
                            ) {
                                rispostaDiInvio()
                            } else {
                                json(401, """{"message":"JWT expired"}""")
                            }
                        }

                        else -> {
                            accessoRiuscito()
                        }
                    }
                })
            account.signIn("a@b.it", "x")

            val esito = account.submit("g-1", FILE_V2)

            assertTrue(esito is SubmitResult.Accepted)
            assertEquals(2, finto!!.quante("/rest/v1/rpc/submit_scoreboard_match"))
            assertEquals(1, finto!!.quante("/auth/v1/token?grant_type=refresh_token"))
        }

    @Test
    fun `se anche dopo il rinnovo il server rifiuta, non si insiste`() =
        runBlocking {
            val account =
                account({ richiesta, _ ->
                    when {
                        richiesta.path!!.contains("refresh_token") -> json(200, rispostaDiSessione("A2", "R2", adesso + 3_600))
                        richiesta.path!!.contains("submit_scoreboard_match") -> erroreRpc(403, "28000", "not_authenticated")
                        else -> accessoRiuscito()
                    }
                })
            account.signIn("a@b.it", "x")

            assertEquals(SubmitResult.NotAuthenticated, account.submit("g-1", FILE_V2))
            assertEquals(2, finto!!.quante("/rest/v1/rpc/submit_scoreboard_match"))
        }

    @Test
    fun `senza sessione l'invio dice di accedere e non chiama la rete`() =
        runBlocking {
            val account = account({ _, _ -> rispostaDiInvio() })

            assertEquals(SubmitResult.NotAuthenticated, account.submit("g-1", FILE_V2))
            assertEquals(0, finto!!.richieste.size)
        }

    @Test
    fun `l'elenco dei gruppi usa l'utente della sessione`() =
        runBlocking {
            val account =
                account({ richiesta, _ ->
                    if (richiesta.path!!.startsWith("/rest/v1/group_members")) {
                        json(200, """[{"group_id":"g-1","role":"owner","groups":{"name":"Padel"}}]""")
                    } else {
                        accessoRiuscito()
                    }
                })
            account.signIn("a@b.it", "x")

            val gruppi = (account.groups() as GroupsOutcome.Ok).groups

            assertEquals(listOf(PadelEliteGroup("g-1", "Padel", "owner")), gruppi)
            assertTrue(
                finto!!
                    .richieste
                    .last()
                    .path!!
                    .contains("user_id=eq.u-1"),
            )
        }
}
