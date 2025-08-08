package com.example.scoreboardessential.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MatchRepository(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("match_state", Context.MODE_PRIVATE)

    private val _team1Score = MutableStateFlow(sharedPreferences.getInt("team1_score", 0))
    val team1Score: StateFlow<Int> = _team1Score

    private val _team2Score = MutableStateFlow(sharedPreferences.getInt("team2_score", 0))
    val team2Score: StateFlow<Int> = _team2Score

    private val _team1Name = MutableStateFlow(sharedPreferences.getString("team1_name", "Team 1")!!)
    val team1Name: StateFlow<String> = _team1Name

    private val _team2Name = MutableStateFlow(sharedPreferences.getString("team2_name", "Team 2")!!)
    val team2Name: StateFlow<String> = _team2Name

    private val _team1Id = MutableStateFlow(sharedPreferences.getLong("team1_id", 0L))
    val team1Id: StateFlow<Long> = _team1Id

    private val _team2Id = MutableStateFlow(sharedPreferences.getLong("team2_id", 0L))
    val team2Id: StateFlow<Long> = _team2Id

    private val _team1Color = MutableStateFlow(sharedPreferences.getInt("team1_color", Color.BLACK))
    val team1Color: StateFlow<Int> = _team1Color

    private val _team2Color = MutableStateFlow(sharedPreferences.getInt("team2_color", Color.BLACK))
    val team2Color: StateFlow<Int> = _team2Color

    private val _timerValue = MutableStateFlow(sharedPreferences.getLong("timer_value", 0L))
    val timerValue: StateFlow<Long> = _timerValue

    private val _isTimerRunning = MutableStateFlow(sharedPreferences.getBoolean("is_timer_running", false))
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning

    private val _team1Scorers = MutableStateFlow(sharedPreferences.getStringSet("team1_scorers", emptySet())!!.toList())
    val team1Scorers: StateFlow<List<String>> = _team1Scorers

    private val _team2Scorers = MutableStateFlow(sharedPreferences.getStringSet("team2_scorers", emptySet())!!.toList())
    val team2Scorers: StateFlow<List<String>> = _team2Scorers

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "team1_score" -> _team1Score.value = prefs.getInt(key, 0)
            "team2_score" -> _team2Score.value = prefs.getInt(key, 0)
            "team1_name" -> _team1Name.value = prefs.getString(key, "Team 1")!!
            "team2_name" -> _team2Name.value = prefs.getString(key, "Team 2")!!
            "team1_id" -> _team1Id.value = prefs.getLong(key, 0L)
            "team2_id" -> _team2Id.value = prefs.getLong(key, 0L)
            "team1_color" -> _team1Color.value = prefs.getInt(key, Color.BLACK)
            "team2_color" -> _team2Color.value = prefs.getInt(key, Color.BLACK)
            "timer_value" -> _timerValue.value = prefs.getLong(key, 0L)
            "is_timer_running" -> _isTimerRunning.value = prefs.getBoolean(key, false)
            "team1_scorers" -> _team1Scorers.value = prefs.getStringSet(key, emptySet())!!.toList()
            "team2_scorers" -> _team2Scorers.value = prefs.getStringSet(key, emptySet())!!.toList()
        }
    }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun addTeam1Score() {
        val newScore = _team1Score.value + 1
        with(sharedPreferences.edit()) {
            putInt("team1_score", newScore)
            apply()
        }
    }

    fun subtractTeam1Score() {
        val newScore = (_team1Score.value - 1).coerceAtLeast(0)
        with(sharedPreferences.edit()) {
            putInt("team1_score", newScore)
            apply()
        }
    }

    fun addTeam2Score() {
        val newScore = _team2Score.value + 1
        with(sharedPreferences.edit()) {
            putInt("team2_score", newScore)
            apply()
        }
    }

    fun subtractTeam2Score() {
        val newScore = (_team2Score.value - 1).coerceAtLeast(0)
        with(sharedPreferences.edit()) {
            putInt("team2_score", newScore)
            apply()
        }
    }

    fun setScores(score1: Int, score2: Int) {
        with(sharedPreferences.edit()) {
            putInt("team1_score", score1)
            putInt("team2_score", score2)
            apply()
        }
    }

    fun setTeam1Name(name: String) {
        with(sharedPreferences.edit()) {
            putString("team1_name", name)
            apply()
        }
    }

    fun setTeam2Name(name: String) {
        with(sharedPreferences.edit()) {
            putString("team2_name", name)
            apply()
        }
    }

    fun setTeamIds(id1: Long, id2: Long) {
        with(sharedPreferences.edit()) {
            putLong("team1_id", id1)
            putLong("team2_id", id2)
            apply()
        }
    }

    fun setTeamColor(team: Int, color: Int) {
        val key = if (team == 1) "team1_color" else "team2_color"
        with(sharedPreferences.edit()) {
            putInt(key, color)
            apply()
        }
    }

    fun resetScores() {
        with(sharedPreferences.edit()) {
            putInt("team1_score", 0)
            putInt("team2_score", 0)
            putStringSet("team1_scorers", emptySet())
            putStringSet("team2_scorers", emptySet())
            apply()
        }
    }

    fun setTimer(seconds: Long) {
        with(sharedPreferences.edit()) {
            putLong("timer_value", seconds * 1000)
            apply()
        }
    }

    fun setTimerValue(millis: Long) {
        with(sharedPreferences.edit()) {
            putLong("timer_value", millis)
            apply()
        }
    }

    fun setTimerRunning(isRunning: Boolean) {
        with(sharedPreferences.edit()) {
            putBoolean("is_timer_running", isRunning)
            apply()
        }
    }

    fun addScorer(team: Int, scorer: String) {
        val key = if (team == 1) "team1_scorers" else "team2_scorers"
        val currentScorers = sharedPreferences.getStringSet(key, emptySet()) ?: emptySet()
        val newScorers = currentScorers.toMutableSet().apply { add(scorer) }
        with(sharedPreferences.edit()) {
            putStringSet(key, newScorers)
            apply()
        }
    }
}
