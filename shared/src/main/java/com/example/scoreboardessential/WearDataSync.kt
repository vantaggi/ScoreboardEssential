// Create a new file: shared/src/main/java/com/example/scoreboardessential/WearDataSync.kt

package com.example.scoreboardessential

import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import android.content.Context
import android.util.Log

/**
 * Centralized data synchronization manager for Mobile <-> Wear communication
 */
class WearDataSync(private val context: Context) {

    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)

    companion object {
        // Data paths for persistent state
        const val PATH_SCORE = "/score_data"
        const val PATH_TEAM_NAMES = "/team_names"
        const val PATH_TIMER_STATE = "/timer_state"
        const val PATH_KEEPER_TIMER = "/keeper_timer"
        const val PATH_MATCH_STATE = "/match_state"
        const val PATH_PLAYERS = "/players"

        // Message paths for immediate actions
        const val MSG_SCORE_CHANGED = "/msg_score_changed"
        const val MSG_TIMER_ACTION = "/msg_timer_action"
        const val MSG_KEEPER_ACTION = "/msg_keeper_action"
        const val MSG_MATCH_ACTION = "/msg_match_action"
        const val MSG_SCORER_SELECTED = "/msg_scorer_selected"

        // Data keys
        const val KEY_TEAM1_SCORE = "team1_score"
        const val KEY_TEAM2_SCORE = "team2_score"
        const val KEY_TEAM1_NAME = "team1_name"
        const val KEY_TEAM2_NAME = "team2_name"
        const val KEY_TIMER_MILLIS = "timer_millis"
        const val KEY_TIMER_RUNNING = "timer_running"
        const val KEY_KEEPER_MILLIS = "keeper_millis"
        const val KEY_KEEPER_RUNNING = "keeper_running"
        const val KEY_MATCH_ACTIVE = "match_active"
        const val KEY_TIMESTAMP = "timestamp"
    }

    // --- Score Synchronization ---
    fun syncScores(team1Score: Int, team2Score: Int) {
        val dataMap = DataMap().apply {
            putInt(KEY_TEAM1_SCORE, team1Score)
            putInt(KEY_TEAM2_SCORE, team2Score)
            putLong(KEY_TIMESTAMP, System.currentTimeMillis())
        }

        val request = PutDataRequest.create(PATH_SCORE).apply {
            data = dataMap.toByteArray()
            setUrgent() // High priority for score updates
        }

        dataClient.putDataItem(request).addOnSuccessListener {
            Log.d("WearDataSync", "Scores synced: T1=$team1Score, T2=$team2Score")
        }

        // Also send immediate message for faster UI update
        sendImmediateMessage(MSG_SCORE_CHANGED, "$team1Score,$team2Score")
    }

    // --- Team Names Synchronization ---
    fun syncTeamNames(team1Name: String, team2Name: String) {
        val dataMap = DataMap().apply {
            putString(KEY_TEAM1_NAME, team1Name)
            putString(KEY_TEAM2_NAME, team2Name)
            putLong(KEY_TIMESTAMP, System.currentTimeMillis())
        }

        val request = PutDataRequest.create(PATH_TEAM_NAMES).apply {
            data = dataMap.toByteArray()
        }

        dataClient.putDataItem(request).addOnSuccessListener {
            Log.d("WearDataSync", "Team names synced: $team1Name vs $team2Name")
        }
    }

    // --- Team Color Synchronization ---
    fun syncTeamColor(team: Int, color: Int) {
        val path = if (team == 1) "/team1_color" else "/team2_color"
        val dataMap = DataMap().apply {
            putInt("color", color)
            putLong(KEY_TIMESTAMP, System.currentTimeMillis())
        }

        val request = PutDataRequest.create(path).apply {
            data = dataMap.toByteArray()
            setUrgent()
        }

        dataClient.putDataItem(request)
    }

    // --- Timer Synchronization ---
    fun syncTimerState(millis: Long, isRunning: Boolean) {
        val dataMap = DataMap().apply {
            putLong(KEY_TIMER_MILLIS, millis)
            putBoolean(KEY_TIMER_RUNNING, isRunning)
            putLong(KEY_TIMESTAMP, System.currentTimeMillis())
        }

        val request = PutDataRequest.create(PATH_TIMER_STATE).apply {
            data = dataMap.toByteArray()
            setUrgent()
        }

        dataClient.putDataItem(request)

        // Send immediate action message
        val action = when {
            isRunning && millis == 0L -> "START"
            isRunning -> "RESUME"
            !isRunning && millis == 0L -> "RESET"
            else -> "PAUSE"
        }
        sendImmediateMessage(MSG_TIMER_ACTION, action)
    }

    // --- Keeper Timer Synchronization ---
    fun syncKeeperTimer(millis: Long, isRunning: Boolean) {
        val dataMap = DataMap().apply {
            putLong(KEY_KEEPER_MILLIS, millis)
            putBoolean(KEY_KEEPER_RUNNING, isRunning)
            putLong(KEY_TIMESTAMP, System.currentTimeMillis())
        }

        val request = PutDataRequest.create(PATH_KEEPER_TIMER).apply {
            data = dataMap.toByteArray()
            setUrgent()
        }

        dataClient.putDataItem(request)
    }

    // --- Match State Synchronization ---
    fun syncMatchState(isActive: Boolean) {
        val dataMap = DataMap().apply {
            putBoolean(KEY_MATCH_ACTIVE, isActive)
            putLong(KEY_TIMESTAMP, System.currentTimeMillis())
        }

        val request = PutDataRequest.create(PATH_MATCH_STATE).apply {
            data = dataMap.toByteArray()
            setUrgent()
        }

        dataClient.putDataItem(request)

        // Send immediate action
        sendImmediateMessage(MSG_MATCH_ACTION, if (isActive) "START_MATCH" else "END_MATCH")
    }

    // --- Player/Scorer Synchronization ---
    fun syncScorerSelected(playerName: String, role: String, team: Int) {
        val message = "$playerName|$role|$team"
        sendImmediateMessage(MSG_SCORER_SELECTED, message)
    }

    // --- Helper for immediate messages ---
    private fun sendImmediateMessage(path: String, message: String) {
        val data = message.toByteArray()

        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, path, data)
                    .addOnSuccessListener {
                        Log.d("WearDataSync", "Message sent to ${node.displayName}: $path = $message")
                    }
                    .addOnFailureListener { e ->
                        Log.e("WearDataSync", "Failed to send message: $path", e)
                    }
            }
        }
    }

    // --- Sync entire state (useful for initial connection) ---
    fun syncFullState(
        team1Score: Int,
        team2Score: Int,
        team1Name: String,
        team2Name: String,
        timerMillis: Long,
        timerRunning: Boolean,
        keeperMillis: Long,
        keeperRunning: Boolean,
        matchActive: Boolean
    ) {
        syncScores(team1Score, team2Score)
        syncTeamNames(team1Name, team2Name)
        syncTimerState(timerMillis, timerRunning)
        syncKeeperTimer(keeperMillis, keeperRunning)
        syncMatchState(matchActive)
    }
}