package it.vantaggi.scoreboardessential.shared.communication

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contratto di filo fra due binari versionati in modo INDIPENDENTE.
 *
 * Il telefono e l'orologio sono due APK distinti, che l'utente aggiorna in momenti diversi (e
 * `wear/build.gradle` usa apposta uno scarto di 2000 sul versionCode). Rinominare una costante
 * qui compila pulito su entrambi i moduli, passa ogni test esistente, e rompe **ogni orologio
 * gia' installato**: il path scritto dal telefono nuovo non corrisponde piu' a quello che
 * l'orologio vecchio ascolta, e il difetto si manifesta solo su un dispositivo reale.
 *
 * Questo test congela i valori LETTERALI. Non e' un test del codice: e' un test della promessa.
 *
 * **Se sei qui perche' questo test e' rosso, la risposta quasi certamente NON e' aggiornare il
 * valore atteso.** Un path o una chiave gia' spediti non si cambiano: se ne aggiunge uno nuovo e
 * si lascia il vecchio in scrittura per almeno una release. Cambiare il valore atteso qui e'
 * legittimo solo per una costante mai arrivata in produzione.
 */
class WearProtocolGoldenTest {
    @Test
    fun `i path v1 sono congelati`() {
        assertEquals("/scoreboard/score", WearConstants.PATH_SCORE)
        assertEquals("/scoreboard/team_names", WearConstants.PATH_TEAM_NAMES)
        assertEquals("/scoreboard/team1_color", WearConstants.PATH_TEAM1_COLOR)
        assertEquals("/scoreboard/team2_color", WearConstants.PATH_TEAM2_COLOR)
        assertEquals("/scoreboard/timer_state", WearConstants.PATH_TIMER_STATE)
        assertEquals("/scoreboard/keeper_timer", WearConstants.PATH_KEEPER_TIMER)
        assertEquals("/scoreboard/match_state", WearConstants.PATH_MATCH_STATE)
        assertEquals("/scoreboard/players", WearConstants.PATH_PLAYERS)
        assertEquals("/scoreboard/team_players", WearConstants.PATH_TEAM_PLAYERS)
        assertEquals("/scoreboard/test_ping", WearConstants.PATH_TEST_PING)
        assertEquals("/scoreboard/scorer_selected", WearConstants.MSG_SCORER_SELECTED)
        assertEquals("/scoreboard/request_sync", WearConstants.MSG_REQUEST_SYNC)
    }

    @Test
    fun `le chiavi DataMap v1 sono congelate`() {
        assertEquals("team1_score", WearConstants.KEY_TEAM1_SCORE)
        assertEquals("team2_score", WearConstants.KEY_TEAM2_SCORE)
        assertEquals("team1_name", WearConstants.KEY_TEAM1_NAME)
        assertEquals("team2_name", WearConstants.KEY_TEAM2_NAME)
        assertEquals("timer_millis", WearConstants.KEY_TIMER_MILLIS)
        assertEquals("timer_running", WearConstants.KEY_TIMER_RUNNING)
        assertEquals("keeper_millis", WearConstants.KEY_KEEPER_MILLIS)
        assertEquals("keeper_running", WearConstants.KEY_KEEPER_RUNNING)
        assertEquals("players", WearConstants.KEY_PLAYERS)
        assertEquals("team1_players", WearConstants.KEY_TEAM1_PLAYERS)
        assertEquals("team2_players", WearConstants.KEY_TEAM2_PLAYERS)
        assertEquals("player_name", WearConstants.KEY_PLAYER_NAME)
        assertEquals("player_id", WearConstants.KEY_PLAYER_ID)
        assertEquals("player_roles", WearConstants.KEY_PLAYER_ROLES)
        assertEquals("team_color", WearConstants.KEY_TEAM_COLOR)
        assertEquals("match_active", WearConstants.KEY_MATCH_ACTIVE)
        assertEquals("test_data", WearConstants.KEY_TEST_DATA)
        assertEquals("team_number", WearConstants.EXTRA_TEAM_NUMBER)
    }

    /**
     * `KEY_TIMESTAMP` regge molto piu' di quanto il suo nome suggerisca.
     *
     * Il Data Layer NON riconsegna un DataItem identico al precedente. Infilare un timestamp in
     * ogni invio rende ogni put diverso dal precedente byte per byte, quindi un punteggio che
     * torna al valore di prima (segna, annulla, risegna) arriva comunque. Toglierlo perche'
     * "sembra inutilizzato" spegnerebbe in silenzio la sincronizzazione dei valori ripetuti.
     */
    @Test
    fun `KEY_TIMESTAMP e' portante e non va rimosso`() {
        assertEquals("timestamp", WearConstants.KEY_TIMESTAMP)
    }

    @Test
    fun `il protocollo v2 e' additivo e non collide con il v1`() {
        assertEquals("/scoreboard/v2/state", WearConstants.PATH_STATE_V2)
        assertEquals("/scoreboard/v2/intent", WearConstants.MSG_SCORE_INTENT)
        assertEquals("/scoreboard/v2/sport", WearConstants.MSG_SPORT_INTENT)
        assertEquals("/scoreboard/v2/intent_batch", WearConstants.MSG_INTENT_BATCH)
        assertEquals("/scoreboard/v2/batch_ack", WearConstants.MSG_BATCH_ACK)
        assertEquals(2, WearConstants.PROTO_VERSION)

        assertEquals("proto_version", WearConstants.KEY_PROTO_VERSION)
        assertEquals("sport_label", WearConstants.KEY_SPORT_LABEL)
        assertEquals("sport_ids", WearConstants.KEY_SPORT_IDS)
        assertEquals("sport_labels", WearConstants.KEY_SPORT_LABELS)
        assertEquals("match_in_progress", WearConstants.KEY_MATCH_IN_PROGRESS)
        assertEquals("event_log", WearConstants.KEY_EVENT_LOG)
        assertEquals("match_over", WearConstants.KEY_MATCH_OVER)
        assertEquals("|", WearConstants.SPORT_SEPARATOR)
        assertEquals("at_millis", WearConstants.KEY_AT_MILLIS)
        assertEquals("intent_batch", WearConstants.KEY_INTENT_BATCH)
        assertEquals(";", WearConstants.BATCH_SEPARATOR)
        assertEquals(",", WearConstants.BATCH_FIELD_SEPARATOR)
        assertEquals("sport_id", WearConstants.KEY_SPORT_ID)
        assertEquals("side1_primary", WearConstants.KEY_SIDE1_PRIMARY)
        assertEquals("side1_secondary", WearConstants.KEY_SIDE1_SECONDARY)
        assertEquals("side2_primary", WearConstants.KEY_SIDE2_PRIMARY)
        assertEquals("side2_secondary", WearConstants.KEY_SIDE2_SECONDARY)
        assertEquals("period_label", WearConstants.KEY_PERIOD_LABEL)
        assertEquals("serving_side", WearConstants.KEY_SERVING_SIDE)
        assertEquals("cap_has_clock", WearConstants.KEY_CAP_HAS_CLOCK)
        assertEquals("cap_has_aux_timer", WearConstants.KEY_CAP_HAS_AUX_TIMER)
        assertEquals("cap_attributes_scorer", WearConstants.KEY_CAP_ATTRIBUTES_SCORER)
        assertEquals("cap_decrement_is_undo", WearConstants.KEY_CAP_DECREMENT_IS_UNDO)
        assertEquals("seq", WearConstants.KEY_SEQ)
        assertEquals("side", WearConstants.KEY_SIDE)
        assertEquals("intent_kind", WearConstants.KEY_INTENT_KIND)
        assertEquals("point", WearConstants.INTENT_POINT)
        assertEquals("correction", WearConstants.INTENT_CORRECTION)
        assertEquals("undo", WearConstants.INTENT_UNDO)
    }

    /**
     * La durata del portiere e' una chiave NUOVA sullo stesso path: `keeper_millis` non cambia
     * ne' valore ne' significato, cosi' un orologio o un telefono non aggiornato continua a
     * leggere quello che legge oggi (L8).
     */
    @Test
    fun `la durata del portiere e' una chiave additiva accanto a keeper_millis`() {
        assertEquals("keeper_duration", WearConstants.KEY_KEEPER_DURATION)
        assertEquals("keeper_millis", WearConstants.KEY_KEEPER_MILLIS)
        assertEquals("/scoreboard/keeper_timer", WearConstants.PATH_KEEPER_TIMER)
    }

    /**
     * Nessun path v2 deve essere prefisso di un path v1 o viceversa: il filtro nel manifest usa
     * `pathPrefix="/scoreboard"` e il dispatch e' su uguaglianza, ma una sovrapposizione renderebbe
     * ambiguo qualunque futuro passaggio a un dispatch per prefisso.
     */
    @Test
    fun `nessun path e' prefisso di un altro`() {
        val paths =
            listOf(
                WearConstants.PATH_SCORE,
                WearConstants.PATH_TEAM_NAMES,
                WearConstants.PATH_TEAM1_COLOR,
                WearConstants.PATH_TEAM2_COLOR,
                WearConstants.PATH_TIMER_STATE,
                WearConstants.PATH_KEEPER_TIMER,
                WearConstants.PATH_MATCH_STATE,
                WearConstants.PATH_PLAYERS,
                WearConstants.PATH_TEAM_PLAYERS,
                WearConstants.PATH_TEST_PING,
                WearConstants.PATH_STATE_V2,
                WearConstants.MSG_SCORER_SELECTED,
                WearConstants.MSG_REQUEST_SYNC,
                WearConstants.MSG_SCORE_INTENT,
            )
        assertEquals("path duplicati", paths.size, paths.toSet().size)
        paths.forEach { a ->
            paths.filter { it != a }.forEach { b ->
                if (b.startsWith("$a/")) {
                    throw AssertionError("$b e' sotto $a: il dispatch diventerebbe ambiguo")
                }
            }
        }
    }

    /**
     * Le capability nuove sono ADDITIVE. `scoreboard_app` resta dichiarata da entrambi i lati per
     * almeno una release: toglierla mentre un orologio non aggiornato dichiara solo quella
     * significherebbe che la coppia smette di vedersi.
     */
    @Test
    fun `le capability sono additive`() {
        assertEquals("scoreboard_app", WearConstants.CAPABILITY_SCOREBOARD_APP)
        assertEquals("scoreboard_phone", WearConstants.CAPABILITY_PHONE)
        assertEquals("scoreboard_watch", WearConstants.CAPABILITY_WATCH)
    }
}
