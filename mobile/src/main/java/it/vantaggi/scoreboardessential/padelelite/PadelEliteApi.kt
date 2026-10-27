package it.vantaggi.scoreboardessential.padelelite

import it.vantaggi.scoreboardessential.core.ConfigurazioneSerata
import it.vantaggi.scoreboardessential.core.GironiSalvati
import it.vantaggi.scoreboardessential.core.ProssimaSerata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/** La sessione di Supabase Auth: i due token, la scadenza dell'accesso e chi e' l'utente. */
data class PadelEliteSession(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch in secondi: quando il token di accesso smette di valere. */
    val expiresAtSec: Long,
    val userId: String,
    val email: String,
)

/** Un gruppo di cui l'utente e' membro, col ruolo che vi ha (owner, admin o member). */
data class PadelEliteGroup(
    val id: String,
    val name: String,
    val role: String,
)

/** Perche' l'accesso non e' riuscito, in parole che la schermata sa dire. */
enum class AuthFailure {
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    RATE_LIMITED,

    /** Solo per il rinnovo: il refresh token e' scaduto o revocato, serve rifare l'accesso. */
    SESSION_EXPIRED,
    OTHER,
}

sealed interface AuthResult {
    data class Ok(
        val session: PadelEliteSession,
    ) : AuthResult

    data class Rejected(
        val reason: AuthFailure,
    ) : AuthResult

    /** Rete assente o risposta 5xx: si puo' riprovare, nulla e' stato deciso. */
    data object Network : AuthResult
}

sealed interface GroupsResult {
    data class Ok(
        val groups: List<PadelEliteGroup>,
    ) : GroupsResult

    data object NotAuthenticated : GroupsResult

    data object Network : GroupsResult
}

/** Un giocatore della rosa di un gruppo nella dashboard (`v2_players`). */
data class RemotePlayer(
    val id: Int,
    val name: String,
)

sealed interface RosterResult {
    /** La rosa del gruppo, in ordine di nome; puo' essere vuota. */
    data class Ok(
        val players: List<RemotePlayer>,
    ) : RosterResult

    data object NotAuthenticated : RosterResult

    /** 403: l'account non puo' leggere la rosa di questo gruppo (non ne e' piu' membro). */
    data object NotAuthorized : RosterResult

    data object Network : RosterResult
}

/**
 * L'esito di una lettura della serata dalla dashboard (`v2_settings`, `v2_rsvp`): gli errori come per la
 * rosa. 401 e' [NotAuthenticated] (chi chiama rinnova il token una volta), 403 [NotAuthorized], il resto
 * che non va (rete, 5xx, corpo che non e' una lista) [Network].
 */
sealed interface LetturaDashboard<out T> {
    data class Ok<out T>(
        val valore: T,
    ) : LetturaDashboard<T>

    data object NotAuthenticated : LetturaDashboard<Nothing>

    data object NotAuthorized : LetturaDashboard<Nothing>

    data object Network : LetturaDashboard<Nothing>
}

/** La voce della casella d'arrivo, com'e' restituita da `submit_scoreboard_match` (senza il file). */
data class InboxItem(
    val id: String,
    val externalId: String,
    /** `pending`, `imported` o `discarded`. */
    val status: String,
    val matchId: String?,
)

sealed interface SubmitResult {
    data class Accepted(
        val item: InboxItem,
        val alreadySubmitted: Boolean,
        /**
         * Il server ha sostituito il file di una voce ancora in attesa (migrazione 64 corretta: lo
         * stesso utente rimanda la stessa partita). `false` se il file era identico, se la voce e'
         * gia' importata o scartata o se l'ha inviata un altro; manca (e vale `false`) in un server vecchio.
         */
        val updated: Boolean = false,
    ) : SubmitResult

    /** `not_authenticated` o un 401 (token scaduto): chi chiama rinnova il token e riprova una volta. */
    data object NotAuthenticated : SubmitResult

    /** Non membro del gruppo: va scelto un altro gruppo. */
    data object NotAuthorized : SubmitResult

    /** Il file non e' valido; [details] e' il primo pezzo che non torna (`matchId`, `players`...). Non si riprova. */
    data class InvalidPayload(
        val details: String?,
    ) : SubmitResult

    /** Oltre 200 KB. Non si riprova. */
    data object PayloadTooLarge : SubmitResult

    /** La casella e' piena (50 per persona, 500 per gruppo, 200 per persona): si riprova piu' tardi. */
    data class InboxFull(
        val details: String?,
    ) : SubmitResult

    /** Rete assente, 5xx o 429: lo stesso file si rimanda, e' idempotente. */
    data object Network : SubmitResult

    /** Una risposta che il contratto non prevede (per esempio la RPC non c'e' ancora: 404). */
    data class Unexpected(
        val httpStatus: Int,
    ) : SubmitResult
}

sealed interface StatusResult {
    data class Found(
        val status: String,
        val matchId: String?,
    ) : StatusResult

    data object NotFound : StatusResult

    data object NotAuthenticated : StatusResult

    data object Network : StatusResult
}

/** La riga della diretta, com'e' restituita da `publish_live_from_app` e `end_live_from_app` (solo cio' che serve). */
data class LiveInfo(
    val id: String,
    val externalId: String,
    /** `live` o `ended`. */
    val status: String,
)

/**
 * L'esito di una chiamata della diretta (`publish_live_from_app`, `end_live_from_app`). Si decide dal
 * `message` della risposta, mai dallo stato HTTP: PostgREST manda 54xxx, 55xxx e P0002 come 500.
 */
sealed interface LiveResult {
    /** Pubblicato. [written] falso = file identico al salvato, oppure la diretta e' gia' chiusa (e [live] lo dice). */
    data class Published(
        val live: LiveInfo?,
        val created: Boolean,
        val written: Boolean,
    ) : LiveResult

    /** Chiusa. [alreadyEnded] vero = lo era gia' (altro admin, o un ritentativo che era andato a buon fine). */
    data class Ended(
        val live: LiveInfo?,
        val alreadyEnded: Boolean,
        /** Il file finale e' stato scritto (contratto finale, PR #221); manca in una risposta senza la chiave e allora vale falso. */
        val written: Boolean = false,
    ) : LiveResult

    /** `not_authenticated` o un 401: chi chiama rinnova il token e riprova una volta. */
    data object NotAuthenticated : LiveResult

    /** Non sei admin del gruppo: la diretta si spegne. */
    data object NotAuthorized : LiveResult

    /** Un difetto del file (o del corpo): non si riprova. */
    data class InvalidPayload(
        val details: String?,
    ) : LiveResult

    data object PayloadTooLarge : LiveResult

    /** Un altro admin trasmette questa partita. */
    data object LiveConflict : LiveResult

    /** Troppe dirette nel gruppo: piu' tardi. */
    data object LiveFull : LiveResult

    /** Il codice d'accesso della diretta non si e' potuto generare: piu' tardi. */
    data object JoinCodeExhausted : LiveResult

    /** Solo per la chiusura: la diretta non c'e' (piu'). */
    data object NotFound : LiveResult

    /** Rete assente, 5xx senza messaggio noto, 408, 429, conflitti transitori (23505, 40P01): si riprova. */
    data object Network : LiveResult

    data class Unexpected(
        val httpStatus: Int,
    ) : LiveResult
}

/**
 * Le chiamate a Supabase che l'app usa, scritte a mano su `HttpURLConnection`.
 *
 * Sono tre famiglie, tutte REST: GoTrue (`/auth/v1/token`) per accesso, ritorno da Google e rinnovo, PostgREST
 * (`/rest/v1/group_members`, `/rest/v1/v2_players`, `/rest/v1/v2_scoreboard_inbox`, e per la Serata
 * `/rest/v1/v2_settings` e `/rest/v1/v2_rsvp`) per leggere, e la RPC
 * `submit_scoreboard_match` per consegnare la partita. Una libreria (supabase-kt, OkHttp) avrebbe
 * portato un'altra dozzina di dipendenze per quattro richieste; su Android `HttpURLConnection` e'
 * gia' OkHttp, e il TLS e i timeout sono quelli di sistema.
 *
 * Tutte le funzioni sono sospese e girano su `Dispatchers.IO`. Un errore di rete non e' mai
 * un'eccezione: e' un valore (`Network`) che chi chiama sa gestire.
 */
class PadelEliteApi(
    private val config: PadelEliteConfig,
    /** Epoch in secondi: iniettabile perche' la scadenza del token si prova con un orologio finto. */
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val timeoutMs: Int = 20_000,
) {
    private class Reply(
        val status: Int,
        val body: String,
    )

    suspend fun signIn(
        email: String,
        password: String,
    ): AuthResult {
        val body = JSONObject().put("email", email).put("password", password).toString()
        return token("password", body)
    }

    /** Il ritorno da Google: il codice del deep link piu' il verificatore PKCE che solo l'app conosce. */
    suspend fun exchangeCode(
        code: String,
        verifier: String,
    ): AuthResult = token("pkce", JSONObject().put("auth_code", code).put("code_verifier", verifier).toString())

    suspend fun refresh(refreshToken: String): AuthResult =
        token("refresh_token", JSONObject().put("refresh_token", refreshToken).toString())

    private suspend fun token(
        grant: String,
        body: String,
    ): AuthResult {
        val reply = send("POST", "/auth/v1/token?grant_type=$grant", accessToken = null, body = body) ?: return AuthResult.Network
        if (reply.status in 200..299) {
            return parseSession(reply.body)?.let { AuthResult.Ok(it) } ?: AuthResult.Rejected(AuthFailure.OTHER)
        }
        if (reply.status >= 500 || (reply.status == 429 && grant == "refresh_token")) return AuthResult.Network
        val json = parseObject(reply.body)
        val codice =
            listOf("error_code", "error", "msg", "message", "error_description")
                .mapNotNull { json?.optString(it)?.takeIf { v -> v.isNotEmpty() } }
                .joinToString(" ")
                .lowercase()
        val motivo =
            when {
                reply.status == 429 || "rate_limit" in codice -> {
                    AuthFailure.RATE_LIMITED
                }

                "email_not_confirmed" in codice || "email not confirmed" in codice -> {
                    AuthFailure.EMAIL_NOT_CONFIRMED
                }

                grant == "refresh_token" -> {
                    AuthFailure.SESSION_EXPIRED
                }

                // Un codice scaduto, gia' usato o col verificatore sbagliato: non e' una password sbagliata.
                grant == "pkce" -> {
                    AuthFailure.OTHER
                }

                "invalid_credentials" in codice || "invalid_grant" in codice || "invalid login" in codice -> {
                    AuthFailure.INVALID_CREDENTIALS
                }

                else -> {
                    AuthFailure.OTHER
                }
            }
        return AuthResult.Rejected(motivo)
    }

    private fun parseSession(raw: String): PadelEliteSession? {
        val json = parseObject(raw) ?: return null
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val user = json.optJSONObject("user")
        val userId = user?.optString("id").orEmpty()
        if (access.isEmpty() || refresh.isEmpty() || userId.isEmpty()) return null
        val scade =
            if (json.has("expires_at")) json.optLong("expires_at") else clock() + json.optLong("expires_in", DEFAULT_LIFETIME_SEC)
        return PadelEliteSession(access, refresh, scade, userId, user?.optString("email").orEmpty())
    }

    /** I gruppi di cui [userId] e' membro, col ruolo. La RLS mostra comunque solo i suoi. */
    suspend fun fetchGroups(
        accessToken: String,
        userId: String,
    ): GroupsResult {
        val filtro = URLEncoder.encode("eq.$userId", "UTF-8")
        val path = "/rest/v1/group_members?select=group_id,role,groups(name)&user_id=$filtro"
        val reply = send("GET", path, accessToken, null) ?: return GroupsResult.Network
        return when {
            reply.status == 401 -> {
                GroupsResult.NotAuthenticated
            }

            reply.status >= 500 || reply.status !in 200..299 -> {
                GroupsResult.Network
            }

            else -> {
                val righe = parseArray(reply.body) ?: return GroupsResult.Network
                GroupsResult.Ok(
                    (0 until righe.length()).mapNotNull { i ->
                        val riga = righe.optJSONObject(i) ?: return@mapNotNull null
                        val id = riga.optString("group_id")
                        if (id.isEmpty()) return@mapNotNull null
                        val nome = riga.optJSONObject("groups")?.optString("name").orEmpty()
                        PadelEliteGroup(id, nome.ifEmpty { id.take(ID_FALLBACK_LENGTH) }, riga.optString("role"))
                    },
                )
            }
        }
    }

    /**
     * La rosa del gruppo [groupId]: i `v2_players` che i membri possono leggere (solo id e nome:
     * il resto non serve e non si chiede). Gli errori come [fetchGroups]: 401 e'
     * [RosterResult.NotAuthenticated], 403 e' [RosterResult.NotAuthorized] (non e' la rete e
     * riprovare non serve: non si e' piu' nel gruppo), il resto che non va e' rete. Una riga senza
     * id positivo o senza nome non e' un giocatore che si possa collegare: si salta.
     */
    suspend fun fetchRoster(
        accessToken: String,
        groupId: String,
    ): RosterResult {
        val filtro = URLEncoder.encode("eq.$groupId", "UTF-8")
        val path = "/rest/v1/v2_players?group_id=$filtro&select=id,name&order=name"
        val reply = send("GET", path, accessToken, null) ?: return RosterResult.Network
        return when {
            reply.status == 401 -> {
                RosterResult.NotAuthenticated
            }

            reply.status == 403 -> {
                RosterResult.NotAuthorized
            }

            reply.status >= 500 || reply.status !in 200..299 -> {
                RosterResult.Network
            }

            else -> {
                val righe = parseArray(reply.body) ?: return RosterResult.Network
                RosterResult.Ok(
                    (0 until righe.length()).mapNotNull { i ->
                        val riga = righe.optJSONObject(i) ?: return@mapNotNull null
                        val id = riga.optInt("id", 0)
                        val nome = riga.optString("name").trim()
                        if (id <= 0 || nome.isEmpty()) return@mapNotNull null
                        RemotePlayer(id, nome)
                    },
                )
            }
        }
    }

    /**
     * La serata configurata del gruppo (`v2_settings`, chiave `venue_config`): giorno della settimana e prima
     * prenotazione. `Ok(null)` se il gruppo non ne ha una, o se il giorno manca o non e' fra 0 e 6 (non c'e'
     * una serata da calcolare). Ora e durata come le legge la dashboard ([ProssimaSerata]).
     */
    suspend fun fetchVenueConfig(
        accessToken: String,
        groupId: String,
    ): LetturaDashboard<ConfigurazioneSerata?> = leggiImpostazione(accessToken, groupId, CHIAVE_SERATA) { parseVenueConfig(it) }

    /**
     * I gironi salvati della serata (`v2_settings`, chiave `gironi_serata`, formato `v: 1`). `Ok(null)` se non
     * ce ne sono o se il formato non e' quello che l'app conosce: una versione futura si ignora, non si indovina.
     */
    suspend fun fetchGironi(
        accessToken: String,
        groupId: String,
    ): LetturaDashboard<GironiSalvati?> = leggiImpostazione(accessToken, groupId, CHIAVE_GIRONI) { parseGironi(it) }

    /**
     * Gli id di `v2_players` confermati (`status = confirmed`) per la serata del [giorno], nell'ordine in cui
     * hanno risposto (e a parita' per id): e' l'ordine di arrivo della serata del telefono.
     */
    suspend fun fetchConfirmed(
        accessToken: String,
        groupId: String,
        giorno: LocalDate,
    ): LetturaDashboard<List<Int>> {
        val gruppo = URLEncoder.encode("eq.$groupId", "UTF-8")
        val path =
            "/rest/v1/v2_rsvp?group_id=$gruppo&session_date=eq.$giorno&status=eq.confirmed" +
                "&select=player_id&order=created_at.asc,player_id.asc"
        return leggiRighe(accessToken, path) { righe ->
            (0 until righe.length())
                .mapNotNull { i -> righe.optJSONObject(i)?.optInt("player_id", 0)?.takeIf { it > 0 } }
                .distinct()
        }
    }

    /** La riga di `v2_settings` con chiave [chiave] del gruppo: il suo `value` (null se la riga non c'e'). */
    private suspend fun <T> leggiImpostazione(
        accessToken: String,
        groupId: String,
        chiave: String,
        interpreta: (JSONObject?) -> T,
    ): LetturaDashboard<T> {
        val gruppo = URLEncoder.encode("eq.$groupId", "UTF-8")
        val path = "/rest/v1/v2_settings?group_id=$gruppo&key=eq.$chiave&select=value"
        return leggiRighe(accessToken, path) { righe -> interpreta(righe.optJSONObject(0)?.optJSONObject("value")) }
    }

    private suspend fun <T> leggiRighe(
        accessToken: String,
        path: String,
        interpreta: (JSONArray) -> T,
    ): LetturaDashboard<T> {
        val reply = send("GET", path, accessToken, null) ?: return LetturaDashboard.Network
        return when {
            reply.status == 401 -> LetturaDashboard.NotAuthenticated
            reply.status == 403 -> LetturaDashboard.NotAuthorized
            reply.status !in 200..299 -> LetturaDashboard.Network
            else -> parseArray(reply.body)?.let { LetturaDashboard.Ok(interpreta(it)) } ?: LetturaDashboard.Network
        }
    }

    private fun parseVenueConfig(valore: JSONObject?): ConfigurazioneSerata? {
        if (valore == null) return null
        val giorno =
            when (val g = valore.opt("day_of_week")) {
                is Number -> g.toDouble().takeIf { it % 1.0 == 0.0 }?.toInt()
                is String -> g.trim().toIntOrNull()
                else -> null
            }
        if (giorno == null || giorno !in 0..6) return null
        // La prima prenotazione, come la dashboard (non la piu' presto): senza prenotazioni valgono 19:00 e 90 minuti.
        val prima = valore.optJSONArray("bookings")?.optJSONObject(0)
        return ConfigurazioneSerata(
            giorno = giorno,
            inizio = ProssimaSerata.ora(prima?.opt("time") as? String),
            durataMinuti = ProssimaSerata.durata(prima?.opt("duration")?.takeIf { it != JSONObject.NULL }),
        )
    }

    private fun parseGironi(valore: JSONObject?): GironiSalvati? {
        if (valore == null || valore.optInt("v", 0) != FORMATO_GIRONI) return null
        val serata = valore.optString("serata").takeIf { DATA_ISO.matches(it) } ?: return null
        val elenco = valore.optJSONArray("gironi") ?: return null
        val gironi =
            (0 until elenco.length()).mapNotNull { i ->
                val girone = elenco.optJSONArray(i) ?: return@mapNotNull null
                (0 until girone.length())
                    .mapNotNull { j -> girone.optInt(j, 0).takeIf { it > 0 } }
                    .distinct()
                    .takeIf { it.isNotEmpty() }
            }
        return GironiSalvati(serata, gironi)
    }

    /** Lo stato della voce di una partita nella casella del gruppo: `pending`, `imported` o `discarded`. */
    suspend fun fetchStatus(
        accessToken: String,
        matchUuid: String,
    ): StatusResult {
        val filtro = URLEncoder.encode("eq.match:$matchUuid", "UTF-8")
        val path = "/rest/v1/v2_scoreboard_inbox?select=status,match_id&external_id=$filtro"
        val reply = send("GET", path, accessToken, null) ?: return StatusResult.Network
        return when {
            reply.status == 401 -> {
                StatusResult.NotAuthenticated
            }

            reply.status >= 500 || reply.status !in 200..299 -> {
                StatusResult.Network
            }

            else -> {
                val riga = parseArray(reply.body)?.optJSONObject(0) ?: return StatusResult.NotFound
                StatusResult.Found(riga.optString("status"), riga.optString("match_id").takeIf { it.isNotEmpty() && it != "null" })
            }
        }
    }

    /**
     * Consegna il file v2 [payloadJson], com'e' scritto da `MatchExporter.toJson`, alla casella del
     * gruppo [groupId]. Ritentare e' sempre sicuro: la voce e' unica per `(gruppo, matchId)`.
     */
    suspend fun submit(
        accessToken: String,
        groupId: String,
        payloadJson: String,
    ): SubmitResult {
        // Il file non si rilegge e non si riscrive: va com'e', dentro il corpo.
        val body = "{\"p_group\":${JSONObject.quote(groupId)},\"p_payload\":$payloadJson}"
        val reply = send("POST", "/rest/v1/rpc/submit_scoreboard_match", accessToken, body) ?: return SubmitResult.Network
        if (reply.status in 200..299) {
            val json = parseObject(reply.body)
            val item = json?.optJSONObject("item") ?: return SubmitResult.Unexpected(reply.status)
            return SubmitResult.Accepted(
                InboxItem(
                    id = item.optString("id"),
                    externalId = item.optString("external_id"),
                    status = item.optString("status"),
                    matchId = item.optString("match_id").takeIf { it.isNotEmpty() && it != "null" },
                ),
                alreadySubmitted = json.optBoolean("already_submitted", false),
                updated = json.optBoolean("updated", false),
            )
        }
        return classifyError(reply.status, reply.body)
    }

    /**
     * PostgREST non mappa le SQLSTATE dell'RPC su stati HTTP affidabili (54000 serve sia
     * `payload_too_large` sia `inbox_full`), quindi l'errore si legge dal `message`, e lo stato
     * HTTP decide solo per cio' che il contratto non nomina.
     */
    private fun classifyError(
        status: Int,
        body: String,
    ): SubmitResult {
        val json = parseObject(body)
        val dettagli = json?.optString("details")?.takeIf { it.isNotEmpty() && it != "null" }
        return when (json?.optString("message")) {
            "not_authenticated" -> {
                SubmitResult.NotAuthenticated
            }

            "not_authorized" -> {
                SubmitResult.NotAuthorized
            }

            "invalid_payload" -> {
                SubmitResult.InvalidPayload(dettagli)
            }

            "payload_too_large" -> {
                SubmitResult.PayloadTooLarge
            }

            "inbox_full" -> {
                SubmitResult.InboxFull(dettagli)
            }

            else -> {
                when {
                    status == 401 -> SubmitResult.NotAuthenticated
                    status >= 500 || status == 408 || status == 429 -> SubmitResult.Network
                    else -> SubmitResult.Unexpected(status)
                }
            }
        }
    }

    /**
     * Pubblica lo stato della partita in diretta: [payloadJson] e' il file v2 PARZIALE, com'e' scritto da
     * `MatchExporter.toJson`, nel corpo senza rileggerlo. Idempotente: lo stesso file due volte risponde
     * `written: false`, e anche questo e' un successo.
     */
    suspend fun publishLive(
        accessToken: String,
        groupId: String,
        payloadJson: String,
    ): LiveResult {
        val body = "{\"p_group\":${JSONObject.quote(groupId)},\"p_payload\":$payloadJson}"
        val reply = send("POST", "/rest/v1/rpc/publish_live_from_app", accessToken, body) ?: return LiveResult.Network
        if (reply.status !in 200..299) return classifyLiveError(reply.status, reply.body)
        val json = parseObject(reply.body) ?: return LiveResult.Unexpected(reply.status)
        return LiveResult.Published(
            live = parseLive(json),
            created = json.optBoolean("created", false),
            // Una risposta senza la chiave non dice che il file non e' stato scritto: vale "scritto".
            written = json.optBoolean("written", true),
        )
    }

    /**
     * Chiude la diretta di [matchId]: col file FINALE ([payloadJson], col vincitore) a partita finita, senza
     * (null, il corpo ha `p_payload: null`) a partita scartata o abbandonata.
     */
    suspend fun endLive(
        accessToken: String,
        groupId: String,
        matchId: String,
        payloadJson: String?,
    ): LiveResult {
        val body =
            "{\"p_group\":${JSONObject.quote(groupId)},\"p_match_id\":${JSONObject.quote(matchId)}," +
                "\"p_payload\":${payloadJson ?: "null"}}"
        val reply = send("POST", "/rest/v1/rpc/end_live_from_app", accessToken, body) ?: return LiveResult.Network
        if (reply.status !in 200..299) return classifyLiveError(reply.status, reply.body)
        val json = parseObject(reply.body) ?: return LiveResult.Unexpected(reply.status)
        return LiveResult.Ended(
            live = parseLive(json),
            alreadyEnded = json.optBoolean("already_ended", false),
            written = json.optBoolean("written", false),
        )
    }

    private fun parseLive(json: JSONObject): LiveInfo? =
        json.optJSONObject("live")?.let {
            LiveInfo(it.optString("id"), it.optString("external_id"), it.optString("status"))
        }

    /**
     * Come [classifyError], per la diretta: il `message` comanda (anche con HTTP 500), lo stato HTTP decide
     * solo per cio' che il contratto non nomina. 23505 e 40P01 sono conflitti transitori di Postgres
     * (indice unico, deadlock): si ritenta.
     */
    private fun classifyLiveError(
        status: Int,
        body: String,
    ): LiveResult {
        val json = parseObject(body)
        val dettagli = json?.optString("details")?.takeIf { it.isNotEmpty() && it != "null" }
        return when (json?.optString("message")) {
            "not_authenticated" -> {
                LiveResult.NotAuthenticated
            }

            "not_authorized" -> {
                LiveResult.NotAuthorized
            }

            "invalid_payload" -> {
                LiveResult.InvalidPayload(dettagli)
            }

            "payload_too_large" -> {
                LiveResult.PayloadTooLarge
            }

            "live_conflict" -> {
                LiveResult.LiveConflict
            }

            "live_full" -> {
                LiveResult.LiveFull
            }

            "join_code_exhausted" -> {
                LiveResult.JoinCodeExhausted
            }

            "not_found" -> {
                LiveResult.NotFound
            }

            else -> {
                val sqlstate = json?.optString("code").orEmpty()
                when {
                    sqlstate == "23505" || sqlstate == "40P01" -> LiveResult.Network
                    status == 401 -> LiveResult.NotAuthenticated
                    status >= 500 || status == 408 || status == 429 -> LiveResult.Network
                    else -> LiveResult.Unexpected(status)
                }
            }
        }
    }

    private suspend fun send(
        method: String,
        path: String,
        accessToken: String?,
        body: String?,
    ): Reply? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(config.baseUrl + path).openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("apikey", config.publishableKey)
                // Senza accesso l'Authorization e' la chiave pubblica, come fa il client ufficiale.
                connection.setRequestProperty("Authorization", "Bearer ${accessToken ?: config.publishableKey}")
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val stream = if (status >= HttpURLConnection.HTTP_BAD_REQUEST) connection.errorStream else connection.inputStream
                Reply(status, stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty())
            } catch (_: IOException) {
                null
            } finally {
                connection?.disconnect()
            }
        }

    private fun parseObject(raw: String): JSONObject? = runCatching { JSONObject(raw) }.getOrNull()

    private fun parseArray(raw: String): JSONArray? = runCatching { JSONArray(raw) }.getOrNull()

    private companion object {
        const val DEFAULT_LIFETIME_SEC = 3600L
        const val ID_FALLBACK_LENGTH = 8

        /** Le due righe di `v2_settings` che la Serata legge: la serata configurata e i gironi salvati. */
        const val CHIAVE_SERATA = "venue_config"
        const val CHIAVE_GIRONI = "gironi_serata"

        /** L'unica versione del formato dei gironi che l'app conosce (`v: 1`, PR #229 della dashboard). */
        const val FORMATO_GIRONI = 1
        val DATA_ISO = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}
