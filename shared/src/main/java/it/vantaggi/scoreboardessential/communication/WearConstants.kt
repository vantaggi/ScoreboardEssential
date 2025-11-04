package it.vantaggi.scoreboardessential.shared.communication

object WearConstants {
    // Capability
    const val CAPABILITY_SCOREBOARD_APP = "scoreboard_app"

    // Data Paths
    const val PATH_SCORE = "/scoreboard/score"
    const val PATH_TEAM_NAMES = "/scoreboard/team_names"
    const val PATH_TEAM1_COLOR = "/scoreboard/team1_color"
    const val PATH_TEAM2_COLOR = "/scoreboard/team2_color"
    const val PATH_TIMER_STATE = "/scoreboard/timer_state"
    const val PATH_KEEPER_TIMER = "/scoreboard/keeper_timer"
    const val PATH_MATCH_STATE = "/scoreboard/match_state"
    const val PATH_PLAYERS = "/scoreboard/players"
    const val PATH_TEAM_PLAYERS = "/scoreboard/team_players"
    const val PATH_TEST_PING = "/scoreboard/test_ping"
    const val PATH_TEST_PONG = "/scoreboard/test_pong"

    // Message Paths
    const val MSG_HEARTBEAT = "/scoreboard/heartbeat"
    const val MSG_SCORER_SELECTED = "/scoreboard/scorer_selected"
    const val MSG_SCORE_CHANGED = "/scoreboard/score_changed"
    const val MSG_TIMER_ACTION = "/scoreboard/timer_action"
    const val MSG_MATCH_ACTION = "/scoreboard/match_action"

    // Channel Paths
    const val CHANNEL_TIMER_STREAM = "/scoreboard/timer_stream"

    // DataMap Keys
    const val KEY_TEAM1_SCORE = "team1_score"
    const val KEY_TEAM2_SCORE = "team2_score"
    const val KEY_TEAM1_NAME = "team1_name"
    const val KEY_TEAM2_NAME = "team2_name"
    const val KEY_TIMER_MILLIS = "timer_millis"
    const val KEY_TIMER_RUNNING = "timer_running"
    const val KEY_KEEPER_MILLIS = "keeper_millis"
    const val KEY_KEEPER_RUNNING = "keeper_running"
    const val KEY_TIMESTAMP = "timestamp"
    const val KEY_PLAYERS = "players"
    const val KEY_TEAM1_PLAYERS = "team1_players"
    const val KEY_TEAM2_PLAYERS = "team2_players"
    const val KEY_PLAYER_NAME = "player_name"
    const val KEY_PLAYER_ROLES = "player_roles"
    const val KEY_PLAYER_ID = "player_id"
    const val KEY_PLAYER_GOALS = "player_goals"
    const val KEY_PLAYER_APPEARANCES = "player_appearances"
    const val KEY_MATCH_ACTIVE = "match_active"
    const val KEY_TEST_DATA = "test_data"

    // Retry Logic
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY_MS = 200L
    const val MESSAGE_TIMEOUT_MS = 5000L
}
