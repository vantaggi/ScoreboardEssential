package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.ExportOrigin
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchPlayer
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.ScoreState
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.core.SportRules
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.ZoneId

/** Un avviatore che conta: nessun servizio vero, solo quante volte il controllo ha chiesto di avviare. Fermare non esiste: il servizio si ferma da solo. */
private class AvviatoreFinto(
    var nega: Boolean = false,
) : DirettaAvviatore {
    var avvii = 0

    override fun avvia(): Boolean {
        avvii++
        return !nega
    }
}

/**
 * Il servizio in primo piano parte e si ferma con la diretta, in tutti i modi in cui la diretta nasce e finisce (D-3).
 * Il controllo non ha logica sua: guarda lo stato della [Diretta], quindi la prova passa dalla diretta vera, col server finto.
 */
@RunWith(AndroidJUnit4::class)
class DirettaServizioTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var finto: ServerFinto? = null
    private val scopeDiretta = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
    private val scopeControllo = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val avviatore = AvviatoreFinto()
    private lateinit var diretta: Diretta
    private lateinit var controllo: DirettaServizioControllo

    @After
    fun chiudi() {
        scopeDiretta.cancel()
        scopeControllo.cancel()
        finto?.chiudi()
    }

    private fun accendi(
        risposte: (RecordedRequest, Int) -> MockResponse = { _, _ -> rispostaDiPubblicazione() },
        regole: SportRules = SportRegistry.forMatch(SportRegistry.PADEL, listOf(1, 2, 3, 4)),
    ): Boolean {
        val s =
            ServerFinto { richiesta, n ->
                if (richiesta.path!!.contains("refresh_token")) json(400, """{"error":"invalid_grant"}""") else risposte(richiesta, n)
            }.also { finto = it }
        val sessione = SessionStore(preferenze(context, "sessione_servizio"), SoftwareSecretBox())
        sessione.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        diretta =
            Diretta(
                PadelEliteAccount(s.config(), PadelEliteApi(s.config()), sessione),
                DirettaStore(preferenze(context, "diretta_servizio")),
                scopeDiretta,
                minIntervalMs = 40,
                retryBaseMs = 40,
                retryLentoMs = 80,
            )
        controllo = DirettaServizioControllo(diretta.stato, avviatore, scopeControllo)
        val foto =
            DirettaFoto(
                UUID_PARTITA,
                regole,
                List(1) { LoggedEvent(ScoringEvent.Point(side = 1), 0L) },
                listOf(MatchPlayer(1, "Marco", 1), MatchPlayer(2, "Anna", 2), MatchPlayer(3, "Luca", 1), MatchPlayer(4, "Sara", 2)),
                ExportOrigin(UUID_PARTITA, 1_790_190_240_000L, ZoneId.of("Europe/Rome"), "1.0"),
            )
        return diretta.accendi("g-1", "Padel", foto)
    }

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

    private fun serveIlServizio() {
        attendi("l'avvio del servizio") { avviatore.avvii == 1 }
        assertEquals(DirettaServizioFase.ATTIVO, controllo.fase.value)
    }

    private fun ilServizioSiFerma() {
        // Il controllo torna a "spento" e non chiede mai di fermare il servizio: se ne va da solo (DirettaServiceTest).
        attendi("il controllo a riposo") { controllo.fase.value == DirettaServizioFase.SPENTO }
        assertEquals("un solo avvio", 1, avviatore.avvii)
    }

    // --- Quando parte ---

    @Test
    fun `parte quando la diretta si accende, una volta sola, anche se passa da in corso a in attesa e ritorno`() {
        // Il server cade (rete) e poi risponde: la diretta va in attesa e riprende, ma il servizio non si riavvia.
        accendi({ _, n -> if (n == 0) json(500, "{}") else rispostaDiPubblicazione() })

        serveIlServizio()
        attendi("l'attesa") { diretta.stato.value.fase == DirettaFase.IN_ATTESA }
        attendi("la ripresa") { diretta.stato.value.fase == DirettaFase.IN_CORSO && controllo.fase.value == DirettaServizioFase.ATTIVO }
        assertEquals(1, avviatore.avvii)
    }

    @Test
    fun `parte anche quando la diretta riprende al ripristino`() {
        val s = ServerFinto { _, _ -> rispostaDiPubblicazione() }.also { finto = it }
        val sessione = SessionStore(preferenze(context, "sessione_servizio_rip"), SoftwareSecretBox())
        sessione.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        val store = DirettaStore(preferenze(context, "diretta_servizio_rip"))
        store.save(DirettaSalvata(UUID_PARTITA, "g-1", "Padel"))
        val d = Diretta(PadelEliteAccount(s.config(), PadelEliteApi(s.config()), sessione), store, scopeDiretta, minIntervalMs = 40)
        val c = DirettaServizioControllo(d.stato, avviatore, scopeControllo)
        val foto =
            DirettaFoto(
                UUID_PARTITA,
                SportRegistry.forMatch(SportRegistry.PADEL, listOf(1, 2, 3, 4)),
                emptyList(),
                emptyList(),
                ExportOrigin(UUID_PARTITA, 1_790_190_240_000L, ZoneId.of("Europe/Rome"), "1.0"),
            )

        d.riprendi(foto)

        attendi("l'avvio") { avviatore.avvii == 1 }
        assertEquals(DirettaServizioFase.ATTIVO, c.fase.value)
    }

    @Test
    fun `se l'avvio in primo piano e' negato la diretta va avanti senza servizio e lo stato lo dice`() {
        avviatore.nega = true

        assertTrue(accendi())

        attendi("il rifiuto") { controllo.fase.value == DirettaServizioFase.NON_AVVIATO }
        assertEquals(1, avviatore.avvii)
        // La diretta non e' toccata dal rifiuto del sistema.
        attendi("la pubblicazione") { finto!!.quante("/rest/v1/rpc/publish_live_from_app") >= 1 }
        assertEquals(DirettaFase.IN_CORSO, diretta.stato.value.fase)
    }

    // --- Quando si ferma: tutti i modi in cui la diretta finisce ---

    @Test
    fun `il controllo torna a riposo a fine partita`() {
        accendi()
        serveIlServizio()

        diretta.chiudi(conRisultato = true)

        ilServizioSiFerma()
    }

    @Test
    fun `il controllo torna a riposo quando la partita e' scartata o cambia sport`() {
        accendi()
        serveIlServizio()

        diretta.chiudi(conRisultato = false)

        ilServizioSiFerma()
    }

    @Test
    fun `il controllo torna a riposo con l'interruttore e con la notifica, che arrivano allo stesso spegni`() {
        accendi()
        serveIlServizio()

        diretta.spegni()

        ilServizioSiFerma()
    }

    @Test
    fun `il controllo torna a riposo per un errore definitivo del server`() {
        accendi({ _, _ -> erroreRpc(500, "42501", "not_authorized") })
        attendi("l'avvio") { avviatore.avvii == 1 }

        attendi("la fermata") { diretta.stato.value.fase == DirettaFase.FERMA }

        ilServizioSiFerma()
    }

    @Test
    fun `il controllo torna a riposo quando il ciclo cade per un Error`() {
        val vere = SportRegistry.forMatch(SportRegistry.PADEL, listOf(1, 2, 3, 4))
        accendi(
            regole =
                object : SportRules by vere {
                    override fun display(state: ScoreState): ScoreDisplay = throw StackOverflowError("finto")
                },
        )
        attendi("l'avvio") { avviatore.avvii == 1 }

        attendi("la fermata") { diretta.stato.value.motivo == DirettaMotivo.ERRORE_GRAVE }

        ilServizioSiFerma()
    }

    @Test
    fun `dopo una chiusura una diretta nuova riavvia il servizio`() {
        accendi()
        serveIlServizio()
        diretta.chiudi(conRisultato = false)
        ilServizioSiFerma()

        val foto =
            DirettaFoto(
                "altra-partita",
                SportRegistry.forMatch(SportRegistry.PADEL, listOf(1, 2, 3, 4)),
                emptyList(),
                emptyList(),
                ExportOrigin("altra-partita", 1_790_190_240_000L, ZoneId.of("Europe/Rome"), "1.0"),
            )
        assertTrue(diretta.accendi("g-1", "Padel", foto))

        attendi("il secondo avvio") { avviatore.avvii == 2 }
        assertEquals(DirettaServizioFase.ATTIVO, controllo.fase.value)
    }

    // --- Il controllo da solo, su uno stato scritto a mano ---

    @Test
    fun `il controllo ignora lo stato spento e non ferma un servizio che non ha avviato`() {
        val stato = MutableStateFlow(DirettaStato())
        val a = AvviatoreFinto()
        val c = DirettaServizioControllo(stato, a, CoroutineScope(Dispatchers.Unconfined))

        stato.value = DirettaStato(DirettaFase.CHIUSA)
        stato.value = DirettaStato(DirettaFase.SPENTA)

        assertEquals(0, a.avvii)
        assertEquals(DirettaServizioFase.SPENTO, c.fase.value)
    }

    @Test
    fun `lo scadere delle sei ore e il rifiuto di startForeground si ricordano finche' la diretta dura, poi tutto si azzera`() {
        val stato = MutableStateFlow(DirettaStato())
        val a = AvviatoreFinto()
        val c = DirettaServizioControllo(stato, a, CoroutineScope(Dispatchers.Unconfined))
        stato.value = DirettaStato(DirettaFase.IN_CORSO)
        assertEquals(DirettaServizioFase.ATTIVO, c.fase.value)

        c.scaduto()
        assertEquals(DirettaServizioFase.SCADUTO, c.fase.value)
        // Il passaggio in attesa non riavvia il servizio scaduto.
        stato.value = DirettaStato(DirettaFase.IN_ATTESA)
        assertEquals(1, a.avvii)
        assertEquals(DirettaServizioFase.SCADUTO, c.fase.value)

        stato.value = DirettaStato(DirettaFase.CHIUSA)
        assertEquals(DirettaServizioFase.SPENTO, c.fase.value)

        // Un avvio negato dopo la chiusura non resuscita lo stato.
        c.avvioNegato()
        c.scaduto()
        assertEquals(DirettaServizioFase.SPENTO, c.fase.value)
    }

    // --- L'avviatore vero ---

    @Test
    fun `l'avviatore vero avvia DirettaService in primo piano e non ha modo di fermarlo da fuori`() {
        val avviatoreVero = DirettaServiceAvviatore(context)

        assertTrue(avviatoreVero.avvia())
        val avviato: Intent? = shadowOf(context as android.app.Application).nextStartedService
        assertNotNull(avviato)
        assertEquals(DirettaService::class.java.name, avviato!!.component!!.className)
    }

    @Test
    fun `un avvio negato in secondo piano non fa crollare niente e dice no`() {
        // ForegroundServiceStartNotAllowedException (API 31+) e' una IllegalStateException.
        val negato =
            DirettaServiceAvviatore(context) { throw IllegalStateException("Not allowed to start service Intent: app is in background") }
        val senzaPermesso = DirettaServiceAvviatore(context) { throw SecurityException("manca FOREGROUND_SERVICE_DATA_SYNC") }

        assertFalse(negato.avvia())
        assertFalse(senzaPermesso.avvia())
    }
}
