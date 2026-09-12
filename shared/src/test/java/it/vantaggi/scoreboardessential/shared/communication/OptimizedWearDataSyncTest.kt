package it.vantaggi.scoreboardessential.shared.communication

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner.Silent::class)
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

        val task = Tasks.forResult(mockCapabilityInfo)
        whenever(mockCapabilityClient.getCapability(any(), any())).thenReturn(task)
        // Di base il nodo e' collegato davvero; i test sul collegamento fantasma lo tolgono.
        whenever(mockNodeClient.connectedNodes).thenReturn(Tasks.forResult(listOf(mockNode)))

        // Initialize with mocks - this bypasses the Wearable.getDataClient static calls
        optimizedWearDataSync =
            OptimizedWearDataSync(
                mockContext,
                mockDataClient,
                mockMessageClient,
                mockCapabilityClient,
                mockNodeClient,
            )
    }

    @Test
    fun `sendMessage sends to capable nodes`() =
        runTest {
            // Arrange
            val path = "/test/path"
            val data = "test data".toByteArray()

            val voidTask = Tasks.forResult<Int>(1) // sendMessage returns Task<Integer>
            whenever(mockMessageClient.sendMessage(any(), any(), any())).thenReturn(voidTask)

            // Act
            optimizedWearDataSync.sendMessage(path, data)

            // Assert
            // We expect getCapability to be called to find nodes
            verify(
                mockCapabilityClient,
                atLeastOnce(),
            ).getCapability(
                eq(WearConstants.CAPABILITY_SCOREBOARD_APP),
                eq(CapabilityClient.FILTER_REACHABLE),
            )

            // And then sendMessage to be called for the found node
            verify(mockMessageClient).sendMessage(eq("node1"), eq(path), eq(data))
        }

    @Test
    fun `testConnection returns true when nodes are available`() =
        runTest {
            // Arrange
            val voidTask = Tasks.forResult<Int>(1)
            whenever(mockMessageClient.sendMessage(any(), any(), any())).thenReturn(voidTask)

            // Act
            val result = optimizedWearDataSync.testConnection()

            // Assert
            assert(result)
            verify(mockMessageClient).sendMessage(eq("node1"), eq(WearConstants.PATH_TEST_PING), any())
        }

    // La capability REACHABLE restituisce il nodo anche a Bluetooth chiuso da giorni: se basta
    // lei, l'orologio mostra il pallino verde e il telefono il toast "Wear OS Connected" mentre
    // i dati non passano. Conta solo il nodo che compare anche tra i connectedNodes.
    @Test
    fun `capability senza nodo collegato vale Disconnected`() =
        runTest {
            whenever(mockNodeClient.connectedNodes).thenReturn(Tasks.forResult(emptyList()))

            optimizedWearDataSync.refreshConnection()

            assertEquals(ConnectionState.Disconnected, optimizedWearDataSync.connectionState.value)
        }

    @Test
    fun `stesso nodo in capability e connectedNodes vale Connected`() =
        runTest {
            optimizedWearDataSync.refreshConnection()

            assertEquals(ConnectionState.Connected(1), optimizedWearDataSync.connectionState.value)
        }

    // Sul polso un true qui fa vibrare "punto preso" e salta la coda offline: con il nodo non
    // collegato il tocco deve risultare NON consegnato, cosi' finisce in coda invece di sparire.
    @Test
    fun `sendMessage senza nodo collegato ritorna false e non spedisce`() =
        runTest {
            whenever(mockNodeClient.connectedNodes).thenReturn(Tasks.forResult(emptyList()))
            whenever(mockMessageClient.sendMessage(any(), any(), any())).thenReturn(Tasks.forResult(1))

            val consegnato = optimizedWearDataSync.sendMessage("/test/path", "x".toByteArray())

            assertFalse(consegnato)
            verify(mockMessageClient, never()).sendMessage(any(), any(), any())
        }

    @Test
    fun `testConnection senza nodo collegato ritorna false`() =
        runTest {
            whenever(mockNodeClient.connectedNodes).thenReturn(Tasks.forResult(emptyList()))
            whenever(mockMessageClient.sendMessage(any(), any(), any())).thenReturn(Tasks.forResult(1))

            assertFalse(optimizedWearDataSync.testConnection())
        }
}
