package it.vantaggi.scoreboardessential.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import it.vantaggi.scoreboardessential.shared.communication.NodoLocale
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * L6: l'orologio scrive match_state e altri path che ascolta. Se il Data Layer gli riconsegna i
 * propri DataItem, quello scritto da lui non e' una notizia del telefono e non va rigiocato.
 */
@RunWith(RobolectricTestRunner::class)
class EcoDeiDataItemTest {
    private val contesto: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setup() {
        NodoLocale.azzera()
    }

    @After
    fun tearDown() {
        NodoLocale.azzera()
    }

    private fun dataItem(
        host: String,
        path: String,
        mappa: DataMap,
    ): DataItem {
        val item = Mockito.mock(DataItem::class.java)
        Mockito.`when`(item.uri).thenReturn(Uri.parse("wear://$host$path"))
        Mockito.`when`(item.data).thenReturn(mappa.toByteArray())
        // DataMapItem rilegge l'item congelato: il finto ritorna se stesso.
        Mockito.`when`(item.freeze()).thenReturn(item)
        return item
    }

    private fun evento(item: DataItem): DataEventBuffer {
        val evento = Mockito.mock(DataEvent::class.java)
        Mockito.`when`(evento.type).thenReturn(DataEvent.TYPE_CHANGED)
        Mockito.`when`(evento.dataItem).thenReturn(item)
        val buffer = Mockito.mock(DataEventBuffer::class.java)
        Mockito.`when`(buffer.iterator()).thenAnswer { mutableListOf(evento).iterator() }
        return buffer
    }

    @Test
    fun `un DataItem scritto dal nodo locale e' scartato e uno del telefono passa`() {
        val nodo = Mockito.mock(Node::class.java)
        Mockito.`when`(nodo.id).thenReturn("orologio-locale")
        val nodeClient = Mockito.mock(NodeClient::class.java)
        Mockito.`when`(nodeClient.localNode).thenReturn(Tasks.forResult(nodo))

        Mockito.mockStatic(Wearable::class.java).use { wearable ->
            wearable
                .`when`<NodeClient> { Wearable.getNodeClient(ArgumentMatchers.any(Context::class.java)) }
                .thenReturn(nodeClient)

            val servizio =
                Robolectric
                    .buildService(WearDataLayerService::class.java)
                    .create()
                    .get()
            // L'id del nodo locale arriva su un listener del thread principale.
            shadowOf(Looper.getMainLooper()).idle()

            val ricevuti = mutableListOf<Intent>()
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        ricevuti.add(intent)
                    }
                }
            val manager = LocalBroadcastManager.getInstance(contesto)
            manager.registerReceiver(receiver, IntentFilter(WearDataLayerService.ACTION_MATCH_STATE_UPDATE))

            try {
                val mappa = DataMap().apply { putBoolean(WearConstants.KEY_MATCH_ACTIVE, false) }

                servizio.onDataChanged(evento(dataItem("orologio-locale", WearConstants.PATH_MATCH_STATE, mappa)))
                shadowOf(Looper.getMainLooper()).idle()
                assertTrue("un DataItem del nodo locale non va inoltrato: ${ricevuti.size}", ricevuti.isEmpty())

                servizio.onDataChanged(evento(dataItem("telefono", WearConstants.PATH_MATCH_STATE, mappa)))
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals("quello del telefono si", 1, ricevuti.size)
            } finally {
                manager.unregisterReceiver(receiver)
            }
        }
    }
}
