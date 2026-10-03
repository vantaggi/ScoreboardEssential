package it.vantaggi.scoreboardessential.shared.communication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

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
        assertEquals("serving_slot", WearConstants.KEY_SERVING_SLOT)
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
        assertEquals("end_match", WearConstants.INTENT_END_MATCH)
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
     * Nessun path deve stare SOTTO un altro, per segmenti: `a` e `a/b` sono in conflitto, `a` e
     * `ab` no. Il dispatch e' su uguaglianza, ma una sovrapposizione renderebbe ambiguo qualunque
     * futuro passaggio a un dispatch per prefisso di segmento. (Il `pathPrefix="/scoreboard"` del
     * manifest e' un prefisso grezzo e per costruzione contiene tutti i path: qui non c'entra.)
     */
    @Test
    fun `nessun path sta sotto un altro per segmenti`() {
        // Per riflessione, non a mano: la lista scritta a mano aveva lasciato fuori MSG_SPORT_INTENT,
        // MSG_INTENT_BATCH e MSG_BATCH_ACK, e ogni path futuro avrebbe fatto lo stesso.
        val paths = costantiDelProtocollo().values.filterIsInstance<String>().filter { it.startsWith("/") }
        assertTrue("nessun path trovato: la riflessione non vede le costanti", paths.size >= 17)
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
     * Ogni costante di [WearConstants] deve stare in [VALORI_CONGELATI], col suo valore LETTERALE.
     *
     * I test sopra congelano le costanti che qualcuno si e' ricordato di elencare: MSG_SPORT_INTENT,
     * MSG_INTENT_BATCH e MSG_BATCH_ACK erano fuori da "nessun path e' prefisso di un altro", e una
     * costante aggiunta domani resterebbe fuori da qualunque test senza che nulla diventi rosso.
     * Qui l'elenco si raccoglie per riflessione e si confronta con quello congelato: aggiungere una
     * costante senza la sua riga in [VALORI_CONGELATI] fa fallire il test.
     *
     * Il rosso per una costante NUOVA si sana AGGIUNGENDO la sua riga. Il rosso per un valore
     * cambiato non si sana cambiando il valore atteso: vale tutto quello che dice la testata.
     */
    @Test
    fun `ogni costante di WearConstants e' congelata nel golden`() {
        val trovate = costantiDelProtocollo()

        val nonClassificate = trovate.keys - VALORI_CONGELATI.keys - NON_DI_FILO
        assertTrue(
            "costanti senza riga nel golden (aggiungila con il suo valore, o in NON_DI_FILO se non viaggia sul filo): " +
                "$nonClassificate",
            nonClassificate.isEmpty(),
        )
        val sparite = (VALORI_CONGELATI.keys + NON_DI_FILO) - trovate.keys
        assertTrue("costanti congelate che non esistono piu' (rinominate o tolte?): $sparite", sparite.isEmpty())
        val doppie = VALORI_CONGELATI.keys.intersect(NON_DI_FILO)
        assertTrue("costanti sia congelate sia non di filo: $doppie", doppie.isEmpty())

        VALORI_CONGELATI.forEach { (nome, atteso) ->
            assertEquals("valore cambiato per $nome", atteso, trovate.getValue(nome))
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

    private companion object {
        /**
         * Le `const val` dell'oggetto: campi statici finali, stringhe o numeri. Un campo di altro
         * tipo (un `val` non const, una lista) non si salta in silenzio: farebbe uscire una
         * costante dal golden senza che nulla diventi rosso, quindi fallisce con il suo nome.
         */
        fun costantiDelProtocollo(): Map<String, Any> {
            val campi =
                WearConstants::class.java.declaredFields
                    .filter { Modifier.isStatic(it.modifiers) && Modifier.isFinal(it.modifiers) }
                    .filter { !it.isSynthetic && it.name != "INSTANCE" }
            val estranei = campi.filter { it.type != String::class.java && !it.type.isPrimitive }
            assertTrue(
                "campi statici di WearConstants che non sono String ne' primitivi (il golden non li sa congelare): " +
                    estranei.map { "${it.name}: ${it.type.simpleName}" },
                estranei.isEmpty(),
            )
            return campi.associate { campo ->
                campo.isAccessible = true
                campo.name to checkNotNull(campo.get(null))
            }
        }

        val VALORI_CONGELATI: Map<String, Any> =
            mapOf(
                // Capability
                "CAPABILITY_SCOREBOARD_APP" to "scoreboard_app",
                "CAPABILITY_PHONE" to "scoreboard_phone",
                "CAPABILITY_WATCH" to "scoreboard_watch",
                // Data path v1
                "PATH_SCORE" to "/scoreboard/score",
                "PATH_TEAM_NAMES" to "/scoreboard/team_names",
                "PATH_TEAM1_COLOR" to "/scoreboard/team1_color",
                "PATH_TEAM2_COLOR" to "/scoreboard/team2_color",
                "PATH_TIMER_STATE" to "/scoreboard/timer_state",
                "PATH_KEEPER_TIMER" to "/scoreboard/keeper_timer",
                "PATH_MATCH_STATE" to "/scoreboard/match_state",
                "PATH_PLAYERS" to "/scoreboard/players",
                "PATH_TEAM_PLAYERS" to "/scoreboard/team_players",
                "PATH_TEST_PING" to "/scoreboard/test_ping",
                // Protocollo v2: path
                "PATH_STATE_V2" to "/scoreboard/v2/state",
                "MSG_SCORE_INTENT" to "/scoreboard/v2/intent",
                "MSG_SPORT_INTENT" to "/scoreboard/v2/sport",
                "MSG_INTENT_BATCH" to "/scoreboard/v2/intent_batch",
                "MSG_BATCH_ACK" to "/scoreboard/v2/batch_ack",
                "PROTO_VERSION" to 2,
                // Protocollo v2: chiavi
                "KEY_PROTO_VERSION" to "proto_version",
                "KEY_SPORT_ID" to "sport_id",
                "KEY_SIDE1_PRIMARY" to "side1_primary",
                "KEY_SIDE1_SECONDARY" to "side1_secondary",
                "KEY_SIDE2_PRIMARY" to "side2_primary",
                "KEY_SIDE2_SECONDARY" to "side2_secondary",
                "KEY_PERIOD_LABEL" to "period_label",
                "KEY_SERVING_SIDE" to "serving_side",
                "KEY_SERVING_SLOT" to "serving_slot",
                "KEY_SPORT_LABEL" to "sport_label",
                "KEY_SPORT_IDS" to "sport_ids",
                "KEY_SPORT_LABELS" to "sport_labels",
                "SPORT_SEPARATOR" to "|",
                "KEY_MATCH_IN_PROGRESS" to "match_in_progress",
                "KEY_EVENT_LOG" to "event_log",
                "KEY_MATCH_OVER" to "match_over",
                "KEY_CAP_HAS_CLOCK" to "cap_has_clock",
                "KEY_CAP_HAS_AUX_TIMER" to "cap_has_aux_timer",
                "KEY_CAP_ATTRIBUTES_SCORER" to "cap_attributes_scorer",
                "KEY_CAP_DECREMENT_IS_UNDO" to "cap_decrement_is_undo",
                "KEY_SEQ" to "seq",
                "KEY_SIDE" to "side",
                "KEY_INTENT_KIND" to "intent_kind",
                "KEY_AT_MILLIS" to "at_millis",
                "KEY_INTENT_BATCH" to "intent_batch",
                "BATCH_SEPARATOR" to ";",
                "BATCH_FIELD_SEPARATOR" to ",",
                "INTENT_POINT" to "point",
                "INTENT_CORRECTION" to "correction",
                "INTENT_UNDO" to "undo",
                "INTENT_END_MATCH" to "end_match",
                // Messaggi v1
                "MSG_SCORER_SELECTED" to "/scoreboard/scorer_selected",
                "MSG_REQUEST_SYNC" to "/scoreboard/request_sync",
                // Chiavi DataMap v1
                "KEY_TEAM1_SCORE" to "team1_score",
                "KEY_TEAM2_SCORE" to "team2_score",
                "KEY_TEAM1_NAME" to "team1_name",
                "KEY_TEAM2_NAME" to "team2_name",
                "KEY_TIMER_MILLIS" to "timer_millis",
                "KEY_TIMER_RUNNING" to "timer_running",
                "KEY_KEEPER_MILLIS" to "keeper_millis",
                "KEY_KEEPER_RUNNING" to "keeper_running",
                "KEY_KEEPER_DURATION" to "keeper_duration",
                "KEY_TIMESTAMP" to "timestamp",
                "KEY_PLAYERS" to "players",
                "KEY_TEAM1_PLAYERS" to "team1_players",
                "KEY_TEAM2_PLAYERS" to "team2_players",
                "KEY_PLAYER_NAME" to "player_name",
                "KEY_PLAYER_ID" to "player_id",
                "KEY_PLAYER_ROLES" to "player_roles",
                "KEY_TEAM_COLOR" to "team_color",
                "KEY_MATCH_ACTIVE" to "match_active",
                "KEY_TEST_DATA" to "test_data",
                "EXTRA_TEAM_NUMBER" to "team_number",
            )

        /**
         * Costanti di [WearConstants] che NON viaggiano sul filo: parametri di ritentativo locali,
         * ognuno libero di cambiare in una release senza rompere un orologio gia' installato. Per
         * questo non hanno un valore congelato, solo il nome: ogni costante nuova va comunque
         * classificata, o qui o in [VALORI_CONGELATI].
         */
        val NON_DI_FILO: Set<String> =
            setOf(
                "MAX_RETRY_ATTEMPTS",
                "RETRY_DELAY_MS",
                "MESSAGE_TIMEOUT_MS",
            )
    }
}
