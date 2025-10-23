package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
* Use Case per aggiornare il punteggio di una squadra
* Gestisce la logica di business: validazione, limiti, e sync con Wear
*/
class UpdateScoreUseCase(
    private val wearDataSync: OptimizedWearDataSync,
) {
    data class ScoreState(
        val team1Score: Int = 0,
        val team2Score: Int = 0,
    )

    private val _scoreState = MutableStateFlow(ScoreState())
    val scoreState: StateFlow<ScoreState> = _scoreState

    /**
     * Incrementa il punteggio del team specificato
     * @param teamNumber 1 o 2
     * @return true se l'operazione ha successo
     */
    fun incrementScore(teamNumber: Int): Boolean {
        if (teamNumber !in 1..2) return false

        val currentState = _scoreState.value
        val newState =
            when (teamNumber) {
                1 -> currentState.copy(team1Score = currentState.team1Score + 1)
                2 -> currentState.copy(team2Score = currentState.team2Score + 1)
                else -> return false
            }

        _scoreState.value = newState
        syncToWear()
        return true
    }

    /**
     * Decrementa il punteggio del team specificato
     * Il punteggio non può andare sotto zero
     */
    fun decrementScore(teamNumber: Int): Boolean {
        if (teamNumber !in 1..2) return false

        val currentState = _scoreState.value
        val newState =
            when (teamNumber) {
                1 -> {
                    if (currentState.team1Score <= 0) return false
                    currentState.copy(team1Score = currentState.team1Score - 1)
                }
                2 -> {
                    if (currentState.team2Score <= 0) return false
                    currentState.copy(team2Score = currentState.team2Score - 1)
                }
                else -> return false
            }

        _scoreState.value = newState
        syncToWear()
        return true
    }

    /**
     * Reset dei punteggi (per nuova partita)
     */
    fun resetScores() {
        _scoreState.value = ScoreState()
        syncToWear()
    }

    /**
     * Sincronizza lo stato corrente con Wear OS
     */
    private fun syncToWear() {
        val state = _scoreState.value
        wearDataSync.syncScores(
            team1Score = state.team1Score,
            team2Score = state.team2Score,
            urgent = true,
        )
    }

    /**
     * Imposta i punteggi da fonte esterna (es. sync da Wear)
     */
    fun setScores(
        team1Score: Int,
        team2Score: Int,
    ) {
        _scoreState.value =
            ScoreState(
                team1Score = team1Score.coerceAtLeast(0),
                team2Score = team2Score.coerceAtLeast(0),
            )
    }
}
