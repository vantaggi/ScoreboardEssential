package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.Vibrator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

/**
 * La scelta del marcatore deve dire al polso se il nome e' arrivato.
 *
 * Prima i nodi si chiedevano a mano e con zero nodi non succedeva niente: la schermata si chiudeva
 * come dopo un invio riuscito, e il gol arrivava poi al telefono senza marcatore.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerSelectionActivityTest {
    @After
    fun ripristina() {
        PlayerSelectionActivity.creaSync = { OptimizedWearDataSync(it) }
    }

    /** Il canale vero con i client GMS finti: l'esito lo decide lo stesso criterio del pallino. */
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
                .thenReturn(Tasks.forResult(1))
            OptimizedWearDataSync(contesto, Mockito.mock(DataClient::class.java), messaggi, capability, nodi)
        }

    /** Apre la scelta con un solo giocatore, tocca Rossi e aspetta che la schermata si chiuda. */
    private fun scegliRossi(): PlayerSelectionActivity {
        val app = RuntimeEnvironment.getApplication()
        val intent =
            Intent(app, PlayerSelectionActivity::class.java)
                .putExtra(WearConstants.EXTRA_TEAM_NUMBER, 1)
                .putExtra(
                    WearDataLayerService.EXTRA_PLAYERS,
                    PlayerData.encodeList(listOf(PlayerData(id = 7, name = "Rossi", roles = emptyList()))),
                )
        val activity = Robolectric.buildActivity(PlayerSelectionActivity::class.java, intent).setup().get()
        val lista = activity.findViewById<RecyclerView>(R.id.player_list)
        lista.measure(0, 0)
        lista.layout(0, 0, 400, 400)
        shadowOf(Looper.getMainLooper()).idle()
        lista.findViewHolderForAdapterPosition(0)!!.itemView.performClick()

        // L'invio passa da Dispatchers.IO, un thread vero: si fa girare il looper finche' l'esito
        // non chiude la schermata, con un limite.
        val limite = System.currentTimeMillis() + 5_000
        while (!activity.isFinishing && System.currentTimeMillis() < limite) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("La schermata non si e' chiusa dopo l'esito", activity.isFinishing)
        return activity
    }

    private fun ultimaVibrazione(): LongArray {
        val vibratore = RuntimeEnvironment.getApplication().getSystemService(Vibrator::class.java)
        return shadowOf(vibratore).pattern
    }

    @Test
    fun `senza nodi la scelta vibra da errore e lo dice a schermo`() {
        PlayerSelectionActivity.creaSync = telefono(collegato = false)

        val activity = scegliRossi()

        assertArrayEquals(WearViewModel.PATTERN_ERRORE, ultimaVibrazione())
        assertEquals(activity.getString(R.string.wear_scorer_not_sent), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `col telefono collegato la scelta vibra la conferma e non dice niente`() {
        // Il controllo del test precedente: l'errore non deve comparire quando il nome e' partito.
        PlayerSelectionActivity.creaSync = telefono(collegato = true)

        scegliRossi()

        assertArrayEquals(HapticFeedbackManager.PATTERN_CONFIRM, ultimaVibrazione())
        assertNull(ShadowToast.getTextOfLatestToast())
    }
}
