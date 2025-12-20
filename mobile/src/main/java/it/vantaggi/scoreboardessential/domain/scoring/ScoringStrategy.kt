package it.vantaggi.scoreboardessential.domain.scoring

import it.vantaggi.scoreboardessential.domain.model.SportType

data class ScoreBoardState(
    val team1Score: String, // Main display score (e.g. "2" or "30")
    val team2Score: String,
    val team1Sets: List<Int> = emptyList(), // For Padel/Tennis
    val team2Sets: List<Int> = emptyList(),
    val servingTeam: Int? = null, // 1 or 2, null if not applicable
    val isMatchFinished: Boolean = false,
    val winnerTeamId: Int? = null,
    val sportType: SportType,
    val servingSide: String? = "R", // "R" (Right/Destra) or "L" (Left/Sinistra)
    val isGoldenPoint: Boolean = false
)

interface ScoringStrategy {
    fun getInitialState(): ScoreBoardState
    fun addPoint(currentState: ScoreBoardState, teamId: Int): ScoreBoardState
    fun removePoint(currentState: ScoreBoardState, teamId: Int): ScoreBoardState // Undo
    fun getSportType(): SportType
}
