package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.core.ExportOrigin
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchPlayer
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.repository.ColorRepository
import it.vantaggi.scoreboardessential.repository.MatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Un gruppo da cui si e' stati tolti (D-3, debito 16): la rilettura del ruolo all'avvio non lo trova piu' nell'elenco, il
 * gruppo scelto si dimentica e la diretta verso di esso si spegne con un motivo proprio. Un errore di rete NON vale come
 * "tolto dal gruppo". Server finto, mai quello vero.
 */
@RunWith(RobolectricTestRunner::class)
class FuoriDalGruppoTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private var finto: ServerFinto? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun prepara() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build())
    }

    @After
    fun chiudi() {
        scope.cancel()
        finto?.chiudi()
    }

    private object NullAvviatore : DirettaAvviatore {
        override fun avvia() = true
    }

    /** I servizi con sessione, il gruppo g-1 scelto da admin e il server che risponde [elenco] alla lettura dei gruppi. */
    private fun servizi(elenco: () -> MockResponse): PadelEliteServices {
        val s =
            ServerFinto { richiesta: RecordedRequest, _ ->
                when {
                    richiesta.path!!.contains("refresh_token") -> json(400, """{"error":"invalid_grant"}""")
                    richiesta.path!!.contains("group_members") -> elenco()
                    else -> rispostaDiPubblicazione()
                }
            }.also { finto = it }
        val sessione = SessionStore(preferenze(app, "sessione_fuori"), SoftwareSecretBox())
        sessione.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), sessione)
        account.selectGroup(PadelEliteGroup("g-1", "Padel", "admin"))
        return PadelEliteServices(
            app,
            s.config(),
            MatchRepository(mock(MatchDao::class.java), app, ColorRepository(app)),
            accountFactory = { account },
            invioStoreFactory = { InvioStore(preferenze(app, "invii_fuori")) },
            direttaStoreFactory = { DirettaStore(preferenze(app, "diretta_fuori")) },
            direttaScope = scope,
            direttaMinIntervalMs = 40,
            direttaAvviatore = NullAvviatore,
            direttaAvviso = NessunAvviso,
        )
    }

    private fun foto() =
        DirettaFoto(
            UUID_PARTITA,
            SportRegistry.forMatch(SportRegistry.PADEL, listOf(1, 2, 3, 4)),
            List(1) { LoggedEvent(ScoringEvent.Point(side = 1), it * 30_000L) },
            listOf(MatchPlayer(1, "Marco", 1), MatchPlayer(2, "Anna", 2), MatchPlayer(3, "Luca", 1), MatchPlayer(4, "Sara", 2)),
            ExportOrigin(UUID_PARTITA, 1_790_190_240_000L, ZoneId.of("Europe/Rome"), "1.0"),
        )

    private fun attendi(
        che: String,
        entroMs: Long = 6_000,
        condizione: () -> Boolean,
    ) {
        val limite = System.currentTimeMillis() + entroMs
        while (!condizione()) {
            check(System.currentTimeMillis() < limite) { "non e' successo: $che" }
            Thread.sleep(10)
        }
    }

    private fun altroGruppo() = json(200, """[{"group_id":"g-2","role":"admin","groups":{"name":"Altro"}}]""")

    private fun accendiLaDiretta(s: PadelEliteServices) {
        assertTrue(s.diretta.accendi("g-1", "Padel", foto()))
        attendi("la prima pubblicazione") { finto!!.quante("/rest/v1/rpc/publish_live_from_app") >= 1 }
        assertEquals(DirettaFase.IN_CORSO, s.diretta.stato.value.fase)
    }

    private fun chiusureInCoda() =
        WorkManager.getInstance(app).getWorkInfosForUniqueWork(DirettaChiusuraWorker.workName(UUID_PARTITA)).get()

    @Test
    fun `tolto dal gruppo il gruppo scelto si dimentica, il comando sparisce e la diretta si spegne col suo motivo`() =
        runBlocking {
            val s = servizi { altroGruppo() }
            accendiLaDiretta(s)
            assertTrue(s.puoTrasmettere("padel"))

            assertTrue("il foglio si ridisegna", s.aggiornaIlRuolo())

            assertNull("il gruppo scelto si dimentica", s.account.selectedGroup())
            assertNull(s.account.selectedGroupRole())
            assertNotNull("l'accesso resta: e' il gruppo che non c'e' piu', non l'account", s.account.session())
            assertFalse(s.puoTrasmettere("padel"))
            assertEquals(DirettaFase.FERMA, s.diretta.stato.value.fase)
            assertEquals(DirettaMotivo.FUORI_DAL_GRUPPO, s.diretta.stato.value.motivo)
            assertFalse(s.diretta.acceso)
            // Chi non e' piu' nel gruppo non puo' chiudere la riga: nessun lavoro di chiusura da ritentare per niente.
            assertTrue(chiusureInCoda().isEmpty())
        }

    @Test
    fun `un elenco vuoto vale come tolto dal gruppo`() =
        runBlocking {
            val s = servizi { json(200, "[]") }
            accendiLaDiretta(s)

            assertTrue(s.aggiornaIlRuolo())

            assertNull(s.account.selectedGroup())
            assertEquals(DirettaMotivo.FUORI_DAL_GRUPPO, s.diretta.stato.value.motivo)
        }

    @Test
    fun `un errore di rete nella rilettura NON vale come tolto dal gruppo`() =
        runBlocking {
            val s = servizi { json(500, "{}") }
            accendiLaDiretta(s)

            assertFalse(s.aggiornaIlRuolo())

            assertEquals("g-1", s.account.selectedGroup()?.first)
            assertEquals("admin", s.account.selectedGroupRole())
            assertTrue(s.puoTrasmettere("padel"))
            assertEquals(DirettaFase.IN_CORSO, s.diretta.stato.value.fase)
            assertTrue(s.diretta.acceso)
        }

    @Test
    fun `se il gruppo c'e' ancora la diretta non si tocca, anche se il ruolo cambia`() =
        runBlocking {
            val s = servizi { json(200, """[{"group_id":"g-1","role":"member","groups":{"name":"Padel"}}]""") }
            accendiLaDiretta(s)

            assertTrue(s.aggiornaIlRuolo())

            assertEquals("g-1", s.account.selectedGroup()?.first)
            assertEquals("member", s.account.selectedGroupRole())
            assertEquals(DirettaFase.IN_CORSO, s.diretta.stato.value.fase)
        }

    @Test
    fun `senza diretta accesa la rilettura dimentica il gruppo e basta`() =
        runBlocking {
            val s = servizi { altroGruppo() }

            assertTrue(s.aggiornaIlRuolo())

            assertNull(s.account.selectedGroup())
            assertEquals(DirettaFase.SPENTA, s.diretta.stato.value.fase)
        }

    @Test
    @Config(qualifiers = "en")
    fun `il foglio lo dice in inglese senza apostrofi`() {
        val stato = DirettaStato(DirettaFase.FERMA, DirettaMotivo.FUORI_DAL_GRUPPO, gruppo = "Padel", matchId = UUID_PARTITA)

        val riga = direttaLook(app, stato)!!

        assertEquals("Live off: you are no longer in this group", riga.text)
        assertEquals(app.getString(R.string.diretta_stop_left_group), riga.text)
        assertFalse(riga.text.contains('\''))
    }

    @Test
    @Config(qualifiers = "it")
    fun `il foglio lo dice in italiano senza apostrofi`() {
        val stato = DirettaStato(DirettaFase.FERMA, DirettaMotivo.FUORI_DAL_GRUPPO, gruppo = "Padel", matchId = UUID_PARTITA)

        val riga = direttaLook(app, stato)!!

        assertEquals("Diretta spenta: non fai più parte di questo gruppo", riga.text)
        assertFalse(riga.text.contains('\''))
    }
}
