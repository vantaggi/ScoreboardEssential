package it.vantaggi.scoreboardessential

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class SimplifiedDataLayerListenerServiceTest {
    private lateinit var service: SimplifiedDataLayerListenerService
    private lateinit var mockDataEventBuffer: DataEventBuffer
    private lateinit var mockDataEvent: DataEvent
    private lateinit var mockDataItem: DataItem
    private lateinit var mockDataMapItem: DataMapItem
    private lateinit var mockDataMap: DataMap
    private lateinit var mockUri: Uri

    private lateinit var dataMapItemStatic: MockedStatic<DataMapItem>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        service = SimplifiedDataLayerListenerService()

        mockDataEventBuffer = mock(DataEventBuffer::class.java)
        mockDataEvent = mock(DataEvent::class.java)
        mockDataItem = mock(DataItem::class.java)
        mockDataMapItem = mock(DataMapItem::class.java)
        mockDataMap = mock(DataMap::class.java)
        mockUri = mock(Uri::class.java)

        dataMapItemStatic = mockStatic(DataMapItem::class.java)
    }

    @After
    fun tearDown() {
        dataMapItemStatic.close()
    }

    /**
     * Un aggiornamento di punteggio dall'orologio arriva all'interfaccia come broadcast locale, coi
     * due valori giusti negli extra, e i valori NON finiscono nel log.
     *
     * Prima c'era solo l'asserzione negativa sul log, con la verifica del broadcast scritta come
     * commento: il test passava anche se il servizio non inviava niente.
     */
    @Test
    fun `onDataChanged inoltra il punteggio 10 a 5 in broadcast e non lo scrive nel log`() {
        // LocalBroadcastManager vuole un contesto: il servizio va costruito da Robolectric.
        service = org.robolectric.Robolectric.buildService(SimplifiedDataLayerListenerService::class.java).get()

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
        val manager = LocalBroadcastManager.getInstance(ApplicationProvider.getApplicationContext())
        manager.registerReceiver(receiver, IntentFilter(SimplifiedDataLayerListenerService.ACTION_SCORE_UPDATE))

        `when`(mockDataEventBuffer.count).thenReturn(1)
        `when`(mockDataEventBuffer.get(0)).thenReturn(mockDataEvent)
        // Iteratore per il forEach del servizio
        `when`(mockDataEventBuffer.iterator()).thenReturn(mutableListOf(mockDataEvent).iterator())

        `when`(mockDataEvent.type).thenReturn(DataEvent.TYPE_CHANGED)
        `when`(mockDataEvent.dataItem).thenReturn(mockDataItem)
        `when`(mockDataItem.uri).thenReturn(mockUri)
        `when`(mockUri.path).thenReturn(WearConstants.PATH_SCORE)

        dataMapItemStatic.`when`<DataMapItem> { DataMapItem.fromDataItem(mockDataItem) }.thenReturn(mockDataMapItem)
        `when`(mockDataMapItem.dataMap).thenReturn(mockDataMap)
        `when`(mockDataMap.getInt(WearConstants.KEY_TEAM1_SCORE, 0)).thenReturn(10)
        `when`(mockDataMap.getInt(WearConstants.KEY_TEAM2_SCORE, 0)).thenReturn(5)

        try {
            service.onDataChanged(mockDataEventBuffer)
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            manager.unregisterReceiver(receiver)
        }

        assertEquals("un solo broadcast di punteggio", 1, ricevuti.size)
        assertEquals(10, ricevuti[0].getIntExtra(WearConstants.KEY_TEAM1_SCORE, -1))
        assertEquals(5, ricevuti[0].getIntExtra(WearConstants.KEY_TEAM2_SCORE, -1))

        val fughe =
            ShadowLog
                .getLogsForTag("SimplifiedDataService")
                .filter { it.msg.contains("T1=10") || it.msg.contains("T2=5") }
        assertTrue("Dati di punteggio nel log: ${fughe.map { it.msg }}", fughe.isEmpty())
    }
}
