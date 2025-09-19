// shared/src/main/java/com/example/scoreboardessential/communication/WearConstants.kt
package it.vantaggi.scoreboardessential.shared.communication

/**
 * Costanti centralizzate per evitare discrepanze nei path
 * Basato sulla documentazione sezione "Silent Failures"
 */
object WearConstants {
    const val CAPABILITY_SCOREBOARD_APP = "scoreboard_essential_app"

    // Path prefissi per intent-filter optimization
    const val PATH_PREFIX = "/scoreboard"

    // DataClient paths (persistenti)
    const val PATH_SCORE = "$PATH_PREFIX/score_data"
    const val PATH_TEAM_NAMES = "$PATH_PREFIX/team_names"
    const val PATH_TEAM_COLORS = "$PATH_PREFIX/team_colors"
    const val PATH_PLAYERS = "$PATH_PREFIX/players"
    const val PATH_MATCH_STATE = "$PATH_PREFIX/match_state"
    const val PATH_TEAM_PLAYERS = "$PATH_PREFIX/team_players"
    const val PATH_TIMER_STATE = "$PATH_PREFIX/timer_state"
    const val PATH_KEEPER_TIMER = "$PATH_PREFIX/keeper_timer"
    const val PATH_TEAM1_COLOR = "$PATH_PREFIX/team1_color"
    const val PATH_TEAM2_COLOR = "$PATH_PREFIX/team2_color"

    // MessageClient paths (effimeri)
    const val MSG_SCORE_CHANGED = "$PATH_PREFIX/msg/score_changed"
    const val MSG_TIMER_ACTION = "$PATH_PREFIX/msg/timer_action"
    const val MSG_KEEPER_ACTION = "$PATH_PREFIX/msg/keeper_action"
    const val MSG_MATCH_ACTION = "$PATH_PREFIX/msg/match_action"
    const val MSG_SCORER_SELECTED = "$PATH_PREFIX/msg/scorer_selected"
    const val MSG_HEARTBEAT = "$PATH_PREFIX/msg/heartbeat"

    // ChannelClient paths (streaming)
    const val CHANNEL_TIMER_STREAM = "$PATH_PREFIX/channel/timer"
    const val CHANNEL_SENSOR_STREAM = "$PATH_PREFIX/channel/sensors"

    // Keys comuni
    const val KEY_TEAM1_SCORE = "team1_score"
    const val KEY_TEAM2_SCORE = "team2_score"
    const val KEY_TEAM1_NAME = "team1_name"
    const val KEY_TEAM2_NAME = "team2_name"
    const val KEY_TIMESTAMP = "timestamp"
    const val KEY_URGENT = "is_urgent"
    const val KEY_TIMER_MILLIS = "timer_millis"
    const val KEY_TIMER_RUNNING = "timer_running"
    const val KEY_KEEPER_MILLIS = "keeper_millis"
    const val KEY_KEEPER_RUNNING = "keeper_running"
    const val KEY_MATCH_ACTIVE = "match_active"

    // Timeouts e retry
    const val MESSAGE_TIMEOUT_MS = 5000L
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY_MS = 1000L
}
