package it.vantaggi.scoreboardessential.domain.scoring

import it.vantaggi.scoreboardessential.domain.model.SportType

class SoccerScoringStrategy : ScoringStrategy {
    override fun getInitialState(): ScoreBoardState {
        return ScoreBoardState(
            team1Score = "0",
            team2Score = "0",
            sportType = SportType.SOCCER
        )
    }

    override fun addPoint(currentState: ScoreBoardState, teamId: Int): ScoreBoardState {
        if (currentState.isMatchFinished) return currentState

        var t1 = currentState.team1Score.toIntOrNull() ?: 0
        var t2 = currentState.team2Score.toIntOrNull() ?: 0

        if (teamId == 1) t1++ else t2++

        return currentState.copy(
            team1Score = t1.toString(),
            team2Score = t2.toString()
        )
    }

    override fun removePoint(currentState: ScoreBoardState, teamId: Int): ScoreBoardState {
        var t1 = currentState.team1Score.toIntOrNull() ?: 0
        var t2 = currentState.team2Score.toIntOrNull() ?: 0

        if (teamId == 1 && t1 > 0) t1--
        if (teamId == 2 && t2 > 0) t2--

        return currentState.copy(
            team1Score = t1.toString(),
            team2Score = t2.toString()
        )
    }

    override fun getSportType(): SportType = SportType.SOCCER
}
