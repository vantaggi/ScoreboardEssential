package it.vantaggi.scoreboardessential.repository

import android.content.Context
import android.content.SharedPreferences
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MatchRepository(
    private val matchDao: MatchDao,
    private val context: Context,
    private val colorRepository: ColorRepository,
) {
    companion object {
        private const val PREFS_NAME = "it.vantaggi.scoreboardessential.PREFERENCES"
        private const val KEY_TEAM1_COLOR = "team1_color"
        private const val KEY_TEAM2_COLOR = "team2_color"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _team1Color =
        MutableStateFlow(colorRepository.getTeam1DefaultColor())
    val team1Color: StateFlow<Int> = _team1Color

    private val _team2Color =
        MutableStateFlow(colorRepository.getTeam2DefaultColor())
    val team2Color: StateFlow<Int> = _team2Color

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                KEY_TEAM1_COLOR ->
                    _team1Color.value =
                        prefs.getInt(
                            key,
                            colorRepository.getTeam1DefaultColor(),
                        )
                KEY_TEAM2_COLOR ->
                    _team2Color.value =
                        prefs.getInt(
                            key,
                            colorRepository.getTeam2DefaultColor(),
                        )
            }
        }

    init {
        _team1Color.value =
            sharedPreferences.getInt(
                KEY_TEAM1_COLOR,
                colorRepository.getTeam1DefaultColor(),
            )
        _team2Color.value =
            sharedPreferences.getInt(
                KEY_TEAM2_COLOR,
                colorRepository.getTeam2DefaultColor(),
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
}
