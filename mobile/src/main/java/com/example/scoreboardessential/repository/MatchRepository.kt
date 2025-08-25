package com.example.scoreboardessential.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.scoreboardessential.database.Match
import com.example.scoreboardessential.database.MatchDao
import com.example.scoreboardessential.database.MatchWithPlayers
import com.example.scoreboardessential.database.MatchWithTeams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


class MatchRepository(
    private val matchDao: MatchDao,
    private val context: Context,
    private val colorRepository: ColorRepository
) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("com.example.scoreboardessential.PREFERENCES", Context.MODE_PRIVATE)

    private val _team1Color =
        MutableStateFlow(colorRepository.getTeam1DefaultColor())
    val team1Color: StateFlow<Int> = _team1Color

    private val _team2Color =
        MutableStateFlow(colorRepository.getTeam2DefaultColor())
    val team2Color: StateFlow<Int> = _team2Color

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                "team1_color" -> _team1Color.value = prefs.getInt(
                    key,
                    colorRepository.getTeam1DefaultColor()
                )
                "team2_color" -> _team2Color.value = prefs.getInt(
                    key,
                    colorRepository.getTeam2DefaultColor()
                )
            }
        }

    init {
        _team1Color.value = sharedPreferences.getInt(
            "team1_color",
            colorRepository.getTeam1DefaultColor()
        )
        _team2Color.value = sharedPreferences.getInt(
            "team2_color",
            colorRepository.getTeam2DefaultColor()
        )
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    fun close() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    val allMatches: Flow<List<MatchWithTeams>> = matchDao.getAllMatchesWithTeams()

    suspend fun deleteMatch(match: Match) {
        matchDao.delete(match)
    }

    fun getActiveMatch(): Flow<MatchWithTeams?> {
        return matchDao.getActiveMatch()
    }

    suspend fun updateActiveMatch(matchId: Long) {
        matchDao.updateActiveMatch(matchId)
    }

    suspend fun deactivateMatch(matchId: Long) {
        matchDao.deactivateMatch(matchId)
    }

    suspend fun insertMatch(match: Match): Long {
        return matchDao.insert(match)
    }

    suspend fun updateMatch(match: Match) {
        matchDao.update(match)
    }
}
