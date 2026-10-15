package it.vantaggi.scoreboardessential.padelelite

import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.ConfigurazioneSerata
import it.vantaggi.scoreboardessential.core.GironiSalvati
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * Le tre letture della serata dalla dashboard contro un server finto: la serata configurata e i gironi
 * (`v2_settings`) e i confermati (`v2_rsvp`). Per ognuna il 200, il 401, il 403 e la rete assente.
 * Nessuna chiamata al Supabase vero.
 */
@RunWith(AndroidJUnit4::class)
class SerataDashboardApiTest {
    private var finto: ServerFinto? = null

    @After
    fun chiudi() {
        finto?.chiudi()
    }

    private fun api(risposte: (RecordedRequest, Int) -> MockResponse): PadelEliteApi {
        finto?.chiudi()
        val s = ServerFinto(risposte).also { finto = it }
        return PadelEliteApi(s.config(), clock = { 1_000L })
    }

    /** Un'API il cui server e' gia' spento: nessuno risponde, come senza rete. */
    private fun senzaRete(): PadelEliteApi {
        val api = api { _, _ -> json(200, "[]") }
        finto!!.chiudi()
        return api
    }

    private fun impostazione(valore: String) = json(200, """[{"value":$valore}]""")

    private val giovedi = LocalDate.of(2026, 10, 15)

    // ---- venue_config ----

    @Test
    fun `la serata configurata si legge dal gruppo e dalla chiave, con la prima prenotazione`() =
        runBlocking {
            val api =
                api { _, _ ->
                    impostazione(
                        """{"day_of_week":4,"end_month":"2026-12","bookings":[""" +
                            """{"venue":"Green","time":"20:30","duration":"60m","courts":2},{"time":"18:00","duration":"120m"}]}""",
                    )
                }

            val esito = api.fetchVenueConfig("A1", "g-1")

            assertEquals(LetturaDashboard.Ok(ConfigurazioneSerata(4, LocalTime.of(20, 30), 60)), esito)
            val richiesta = finto!!.richieste.single()
            assertEquals("/rest/v1/v2_settings?group_id=eq.g-1&key=eq.venue_config&select=value", richiesta.path)
            assertEquals("Bearer A1", richiesta.getHeader("Authorization"))
            assertEquals(CHIAVE_DI_PROVA, richiesta.getHeader("apikey"))
        }

    @Test
    fun `senza prenotazioni valgono le 19 e 90 minuti, senza durata 90 minuti`() =
        runBlocking {
            assertEquals(
                LetturaDashboard.Ok(ConfigurazioneSerata(2, LocalTime.of(19, 0), 90)),
                api { _, _ -> impostazione("""{"day_of_week":2,"bookings":[]}""") }.fetchVenueConfig("A1", "g-1"),
            )
            assertEquals(
                LetturaDashboard.Ok(ConfigurazioneSerata(2, LocalTime.of(21, 0), 90)),
                api { _, _ -> impostazione("""{"day_of_week":"2","bookings":[{"time":"21:00","duration":null}]}""") }
                    .fetchVenueConfig("A1", "g-1"),
            )
        }

    @Test
    fun `nessuna riga, o un giorno che manca o fuori da 0-6, e' nessuna serata configurata`() =
        runBlocking {
            assertEquals(LetturaDashboard.Ok(null), api { _, _ -> json(200, "[]") }.fetchVenueConfig("A1", "g-1"))
            assertEquals(LetturaDashboard.Ok(null), api { _, _ -> impostazione("""{"bookings":[]}""") }.fetchVenueConfig("A1", "g-1"))
            assertEquals(LetturaDashboard.Ok(null), api { _, _ -> impostazione("""{"day_of_week":7}""") }.fetchVenueConfig("A1", "g-1"))
            assertEquals(LetturaDashboard.Ok(null), api { _, _ -> impostazione("""{"day_of_week":2.5}""") }.fetchVenueConfig("A1", "g-1"))
        }

    @Test
    fun `la serata configurata con 401, 403 e senza rete`() =
        runBlocking {
            assertEquals(
                LetturaDashboard.NotAuthenticated,
                api { _, _ -> json(401, """{"message":"JWT expired"}""") }.fetchVenueConfig("A1", "g-1"),
            )
            assertEquals(LetturaDashboard.NotAuthorized, api { _, _ -> json(403, "{}") }.fetchVenueConfig("A1", "g-1"))
            assertEquals(LetturaDashboard.Network, api { _, _ -> json(503, "") }.fetchVenueConfig("A1", "g-1"))
            assertEquals(LetturaDashboard.Network, api { _, _ -> json(200, """{"non":"una lista"}""") }.fetchVenueConfig("A1", "g-1"))
            assertEquals(LetturaDashboard.Network, senzaRete().fetchVenueConfig("A1", "g-1"))
        }

    // ---- v2_rsvp ----

    @Test
    fun `i confermati si chiedono per gruppo, data e stato, in ordine di risposta, senza doppioni`() =
        runBlocking {
            val api = api { _, _ -> json(200, """[{"player_id":12},{"player_id":3},{"player_id":12},{"player_id":0},{}]""") }

            val esito = api.fetchConfirmed("A1", "g-1", giovedi)

            assertEquals(LetturaDashboard.Ok(listOf(12, 3)), esito)
            val percorso = finto!!.richieste.single().path!!
            assertTrue(percorso, percorso.startsWith("/rest/v1/v2_rsvp?"))
            for (pezzo in listOf(
                "group_id=eq.g-1",
                "session_date=eq.2026-10-15",
                "status=eq.confirmed",
                "select=player_id",
                "order=created_at.asc,player_id.asc",
            )) {
                assertTrue("manca $pezzo in $percorso", percorso.contains(pezzo))
            }
        }

    @Test
    fun `i confermati con 401, 403 e senza rete`() =
        runBlocking {
            assertEquals(LetturaDashboard.NotAuthenticated, api { _, _ -> json(401, "{}") }.fetchConfirmed("A1", "g-1", giovedi))
            assertEquals(LetturaDashboard.NotAuthorized, api { _, _ -> json(403, "{}") }.fetchConfirmed("A1", "g-1", giovedi))
            assertEquals(LetturaDashboard.Network, senzaRete().fetchConfirmed("A1", "g-1", giovedi))
        }

    // ---- gironi_serata ----

    @Test
    fun `i gironi salvati si leggono con la loro serata`() =
        runBlocking {
            val api =
                api { _, _ ->
                    impostazione(
                        """{"v":1,"serata":"2026-10-15","gironi":[[3,12,7,9],[1,2,4,5],[],[8,null,8]],""" +
                            """"presenti":[1,2,3,4,5,7,8,9,12],"aggiornata_il":"2026-10-14T18:00:00.000Z"}""",
                    )
                }

            val esito = api.fetchGironi("A1", "g-1")

            // Un girone vuoto si salta, un id nullo o ripetuto anche.
            assertEquals(
                LetturaDashboard.Ok(GironiSalvati("2026-10-15", listOf(listOf(3, 12, 7, 9), listOf(1, 2, 4, 5), listOf(8)))),
                esito,
            )
            assertEquals("/rest/v1/v2_settings?group_id=eq.g-1&key=eq.gironi_serata&select=value", finto!!.richieste.single().path)
        }

    @Test
    fun `nessun girone, un formato sconosciuto o una data illeggibile non sono gironi`() =
        runBlocking {
            assertEquals(LetturaDashboard.Ok(null), api { _, _ -> json(200, "[]") }.fetchGironi("A1", "g-1"))
            assertEquals(
                LetturaDashboard.Ok(null),
                api { _, _ -> impostazione("""{"v":2,"serata":"2026-10-15","gironi":[[1,2,3,4]]}""") }.fetchGironi("A1", "g-1"),
            )
            assertEquals(
                LetturaDashboard.Ok(null),
                api { _, _ -> impostazione("""{"v":1,"serata":"giovedi","gironi":[[1,2,3,4]]}""") }.fetchGironi("A1", "g-1"),
            )
        }

    @Test
    fun `i gironi con 401, 403 e senza rete`() =
        runBlocking {
            assertEquals(LetturaDashboard.NotAuthenticated, api { _, _ -> json(401, "{}") }.fetchGironi("A1", "g-1"))
            assertEquals(LetturaDashboard.NotAuthorized, api { _, _ -> json(403, "{}") }.fetchGironi("A1", "g-1"))
            assertEquals(LetturaDashboard.Network, senzaRete().fetchGironi("A1", "g-1"))
        }

    // ---- Il rinnovo del token passa anche da qui ----

    @Test
    fun `un 401 rinnova il token una volta e ripete la lettura`() =
        runBlocking {
            val s =
                ServerFinto { r, n ->
                    when {
                        r.path!!.startsWith("/auth/") -> json(200, rispostaDiSessione("A2", "R2", Long.MAX_VALUE / 2))
                        n == 0 -> json(401, """{"message":"JWT expired"}""")
                        else -> impostazione("""{"day_of_week":4}""")
                    }
                }.also { finto = it }
            val store =
                SessionStore(
                    preferenze(
                        androidx.test.core.app.ApplicationProvider
                            .getApplicationContext(),
                        "sessione_serata",
                    ),
                    SoftwareSecretBox(),
                )
            store.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
            val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), store)

            assertEquals(LetturaDashboard.Ok(ConfigurazioneSerata(4)), account.venueConfig("g-1"))
            val autorizzazioni = s.richieste.filter { it.path!!.startsWith("/rest/") }.map { it.getHeader("Authorization") }
            assertEquals(listOf("Bearer A1", "Bearer A2"), autorizzazioni)
        }
}
