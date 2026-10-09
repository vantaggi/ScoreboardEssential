package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.ExportOrigin
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchPlayer
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/**
 * La diretta (D-2) contro il contratto del server (migrazione 67): conflazione, serie, errori letti dal `message`
 * anche con HTTP 500, file parziale coerente, ripresa e silenzio dopo la chiusura. Server finto, mai quello vero.
 */
@RunWith(AndroidJUnit4::class)
class DirettaTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var finto: ServerFinto? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val chiusure = mutableListOf<DirettaChiusuraRichiesta>()
    private lateinit var diretta: Diretta
    private lateinit var store: DirettaStore

    @After
    fun chiudi() {
        scope.cancel()
        finto?.chiudi()
    }

    private val inviiAlServer get() = finto!!.quante("/rest/v1/rpc/publish_live_from_app")

    private fun corpi() =
        finto!!
            .richieste
            .filter { it.path == "/rest/v1/rpc/publish_live_from_app" }
            .map { JSONObject(it.body.clone().readUtf8()) }

    private fun payloadDi(corpo: JSONObject) = corpo.getJSONObject("p_payload")

    /** Una diretta con sessione valida, intervallo e ritentativi accorciati (i test non aspettano un secondo vero per volta). */
    private fun accendiIlServer(
        intervalloMs: Long = 100,
        risposte: (RecordedRequest, Int) -> MockResponse = { _, _ -> rispostaDiPubblicazione() },
    ): Diretta {
        val s =
            ServerFinto { richiesta, n ->
                if (richiesta.path!!.contains("refresh_token")) json(400, """{"error":"invalid_grant"}""") else risposte(richiesta, n)
            }.also { finto = it }
        val sessione = SessionStore(preferenze(context, "sessione_diretta"), SoftwareSecretBox())
        sessione.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), sessione)
        store = DirettaStore(preferenze(context, "diretta_salvata"))
        chiusure.clear()
        diretta =
            Diretta(
                account,
                store,
                scope,
                collegamenti = { mapOf(1 to 101) },
                allaChiusura = { chiusure += it },
                minIntervalMs = intervalloMs,
                retryBaseMs = 60,
                retryLentoMs = 120,
            )
        return diretta
    }

    private fun foto(
        punti: Int,
        giocatori: Int = 4,
        nome: String = "Marco",
        matchId: String = UUID_PARTITA,
    ): DirettaFoto {
        val rose =
            listOf(
                MatchPlayer(1, nome, 1),
                MatchPlayer(2, "Anna", 2),
                MatchPlayer(3, "Luca", 1),
                MatchPlayer(4, "Sara", 2),
            ).take(giocatori)
        return DirettaFoto(
            matchId,
            SportRegistry.forMatch(SportRegistry.PADEL, listOf(1, 2, 3, 4)),
            List(punti) { LoggedEvent(ScoringEvent.Point(side = 1), it * 30_000L) },
            rose,
            ExportOrigin(matchId, 1_790_190_240_000L, ZoneId.of("Europe/Rome"), "1.0"),
        )
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

    @Test
    fun `accesa a inizio partita pubblica il file parziale con la timeline vuota e il vincitore nullo`() {
        val d = accendiIlServer()

        assertTrue(d.accendi("g-1", "Padel", foto(0)))
        attendi("la prima pubblicazione") { inviiAlServer >= 1 }

        val corpo = corpi().first()
        assertEquals("g-1", corpo.getString("p_group"))
        val file = payloadDi(corpo)
        assertEquals(2, file.getInt("formatVersion"))
        assertEquals("padel", file.getString("sportId"))
        assertEquals(UUID_PARTITA, file.getString("matchId"))
        assertEquals(0, file.getJSONArray("timeline").length())
        assertEquals(0, file.getJSONArray("setScores").length())
        assertTrue(file.isNull("winnerTeam"))
        // I collegamenti del gruppo (R-1) viaggiano come nell'invio: il giocatore 1 e' la 101 della dashboard.
        val marco = file.getJSONArray("players").getJSONObject(0)
        assertEquals(101, marco.getInt("padelPlayerId"))
        attendi("lo stato in corso") { d.stato.value.fase == DirettaFase.IN_CORSO }
        assertEquals("Padel", d.stato.value.gruppo)
        assertEquals(DirettaSalvata(UUID_PARTITA, "g-1", "Padel"), store.load())
    }

    @Test
    fun `il file parziale a meta' partita ha i punti finora, i soli set chiusi e nessun vincitore`() {
        val d = accendiIlServer()

        d.accendi("g-1", "Padel", foto(12))
        attendi("la pubblicazione") { inviiAlServer >= 1 }

        val file = payloadDi(corpi().first())
        assertEquals(12, file.getJSONArray("timeline").length())
        assertEquals(0, file.getJSONArray("setScores").length())
        assertTrue(file.isNull("winnerTeam"))
    }

    @Test
    fun `dieci punti in 300 millisecondi sono poche richieste e l'ultima ha tutti i punti`() {
        val d = accendiIlServer(intervalloMs = 300)
        d.accendi("g-1", "Padel", foto(0))
        attendi("la prima pubblicazione") { inviiAlServer >= 1 }

        for (n in 1..10) {
            d.cambiata(foto(n))
            Thread.sleep(30)
        }
        attendi("l'ultimo stato") { corpi().last().let { payloadDi(it).getJSONArray("timeline").length() } == 10 }
        // Il tempo che eventuali richieste in piu' (segnali non fusi) hanno per arrivare: senza conflazione sarebbero undici.
        Thread.sleep(1_000)

        // Una a vuoto, una per l'ultimo stato (al piu' una in mezzo): mai dieci.
        assertTrue("richieste: $inviiAlServer", inviiAlServer in 2..4)
        assertEquals(10, payloadDi(corpi().last()).getJSONArray("timeline").length())
    }

    @Test
    fun `mai due richieste in volo per la stessa partita`() {
        val inVolo = AtomicInteger()
        val massimo = AtomicInteger()
        val d =
            accendiIlServer(intervalloMs = 0) { _, _ ->
                massimo.accumulateAndGet(inVolo.incrementAndGet()) { a, b -> maxOf(a, b) }
                Thread.sleep(120)
                inVolo.decrementAndGet()
                rispostaDiPubblicazione()
            }
        d.accendi("g-1", "Padel", foto(0))
        for (n in 1..6) {
            d.cambiata(foto(n))
            Thread.sleep(40)
        }
        attendi("l'ultimo stato") { corpi().isNotEmpty() && payloadDi(corpi().last()).getJSONArray("timeline").length() == 6 }

        assertEquals("richieste contemporanee", 1, massimo.get())
    }

    @Test
    fun `un file identico al precedente non rifa' la richiesta`() {
        val d = accendiIlServer()
        d.accendi("g-1", "Padel", foto(3))
        attendi("la prima pubblicazione") { inviiAlServer >= 1 }

        d.cambiata(foto(3))
        Thread.sleep(400)

        assertEquals(1, inviiAlServer)
    }

    @Test
    fun `not_authorized con HTTP 500 spegne la diretta e non si ritenta`() {
        val d = accendiIlServer { _, _ -> erroreRpc(500, "P0001", "not_authorized") }

        d.accendi("g-1", "Padel", foto(1))
        attendi("lo stato fermo") { d.stato.value.fase == DirettaFase.FERMA }

        assertEquals(DirettaMotivo.NON_ADMIN, d.stato.value.motivo)
        d.cambiata(foto(2))
        Thread.sleep(300)
        assertEquals(1, inviiAlServer)
        assertNull(store.load())
        // Non sei admin: non c'e' nulla da chiudere.
        assertTrue(chiusure.isEmpty())
    }

    @Test
    fun `live_conflict con HTTP 500 spegne senza chiudere la diretta di un altro admin`() {
        val d = accendiIlServer { _, _ -> erroreRpc(500, "P0001", "live_conflict") }

        d.accendi("g-1", "Padel", foto(1))
        attendi("lo stato fermo") { d.stato.value.fase == DirettaFase.FERMA }

        assertEquals(DirettaMotivo.ALTRO_ADMIN, d.stato.value.motivo)
        assertTrue(chiusure.isEmpty())
    }

    @Test
    fun `invalid_payload spegne col dettaglio e chiude la diretta senza risultato`() {
        val d = accendiIlServer { _, _ -> erroreRpc(500, "22023", "invalid_payload", "players") }

        d.accendi("g-1", "Padel", foto(1))
        attendi("lo stato fermo") { d.stato.value.fase == DirettaFase.FERMA }

        assertEquals(DirettaMotivo.FILE_NON_VALIDO, d.stato.value.motivo)
        assertEquals("players", d.stato.value.dettaglio)
        assertEquals(listOf(DirettaChiusuraRichiesta(UUID_PARTITA, "g-1", conRisultato = false)), chiusure)
    }

    @Test
    fun `payload_too_large spegne`() {
        val d = accendiIlServer { _, _ -> erroreRpc(500, "54000", "payload_too_large") }

        d.accendi("g-1", "Padel", foto(1))
        attendi("lo stato fermo") { d.stato.value.fase == DirettaFase.FERMA }

        assertEquals(DirettaMotivo.FILE_TROPPO_GRANDE, d.stato.value.motivo)
    }

    @Test
    fun `live_full e join_code_exhausted aspettano e riprovano da soli`() {
        val d =
            accendiIlServer { _, n ->
                when (n) {
                    0 -> erroreRpc(500, "54000", "live_full")
                    1 -> erroreRpc(500, "55000", "join_code_exhausted")
                    else -> rispostaDiPubblicazione()
                }
            }

        d.accendi("g-1", "Padel", foto(1))
        attendi("l'attesa per troppe dirette") { d.stato.value.motivo == DirettaMotivo.TROPPE_DIRETTE }
        attendi("l'attesa per il codice") { d.stato.value.motivo == DirettaMotivo.CODICE_ESAURITO }
        attendi("la ripresa") { d.stato.value.fase == DirettaFase.IN_CORSO }

        assertEquals(3, inviiAlServer)
        assertEquals(DirettaSalvata(UUID_PARTITA, "g-1", "Padel"), store.load())
    }

    @Test
    fun `un 500 senza message noto e un conflitto transitorio si ritentano`() {
        val d =
            accendiIlServer { _, n ->
                when (n) {
                    0 -> json(500, "boom")
                    1 -> erroreRpc(500, "23505", "duplicate key value")
                    2 -> erroreRpc(500, "40P01", "deadlock detected")
                    else -> rispostaDiPubblicazione()
                }
            }

        d.accendi("g-1", "Padel", foto(1))
        attendi("la ripresa") { inviiAlServer >= 4 && d.stato.value.fase == DirettaFase.IN_CORSO }

        assertEquals(4, inviiAlServer)
    }

    @Test
    fun `senza rete salta e ripubblica l'ultimo stato quando il server risponde`() {
        var giu = true
        val d = accendiIlServer { _, _ -> if (giu) json(503, "") else rispostaDiPubblicazione() }
        d.accendi("g-1", "Padel", foto(1))
        attendi("l'attesa per la rete") { d.stato.value.motivo == DirettaMotivo.RETE }

        d.cambiata(foto(5))
        giu = false
        attendi("la ripubblicazione") { d.stato.value.fase == DirettaFase.IN_CORSO }

        // Nessuna coda: conta l'ultimo stato, con tutti i cinque punti.
        assertEquals(5, payloadDi(corpi().last()).getJSONArray("timeline").length())
    }

    @Test
    fun `una diretta gia' chiusa sul server (written falso e ended) si ferma per sempre`() {
        val d = accendiIlServer { _, _ -> rispostaDiPubblicazione(scritto = false, stato = "ended") }

        d.accendi("g-1", "Padel", foto(1))
        attendi("lo stato fermo") { d.stato.value.fase == DirettaFase.FERMA }

        assertEquals(DirettaMotivo.GIA_CHIUSA, d.stato.value.motivo)
        assertNull(store.load())
    }

    @Test
    fun `written falso su una diretta ancora live e' il file identico e continua`() {
        val d = accendiIlServer { _, _ -> rispostaDiPubblicazione(scritto = false, stato = "live") }

        d.accendi("g-1", "Padel", foto(1))
        attendi("la pubblicazione") { inviiAlServer >= 1 }
        d.cambiata(foto(2))
        attendi("la seconda") { inviiAlServer >= 2 }

        assertEquals(DirettaFase.IN_CORSO, d.stato.value.fase)
    }

    @Test
    fun `con meno di quattro giocatori non trasmette e lo dice, poi parte appena sono quattro`() {
        val d = accendiIlServer()

        d.accendi("g-1", "Padel", foto(0, giocatori = 3))
        attendi("l'attesa dei giocatori") { d.stato.value.motivo == DirettaMotivo.SERVONO_QUATTRO }
        assertEquals(0, inviiAlServer)

        d.cambiata(foto(0))
        attendi("la pubblicazione") { inviiAlServer == 1 }
        assertEquals(DirettaFase.IN_CORSO, d.stato.value.fase)
    }

    @Test
    fun `un nome oltre i 60 caratteri non si trasmette, non si taglia, e lo dice`() {
        val d = accendiIlServer()
        val lungo = "M".repeat(61)

        d.accendi("g-1", "Padel", foto(1, nome = lungo))
        attendi("l'attesa del nome") { d.stato.value.motivo == DirettaMotivo.NOME_TROPPO_LUNGO }

        assertEquals(0, inviiAlServer)
        assertEquals("M".repeat(24), d.stato.value.dettaglio)
        // Accorciato il nome, la diretta riprende da sola.
        d.cambiata(foto(1, nome = "M".repeat(60)))
        attendi("la pubblicazione") { inviiAlServer == 1 }
    }

    @Test
    fun `dopo la chiusura nessuna pubblicazione per quella partita e la chiusura e' consegnata`() {
        val d = accendiIlServer()
        d.accendi("g-1", "Padel", foto(1))
        attendi("la prima pubblicazione") { inviiAlServer >= 1 }

        d.chiudi(conRisultato = true)
        val prima = inviiAlServer
        d.cambiata(foto(2))
        d.cambiata(foto(3))
        Thread.sleep(400)

        assertEquals(prima, inviiAlServer)
        assertEquals(DirettaFase.CHIUSA, d.stato.value.fase)
        assertEquals(listOf(DirettaChiusuraRichiesta(UUID_PARTITA, "g-1", conRisultato = true)), chiusure)
        assertNull(store.load())
        // La chiusura e' definitiva: per la stessa partita non si riapre; per un'altra si'.
        assertFalse(d.accendi("g-1", "Padel", foto(2)))
        assertTrue(d.accendi("g-1", "Padel", foto(0, matchId = "altra-partita")))
    }

    @Test
    fun `spegnere a mano chiude senza risultato`() {
        val d = accendiIlServer()
        d.accendi("g-1", "Padel", foto(1))

        d.spegni()

        assertEquals(listOf(DirettaChiusuraRichiesta(UUID_PARTITA, "g-1", conRisultato = false)), chiusure)
        assertEquals(DirettaFase.CHIUSA, d.stato.value.fase)
    }

    @Test
    fun `al ripristino la diretta accesa per la stessa partita riprende`() {
        val d = accendiIlServer()
        store.save(DirettaSalvata(UUID_PARTITA, "g-1", "Padel"))

        d.riprendi(foto(7))
        attendi("la pubblicazione") { inviiAlServer >= 1 }

        assertEquals(7, payloadDi(corpi().first()).getJSONArray("timeline").length())
        assertEquals(DirettaFase.IN_CORSO, d.stato.value.fase)
        assertEquals("Padel", d.stato.value.gruppo)
    }

    @Test
    fun `al ripristino una diretta salvata per un'altra partita si chiude senza risultato e non trasmette`() {
        val d = accendiIlServer()
        store.save(DirettaSalvata("partita-persa", "g-1", "Padel"))

        d.riprendi(foto(7))
        Thread.sleep(300)

        assertEquals(0, inviiAlServer)
        assertEquals(listOf(DirettaChiusuraRichiesta("partita-persa", "g-1", conRisultato = false)), chiusure)
        assertNull(store.load())
    }

    @Test
    fun `senza una diretta salvata il ripristino non fa niente`() {
        val d = accendiIlServer()

        d.riprendi(foto(7))
        d.riprendi(null)
        Thread.sleep(200)

        assertEquals(0, inviiAlServer)
        assertTrue(chiusure.isEmpty())
        assertEquals(DirettaFase.SPENTA, d.stato.value.fase)
    }
}
