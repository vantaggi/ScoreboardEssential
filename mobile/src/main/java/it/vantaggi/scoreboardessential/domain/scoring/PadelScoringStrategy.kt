package it.vantaggi.scoreboardessential.domain.scoring

import it.vantaggi.scoreboardessential.domain.model.SportType

class PadelScoringStrategy(
    private val initialGoldenPoint: Boolean = false // If false, use Deuce-Adv logic
) : ScoringStrategy {

    private val POINT_SEQUENCE = listOf("0", "15", "30", "40")

    override fun getSportType(): SportType = SportType.PADEL

    override fun getInitialState(): ScoreBoardState {
        return ScoreBoardState(
            team1Score = "0",
            team2Score = "0",
            team1Sets = listOf(0),
            team2Sets = listOf(0),
            servingTeam = 1,
            sportType = SportType.PADEL,
            servingSide = "R",
            isGoldenPoint = false
        )
    }

    override fun addPoint(currentState: ScoreBoardState, teamId: Int): ScoreBoardState {
        if (currentState.isMatchFinished) return currentState

        // Check for Tie Break
        if (isTieBreak(currentState)) {
            return handleTieBreakPoint(currentState, teamId)
        }

        // Standard Game Logic
        return handleStandardGamePoint(currentState, teamId)
    }

    private fun handleStandardGamePoint(state: ScoreBoardState, teamId: Int): ScoreBoardState {
        val p1 = state.team1Score
        val p2 = state.team2Score
        
        val idx1 = POINT_SEQUENCE.indexOf(p1)
        val idx2 = POINT_SEQUENCE.indexOf(p2)
        
        val isAdv1 = p1 == "Adv"
        val isAdv2 = p2 == "Adv"

        // Resulting points/state
        var nextP1 = p1
        var nextP2 = p2
        var nextIsGoldenPoint = state.isGoldenPoint
        var winGame = false

        if (teamId == 1) {
            when {
                // If it's Golden Point, immediate win
                state.isGoldenPoint && p1 == "40" && p2 == "40" -> winGame = true
                
                // Win from Advantage
                isAdv1 -> winGame = true
                
                // Win from 40 if opponent < 40
                p1 == "40" && idx2 < 3 -> winGame = true
                
                // 40-40 Situations
                p1 == "40" && p2 == "40" -> {
                    if (isAdv2) {
                        nextP1 = "40"
                        nextP2 = "40"
                        nextIsGoldenPoint = true // Return to Deuce -> Golden Point
                    } else {
                        // Pure 40-40 -> Go to Advantage
                        nextP1 = "Adv"
                    }
                }
                
                // Normal increment (0, 15, 30 -> 15, 30, 40)
                idx1 != -1 && idx1 < 3 -> nextP1 = POINT_SEQUENCE[idx1 + 1]
            }
        } else {
            when {
                // If it's Golden Point, immediate win
                state.isGoldenPoint && p1 == "40" && p2 == "40" -> winGame = true
                
                // Win from Advantage
                isAdv2 -> winGame = true
                
                // Win from 40 if opponent < 40
                p2 == "40" && idx1 < 3 -> winGame = true
                
                // 40-40 Situations
                p1 == "40" && p2 == "40" -> {
                    if (isAdv1) {
                        nextP1 = "40"
                        nextP2 = "40"
                        nextIsGoldenPoint = true
                    } else {
                        // Pure 40-40 -> Go to Advantage
                        nextP2 = "Adv"
                    }
                }
                
                // Normal increment
                idx2 != -1 && idx2 < 3 -> nextP2 = POINT_SEQUENCE[idx2 + 1]
            }
        }

        if (winGame) {
            return winGame(state, teamId)
        }

        // Calculate Next Serving Side
        val nextIdx1 = POINT_SEQUENCE.indexOf(nextP1).takeIf { it != -1 } ?: 4 // Adv is 4
        val nextIdx2 = POINT_SEQUENCE.indexOf(nextP2).takeIf { it != -1 } ?: 4
        val sum = nextIdx1 + nextIdx2
        val nextServingSide = if (sum % 2 == 0) "R" else "L"

        return state.copy(
            team1Score = nextP1,
            team2Score = nextP2,
            isGoldenPoint = nextIsGoldenPoint,
            servingSide = nextServingSide
        )
    }

    private fun isTieBreak(state: ScoreBoardState): Boolean {
        val currentSetIndex = state.team1Sets.size - 1
        val t1Games = state.team1Sets[currentSetIndex]
        val t2Games = state.team2Sets[currentSetIndex]
        return t1Games == 6 && t2Games == 6
    }

    private fun handleTieBreakPoint(state: ScoreBoardState, teamId: Int): ScoreBoardState {
        var p1 = state.team1Score.toIntOrNull() ?: 0
        var p2 = state.team2Score.toIntOrNull() ?: 0

        val originalServingTeam = getTieBreakStarter(state)

        if (teamId == 1) p1++ else p2++

        // Check for Tie Break Win (11 points and 2 clear as per user request)
        if (p1 >= 11 && p1 >= p2 + 2) return winSet(state, 1)
        if (p2 >= 11 && p2 >= p1 + 2) return winSet(state, 2)

        // Tie Break Server Rotation (ABBA style logic for teams)
        // Point 1: A. Points 2,3: B. Points 4,5: A. Points 6,7: B...
        val totalPoints = p1 + p2
        val currentServingTeam = if (totalPoints == 0) {
            originalServingTeam
        } else {
            val sequenceIndex = (totalPoints - 1) / 2
            if (sequenceIndex % 2 == 0) {
                // Team B's turn to serve (if first was A)
                if (originalServingTeam == 1) 2 else 1
            } else {
                // Team A's turn to serve
                originalServingTeam
            }
        }

        // Serving Side in Tie Break alternates every point
        val nextServingSide = if (totalPoints % 2 == 0) "R" else "L"

        return state.copy(
            team1Score = p1.toString(),
            team2Score = p2.toString(),
            servingTeam = currentServingTeam,
            servingSide = nextServingSide
        )
    }

    private fun getTieBreakStarter(state: ScoreBoardState): Int {
        // Tiebreak starts after 6-6 (12 games).
        // If they serve 1 game each, after 12 games it should be the turn of the one who started the set.
        // However, we don't track who started the set.
        // We'll trust the servingTeam from the state at 6-6.
        return state.servingTeam ?: 1
    }

    private fun winGame(state: ScoreBoardState, winnerTeamId: Int): ScoreBoardState {
        val sets1 = state.team1Sets.toMutableList()
        val sets2 = state.team2Sets.toMutableList()
        val currentSetIdx = sets1.size - 1
        
        if (winnerTeamId == 1) sets1[currentSetIdx]++ else sets2[currentSetIdx]++
        
        val g1 = sets1[currentSetIdx]
        val g2 = sets2[currentSetIdx]

        // Check for Set Win
        // 6 games (by 2 clear), or 7-5, or 7-6 (Tiebreak outcome handled in winSet)
        var setWon = false
        if ((g1 >= 6 && g1 >= g2 + 2) || (g1 == 7 && g2 == 5)) {
             setWon = true
             if (countSetsWon(sets1, sets2, 1) + 1 == 2) {
                 return state.copy(
                     team1Sets = sets1, team2Sets = sets2,
                     team1Score = "0", team2Score = "0",
                     isMatchFinished = true, winnerTeamId = 1,
                     isGoldenPoint = false, servingSide = "R"
                 )
             }
        } else if ((g2 >= 6 && g2 >= g1 + 2) || (g2 == 7 && g1 == 5)) {
            setWon = true
            if (countSetsWon(sets1, sets2, 2) + 1 == 2) {
                 return state.copy(
                     team1Sets = sets1, team2Sets = sets2,
                     team1Score = "0", team2Score = "0",
                     isMatchFinished = true, winnerTeamId = 2,
                     isGoldenPoint = false, servingSide = "R"
                 )
             }
        }

        val nextServing = if (state.servingTeam == 1) 2 else 1
        if (setWon) {
            sets1.add(0)
            sets2.add(0)
        }
        
        return state.copy(
            team1Score = "0",
            team2Score = "0",
            team1Sets = sets1,
            team2Sets = sets2,
            servingTeam = nextServing,
            isGoldenPoint = false,
            servingSide = "R"
        )
    }
    
    private fun winSet(state: ScoreBoardState, winnerTeamId: Int): ScoreBoardState {
        val sets1 = state.team1Sets.toMutableList()
        val sets2 = state.team2Sets.toMutableList()
        val currentSetIdx = sets1.size - 1
        
        if (winnerTeamId == 1) sets1[currentSetIdx] = 7 else sets2[currentSetIdx] = 7
        
        if (countSetsWon(sets1, sets2, 1) == 2) return state.copy(team1Sets=sets1, team2Sets=sets2, isMatchFinished=true, winnerTeamId=1, team1Score="0", team2Score="0")
        if (countSetsWon(sets1, sets2, 2) == 2) return state.copy(team1Sets=sets1, team2Sets=sets2, isMatchFinished=true, winnerTeamId=2, team1Score="0", team2Score="0")
        
        sets1.add(0)
        sets2.add(0)
        val nextServing = if (state.servingTeam == 1) 2 else 1

        return state.copy(
            team1Score = "0",
            team2Score = "0",
            team1Sets = sets1,
            team2Sets = sets2,
            servingTeam = nextServing,
            isGoldenPoint = false,
            servingSide = "R"
        )
    }

    private fun countSetsWon(s1: List<Int>, s2: List<Int>, teamToCheck: Int): Int {
        var won = 0
        for (i in s1.indices) {
            if (i >= s2.size) break
            val g1 = s1[i]
            val g2 = s2[i]
            if (g1 >= 6 && g1 >= g2 + 2) { if(teamToCheck==1) won++ }
            else if (g1 == 7 && g2 == 5) { if(teamToCheck==1) won++ }
            else if (g1 == 7 && g2 == 6) { if(teamToCheck==1) won++ }
            
            if (g2 >= 6 && g2 >= g1 + 2) { if(teamToCheck==2) won++ }
            else if (g2 == 7 && g1 == 5) { if(teamToCheck==2) won++ }
            else if (g2 == 7 && g1 == 6) { if(teamToCheck==2) won++ }
        }
        return won
    }

    override fun removePoint(currentState: ScoreBoardState, teamId: Int): ScoreBoardState {
        return currentState // Handled by ViewModel undo stack
    }
}
