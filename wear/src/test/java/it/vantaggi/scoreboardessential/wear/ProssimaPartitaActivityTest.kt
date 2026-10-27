package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.Vibrator
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.material.button.MaterialButton
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.util.Collections

/**
 * La schermata della prossima partita: cosa chiede al telefono e come legge la risposta.
 *
 * Rosso se due tocchi non diventano lo scambio di quei due posti sulla base vista, se la risposta del
 * telefono non ridisegna i nomi, se senza risposta la schermata non lo dice, o se Inizia non torna al
 * quadrante quando il telefono conferma che la partita e' a tabellone.
 */
@RunWith(RobolectricTestRunner::class)
class ProssimaPartitaActivityTest {
    private val mandati: MutableList<Pair<String, DataMap>> = Collections.synchronizedList(mutableListOf())

    @After
    fun ripristina() {
        ProssimaPartitaActivity.creaSync = { OptimizedWearDataSync(it) }
    }

    /** Il canale vero con i client GMS finti; i messaggi spediti finiscono in [mandati]. */
    private fun telefono(collegato: Boolean): (Context) -> OptimizedWearDataSync =
        { contesto ->
            val nodo = Mockito.mock(Node::class.java)
            Mockito.`when`(nodo.id).thenReturn("telefono")
            val info = Mockito.mock(CapabilityInfo::class.java)
            Mockito.`when`(info.nodes).thenReturn(if (collegato) setOf(nodo) else emptySet())
            val capability = Mockito.mock(CapabilityClient::class.java)
            Mockito
                .`when`(capability.getCapability(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(Tasks.forResult(info))
            val nodi = Mockito.mock(NodeClient::class.java)
            Mockito.`when`(nodi.connectedNodes).thenReturn(Tasks.forResult(if (collegato) listOf(nodo) else emptyList()))
            val messaggi = Mockito.mock(MessageClient::class.java)
            Mockito
                .`when`(messaggi.sendMessage(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer { invocazione ->
                    mandati += invocazione.getArgument<String>(1) to DataMap.fromByteArray(invocazione.getArgument(2))
                    Tasks.forResult(1)
                }
            OptimizedWearDataSync(contesto, Mockito.mock(DataClient::class.java), messaggi, capability, nodi)
        }

    private val coppie =
        CoppieDalTelefono(
            numero = 2,
            giocatori = listOf("Vantaggi", "Bianchi", "Glorio", "Rossi"),
            base = "1,4/2,3",
            sigle = listOf("VAN/BIA", "GLO/ROS"),
            panchina = listOf("Verdi"),
            versione = 10L,
        )

    private fun apri(collegato: Boolean = true): ProssimaPartitaActivity {
        ProssimaPartitaActivity.creaSync = telefono(collegato)
        val intent = ProssimaPartitaActivity.intent(RuntimeEnvironment.getApplication(), coppie, 0xFFC8F135.toInt(), 0xFF00E5FF.toInt())
        return Robolectric.buildActivity(ProssimaPartitaActivity::class.java, intent).setup().get()
    }

    private fun riga(
        activity: ProssimaPartitaActivity,
        posto: Int,
    ): View = activity.findViewById<LinearLayout>(if (posto < 2) R.id.prossima_coppia1 else R.id.prossima_coppia2).getChildAt(posto % 2)

    private fun nome(
        activity: ProssimaPartitaActivity,
        posto: Int,
    ) = riga(activity, posto).findViewById<TextView>(R.id.player_name).text.toString()

    private fun avviso(activity: ProssimaPartitaActivity) = activity.findViewById<TextView>(R.id.prossima_avviso).text.toString()

    /** L'invio passa da Dispatchers.IO, un thread vero: si fa girare il looper finche' arriva, con un limite. */
    private fun aspetta(condizione: () -> Boolean) {
        val limite = System.currentTimeMillis() + 5_000
        while (!condizione() && System.currentTimeMillis() < limite) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("la condizione non si e' avverata", condizione())
    }

    private fun rispondi(nuove: CoppieDalTelefono) {
        LocalBroadcastManager.getInstance(RuntimeEnvironment.getApplication()).sendBroadcast(
            Intent(WearDataLayerService.ACTION_PAIRS_UPDATE)
                .putExtra(WearDataLayerService.EXTRA_PAIRS_PAYLOAD, ProssimaPartitaActivity.comeDataMap(nuove).toByteArray()),
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `mostra il numero, le coppie in ordine dei posti e chi resta fuori`() {
        val activity = apri()

        assertEquals(
            activity.getString(R.string.wear_next_match_title, 2),
            activity.findViewById<TextView>(R.id.prossima_titolo).text.toString(),
        )
        assertEquals(listOf("Vantaggi", "Bianchi", "Glorio", "Rossi"), (0..3).map { nome(activity, it) })
        assertEquals("VAN/BIA", activity.findViewById<TextView>(R.id.prossima_sigla1).text.toString())
        assertEquals(
            activity.getString(R.string.wear_next_bench, "Verdi"),
            activity.findViewById<TextView>(R.id.prossima_panchina).text.toString(),
        )
        assertEquals(activity.getString(R.string.wear_next_hint), avviso(activity))
    }

    @Test
    fun `due tocchi chiedono lo scambio di quei posti sulla base vista, e la risposta ridisegna i nomi`() {
        val activity = apri()

        riga(activity, 1).performClick()
        assertEquals(activity.getString(R.string.wear_next_pick, "Bianchi"), avviso(activity))
        assertTrue("la riga scelta lo dice", riga(activity, 1).isSelected)
        riga(activity, 2).performClick()
        aspetta { mandati.isNotEmpty() }

        val (path, dati) = mandati.single()
        assertEquals(WearConstants.MSG_SERATA_INTENT, path)
        assertEquals(WearConstants.SERATA_SWAP, dati.getString(WearConstants.KEY_SERATA_ACTION))
        assertEquals("1,4/2,3", dati.getString(WearConstants.KEY_NEXT_BASE))
        assertEquals(1, dati.getInt(WearConstants.KEY_SWAP_FROM))
        assertEquals(2, dati.getInt(WearConstants.KEY_SWAP_TO))
        assertTrue(dati.getLong(WearConstants.KEY_SEQ) > 0L)
        assertEquals(activity.getString(R.string.wear_next_waiting), avviso(activity))
        assertFalse("in attesa i comandi sono fermi", activity.findViewById<MaterialButton>(R.id.prossima_ruota).isEnabled)

        rispondi(coppie.copy(giocatori = listOf("Vantaggi", "Glorio", "Bianchi", "Rossi"), base = "1,2/4,3", versione = 11L))

        assertEquals(listOf("Vantaggi", "Glorio", "Bianchi", "Rossi"), (0..3).map { nome(activity, it) })
        assertEquals(activity.getString(R.string.wear_next_hint), avviso(activity))
        assertTrue(activity.findViewById<MaterialButton>(R.id.prossima_ruota).isEnabled)
    }

    @Test
    fun `un secondo tocco sullo stesso nome lo lascia senza chiedere niente`() {
        val activity = apri()

        riga(activity, 0).performClick()
        riga(activity, 0).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(riga(activity, 0).isSelected)
        assertTrue(mandati.isEmpty())
    }

    @Test
    fun `senza risposta entro tre secondi lo dice e vibra come un tocco non confermato`() {
        val activity = apri()

        activity.findViewById<MaterialButton>(R.id.prossima_ruota).performClick()
        aspetta { mandati.isNotEmpty() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ProssimaPartitaActivity.ATTESA_MS))

        assertEquals(activity.getString(R.string.wear_next_no_answer), avviso(activity))
        val vibratore = RuntimeEnvironment.getApplication().getSystemService(Vibrator::class.java)
        assertArrayEquals(WearPatterns.NON_CONFERMATO, shadowOf(vibratore).pattern)
        assertTrue(activity.findViewById<MaterialButton>(R.id.prossima_ruota).isEnabled)
    }

    @Test
    fun `una versione piu' vecchia di quella a schermo non cambia niente`() {
        val activity = apri()

        rispondi(coppie.copy(giocatori = listOf("A", "B", "C", "D"), versione = 9L))

        assertEquals("Vantaggi", nome(activity, 0))
    }

    @Test
    fun `Inizia torna al quadrante quando il telefono dice che la partita e' a tabellone`() {
        val activity = apri()

        activity.findViewById<MaterialButton>(R.id.prossima_inizia).performClick()
        aspetta { mandati.isNotEmpty() }
        assertEquals(WearConstants.SERATA_START, mandati.single().second.getString(WearConstants.KEY_SERATA_ACTION))
        assertFalse(activity.isFinishing)

        rispondi(coppie.copy(consegnata = true, versione = 11L))

        assertTrue(activity.isFinishing)
    }
}
