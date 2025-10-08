package it.vantaggi.scoreboardessential

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.net.Uri
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WearCommunicationTest {

    @Test
    fun testNodeConnection() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nodeClient = Wearable.getNodeClient(context)

        val nodes = Tasks.await(nodeClient.connectedNodes)

        assertTrue("At least one node should be connected", nodes.isNotEmpty())
        nodes.forEach { node ->
            Log.d("WearTest", "Connected node: ${node.displayName} (${node.id})")
        }
    }

    @Test
    fun testScoreSync() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val syncManager = OptimizedWearDataSync(context)

        // Test invio score con urgent
        syncManager.syncScores(2, 3, urgent = true)

        // Attendi sincronizzazione
        delay(2000)

        // Verifica che i dati siano stati salvati
        val dataClient = Wearable.getDataClient(context)
        val dataItems = Tasks.await(dataClient.getDataItems(
            Uri.parse("wear://*/scoreboard/score_data")
        ))

        assertTrue("Score data should be synced", dataItems.count > 0)

        dataItems.release()
    }

    @Test
    fun testMessageRetry() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val syncManager = OptimizedWearDataSync(context)

        // Test invio messaggio con retry
        syncManager.sendMessageWithRetry(
            WearConstants.MSG_TIMER_ACTION,
            "START".toByteArray(),
            maxRetries = 3
        )

        // Verifica nei log che il retry sia avvenuto se necessario
    }

    @Test
    fun testBidirectionalDataSync() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val latch = CountDownLatch(1)
        val testData = UUID.randomUUID().toString()
        val dataClient = Wearable.getDataClient(context)

        val listener = DataClient.OnDataChangedListener { dataEvents ->
            dataEvents.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearConstants.PATH_TEST_PONG) {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val receivedData = dataMap.getString(WearConstants.KEY_TEST_DATA)
                    if (receivedData == testData) {
                        latch.countDown()
                    }
                }
            }
            dataEvents.release()
        }

        try {
            dataClient.addListener(listener, Uri.parse("wear://*${WearConstants.PATH_TEST_PONG}"), DataClient.FILTER_LITERAL)

            val request = PutDataMapRequest.create(WearConstants.PATH_TEST_PING).apply {
                dataMap.putString(WearConstants.KEY_TEST_DATA, testData)
            }
            dataClient.putDataItem(request.asPutDataRequest())

            val result = latch.await(10, TimeUnit.SECONDS)
            assertTrue("Il test è andato in timeout, non è stata ricevuta risposta 'pong' dal Wear OS.", result)

        } finally {
            dataClient.removeListener(listener)
        }
    }
}