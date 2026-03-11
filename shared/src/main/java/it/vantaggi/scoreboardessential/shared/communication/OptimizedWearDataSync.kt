package it.vantaggi.scoreboardessential.shared.communication

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import it.vantaggi.scoreboardessential.shared.BuildConfig
import com.google.android.gms.wearable.Asset
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
    private val dataClient: DataClient = Wearable.getDataClient(context),
    private val messageClient: MessageClient = Wearable.getMessageClient(context),
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context),
    private val nodeClient: NodeClient = Wearable.getNodeClient(context),
) {
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
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Connected to ${nodes.size} nodes: ${nodes.joinToString { it.displayName }}")
                    }
                } else {
                    _connectionState.value = ConnectionState.Disconnected
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "No connected nodes found.")
                    }
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
                            is Asset -> dataMap.putAsset(key, value)
                        }
                    }
                    dataMap.putLong(WearConstants.KEY_TIMESTAMP, System.currentTimeMillis())

                    val putDataRequest = putDataMapRequest.asPutDataRequest()
                    if (urgent) {
                        putDataRequest.setUrgent()
                    }

                    val payloadSize = putDataRequest.data?.size ?: 0
                    if (payloadSize > 90 * 1024) { // 90KB safety limit
                        Log.e(TAG, "Payload too large: $payloadSize bytes. Max 90KB allowed. Skipping send to avoid crash.")
                        return@withContext
                    }

                    dataClient.putDataItem(putDataRequest).await()
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Data sent successfully to path: $path")
                    }
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
                // Use capabilityClient to find nodes that actually have the app and are reachable
                val nodes =
                    capabilityClient
                        .getCapability(WearConstants.CAPABILITY_SCOREBOARD_APP, CapabilityClient.FILTER_REACHABLE)
                        .await()
                        .nodes

                if (nodes.isEmpty()) {
                    Log.w(TAG, "sendMessage: No capable nodes found for path: $path")
                    return@withContext
                }

                nodes.forEach { node ->
                    try {
                        messageClient.sendMessage(node.id, path, data).await()
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Message sent to node ${node.displayName} (${node.id}): $path")
                        }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) {
                            Log.e(TAG, "Failed to send message to node ${node.displayName} (${node.id}): $path", e)
                        } else {
                            Log.e(TAG, "Failed to send message to node: $path", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve nodes for message: $path", e)
            }
        }
    }

    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Use capabilityClient for consistent testing with sendMessage
                val nodes =
                    capabilityClient
                        .getCapability(WearConstants.CAPABILITY_SCOREBOARD_APP, CapabilityClient.FILTER_REACHABLE)
                        .await()
                        .nodes

                if (nodes.isEmpty()) {
                    Log.w(TAG, "Test Connection: No capable nodes found.")
                    return@withContext false
                }
                // Send a test message to the first available node (which is presumably the most relevant one)
                val node = nodes.first()
                val testMessage = "ping".toByteArray()
                messageClient.sendMessage(node.id, WearConstants.PATH_TEST_PING, testMessage).await()
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Test Connection: Ping sent successfully to node ${node.displayName} (${node.id}).")
                }
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
