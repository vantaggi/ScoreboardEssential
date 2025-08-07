package com.example.scoreboardessential

import com.example.scoreboardessential.repository.MatchRepository
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// A service that listens for messages from the Wear OS app.
class DataLayerListenerService : WearableListenerService() {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val matchRepository: MatchRepository by lazy {
        (application as ScoreboardEssentialApplication).matchRepository
    }

    // Called when a message is received from the Wear OS app.
    override fun onMessageReceived(messageEvent: MessageEvent) {
        coroutineScope.launch {
            when (messageEvent.path) {
                "/score_update" -> {
                    val team = String(messageEvent.data)
                    if (team == "team_1") {
                        matchRepository.addTeam1Score()
                    } else if (team == "team_2") {
                        matchRepository.addTeam2Score()
                    }
                }
                "/start_stop_timer" -> {
                    // This requires access to the ViewModel's startStopTimer method,
                    // which contains the CountDownTimer logic.
                    // For now, let's just toggle the running state in the repo.
                    // The ViewModel will observe this and start/stop the timer.
                    val isRunning = matchRepository.isTimerRunning.value
                    matchRepository.setTimerRunning(!isRunning)
                }
                "/reset_timer" -> {
                    matchRepository.setTimerValue(0)
                    matchRepository.setTimerRunning(false)
                }
                "/reset_scores" -> {
                    matchRepository.resetScores()
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        // TODO: Handle data changes from the wear app if necessary (e.g., timer sync)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
