package it.vantaggi.scoreboardessential.shared.communication

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class OptimizedWearDataSyncTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockDataClient: DataClient

    @Mock
    private lateinit var mockMessageClient: MessageClient

    @Mock
    private lateinit var mockCapabilityClient: CapabilityClient

    @Mock
    private lateinit var mockNodeClient: NodeClient

    @Mock
    private lateinit var mockCapabilityInfo: CapabilityInfo

    @Mock
    private lateinit var mockNode: Node

    private lateinit var optimizedWearDataSync: OptimizedWearDataSync

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Basic setup for capability check
        whenever(mockCapabilityInfo.nodes).thenReturn(setOf(mockNode))
        whenever(mockNode.id).thenReturn("node1")
        whenever(mockNode.displayName).thenReturn("Test Node")
        
        // Mock the Tasks.await calls by returning completed tasks or mocking suspend funs if using play-services-coroutines
        // Actually, OptimizedWearDataSync uses `await()`, which is an extension function on Task.
        // Mocking Task API is hard. 
        // Ideally we should use a wrapper for Task execution or just accept that we can't easily unit test the GMS Task part without PowerMock.
        // However, we can use `Tasks.forResult` if we can control the return value of client methods.
        
        // Since OptimizedWearDataSync calls `capabilityClient.getCapability(...)`, strictly speaking we'd need to mock it returning a Task.
        
        val task = Tasks.forResult(mockCapabilityInfo)
        whenever(mockCapabilityClient.getCapability(any(), any())).thenReturn(task)

        // Initialize with mocks - this bypasses the Wearable.getDataClient static calls
        optimizedWearDataSync = OptimizedWearDataSync(
            mockContext,
            mockDataClient,
            mockMessageClient,
            mockCapabilityClient,
            mockNodeClient
        )
    }

    @Test
    fun `sendMessage sends to capable nodes`() = runTest {
        // Arrange
        val path = "/test/path"
        val data = "test data".toByteArray()
        
        val voidTask = Tasks.forResult<Int>(1) // sendMessage returns Task<Integer>
        whenever(mockMessageClient.sendMessage(any(), any(), any())).thenReturn(voidTask)

        // Act
        optimizedWearDataSync.sendMessage(path, data)

        // Assert
        // We expect getCapability to be called to find nodes
        verify(mockCapabilityClient).getCapability(eq(WearConstants.CAPABILITY_SCOREBOARD_APP), eq(CapabilityClient.FILTER_REACHABLE))
        
        // And then sendMessage to be called for the found node
        verify(mockMessageClient).sendMessage(eq("node1"), eq(path), eq(data))
    }
    
    @Test
    fun `testConnection returns true when nodes are available`() = runTest {
        // Arrange
        val voidTask = Tasks.forResult<Int>(1)
        whenever(mockMessageClient.sendMessage(any(), any(), any())).thenReturn(voidTask)

        // Act
        val result = optimizedWearDataSync.testConnection()

        // Assert
        assert(result)
        verify(mockMessageClient).sendMessage(eq("node1"), eq(WearConstants.PATH_TEST_PING), any())
    }
}
