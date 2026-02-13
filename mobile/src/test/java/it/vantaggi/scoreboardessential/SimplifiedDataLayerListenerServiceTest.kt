package it.vantaggi.scoreboardessential

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import android.net.Uri

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
        // Mock context/application/resources if needed, but Service should be fine for simple methods
        // However, LocalBroadcastManager needs a context. We might need to attach base context if we were running as a real service
        // Since we call onDataChanged directly, we need to ensure LocalBroadcastManager.getInstance(this) works.
        // SimplifiedDataLayerListenerService is a Service, so 'this' is a Context.
        // In Robolectric, we can use a spy or shadow, or just instantiate it.
        // But since we are calling a method on an instance created with 'new', it doesn't have a Context attached.
        // We need to attach it. A common way is using Robolectric.buildService

        // But let's try just setting up the mocks first.

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

    @Test
    fun `onDataChanged logs score update with sensitive data`() {
        // Arrange
        // We need a context for LocalBroadcastManager
        val serviceController = org.robolectric.Robolectric.buildService(SimplifiedDataLayerListenerService::class.java)
        service = serviceController.get()

        // Setup mocks
        `when`(mockDataEventBuffer.count).thenReturn(1)
        `when`(mockDataEventBuffer.get(0)).thenReturn(mockDataEvent)
        // Iterator for forEachIndexed
        val iterator = mutableListOf(mockDataEvent).iterator()
        `when`(mockDataEventBuffer.iterator()).thenReturn(iterator)

        `when`(mockDataEvent.type).thenReturn(DataEvent.TYPE_CHANGED)
        `when`(mockDataEvent.dataItem).thenReturn(mockDataItem)
        `when`(mockDataItem.uri).thenReturn(mockUri)
        `when`(mockUri.path).thenReturn(WearConstants.PATH_SCORE)

        dataMapItemStatic.`when`<DataMapItem> { DataMapItem.fromDataItem(mockDataItem) }.thenReturn(mockDataMapItem)
        `when`(mockDataMapItem.dataMap).thenReturn(mockDataMap)

        // Sensitive data
        val team1Score = 10
        val team2Score = 5
        `when`(mockDataMap.getInt(WearConstants.KEY_TEAM1_SCORE, 0)).thenReturn(team1Score)
        `when`(mockDataMap.getInt(WearConstants.KEY_TEAM2_SCORE, 0)).thenReturn(team2Score)

        // Act
        service.onDataChanged(mockDataEventBuffer)

        // Assert
        val logs = ShadowLog.getLogsForTag("SimplifiedDataService")
        val sensitiveLog = logs.find { it.msg.contains("Broadcasted score update: T1=10, T2=5") }

        if (sensitiveLog != null) {
             // This confirms the vulnerability exists (for reproduction)
             // or fails if we fixed it.
             // Since I want to verify the fix later, I should assert that it DOES NOT contain this.
             // But for reproduction, I want to show it exists.
             // I'll leave a comment.
        }

        // To verify the fix, we assert that no log contains the sensitive values
        val leaks = logs.any { it.msg.contains("T1=10") || it.msg.contains("T2=5") }
        if (leaks) {
            fail("Sensitive score data logged!")
        }
    }
}
