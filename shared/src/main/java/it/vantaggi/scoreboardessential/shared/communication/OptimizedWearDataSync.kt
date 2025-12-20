package it.vantaggi.scoreboardessential.shared.communication

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class ConnectionState {
    data class Connected(
        val nodeCount: Int,
    ) : ConnectionState()

    object Disconnected : ConnectionState()

    data class Error(
        val message: String,
    ) : ConnectionState()
}

class OptimizedWearDataSync(
    private val context: Context,
) {
    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context)
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val capabilityListener =
        CapabilityClient.OnCapabilityChangedListener {
            updateConnectedNodes()
        }

    companion object {
        private const val TAG = "OptimizedWearDataSync"
    }

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        capabilityClient.addListener(capabilityListener, WearConstants.CAPABILITY_SCOREBOARD_APP)
        updateConnectedNodes()
    }

    private fun updateConnectedNodes() {
        coroutineScope.launch {
            try {
                val nodes =
                    capabilityClient
                        .getCapability(WearConstants.CAPABILITY_SCOREBOARD_APP, CapabilityClient.FILTER_REACHABLE)
                        .await()
                        .nodes

                if (nodes.isNotEmpty()) {
                    _connectionState.value = ConnectionState.Connected(nodes.size)
                    Log.d(TAG, "Connected to ${nodes.size} nodes: ${nodes.joinToString { it.displayName }}")
                } else {
                    _connectionState.value = ConnectionState.Disconnected
                    Log.d(TAG, "No connected nodes found.")
                }
            } catch (e: ApiException) {
                _connectionState.value = ConnectionState.Error("API Exception: ${e.message}")
                Log.e(TAG, "Error monitoring connection", e)
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error("Generic error: ${e.message}")
                Log.e(TAG, "An unexpected error occurred", e)
            }
        }
    }

    suspend fun sendData(
        path: String,
        data: Map<String, Any>,
        urgent: Boolean = false,
    ) {
        withContext(Dispatchers.IO) {
            var attempt = 0
            var success = false
            while (attempt < WearConstants.MAX_RETRY_ATTEMPTS && !success) {
                try {
                    val putDataMapRequest = PutDataMapRequest.create(path)
                    val dataMap = putDataMapRequest.dataMap

                    data.forEach { (key, value) ->
                        when (value) {
                            is Int -> dataMap.putInt(key, value)
                            is String -> dataMap.putString(key, value)
                            is Boolean -> dataMap.putBoolean(key, value)
                            is Long -> dataMap.putLong(key, value)
                            is Double -> dataMap.putDouble(key, value)
                            is Float -> dataMap.putFloat(key, value)
                            is IntArray -> dataMap.putIntegerArrayList(key, ArrayList(value.toList()))
                            is ArrayList<*> -> {
                                if (value.isNotEmpty() && value[0] is Int) {
                                    dataMap.putIntegerArrayList(key, value as ArrayList<Int>)
                                } else if (value.isNotEmpty() && value[0] is String) {
                                    dataMap.putStringArrayList(key, value as ArrayList<String>)
                                }
                            }
                        }
                    }
                    dataMap.putLong(WearConstants.KEY_TIMESTAMP, System.currentTimeMillis())

                    val putDataRequest = putDataMapRequest.asPutDataRequest()
                    if (urgent) {
                        putDataRequest.setUrgent()
                    }

                    dataClient.putDataItem(putDataRequest).await()
                    Log.d(TAG, "Data sent successfully to path: $path")
                    success = true
                } catch (e: Exception) {
                    attempt++
                    Log.e(TAG, "Failed to send data to path: $path (Attempt $attempt/${WearConstants.MAX_RETRY_ATTEMPTS})", e)
                    if (attempt < WearConstants.MAX_RETRY_ATTEMPTS) {
                        delay(WearConstants.RETRY_DELAY_MS)
                    }
                }
            }
        }
    }

    suspend fun sendMessage(
        path: String,
        data: ByteArray? = null,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, path, data).await()
                }
                Log.d(TAG, "Message sent to ${nodes.size} nodes: $path")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message: $path", e)
            }
        }
    }

    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.w(TAG, "Test Connection: No nodes found.")
                    return@withContext false
                }
                // Send a test message to the first available node
                val testMessage = "ping".toByteArray()
                messageClient.sendMessage(nodes.first().id, WearConstants.PATH_TEST_PING, testMessage).await()
                Log.d(TAG, "Test Connection: Ping sent successfully.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Test Connection: Failed.", e)
                false
            }
        }
    }

    suspend fun syncTeamPlayers(
        team1Players: String,
        team2Players: String,
    ) {
        val data =
            mapOf(
                WearConstants.KEY_TEAM1_PLAYERS to team1Players,
                WearConstants.KEY_TEAM2_PLAYERS to team2Players,
            )
        sendData(WearConstants.PATH_TEAM_PLAYERS, data)
    }

    fun cleanup() {
        capabilityClient.removeListener(capabilityListener)
        coroutineScope.cancel()
    }
}
