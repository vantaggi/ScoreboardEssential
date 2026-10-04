package it.vantaggi.scoreboardessential

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.UUID

/**
 * Il protocollo v2 lato telefono: le intenzioni che l'orologio manda con MessageClient arrivano
 * all'interfaccia come broadcast locali, una sola volta ciascuna.
 *
 * La memoria dell'ultima sequenza e' statica (sopravvive alla ricreazione del servizio) e quindi
 * anche fra un test e l'altro: ogni test usa nodi con un id nuovo, altrimenti dipenderebbe
 * dall'ordine in cui girano.
 */
@RunWith(RobolectricTestRunner::class)
class ProtocolloV2DelTelefonoTest {
    private val azioni =
        listOf(
            SimplifiedDataLayerListenerService.ACTION_SCORE_INTENT,
            SimplifiedDataLayerListenerService.ACTION_SPORT_INTENT,
            SimplifiedDataLayerListenerService.ACTION_INTENT_BATCH,
        )

    private val ricevuti = mutableListOf<Intent>()
    private lateinit var manager: LocalBroadcastManager
    private lateinit var service: SimplifiedDataLayerListenerService
    private lateinit var nodoA: String
    private lateinit var nodoB: String

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                ricevuti.add(intent)
            }
        }

    @Before
    fun setup() {
        nodoA = "orologio-A-${UUID.randomUUID()}"
        nodoB = "orologio-B-${UUID.randomUUID()}"
        service = Robolectric.buildService(SimplifiedDataLayerListenerService::class.java).get()
        manager = LocalBroadcastManager.getInstance(ApplicationProvider.getApplicationContext())
        // Singleton di processo: i ricevitori che altri test hanno lasciato (i MainViewModel non
        // passano da onCleared) farebbero credere al servizio che qualcuno ascolti anche qui.
        val azioniRegistrate = LocalBroadcastManager::class.java.getDeclaredField("mActions")
        azioniRegistrate.isAccessible = true
        (azioniRegistrate.get(manager) as MutableMap<*, *>).clear()
        val filtro = IntentFilter().apply { azioni.forEach { addAction(it) } }
        manager.registerReceiver(receiver, filtro)
    }

    @After
    fun tearDown() {
        manager.unregisterReceiver(receiver)
    }

    private fun messaggio(
        nodo: String,
        path: String,
        dati: DataMap,
    ): MessageEvent {
        val evento = mock(MessageEvent::class.java)
        `when`(evento.path).thenReturn(path)
        `when`(evento.sourceNodeId).thenReturn(nodo)
        `when`(evento.data).thenReturn(dati.toByteArray())
        return evento
    }

    private fun tocco(
        nodo: String,
        seq: Long,
        lato: Int = 1,
    ): MessageEvent =
        messaggio(
            nodo,
            WearConstants.MSG_SCORE_INTENT,
            DataMap().apply {
                putInt(WearConstants.KEY_SIDE, lato)
                putLong(WearConstants.KEY_SEQ, seq)
            },
        )

    private fun cambioSport(
        nodo: String,
        seq: Long,
        sportId: String = "padel",
    ): MessageEvent =
        messaggio(
            nodo,
            WearConstants.MSG_SPORT_INTENT,
            DataMap().apply {
                putString(WearConstants.KEY_SPORT_ID, sportId)
                putLong(WearConstants.KEY_SEQ, seq)
            },
        )

    private fun arretrato(
        nodo: String,
        seq: Long,
    ): MessageEvent =
        messaggio(
            nodo,
            WearConstants.MSG_INTENT_BATCH,
            DataMap().apply {
                putString(WearConstants.KEY_INTENT_BATCH, "1,2,1")
                putLong(WearConstants.KEY_SEQ, seq)
            },
        )

    /** Consegna i messaggi al servizio e svuota la coda del looper, dove LocalBroadcastManager recapita. */
    private fun consegna(vararg messaggi: MessageEvent) {
        messaggi.forEach { service.onMessageReceived(it) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun lati(): List<Int> =
        ricevuti
            .filter { it.action == SimplifiedDataLayerListenerService.ACTION_SCORE_INTENT }
            .map { it.getIntExtra(WearConstants.KEY_SIDE, -1) }

    @Test
    fun `una sequenza crescente viene accettata tutta, nell'ordine`() {
        consegna(tocco(nodoA, 1, lato = 1), tocco(nodoA, 2, lato = 2), tocco(nodoA, 3, lato = 1))

        assertEquals(listOf(1, 2, 1), lati())
    }

    @Test
    fun `il tocco inoltrato porta lato, tipo e ora dell'orologio`() {
        val dati =
            DataMap().apply {
                putInt(WearConstants.KEY_SIDE, 2)
                putLong(WearConstants.KEY_SEQ, 1L)
                putString(WearConstants.KEY_INTENT_KIND, WearConstants.INTENT_CORRECTION)
                putLong(WearConstants.KEY_AT_MILLIS, 123_456L)
            }
        consegna(messaggio(nodoA, WearConstants.MSG_SCORE_INTENT, dati))

        assertEquals(1, ricevuti.size)
        with(ricevuti[0]) {
            assertEquals(2, getIntExtra(WearConstants.KEY_SIDE, -1))
            assertEquals(WearConstants.INTENT_CORRECTION, getStringExtra(WearConstants.KEY_INTENT_KIND))
            assertEquals(123_456L, getLongExtra(WearConstants.KEY_AT_MILLIS, -1L))
        }
    }

    @Test
    fun `una sequenza ripetuta viene scartata`() {
        consegna(tocco(nodoA, 4), tocco(nodoA, 4), tocco(nodoA, 4))

        assertEquals("riconsegnato tre volte, applicato una", 1, ricevuti.size)
    }

    @Test
    fun `un cambio sport e un arretrato riconsegnati vengono applicati una volta sola`() {
        consegna(cambioSport(nodoA, 1), cambioSport(nodoA, 1))
        consegna(arretrato(nodoB, 1), arretrato(nodoB, 1))

        assertEquals(
            listOf(
                SimplifiedDataLayerListenerService.ACTION_SPORT_INTENT,
                SimplifiedDataLayerListenerService.ACTION_INTENT_BATCH,
            ),
            ricevuti.map { it.action },
        )
    }

    @Test
    fun `una sequenza piu' vecchia dell'ultima viene scartata`() {
        consegna(tocco(nodoA, 5), tocco(nodoA, 3), tocco(nodoA, 6))

        // Il 3 e' arrivato dopo il 5: era un messaggio invertito o riconsegnato.
        assertEquals(2, ricevuti.size)
    }

    @Test
    fun `due orologi hanno contatori indipendenti`() {
        consegna(tocco(nodoA, 7, lato = 1))
        // Il secondo orologio parte da 1: con un contatore unico verrebbe scartato in blocco.
        consegna(tocco(nodoB, 1, lato = 2), tocco(nodoB, 2, lato = 2))
        // E ognuno continua a scartare le proprie ripetizioni, non quelle dell'altro.
        consegna(tocco(nodoA, 7, lato = 1), tocco(nodoB, 2, lato = 2), tocco(nodoA, 8, lato = 1))

        assertEquals(listOf(1, 2, 2, 1), lati())
    }

    @Test
    fun `la memoria dell'ultima sequenza sopravvive alla ricreazione del servizio`() {
        consegna(tocco(nodoA, 2))
        // Il sistema distrugge e ricrea il servizio fra un messaggio e l'altro.
        service = Robolectric.buildService(SimplifiedDataLayerListenerService::class.java).get()
        consegna(tocco(nodoA, 2), tocco(nodoA, 3))

        assertEquals(2, ricevuti.size)
    }

    @Test
    fun `tocco, cambio sport e arretrato condividono la stessa sequenza per nodo`() {
        consegna(tocco(nodoA, 5))
        // Un cambio sport con sequenza gia' coperta dal tocco 5 e' una riconsegna, non un evento nuovo.
        consegna(cambioSport(nodoA, 5), cambioSport(nodoA, 4), arretrato(nodoA, 5))
        assertEquals(1, ricevuti.size)

        consegna(cambioSport(nodoA, 6, "tennis"), arretrato(nodoA, 7))
        assertEquals(
            listOf(
                SimplifiedDataLayerListenerService.ACTION_SCORE_INTENT,
                SimplifiedDataLayerListenerService.ACTION_SPORT_INTENT,
                SimplifiedDataLayerListenerService.ACTION_INTENT_BATCH,
            ),
            ricevuti.map { it.action },
        )
        assertEquals("tennis", ricevuti[1].getStringExtra(WearConstants.KEY_SPORT_ID))
        assertEquals("1,2,1", ricevuti[2].getStringExtra(WearConstants.KEY_INTENT_BATCH))
        assertEquals(7L, ricevuti[2].getLongExtra(WearConstants.KEY_SEQ, -1L))
    }

    @Test
    fun `sequenza zero o negativa e lato non valido vengono scartati`() {
        // La guardia `seq <= 0` del servizio e' ridondante: anche senza, 0 e i negativi non superano
        // `seq <= ultima`, perche' la memoria parte da 0 e conserva solo sequenze positive. Per le
        // sequenze questo test documenta il contratto, non lo protegge. Quello che protegge e' il
        // lato non valido: lo scarto deve avvenire PRIMA di aggiornare la sequenza del nodo.
        consegna(tocco(nodoA, 0), tocco(nodoA, -3), tocco(nodoA, 1, lato = 3), tocco(nodoA, 1, lato = 0))

        assertEquals(0, ricevuti.size)

        // Gli scarti non hanno consumato la sequenza 1.
        consegna(tocco(nodoA, 1, lato = 1))
        assertEquals(1, ricevuti.size)
    }

    // --- L5: senza ricevitori, il servizio non consuma e non perde ---

    private class Risposta(
        val nodo: String,
        val path: String,
        val dati: DataMap,
    )

    private val risposte = mutableListOf<Risposta>()
    private val rispondiDiProduzione = SimplifiedDataLayerListenerService.rispondi

    private fun catturaLeRisposte() {
        SimplifiedDataLayerListenerService.rispondi = { _, nodo, path, dati ->
            risposte += Risposta(nodo, path, DataMap.fromByteArray(dati))
        }
    }

    private fun arretratoConId(
        nodo: String,
        seq: Long,
        batchId: Long,
        base: String? = "0",
    ): MessageEvent =
        messaggio(
            nodo,
            WearConstants.MSG_INTENT_BATCH,
            DataMap().apply {
                putString(WearConstants.KEY_INTENT_BATCH, "point,1,1")
                putLong(WearConstants.KEY_SEQ, seq)
                putLong(WearConstants.KEY_BATCH_ID, batchId)
                if (base != null) putString(WearConstants.KEY_BATCH_BASE, base)
            },
        )

    @Test
    fun `il batch inoltrato porta id, base e nodo`() {
        consegna(arretratoConId(nodoA, 3, 900L, base = "2:123"))

        with(ricevuti.single()) {
            assertEquals(900L, getLongExtra(WearConstants.KEY_BATCH_ID, 0L))
            assertEquals("2:123", getStringExtra(WearConstants.KEY_BATCH_BASE))
            assertEquals(nodoA, getStringExtra(SimplifiedDataLayerListenerService.EXTRA_NODE_ID))
        }
    }

    @Test
    fun `un batch senza id ne' base (orologio non aggiornato) passa senza base`() {
        consegna(arretrato(nodoA, 3))

        with(ricevuti.single()) {
            assertEquals(0L, getLongExtra(WearConstants.KEY_BATCH_ID, 0L))
            assertEquals(null, getStringExtra(WearConstants.KEY_BATCH_BASE))
            assertEquals("senza uuid il ViewModel non controlla l'identita'", null, getStringExtra(WearConstants.KEY_MATCH_UUID))
        }
    }

    /** L5 D1: l'identita' della partita su cui l'orologio ha calcolato arriva al ViewModel. */
    @Test
    fun `il batch inoltrato porta l'identita' della partita`() {
        consegna(
            messaggio(
                nodoA,
                WearConstants.MSG_INTENT_BATCH,
                DataMap().apply {
                    putString(WearConstants.KEY_INTENT_BATCH, "point,1,1")
                    putLong(WearConstants.KEY_SEQ, 3L)
                    putLong(WearConstants.KEY_BATCH_ID, 902L)
                    putString(WearConstants.KEY_BATCH_BASE, "0")
                    putString(WearConstants.KEY_MATCH_UUID, "partita-A")
                },
            ),
        )

        assertEquals("partita-A", ricevuti.single().getStringExtra(WearConstants.KEY_MATCH_UUID))
    }

    /** Rilievo 2: l'app chiusa non deve consumare la sequenza, o il rinvio verrebbe scartato come "gia' visto". */
    @Test
    fun `un batch senza ricevitori non consuma la sequenza e il telefono dice di riprovare`() {
        catturaLeRisposte()
        try {
            manager.unregisterReceiver(receiver)
            consegna(arretratoConId(nodoA, 5, 901L))
            assertEquals("nessuno l'ha applicato", 0, ricevuti.size)
            val nack = risposte.single()
            assertEquals(WearConstants.MSG_BATCH_NACK, nack.path)
            assertEquals(nodoA, nack.nodo)
            assertEquals(WearConstants.NACK_RETRY, nack.dati.getString(WearConstants.KEY_BATCH_NACK_REASON))
            assertEquals(901L, nack.dati.getLong(WearConstants.KEY_BATCH_ID))

            // L'app torna: lo stesso arretrato, rinviato con la STESSA sequenza, passa.
            manager.registerReceiver(receiver, IntentFilter().apply { azioni.forEach { addAction(it) } })
            consegna(arretratoConId(nodoA, 5, 901L))
            assertEquals(1, ricevuti.size)
        } finally {
            SimplifiedDataLayerListenerService.rispondi = rispondiDiProduzione
        }
    }

    @Test
    fun `un batch preso da un ricevitore non riceve nessun NACK dal servizio`() {
        catturaLeRisposte()
        try {
            consegna(arretratoConId(nodoA, 5, 902L))

            assertEquals(1, ricevuti.size)
            assertEquals("a rispondere e' il ViewModel, quando ha applicato", 0, risposte.size)
        } finally {
            SimplifiedDataLayerListenerService.rispondi = rispondiDiProduzione
        }
    }

    /** Rilievo 5: un tocco arrivato senza ViewModel non si perde, si mette da parte per quando nasce. */
    @Test
    fun `un tocco senza ricevitori viene messo da parte per il ViewModel`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        IntentiInAttesa(app).prendiTutte()
        manager.unregisterReceiver(receiver)

        consegna(tocco(nodoA, 1, lato = 2))

        val messe = IntentiInAttesa(app).prendiTutte()
        assertEquals(1, messe.size)
        assertEquals(2, messe[0].side)
        assertEquals(WearConstants.INTENT_POINT, messe[0].kind)
    }

    /** L5 custodia: il polso deve sapere che il tocco e' da parte, o dice NON CONFERMATO e l'utente lo ripete. */
    @Test
    fun `un tocco messo da parte viene detto all'orologio con la sua sequenza`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        IntentiInAttesa(app).prendiTutte()
        catturaLeRisposte()
        try {
            manager.unregisterReceiver(receiver)

            consegna(tocco(nodoA, 41, lato = 2))

            val custodia = risposte.single()
            assertEquals(WearConstants.MSG_INTENT_CUSTODIA, custodia.path)
            assertEquals(nodoA, custodia.nodo)
            assertEquals(41L, custodia.dati.getLong(WearConstants.KEY_SEQ))
        } finally {
            SimplifiedDataLayerListenerService.rispondi = rispondiDiProduzione
            IntentiInAttesa(app).prendiTutte()
        }
    }

    @Test
    fun `un tocco preso da un ricevitore non riceve la custodia, risponde lo stato`() {
        catturaLeRisposte()
        try {
            consegna(tocco(nodoA, 42))

            assertEquals(1, ricevuti.size)
            assertEquals(0, risposte.size)
        } finally {
            SimplifiedDataLayerListenerService.rispondi = rispondiDiProduzione
        }
    }

    @Test
    fun `una chiusura senza ricevitori non e' in custodia, perche' non e' stata messa da parte`() {
        catturaLeRisposte()
        try {
            manager.unregisterReceiver(receiver)

            consegna(chiusura(nodoA, 43))

            assertEquals(0, risposte.size)
        } finally {
            SimplifiedDataLayerListenerService.rispondi = rispondiDiProduzione
        }
    }

    /** Oltre il tetto il tocco non e' da parte: dire "in custodia" sarebbe una bugia, il polso dice NON CONFERMATO. */
    @Test
    fun `oltre il tetto il tocco non e' in custodia e l'orologio non riceve risposta`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val coda = IntentiInAttesa(app)
        coda.prendiTutte()
        repeat(IntentiInAttesa.MASSIMO) { coda.aggiungi(WearConstants.INTENT_POINT, 1, System.currentTimeMillis()) }
        catturaLeRisposte()
        try {
            manager.unregisterReceiver(receiver)

            consegna(tocco(nodoA, 44))

            assertEquals(0, risposte.size)
            assertEquals(IntentiInAttesa.MASSIMO, coda.prendiTutte().size)
        } finally {
            SimplifiedDataLayerListenerService.rispondi = rispondiDiProduzione
            coda.prendiTutte()
        }
    }

    @Test
    fun `con un ricevitore il tocco non si mette da parte, ci pensa il ViewModel`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        IntentiInAttesa(app).prendiTutte()

        consegna(tocco(nodoA, 1, lato = 2))

        assertEquals(1, ricevuti.size)
        assertEquals(emptyList<IntentiInAttesa.Voce>(), IntentiInAttesa(app).prendiTutte())
    }

    @Test
    fun `una chiusura senza ricevitori non si mette da parte, arrivata ore dopo chiuderebbe un'altra partita`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        IntentiInAttesa(app).prendiTutte()
        manager.unregisterReceiver(receiver)

        consegna(chiusura(nodoA, 1))

        assertEquals(emptyList<IntentiInAttesa.Voce>(), IntentiInAttesa(app).prendiTutte())
    }

    @Test
    fun `i tocchi messi da parte hanno un tetto e una validita'`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val coda = IntentiInAttesa(app)
        coda.prendiTutte()
        val adesso = 1_700_000_000_000L
        coda.aggiungi(WearConstants.INTENT_POINT, 1, adesso - IntentiInAttesa.VALIDITA_MS - 1)
        coda.aggiungi(WearConstants.INTENT_POINT, 2, adesso - 1000)

        // Il tocco di ieri non cade nella partita di oggi.
        assertEquals(listOf(2), coda.prendiTutte(adesso).map { it.side })

        repeat(IntentiInAttesa.MASSIMO + 10) { coda.aggiungi(WearConstants.INTENT_POINT, 1, adesso) }
        assertEquals(IntentiInAttesa.MASSIMO, coda.prendiTutte(adesso).size)
    }

    /** L5 L4: oltre il tetto la voce non entra e chi chiama lo sa, invece di scartarla in silenzio. */
    @Test
    fun `oltre il tetto aggiungi dice di no e non scrive`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val coda = IntentiInAttesa(app)
        coda.prendiTutte()
        val adesso = 1_700_000_000_000L
        repeat(IntentiInAttesa.MASSIMO) { assertTrue(coda.aggiungi(WearConstants.INTENT_POINT, 1, adesso)) }

        assertFalse("la 201esima non entra", coda.aggiungi(WearConstants.INTENT_POINT, 2, adesso))

        val prese = coda.prendiTutte(adesso)
        assertEquals(IntentiInAttesa.MASSIMO, prese.size)
        assertTrue("nessuna voce del lato 2", prese.all { it.side == 1 })
    }

    /**
     * L5 L4: servizio e ViewModel hanno ognuno la propria istanza sulle stesse preferenze, quindi il
     * lock deve valere per processo. Un thread che tiene il lock condiviso blocca un'ALTRA istanza:
     * con `@Synchronized` di metodo (per istanza) la seconda passerebbe e i due leggi-modifica-scrivi
     * si intreccerebbero.
     */
    @Test
    fun `due istanze di IntentiInAttesa si escludono a vicenda con un lock condiviso`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        IntentiInAttesa(app).prendiTutte()
        val finito = java.util.concurrent.CountDownLatch(1)
        val altra =
            Thread {
                IntentiInAttesa(app).aggiungi(WearConstants.INTENT_POINT, 1, System.currentTimeMillis())
                finito.countDown()
            }

        synchronized(IntentiInAttesa.lock) {
            altra.start()
            assertFalse("l'altra istanza deve aspettare il lock", finito.await(300, java.util.concurrent.TimeUnit.MILLISECONDS))
        }
        assertTrue("rilasciato il lock, l'altra finisce", finito.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(1, IntentiInAttesa(app).prendiTutte().size)
    }

    private fun chiusura(
        nodo: String,
        seq: Long,
    ): MessageEvent =
        messaggio(
            nodo,
            WearConstants.MSG_SCORE_INTENT,
            DataMap().apply {
                // Nessun lato: la chiusura non riguarda una squadra.
                putString(WearConstants.KEY_INTENT_KIND, WearConstants.INTENT_END_MATCH)
                putLong(WearConstants.KEY_SEQ, seq)
            },
        )

    /** L4: la chiusura passa senza lato, una volta sola per sequenza, e con la sequenza dei punti. */
    @Test
    fun `la chiusura della partita passa senza lato e una sola volta per sequenza`() {
        consegna(tocco(nodoA, 1), chiusura(nodoA, 2), chiusura(nodoA, 2), tocco(nodoA, 2))

        assertEquals(
            listOf(WearConstants.INTENT_POINT, WearConstants.INTENT_END_MATCH),
            ricevuti.map { it.getStringExtra(WearConstants.KEY_INTENT_KIND) },
        )
        // Una sequenza gia' vista non si riapplica nemmeno da un messaggio di altro tipo.
        consegna(chiusura(nodoA, 1))
        assertEquals(2, ricevuti.size)
    }
}
