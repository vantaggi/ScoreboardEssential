package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.PresenteDellaDashboard
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.PadelEliteLink
import it.vantaggi.scoreboardessential.database.Player
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * La serata della dashboard come la legge la schermata Serata: le quattro letture contro un server finto,
 * la data calcolata con un orologio finto (giovedi' 15 ottobre 2026), la traduzione coi collegamenti R-1
 * del database vero in memoria, e gli stati (spenta, non configurata, errore, accesso scaduto).
 */
@RunWith(AndroidJUnit4::class)
class LettoreSerataDashboardTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private var finto: ServerFinto? = null

    private val giovedi = LocalDate.of(2026, 10, 15)

    @Before
    fun avvia() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun chiudi() {
        db.close()
        finto?.chiudi()
    }

    private val rosaDellaDashboard =
        """[{"id":10,"name":"Vantaggi"},{"id":11,"name":"Trinari"},""" +
            """{"id":12,"name":"Nudi"},{"id":13,"name":"Porcacchia"}]"""

    private fun serataDelGiovedi(giorno: Int = 4) =
        json(200, """[{"value":{"day_of_week":$giorno,"bookings":[{"time":"19:00","duration":"90m"}]}}]""")

    private fun gironiDel(serata: String) = json(200, """[{"value":{"v":1,"serata":"$serata","gironi":[[10,11,12,13]]}}]""")

    /** Il server della dashboard: ogni percorso la sua risposta, salvo [eccezioni]. */
    private fun dashboard(
        venue: MockResponse = serataDelGiovedi(),
        confermati: MockResponse = json(200, """[{"player_id":12},{"player_id":10},{"player_id":13}]"""),
        gironi: MockResponse = gironiDel("2026-10-15"),
        rosa: MockResponse = json(200, rosaDellaDashboard),
    ): (RecordedRequest, Int) -> MockResponse =
        { r, _ ->
            val percorso = r.path.orEmpty()
            when {
                percorso.startsWith("/auth/") -> json(400, """{"error":"invalid_grant"}""")
                percorso.contains("key=eq.venue_config") -> venue
                percorso.contains("key=eq.gironi_serata") -> gironi
                percorso.startsWith("/rest/v1/v2_rsvp") -> confermati
                percorso.startsWith("/rest/v1/v2_players") -> rosa
                else -> json(404, "{}")
            }
        }

    private fun lettore(
        conAccesso: Boolean = true,
        conGruppo: Boolean = true,
        adesso: LocalDateTime = giovedi.atTime(10, 0),
        risposte: (RecordedRequest, Int) -> MockResponse = dashboard(),
    ): LettoreSerataDashboard {
        finto?.chiudi()
        val s = ServerFinto(risposte).also { finto = it }
        val store = SessionStore(preferenze(context, "sessione_lettore"), SoftwareSecretBox())
        if (conAccesso) store.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), store)
        if (conGruppo) account.selectGroup(PadelEliteGroup("g-1", "Padel", "member"))
        return LettoreSerataDashboard(account, db.padelEliteLinkDao(), db.playerDao()) { adesso }
    }

    private fun giocatore(nome: String): Int =
        runBlocking {
            db.playerDao().insert(Player(playerName = nome, appearances = 0, goals = 0)).toInt()
        }

    private fun collega(
        locale: Int,
        remoto: Int,
        nome: String,
    ) = runBlocking { db.padelEliteLinkDao().link(PadelEliteLink(locale, "g-1", remoto, nome)) }

    @Test
    fun `la serata di oggi coi presenti tradotti, i collegati come giocatori dell'app e gli altri come ospiti`() =
        runBlocking {
            val mattia = giocatore("Mattia")
            collega(mattia, 10, "Vantaggi")

            val pronta = lettore().leggi() as SerataDashboard.Pronta

            assertEquals(giovedi, pronta.giorno)
            assertEquals(
                listOf(
                    PresenteDellaDashboard.Ospite(12, "Nudi"),
                    PresenteDellaDashboard.Collegato(10, "Mattia", mattia),
                    PresenteDellaDashboard.Ospite(13, "Porcacchia"),
                ),
                pronta.presenti.voci,
            )
            assertEquals(2, pronta.presenti.ospiti)
            assertEquals("i gironi di stasera ci sono", 1, pronta.gironi.size)
            assertFalse(pronta.gironiDiUnAltraSerata)
            // I confermati si chiedono per la data calcolata, non per oggi qualunque.
            assertTrue(finto!!.richieste.any { it.path!!.contains("session_date=eq.2026-10-15") })
        }

    @Test
    fun `dopo la fine della serata di oggi si leggono i confermati della settimana dopo e i gironi di oggi si ignorano`() =
        runBlocking {
            val pronta = lettore(adesso = giovedi.atTime(21, 0)).leggi() as SerataDashboard.Pronta

            assertEquals(giovedi.plusDays(7), pronta.giorno)
            assertTrue(finto!!.richieste.any { it.path!!.contains("session_date=eq.2026-10-22") })
            assertTrue("nessun girone di un'altra serata", pronta.gironi.isEmpty())
            assertTrue(pronta.gironiDiUnAltraSerata)
        }

    @Test
    fun `nessun presente confermato e' una serata pronta e vuota`() =
        runBlocking {
            val pronta = lettore(risposte = dashboard(confermati = json(200, "[]"))).leggi() as SerataDashboard.Pronta
            assertTrue(pronta.presenti.voci.isEmpty())
        }

    @Test
    fun `senza venue_config la serata non e' configurata e non si chiede altro`() =
        runBlocking {
            assertEquals(SerataDashboard.NonConfigurata, lettore(risposte = dashboard(venue = json(200, "[]"))).leggi())
            assertEquals(1, finto!!.richieste.size)
        }

    @Test
    fun `senza accesso o senza gruppo la riga non c'e' e la rete non si tocca`() =
        runBlocking {
            assertEquals(SerataDashboard.Spenta, lettore(conAccesso = false).leggi())
            assertEquals(0, finto!!.richieste.size)
            assertEquals(SerataDashboard.Spenta, lettore(conGruppo = false).leggi())
            assertEquals(0, finto!!.richieste.size)
        }

    @Test
    fun `una lettura che non risponde e' un errore da riprovare`() =
        runBlocking {
            assertEquals(SerataDashboard.Errore, lettore(risposte = dashboard(gironi = json(503, ""))).leggi())
            assertEquals(SerataDashboard.Errore, lettore(risposte = dashboard(rosa = json(500, ""))).leggi())
            val spento = lettore()
            finto!!.chiudi()
            assertEquals(SerataDashboard.Errore, spento.leggi())
        }

    @Test
    fun `un 401 con il rinnovo rifiutato e' l'accesso scaduto, un 403 e' fuori dal gruppo`() =
        runBlocking {
            assertEquals(SerataDashboard.AccessoScaduto, lettore(risposte = dashboard(confermati = json(401, "{}"))).leggi())
            assertEquals(SerataDashboard.FuoriDalGruppo, lettore(risposte = dashboard(venue = json(403, "{}"))).leggi())
            assertEquals(SerataDashboard.FuoriDalGruppo, lettore(risposte = dashboard(rosa = json(403, "{}"))).leggi())
        }
}
