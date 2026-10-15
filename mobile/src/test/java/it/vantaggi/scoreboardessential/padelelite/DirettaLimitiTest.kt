package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/** Un `Error` vero (non un'`Exception`): il giro della diretta non lo prende, e deve farlo il ciclo. */
private class ErroreFinto : Error("errore finto")

/**
 * I limiti chiusi in D-3 (debiti 8, 9, 10): la chiusura in sospeso che sopravvive a un processo ucciso, il ciclo
 * che cade per un `Error`, l'eccezione persistente con un tetto e il nome mai vuoto. Server finto, mai quello vero.
 */
@RunWith(AndroidJUnit4::class)
class DirettaLimitiTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var finto: ServerFinto? = null
    private val sfuggiti = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> sfuggiti += e })
    private val chiusure = java.util.Collections.synchronizedList(mutableListOf<DirettaChiusuraRichiesta>())
    private lateinit var store: DirettaStore

    @After
    fun chiudi() {
        scope.cancel()
        finto?.chiudi()
    }

    private fun server(risposte: (RecordedRequest, Int) -> MockResponse = { _, _ -> rispostaDiPubblicazione() }): PadelEliteAccount {
        val s =
            ServerFinto { richiesta, n ->
                if (richiesta.path!!.contains("refresh_token")) json(400, """{"error":"invalid_grant"}""") else risposte(richiesta, n)
            }.also { finto = it }
        val sessione = SessionStore(preferenze(context, "sessione_limiti"), SoftwareSecretBox())
        sessione.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        return PadelEliteAccount(s.config(), PadelEliteApi(s.config()), sessione)
    }

    private fun nuovaDiretta(
        account: PadelEliteAccount,
        nello: CoroutineScope = scope,
        maxEccezioni: Int = Diretta.MAX_ECCEZIONI,
        allaChiusura: suspend (DirettaChiusuraRichiesta) -> Unit = { chiusure += it },
    ) = Diretta(
        account,
        store,
        nello,
        collegamenti = { mapOf(1 to 101) },
        allaChiusura = allaChiusura,
        minIntervalMs = 50,
        retryBaseMs = 40,
        retryLentoMs = 80,
        maxEccezioni = maxEccezioni,
    )

    private fun foto(
        punti: Int,
        matchId: String = UUID_PARTITA,
        regole: SportRules = SportRegistry.forMatch(SportRegistry.PADEL, listOf(1, 2, 3, 4)),
    ) = DirettaFoto(
        matchId,
        regole,
        List(punti) { LoggedEvent(ScoringEvent.Point(side = 1), it * 30_000L) },
        listOf(MatchPlayer(1, "Marco", 1), MatchPlayer(2, "Anna", 2), MatchPlayer(3, "Luca", 1), MatchPlayer(4, "Sara", 2)),
        ExportOrigin(matchId, 1_790_190_240_000L, ZoneId.of("Europe/Rome"), "1.0"),
    )

    /** Le stesse regole del padel, ma `display` lancia [errore]: il file si costruisce dallo stesso motore, quindi il giro cade. */
    private fun regoleCheLanciano(
        dopo: Int = 0,
        errore: () -> Throwable,
    ): SportRules {
        val vere = SportRegistry.forMatch(SportRegistry.PADEL, listOf(1, 2, 3, 4))
        val chiamate = AtomicInteger()
        return object : SportRules by vere {
            override fun display(state: ScoreState): ScoreDisplay =
                if (chiamate.getAndIncrement() < dopo) vere.display(state) else throw errore()
        }
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

    // --- Chiusura in sospeso (debito 8) ---

    @Test
    fun `una chiusura segnata prima di consegnarla sopravvive al processo ucciso e si accoda al riavvio`() {
        store = DirettaStore(preferenze(context, "diretta_limiti"))
        val inVolo = AtomicInteger()
        val d =
            nuovaDiretta(
                server { _, _ ->
                    inVolo.incrementAndGet()
                    Thread.sleep(800)
                    rispostaDiPubblicazione()
                },
            )
        d.accendi("g-1", "Padel", foto(1))
        attendi("la richiesta in volo") { inVolo.get() == 1 }

        // Chiusa con la richiesta in volo: la consegna aspetta la sua fine, ma il segno e' gia' su disco.
        d.chiudi(conRisultato = true)
        assertTrue("la chiusura non e' ancora partita", chiusure.isEmpty())
        assertEquals(listOf(DirettaChiusuraRichiesta(UUID_PARTITA, "g-1", conRisultato = true)), store.pendenti())
        assertNull("la diretta salvata non c'e' piu'", store.load())
        // Il foglio puo' gia' dire "Chiusura in corso" invece di "Diretta chiusa".
        assertEquals(setOf(UUID_PARTITA), d.chiusureInVolo.value)

        // Il processo muore qui: lo scope e tutto cio' che aspettava spariscono. Resta solo il disco.
        scope.cancel()
        assertTrue(chiusure.isEmpty())

        // Il riavvio: istanza nuova sugli stessi dati. La chiusura si accoda e il segno si toglie.
        val nuovoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val dopo = nuovaDiretta(server(), nuovoScope)
            dopo.riprendi(foto = null)

            // La consegna e' asincrona (il segno si toglie a lavoro scritto): si aspetta la fine.
            attendi("il segno tolto") { store.pendenti().isEmpty() && dopo.chiusureInVolo.value.isEmpty() }
            assertEquals(listOf(DirettaChiusuraRichiesta(UUID_PARTITA, "g-1", conRisultato = true)), chiusure.toList())
            // Una seconda ripresa non la accoda ancora.
            dopo.riprendi(foto = null)
            assertEquals(1, chiusure.size)
        } finally {
            nuovoScope.cancel()
        }
    }

    @Test
    fun `una diretta chiusa un attimo prima della morte del processo non si riaccende al ripristino`() {
        store = DirettaStore(preferenze(context, "diretta_limiti_ripresa"))
        // Il processo e' morto fra il segno della chiusura e la pulizia della diretta salvata.
        store.save(DirettaSalvata(UUID_PARTITA, "g-1", "Padel"))
        store.segnaPendente(DirettaChiusuraRichiesta(UUID_PARTITA, "g-1", conRisultato = false))
        val d = nuovaDiretta(server())

        d.riprendi(foto(3))

        assertEquals(DirettaFase.SPENTA, d.stato.value.fase)
        assertFalse(d.acceso)
        assertNull(store.load())
        attendi("la chiusura") { chiusure.isNotEmpty() }
        assertEquals(listOf(DirettaChiusuraRichiesta(UUID_PARTITA, "g-1", conRisultato = false)), chiusure.toList())
        assertEquals(0, finto!!.quante("/rest/v1/rpc/publish_live_from_app"))
    }

    @Test
    fun `senza richiesta in volo la chiusura si accoda subito e non lascia segni`() {
        store = DirettaStore(preferenze(context, "diretta_limiti_subito"))
        val d = nuovaDiretta(server())
        d.accendi("g-1", "Padel", foto(1))
        attendi("la prima pubblicazione") { finto!!.quante("/rest/v1/rpc/publish_live_from_app") >= 1 }

        d.chiudi(conRisultato = false)

        attendi("la chiusura") { chiusure.isNotEmpty() }
        attendi("il segno tolto") { store.pendenti().isEmpty() }
        attendi("la partita non e' piu' in volo") { d.chiusureInVolo.value.isEmpty() }
    }

    @Test
    fun `lo store ricorda piu' chiusure in sospeso e toglie quella accodata`() {
        store = DirettaStore(preferenze(context, "diretta_limiti_store"))
        val a = DirettaChiusuraRichiesta("m-a", "g-1", conRisultato = true)
        val b = DirettaChiusuraRichiesta("m-b", "g-2", conRisultato = false)

        store.segnaPendente(a)
        store.segnaPendente(b)
        // Segnare di nuovo la stessa partita non la raddoppia.
        store.segnaPendente(a)
        assertEquals(setOf(a, b), store.pendenti().toSet())
        assertEquals(2, store.pendenti().size)

        store.togliPendente("m-a")
        assertEquals(listOf(b), store.pendenti())
        store.togliPendente("m-b")
        assertTrue(store.pendenti().isEmpty())
    }

    // --- La finestra fra l'accodamento e il lavoro scritto (debito 13) ---

    @Test
    fun `il segno della chiusura resta finche' il lavoro non e' scritto, non finche' e' stato chiesto`() {
        store = DirettaStore(preferenze(context, "diretta_limiti_scritto"))
        val cancello = CompletableDeferred<Unit>()
        val d =
            nuovaDiretta(server(), allaChiusura = {
                chiusure += it
                cancello.await()
            })
        d.accendi("g-1", "Padel", foto(1))
        attendi("la prima pubblicazione") { finto!!.quante("/rest/v1/rpc/publish_live_from_app") >= 1 }

        d.chiudi(conRisultato = false)

        // L'accodamento e' stato chiesto, ma WorkManager non ha ancora scritto il lavoro: un processo ucciso qui deve ritrovare il segno.
        attendi("l'accodamento chiesto") { chiusure.isNotEmpty() }
        Thread.sleep(150)
        assertEquals(listOf(DirettaChiusuraRichiesta(UUID_PARTITA, "g-1", conRisultato = false)), store.pendenti())
        assertEquals("il ripristino non la accoda una seconda volta", setOf(UUID_PARTITA), d.chiusureInVolo.value)
        d.riprendi(foto = null)
        Thread.sleep(100)
        assertEquals(1, chiusure.size)

        // Il lavoro e' scritto: solo ora il segno si toglie.
        cancello.complete(Unit)
        attendi("il segno tolto") { store.pendenti().isEmpty() }
        attendi("la partita non e' piu' in volo") { d.chiusureInVolo.value.isEmpty() }
    }

    @Test
    fun `se il lavoro non si scrive il segno resta e il ripristino dopo lo riprova`() {
        store = DirettaStore(preferenze(context, "diretta_limiti_fallita"))
        val account = server()
        val d = nuovaDiretta(account, allaChiusura = { throw IllegalStateException("WorkManager non risponde") })
        d.accendi("g-1", "Padel", foto(1))
        attendi("la prima pubblicazione") { finto!!.quante("/rest/v1/rpc/publish_live_from_app") >= 1 }

        d.chiudi(conRisultato = true)

        attendi("la partita non e' piu' in volo") { d.chiusureInVolo.value.isEmpty() }
        assertEquals("il segno resta", listOf(DirettaChiusuraRichiesta(UUID_PARTITA, "g-1", conRisultato = true)), store.pendenti())
        assertTrue("l'eccezione non arriva al gestore dello scope", sfuggiti.isEmpty())

        // Il prossimo avvio la ritrova e, con WorkManager di nuovo vivo, la accoda.
        val riuscita = nuovaDiretta(account)
        riuscita.riprendi(foto = null)
        attendi("la chiusura accodata") { chiusure.isNotEmpty() }
        attendi("il segno tolto") { store.pendenti().isEmpty() }
    }

    // --- Un gruppo da cui si e' stati tolti (debito 16) ---

    @Test
    fun `fuoriDalGruppo spegne la diretta verso quel gruppo con un motivo suo e senza chiuderla sul server`() {
        store = DirettaStore(preferenze(context, "diretta_limiti_fuori"))
        val d = nuovaDiretta(server())
        d.accendi("g-1", "Padel", foto(1))
        attendi("la prima pubblicazione") { finto!!.quante("/rest/v1/rpc/publish_live_from_app") >= 1 }

        d.fuoriDalGruppo("g-1")

        assertEquals(DirettaFase.FERMA, d.stato.value.fase)
        assertEquals(DirettaMotivo.FUORI_DAL_GRUPPO, d.stato.value.motivo)
        assertFalse(d.acceso)
        assertNull("niente da riprendere", store.load())
        // Chi non e' piu' nel gruppo non puo' chiudere la riga: nessuna richiesta di chiusura, nessun segno.
        Thread.sleep(200)
        assertTrue(chiusure.isEmpty())
        assertTrue(store.pendenti().isEmpty())
    }

    @Test
    fun `fuoriDalGruppo non tocca la diretta verso un altro gruppo`() {
        store = DirettaStore(preferenze(context, "diretta_limiti_altro_gruppo"))
        val d = nuovaDiretta(server())
        d.accendi("g-1", "Padel", foto(1))
        attendi("la prima pubblicazione") { finto!!.quante("/rest/v1/rpc/publish_live_from_app") >= 1 }

        d.fuoriDalGruppo("g-2")

        assertTrue(d.acceso)
        assertEquals(DirettaFase.IN_CORSO, d.stato.value.fase)
        assertEquals(DirettaSalvata(UUID_PARTITA, "g-1", "Padel"), store.load())
    }

    @Test
    fun `fuoriDalGruppo dimentica la diretta salvata verso quel gruppo prima che il ripristino la riprenda`() {
        store = DirettaStore(preferenze(context, "diretta_limiti_salvata"))
        store.save(DirettaSalvata(UUID_PARTITA, "g-1", "Padel"))
        val d = nuovaDiretta(server())

        d.fuoriDalGruppo("g-1")
        d.riprendi(foto(3))

        assertNull(store.load())
        assertFalse(d.acceso)
        // Il foglio dice perche' la diretta non riparte, anche se la rete ha risposto prima del ripristino.
        assertEquals(DirettaFase.FERMA, d.stato.value.fase)
        assertEquals(DirettaMotivo.FUORI_DAL_GRUPPO, d.stato.value.motivo)
        assertEquals("Padel", d.stato.value.gruppo)
        assertEquals(UUID_PARTITA, d.stato.value.matchId)
        Thread.sleep(200)
        assertEquals(0, finto!!.quante("/rest/v1/rpc/publish_live_from_app"))
    }

    // --- Error nel ciclo (debito 9) ---

    @Test
    fun `un Error nel ciclo ferma la diretta con un motivo e chiude sul server, invece di restare in diretta a parole`() {
        store = DirettaStore(preferenze(context, "diretta_limiti_error"))
        val d = nuovaDiretta(server())

        d.accendi("g-1", "Padel", foto(1, regole = regoleCheLanciano { ErroreFinto() }))

        attendi("lo stato fermo") { d.stato.value.fase == DirettaFase.FERMA }
        assertEquals(DirettaMotivo.ERRORE_GRAVE, d.stato.value.motivo)
        assertEquals("ErroreFinto", d.stato.value.dettaglio)
        assertFalse(d.acceso)
        assertNull("niente da riprendere", store.load())
        // Nessun file e' mai arrivato al server: non c'e' niente da chiudere li' (come dice il KDoc di `ferma`).
        Thread.sleep(200)
        assertTrue(chiusure.isEmpty())
        assertTrue(store.pendenti().isEmpty())
        // L'Error non e' inghiottito: arriva al gestore dello scope (che in produzione lo scrive nel log).
        attendi("l'Error al gestore") { sfuggiti.any { it is ErroreFinto } }
    }

    @Test
    fun `un Error dopo che il server ha ricevuto un file chiude la diretta anche sul server`() {
        store = DirettaStore(preferenze(context, "diretta_limiti_error_dopo"))
        val d = nuovaDiretta(server())

        d.accendi("g-1", "Padel", foto(1, regole = regoleCheLanciano(dopo = 1) { ErroreFinto() }))
        attendi("il primo file al server") { finto!!.quante("/rest/v1/rpc/publish_live_from_app") == 1 }
        d.cambiata(foto(2, regole = regoleCheLanciano { ErroreFinto() }))

        attendi("lo stato fermo") { d.stato.value.motivo == DirettaMotivo.ERRORE_GRAVE }
        attendi("la chiusura senza risultato") { chiusure.isNotEmpty() }
        assertEquals(listOf(DirettaChiusuraRichiesta(UUID_PARTITA, "g-1", conRisultato = false)), chiusure.toList())
    }

    // --- Eccezione persistente (debito 10) ---

    @Test
    fun `la stessa eccezione di fila ha un tetto, si ferma con un nome mai vuoto anche per una classe anonima`() {
        store = DirettaStore(preferenze(context, "diretta_limiti_tetto"))
        val d = nuovaDiretta(server(), maxEccezioni = 3)

        d.accendi("g-1", "Padel", foto(1, regole = regoleCheLanciano { object : RuntimeException("anonima") {} }))

        // Prima del tetto aspetta e riprova, e il nome c'e'.
        attendi("l'attesa") { d.stato.value.motivo == DirettaMotivo.ERRORE_INATTESO }
        assertTrue(
            "nome vuoto",
            d.stato.value.dettaglio
                .orEmpty()
                .isNotEmpty(),
        )
        // Al terzo giro di fila si ferma e chiude.
        attendi("la fermata") { d.stato.value.fase == DirettaFase.FERMA }
        assertEquals(DirettaMotivo.ERRORE_GRAVE, d.stato.value.motivo)
        assertTrue(
            d.stato.value.dettaglio
                .orEmpty()
                .isNotEmpty(),
        )
        // Mai arrivato un file al server: niente da chiudere li'.
        Thread.sleep(200)
        assertTrue(chiusure.isEmpty())
        assertFalse(d.acceso)
    }

    // --- Punteggio per la notifica ---

    @Test
    fun `il punteggio per la notifica viene dal motore del file ed e' vuoto prima del primo giro e dopo la chiusura`() {
        store = DirettaStore(preferenze(context, "diretta_limiti_punteggio"))
        val d = nuovaDiretta(server())
        assertEquals("", d.punteggio.value)

        d.accendi("g-1", "Padel", foto(2))
        attendi("il punteggio") { d.punteggio.value.isNotEmpty() }
        assertTrue(d.punteggio.value, d.punteggio.value.startsWith("30-0"))

        d.cambiata(foto(3))
        attendi("il punto dopo") { d.punteggio.value.startsWith("40-0") }

        d.spegni()
        assertEquals("", d.punteggio.value)
    }

    @Test
    fun `punteggioInTesto mette il punto e dietro il dettaglio, e tace se non c'e' niente`() {
        assertEquals("40-15 · 6-4 · 3-2", punteggioInTesto(ScoreDisplay("40", "6-4 · 3-2", "15", null)))
        assertEquals("2-1", punteggioInTesto(ScoreDisplay("2", null, "1", null)))
        assertEquals("", punteggioInTesto(ScoreDisplay("", null, "", null)))
    }
}
