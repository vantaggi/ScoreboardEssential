package it.vantaggi.scoreboardessential.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow

class MatchSettingsRepository(
    context: Context,
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("match_settings_prefs", Context.MODE_PRIVATE)

    suspend fun setTeam1Name(name: String) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putString(KEY_TEAM1_NAME, name)
            }
        }

    suspend fun getTeam1Name(): String =
        withContext(Dispatchers.IO) {
            sharedPreferences.getString(KEY_TEAM1_NAME, "Team 1") ?: "Team 1"
        }

    suspend fun setTeam2Name(name: String) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putString(KEY_TEAM2_NAME, name)
            }
        }

    suspend fun getTeam2Name(): String =
        withContext(Dispatchers.IO) {
            sharedPreferences.getString(KEY_TEAM2_NAME, "Team 2") ?: "Team 2"
        }

    suspend fun setTeam1Color(color: Int) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putInt(KEY_TEAM1_COLOR, color)
            }
        }

    suspend fun getTeam1Color(): Int =
        withContext(Dispatchers.IO) {
            sharedPreferences.getInt(KEY_TEAM1_COLOR, Color.parseColor("#FFD600"))
        }

    suspend fun setTeam2Color(color: Int) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putInt(KEY_TEAM2_COLOR, color)
            }
        }

    suspend fun getTeam2Color(): Int =
        withContext(Dispatchers.IO) {
            sharedPreferences.getInt(KEY_TEAM2_COLOR, Color.parseColor("#76FF03"))
        }

    suspend fun setKeeperTimerDuration(duration: Long) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putLong(KEY_KEEPER_TIMER_DURATION, duration)
            }
        }

    suspend fun getKeeperTimerDuration(): Long =
        withContext(Dispatchers.IO) {
            sharedPreferences.getLong(KEY_KEEPER_TIMER_DURATION, 30L)
        }

    fun getSettingsFlow(): Flow<MatchSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            trySend(
                MatchSettings(
                    prefs.getString(KEY_TEAM1_NAME, "Team 1") ?: "Team 1",
                    prefs.getString(KEY_TEAM2_NAME, "Team 2") ?: "Team 2",
                    prefs.getInt(KEY_TEAM1_COLOR, Color.parseColor("#FFD600")),
                    prefs.getInt(KEY_TEAM2_COLOR, Color.parseColor("#76FF03")),
                    prefs.getLong(KEY_KEEPER_TIMER_DURATION, 30L)
                )
            )
        }

        // Emit initial value
        trySend(
            MatchSettings(
                sharedPreferences.getString(KEY_TEAM1_NAME, "Team 1") ?: "Team 1",
                sharedPreferences.getString(KEY_TEAM2_NAME, "Team 2") ?: "Team 2",
                sharedPreferences.getInt(KEY_TEAM1_COLOR, Color.parseColor("#FFD600")),
                sharedPreferences.getInt(KEY_TEAM2_COLOR, Color.parseColor("#76FF03")),
                sharedPreferences.getLong(KEY_KEEPER_TIMER_DURATION, 30L)
            )
        )

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val KEY_TEAM1_NAME = "team1_name"
        private const val KEY_TEAM2_NAME = "team2_name"
        private const val KEY_TEAM1_COLOR = "team1_color"
        private const val KEY_TEAM2_COLOR = "team2_color"
        private const val KEY_KEEPER_TIMER_DURATION = "keeper_timer_duration"
    }
}

data class MatchSettings(
    val team1Name: String,
    val team2Name: String,
    val team1Color: Int,
    val team2Color: Int,
    val keeperTimerDuration: Long
)
