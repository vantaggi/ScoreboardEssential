package it.vantaggi.scoreboardessential.padelelite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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

/**
 * Le chiamate a Supabase che l'app usa, scritte a mano su `HttpURLConnection`.
 *
 * Sono tre famiglie, tutte REST: GoTrue (`/auth/v1/token`) per accesso e rinnovo, PostgREST
 * (`/rest/v1/group_members`, `/rest/v1/v2_scoreboard_inbox`) per leggere, e la RPC
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
    }
}
