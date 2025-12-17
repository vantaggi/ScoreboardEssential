package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

class UpdateScoreUseCase(
    private val wearDataSync: OptimizedWearDataSync
) {
    data class ScoreState(
        val team1Score: Int = 0,
        val team2Score: Int = 0
    )

    private val _scoreState = MutableStateFlow(ScoreState())
    val scoreState: StateFlow<ScoreState> = _scoreState.asStateFlow()

    suspend fun incrementScore(teamId: Int): Boolean {
        if (teamId != 1 && teamId != 2) return false

        val previousState = _scoreState.value
        val newState = _scoreState.updateAndGet { current ->
            if (teamId == 1) {
                current.copy(team1Score = current.team1Score + 1)
            } else {
                current.copy(team2Score = current.team2Score + 1)
            }
        }

        if (newState != previousState) {
            syncScores()
            return true
        }
        return false
    }

    suspend fun decrementScore(teamId: Int): Boolean {
        if (teamId != 1 && teamId != 2) return false

        val previousState = _scoreState.value
        val newState = _scoreState.updateAndGet { current ->
            if (teamId == 1) {
                if (current.team1Score > 0) {
                    current.copy(team1Score = current.team1Score - 1)
                } else current
            } else {
                if (current.team2Score > 0) {
                    current.copy(team2Score = current.team2Score - 1)
                } else current
            }
        }

        if (newState != previousState) {
            syncScores()
            return true
        }
        return false
    }

    suspend fun resetScores() {
        _scoreState.updateAndGet { ScoreState(0, 0) }
        syncScores()
    }

    private suspend fun syncScores() {
        val current = _scoreState.value
        val data = mapOf(
            WearConstants.KEY_TEAM1_SCORE to current.team1Score,
            WearConstants.KEY_TEAM2_SCORE to current.team2Score
        )
        wearDataSync.sendData(WearConstants.PATH_SCORE, data, urgent = true)
    }
}
