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

    private fun lati() = ricevuti.filter { it.action == SimplifiedDataLayerListenerService.ACTION_SCORE_INTENT }.map {
        it.getIntExtra(WearConstants.KEY_SIDE, -1)
    }

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
        consegna(tocco(nodoA, 0), tocco(nodoA, -3), tocco(nodoA, 1, lato = 3), tocco(nodoA, 1, lato = 0))

        assertEquals(0, ricevuti.size)

        // Gli scarti non hanno consumato la sequenza 1.
        consegna(tocco(nodoA, 1, lato = 1))
        assertEquals(1, ricevuti.size)
    }
}
