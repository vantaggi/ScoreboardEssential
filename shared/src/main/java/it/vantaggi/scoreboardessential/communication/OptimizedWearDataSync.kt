// shared/src/main/java/com/example/scoreboardessential/communication/OptimizedWearDataSync.kt
package it.vantaggi.scoreboardessential.shared.communication

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementazione ottimizzata basata sulla documentazione ufficiale
 * Incorpora best practices per batteria, affidabilità e debugging
 */
class OptimizedWearDataSync(
    private val context: Context,
) {
    companion object {
        private const val TAG = "OptimizedWearDataSync"
        private const val BATCH_DELAY_MS = 500L
        private const val BATCH_SIZE_LIMIT = 10
    }

    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val channelClient: ChannelClient = Wearable.getChannelClient(context)
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context)

    // Connection state tracking
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _connectedNodes = MutableStateFlow<Set<Node>>(emptySet())
    val connectedNodes: StateFlow<Set<Node>> = _connectedNodes

    // Batching mechanism
    private val batchQueue = mutableListOf<BatchedData>()
    private var batchJob: Job? = null

    // Heartbeat for connection monitoring
    private var heartbeatJob: Job? = null
    private val isHeartbeatActive = AtomicBoolean(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        startConnectionMonitoring()
    }

    data class BatchedData(
        val path: String,
        val dataMap: DataMap,
        val isUrgent: Boolean,
    )

    private fun startConnectionMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    // Cerca i nodi che hanno la nostra capability e sono raggiungibili
                    val capabilityInfo =
                        Tasks.await(
                            capabilityClient.getCapability(
                                WearConstants.CAPABILITY_SCOREBOARD_APP,
                                CapabilityClient.FILTER_REACHABLE,
                            ),
                        )

                    // Aggiorna la nostra lista di nodi attivi
                    _connectedNodes.value = capabilityInfo.nodes

                    // Lo stato della connessione è VERO solo se c'è almeno un nodo con la nostra app
                    val isAppConnected = capabilityInfo.nodes.isNotEmpty()
                    _isConnected.value = isAppConnected

                    if (isAppConnected) {
                        Log.d(TAG, "App connected on nodes: ${capabilityInfo.nodes.joinToString { it.displayName }}")
                        if (!isHeartbeatActive.get()) {
                            startHeartbeat()
                        }
                    } else {
                        Log.w(TAG, "No connected nodes with the required capability found.")
                        stopHeartbeat()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking for capable nodes", e)
                    _isConnected.value = false
                    _connectedNodes.value = emptySet()
                }

                delay(5000) // Controlla ogni 5 secondi
            }
        }
    }

    /**
     * Heartbeat per verificare la connessione bidirezionale
     */
    private fun startHeartbeat() {
        if (isHeartbeatActive.compareAndSet(false, true)) {
            heartbeatJob =
                scope.launch {
                    while (isActive && isHeartbeatActive.get()) {
                        sendHeartbeat()
                        delay(30000) // Heartbeat ogni 30 secondi
                    }
                }
        }
    }

    private fun stopHeartbeat() {
        isHeartbeatActive.set(false)
        heartbeatJob?.cancel()
    }

    private suspend fun sendHeartbeat() {
        _connectedNodes.value.forEach { node ->
            try {
                val result =
                    messageClient.sendMessage(
                        node.id,
                        WearConstants.MSG_HEARTBEAT,
                        System.currentTimeMillis().toString().toByteArray(),
                    )
                Tasks.await(result, 3, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.w(TAG, "Heartbeat failed to ${node.displayName}")
            }
        }
    }

    /**
     * Sincronizza i punteggi con priorità URGENT
     * Sezione 4: "setUrgent() per dati critici"
     */
    fun syncScores(
        team1Score: Int,
        team2Score: Int,
        urgent: Boolean = true,
    ) {
        Log.d(TAG, "Invio punteggi: T1=$team1Score, T2=$team2Score")
        val dataMap =
            DataMap().apply {
                putInt(WearConstants.KEY_TEAM1_SCORE, team1Score)
                putInt(WearConstants.KEY_TEAM2_SCORE, team2Score)
                putLong(WearConstants.KEY_TIMESTAMP, System.currentTimeMillis())
            }

        if (urgent) {
            // Invio immediato per punteggi
            sendDataImmediate(WearConstants.PATH_SCORE, dataMap, true)
        } else {
            // Aggiungi al batch per invio differito
            addToBatch(WearConstants.PATH_SCORE, dataMap, false)
        }
    }

    fun syncTeamNames(
        team1Name: String,
        team2Name: String,
    ) {
        Log.d(TAG, "Invio nomi squadra: T1=$team1Name, T2=$team2Name")
        val dataMap =
            DataMap().apply {
                putString(WearConstants.KEY_TEAM1_NAME, team1Name)
                putString(WearConstants.KEY_TEAM2_NAME, team2Name)
                putLong(WearConstants.KEY_TIMESTAMP, System.currentTimeMillis())
            }
        sendDataImmediate(WearConstants.PATH_TEAM_NAMES, dataMap, false)
    }

    fun syncTeamColor(
        team: Int,
        color: Int,
    ) {
        val path = if (team == 1) WearConstants.PATH_TEAM1_COLOR else WearConstants.PATH_TEAM2_COLOR
        Log.d(TAG, "Invio colore squadra: Team=$team, Colore=$color")
        val dataMap =
            DataMap().apply {
                putInt("color", color)
                putLong(WearConstants.KEY_TIMESTAMP, System.currentTimeMillis())
            }
        sendDataImmediate(path, dataMap, true)
    }

    fun syncTimerState(
        millis: Long,
        isRunning: Boolean,
    ) {
        Log.d(TAG, "Invio stato timer: Millis=$millis, Running=$isRunning")
        val dataMap =
            DataMap().apply {
                putLong(WearConstants.KEY_TIMER_MILLIS, millis)
                putBoolean(WearConstants.KEY_TIMER_RUNNING, isRunning)
                putLong(WearConstants.KEY_TIMESTAMP, System.currentTimeMillis())
            }
        sendDataImmediate(WearConstants.PATH_TIMER_STATE, dataMap, true)
    }

    fun syncKeeperTimer(
        millis: Long,
        isRunning: Boolean,
    ) {
        Log.d(TAG, "Invio stato keeper timer: Millis=$millis, Running=$isRunning")
        val dataMap =
            DataMap().apply {
                putLong(WearConstants.KEY_KEEPER_MILLIS, millis)
                putBoolean(WearConstants.KEY_KEEPER_RUNNING, isRunning)
                putLong(WearConstants.KEY_TIMESTAMP, System.currentTimeMillis())
            }
        sendDataImmediate(WearConstants.PATH_KEEPER_TIMER, dataMap, true)
    }

    fun syncMatchState(isActive: Boolean) {
        Log.d(TAG, "Invio stato partita: Active=$isActive")
        val dataMap =
            DataMap().apply {
                putBoolean("match_active", isActive)
                putLong(WearConstants.KEY_TIMESTAMP, System.currentTimeMillis())
            }
        sendDataImmediate(WearConstants.PATH_MATCH_STATE, dataMap, true)
    }

    fun syncScorerSelected(
        playerName: String,
        roles: List<String>,
        team: Int,
    ) {
        val rolesString = roles.joinToString(",")
        val message = "$playerName|$rolesString|$team"
        Log.d(TAG, "Invio marcatore selezionato: $message")
        sendMessageWithRetry(WearConstants.MSG_SCORER_SELECTED, message.toByteArray())
    }

    fun syncPlayerList(players: List<it.vantaggi.scoreboardessential.shared.PlayerData>) {
        Log.d(TAG, "Invio lista giocatori: Count=${players.size}")
        val dataMap = DataMap()
        val playerArrayList = ArrayList<DataMap>()

        players.forEach { player ->
            val playerMap =
                DataMap().apply {
                    putString(WearConstants.KEY_PLAYER_NAME, player.name)
                    putStringArrayList(WearConstants.KEY_PLAYER_ROLES, ArrayList(player.roles))
                    putInt(WearConstants.KEY_PLAYER_ID, player.id)
                    putInt(WearConstants.KEY_PLAYER_GOALS, player.goals)
                    putInt(WearConstants.KEY_PLAYER_APPEARANCES, player.appearances)
                }
            playerArrayList.add(playerMap)
        }

        dataMap.putDataMapArrayList(WearConstants.KEY_PLAYERS, playerArrayList)
        dataMap.putLong(WearConstants.KEY_TIMESTAMP, System.currentTimeMillis())

        sendDataImmediate(WearConstants.PATH_PLAYERS, dataMap, true)
    }

    fun syncTeamPlayers(
        team1Players: List<it.vantaggi.scoreboardessential.shared.PlayerData>,
        team2Players: List<it.vantaggi.scoreboardessential.shared.PlayerData>,
    ) {
        Log.d(TAG, "Invio giocatori squadra: T1=${team1Players.size}, T2=${team2Players.size}")
        val dataMap = DataMap()

        val team1ArrayList = ArrayList<DataMap>()
        team1Players.forEach { player ->
            Log.d(TAG, "Syncing team 1 player ${player.name} with roles: ${player.roles.joinToString(",")}")
            val playerMap =
                DataMap().apply {
                    putString(WearConstants.KEY_PLAYER_NAME, player.name)
                    putStringArrayList(WearConstants.KEY_PLAYER_ROLES, ArrayList(player.roles))
                    putInt(WearConstants.KEY_PLAYER_ID, player.id)
                    putInt(WearConstants.KEY_PLAYER_GOALS, player.goals)
                    putInt(WearConstants.KEY_PLAYER_APPEARANCES, player.appearances)
                }
            team1ArrayList.add(playerMap)
        }

        val team2ArrayList = ArrayList<DataMap>()
        team2Players.forEach { player ->
            Log.d(TAG, "Syncing team 2 player ${player.name} with roles: ${player.roles.joinToString(",")}")
            val playerMap =
                DataMap().apply {
                    putString(WearConstants.KEY_PLAYER_NAME, player.name)
                    putStringArrayList(WearConstants.KEY_PLAYER_ROLES, ArrayList(player.roles))
                    putInt(WearConstants.KEY_PLAYER_ID, player.id)
                    putInt(WearConstants.KEY_PLAYER_GOALS, player.goals)
                    putInt(WearConstants.KEY_PLAYER_APPEARANCES, player.appearances)
                }
            team2ArrayList.add(playerMap)
        }

        dataMap.putDataMapArrayList(WearConstants.KEY_TEAM1_PLAYERS, team1ArrayList)
        dataMap.putDataMapArrayList(WearConstants.KEY_TEAM2_PLAYERS, team2ArrayList)
        dataMap.putLong(WearConstants.KEY_TIMESTAMP, System.currentTimeMillis())

        sendDataImmediate(WearConstants.PATH_TEAM_PLAYERS, dataMap, true)
    }

    fun syncFullState(
        team1Score: Int,
        team2Score: Int,
        team1Name: String,
        team2Name: String,
        timerMillis: Long,
        timerRunning: Boolean,
        keeperMillis: Long,
        keeperRunning: Boolean,
        matchActive: Boolean,
    ) {
        syncScores(team1Score, team2Score, urgent = true)
        syncTeamNames(team1Name, team2Name)
        syncTimerState(timerMillis, timerRunning)
        syncKeeperTimer(keeperMillis, keeperRunning)
        syncMatchState(matchActive)
    }

    /**
     * Batching per ottimizzare la batteria
     * Basato su "Minimizzare e Raggruppare le Trasmissioni"
     */
    private fun addToBatch(
        path: String,
        dataMap: DataMap,
        isUrgent: Boolean,
    ) {
        synchronized(batchQueue) {
            batchQueue.add(BatchedData(path, dataMap, isUrgent))

            if (batchQueue.size >= BATCH_SIZE_LIMIT) {
                flushBatch()
            } else {
                scheduleBatchFlush()
            }
        }
    }

    private fun scheduleBatchFlush() {
        batchJob?.cancel()
        batchJob =
            scope.launch {
                delay(BATCH_DELAY_MS)
                flushBatch()
            }
    }

    private fun flushBatch() {
        synchronized(batchQueue) {
            if (batchQueue.isEmpty()) return

            scope.launch {
                val itemsToSend = batchQueue.toList()
                batchQueue.clear()

                itemsToSend.forEach { item ->
                    sendDataImmediate(item.path, item.dataMap, item.isUrgent)
                }
            }
        }
    }

    /**
     * Invio dati con gestione errori robusta
     */
    private fun sendDataImmediate(
        path: String,
        dataMap: DataMap,
        urgent: Boolean,
    ) {
        scope.launch {
            if (!_isConnected.value) {
                Log.w(TAG, "Cannot send data for path $path: no connected nodes")
                return@launch
            }

            try {
                val request =
                    PutDataRequest.create(path).apply {
                        data = dataMap.toByteArray()
                        if (urgent) {
                            setUrgent()
                        }
                    }

                val dataItem = Tasks.await(dataClient.putDataItem(request))
                Log.d(TAG, "Dati inviati con successo per il path: ${dataItem.uri.path}")
            } catch (e: Exception) {
                Log.e(TAG, "Fallimento invio dati per il path: $path", e)
            }
        }
    }

    /**
     * Invio messaggi con retry logic
     * Basato su MessageClient "fire-and-forget" con miglioramenti
     */
    fun sendMessageWithRetry(
        path: String,
        data: ByteArray,
        targetNodeId: String? = null,
        maxRetries: Int = WearConstants.MAX_RETRY_ATTEMPTS,
    ) {
        scope.launch {
            val nodes =
                if (targetNodeId != null) {
                    _connectedNodes.value.filter { it.id == targetNodeId }
                } else {
                    _connectedNodes.value
                }

            if (nodes.isEmpty()) {
                Log.w(TAG, "No target nodes for message: $path")
                return@launch
            }

            nodes.forEach { node ->
                var retryCount = 0
                var success = false

                while (!success && retryCount < maxRetries) {
                    try {
                        val result = messageClient.sendMessage(node.id, path, data)
                        Tasks.await(result, WearConstants.MESSAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        success = true
                        Log.d(TAG, "Message sent to ${node.displayName}: $path")
                    } catch (e: Exception) {
                        retryCount++
                        if (retryCount < maxRetries) {
                            Log.w(TAG, "Retry $retryCount/$maxRetries for message to ${node.displayName}")
                            delay(WearConstants.RETRY_DELAY_MS * retryCount)
                        } else {
                            Log.e(TAG, "Failed to send message after $maxRetries attempts", e)
                        }
                    }
                }
            }
        }
    }

    /**
     * Streaming di dati timer usando ChannelClient
     * Per dati continui come il timer
     */
    fun startTimerStreaming(
        onChannelOpened: (ChannelClient.Channel) -> Unit,
        onStreamData: suspend (ByteArray) -> Unit,
    ) {
        scope.launch {
            val nodes = _connectedNodes.value
            if (nodes.isEmpty()) return@launch

            nodes.forEach { node ->
                try {
                    val channel =
                        Tasks.await(
                            channelClient.openChannel(
                                node.id,
                                WearConstants.CHANNEL_TIMER_STREAM,
                            ),
                        )

                    onChannelOpened(channel)

                    // Setup input stream listener
                    val inputStream = Tasks.await(channelClient.getInputStream(channel))
                    val buffer = ByteArray(1024)

                    while (isActive) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead > 0) {
                            onStreamData(buffer.copyOf(bytesRead))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in timer streaming", e)
                }
            }
        }
    }

    /**
     * Cleanup risorse
     */
    fun cleanup() {
        scope.cancel()
        stopHeartbeat()
        batchJob?.cancel()
        flushBatch() // Invia dati pendenti prima di chiudere
    }
}
