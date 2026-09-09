package it.vantaggi.scoreboardessential.shared.communication

/**
 * Single source of truth for the phone <-> watch wire protocol.
 *
 * Every path and DataMap key declared here is actively used by both ends of the
 * Wearable Data Layer sync. Do not add constants speculatively: an unused path is
 * indistinguishable from a protocol bug during debugging.
 */
object WearConstants {
    // Capability
    //
    // scoreboard_app e' dichiarata da ENTRAMBI i lati, quindi non distingue "esiste un telefono"
    // da "esiste un orologio". Le due nuove sono ADDITIVE e vanno dichiarate INSIEME alla vecchia
    // per almeno una release: se il telefono smettesse di cercare scoreboard_app mentre un
    // orologio non aggiornato dichiara solo quella, la coppia smetterebbe di vedersi.
    const val CAPABILITY_SCOREBOARD_APP = "scoreboard_app"
    const val CAPABILITY_PHONE = "scoreboard_phone"
    const val CAPABILITY_WATCH = "scoreboard_watch"

    // Data Paths (DataClient items, state that should persist on the node)
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

    // --- Protocollo v2 ---------------------------------------------------------------------
    //
    // Regola assoluta: si AGGIUNGE un path, non se ne cambia mai uno. La leva di compatibilita'
    // e' verificata nel codice: ne' SimplifiedDataLayerListenerService ne' WearDataLayerService
    // hanno un ramo `else` nel loro `when` sul path, quindi un path sconosciuto e' un no-op
    // silenzioso su un orologio non aggiornato.
    //
    // Nel v2 il telefono e' AUTORITATIVO e manda il punteggio gia' impaginato. L'orologio non
    // esegue mai regole: rende stringhe. Cosi' aggiungere uno sport non richiede una riga di
    // codice sul lato orologio, e sparisce la classe di aggiornamenti perduti in cui i due lati
    // calcolavano il punteggio ciascuno dalla propria copia e pubblicavano il valore assoluto
    // sullo stesso path coalescente.
    const val PATH_STATE_V2 = "/scoreboard/v2/state"

    /** Il tocco sull'orologio e' un'INTENZIONE, non uno stato. Su MessageClient: non coalescente. */
    const val MSG_SCORE_INTENT = "/scoreboard/v2/intent"

    const val PROTO_VERSION = 2

    const val KEY_PROTO_VERSION = "proto_version"
    const val KEY_SPORT_ID = "sport_id"
    const val KEY_SIDE1_PRIMARY = "side1_primary"
    const val KEY_SIDE1_SECONDARY = "side1_secondary"
    const val KEY_SIDE2_PRIMARY = "side2_primary"
    const val KEY_SIDE2_SECONDARY = "side2_secondary"
    const val KEY_PERIOD_LABEL = "period_label"
    const val KEY_SERVING_SIDE = "serving_side"

    // Capacita' che l'orologio deve conoscere per non mostrare comandi privi di senso.
    const val KEY_CAP_HAS_CLOCK = "cap_has_clock"
    const val KEY_CAP_HAS_AUX_TIMER = "cap_has_aux_timer"
    const val KEY_CAP_ATTRIBUTES_SCORER = "cap_attributes_scorer"
    const val KEY_CAP_DECREMENT_IS_UNDO = "cap_decrement_is_undo"

    /** Numero di sequenza monotono per nodo: il telefono ignora cio' che ha gia' visto. */
    const val KEY_SEQ = "seq"
    const val KEY_SIDE = "side"

    // Message Paths (MessageClient, fire-and-forget triggers)
    const val MSG_SCORER_SELECTED = "/scoreboard/scorer_selected"
    const val MSG_REQUEST_SYNC = "/scoreboard/request_sync"

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
    const val KEY_PLAYER_ID = "player_id"
    const val KEY_PLAYER_ROLES = "player_roles"
    const val KEY_TEAM_COLOR = "team_color"
    const val KEY_MATCH_ACTIVE = "match_active"
    const val KEY_TEST_DATA = "test_data"
    const val EXTRA_TEAM_NUMBER = "team_number"

    // Retry Logic
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY_MS = 200L
    const val MESSAGE_TIMEOUT_MS = 5000L
}
