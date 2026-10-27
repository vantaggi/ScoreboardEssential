package it.vantaggi.scoreboardessential.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import it.vantaggi.scoreboardessential.core.DeuceRule
import it.vantaggi.scoreboardessential.core.RacketRules
import it.vantaggi.scoreboardessential.core.SportRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class MatchSettingsRepository(
    context: Context,
    private val colorRepository: ColorRepository,
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
            sharedPreferences.getString(KEY_TEAM1_NAME, DEFAULT_TEAM1_NAME) ?: DEFAULT_TEAM1_NAME
        }

    suspend fun setTeam2Name(name: String) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putString(KEY_TEAM2_NAME, name)
            }
        }

    suspend fun getTeam2Name(): String =
        withContext(Dispatchers.IO) {
            sharedPreferences.getString(KEY_TEAM2_NAME, DEFAULT_TEAM2_NAME) ?: DEFAULT_TEAM2_NAME
        }

    suspend fun setTeam1Color(color: Int) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putInt(KEY_TEAM1_COLOR, color)
            }
        }

    suspend fun getTeam1Color(): Int =
        withContext(Dispatchers.IO) {
            sharedPreferences.getInt(KEY_TEAM1_COLOR, colorRepository.getTeam1DefaultColor())
        }

    suspend fun setTeam2Color(color: Int) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putInt(KEY_TEAM2_COLOR, color)
            }
        }

    suspend fun getTeam2Color(): Int =
        withContext(Dispatchers.IO) {
            sharedPreferences.getInt(KEY_TEAM2_COLOR, colorRepository.getTeam2DefaultColor())
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

    /**
     * Lo sport selezionato.
     *
     * Il default e' il calcio, quindi ogni installazione esistente non vede nulla di nuovo. La
     * stringa non e' interpretata qui: la verita' sulle regole vive nel registro in :core.
     */
    suspend fun setActiveSport(sportId: String) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putString(KEY_ACTIVE_SPORT, sportId)
            }
        }

    suspend fun getActiveSport(): String =
        withContext(Dispatchers.IO) {
            sharedPreferences.getString(KEY_ACTIVE_SPORT, DEFAULT_SPORT) ?: DEFAULT_SPORT
        }

    /**
     * La regola del 40-40 scelta per uno sport con racchetta, o null se non e' mai stata scelta:
     * vale quella del registro (punto secco nel padel, vantaggi nel tennis). Una per sport, perche'
     * chi gioca il padel col killer point non per questo gioca il tennis senza vantaggi.
     */
    suspend fun setDeuceRule(
        sportId: String,
        regola: DeuceRule,
    ) = withContext(Dispatchers.IO) {
        sharedPreferences.edit {
            putString(chiaveDel40(sportId), regola.name)
        }
    }

    suspend fun getDeuceRule(sportId: String): DeuceRule? =
        withContext(Dispatchers.IO) {
            DeuceRule.daNome(sharedPreferences.getString(chiaveDel40(sportId), null))
        }

    private fun regoleDel40(prefs: SharedPreferences): Map<String, DeuceRule> =
        SportRegistry
            .selectable()
            .filterIsInstance<RacketRules>()
            .mapNotNull { sport -> DeuceRule.daNome(prefs.getString(chiaveDel40(sport.id), null))?.let { sport.id to it } }
            .toMap()

    fun getSettingsFlow(): Flow<MatchSettings> =
        callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                    trySend(
                        MatchSettings(
                            prefs.getString(KEY_TEAM1_NAME, DEFAULT_TEAM1_NAME) ?: DEFAULT_TEAM1_NAME,
                            prefs.getString(KEY_TEAM2_NAME, DEFAULT_TEAM2_NAME) ?: DEFAULT_TEAM2_NAME,
                            prefs.getInt(KEY_TEAM1_COLOR, colorRepository.getTeam1DefaultColor()),
                            prefs.getInt(KEY_TEAM2_COLOR, colorRepository.getTeam2DefaultColor()),
                            prefs.getLong(KEY_KEEPER_TIMER_DURATION, 30L),
                            prefs.getString(KEY_ACTIVE_SPORT, DEFAULT_SPORT) ?: DEFAULT_SPORT,
                            regoleDel40(prefs),
                        ),
                    )
                }

            // Emit initial value
            trySend(
                MatchSettings(
                    sharedPreferences.getString(KEY_TEAM1_NAME, DEFAULT_TEAM1_NAME) ?: DEFAULT_TEAM1_NAME,
                    sharedPreferences.getString(KEY_TEAM2_NAME, DEFAULT_TEAM2_NAME) ?: DEFAULT_TEAM2_NAME,
                    sharedPreferences.getInt(KEY_TEAM1_COLOR, colorRepository.getTeam1DefaultColor()),
                    sharedPreferences.getInt(KEY_TEAM2_COLOR, colorRepository.getTeam2DefaultColor()),
                    sharedPreferences.getLong(KEY_KEEPER_TIMER_DURATION, 30L),
                    sharedPreferences.getString(KEY_ACTIVE_SPORT, DEFAULT_SPORT) ?: DEFAULT_SPORT,
                    regoleDel40(sharedPreferences),
                ),
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
        private const val KEY_ACTIVE_SPORT = "active_sport"
        private const val DEFAULT_SPORT = "football"

        /**
         * I nomi predefiniti come stanno nei dati: dicono "nessun nome scelto" (EtichettaSquadra) e
         * restano in inglese perche' arrivano anche all'orologio e nelle partite salvate. A schermo
         * li traduce EtichettaSquadra.mostrato.
         */
        const val DEFAULT_TEAM1_NAME = "Team 1"
        const val DEFAULT_TEAM2_NAME = "Team 2"

        private fun chiaveDel40(sportId: String) = "deuce_rule_$sportId"
    }
}

data class MatchSettings(
    val team1Name: String,
    val team2Name: String,
    val team1Color: Int,
    val team2Color: Int,
    val keeperTimerDuration: Long,
    val activeSport: String = "football",
    /** La regola del 40-40 scelta, per sport; uno sport che manca gioca con quella del registro. */
    val regoleDel40: Map<String, DeuceRule> = emptyMap(),
)
