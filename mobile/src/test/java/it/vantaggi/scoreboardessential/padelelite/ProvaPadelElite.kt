package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Attrezzi comuni dei test di Padel Elite. Nessuna prova parla col Supabase vero: tutto passa da
 * un MockWebServer su localhost, con URL e chiave inventati.
 */
internal const val CHIAVE_DI_PROVA = "sb_publishable_di_prova"

/** AES-GCM con una chiave in memoria: il Keystore di Android non esiste sulla JVM dei test. */
internal class SoftwareSecretBox : SecretBox {
    private val key = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")

    override fun seal(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray? =
        runCatching {
            val cipher =
                Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12)) }
            cipher.doFinal(sealed, 12, sealed.size - 12)
        }.getOrNull()
}

internal fun MockWebServer.configurazione() = PadelEliteConfig(url("/").toString().trimEnd('/'), CHIAVE_DI_PROVA)

internal fun rispostaDiSessione(
    access: String,
    refresh: String,
    scadenza: Long,
    userId: String = "u-1",
    email: String = "mattia@example.com",
): String =
    """{"access_token":"$access","token_type":"bearer","expires_in":3600,"expires_at":$scadenza,""" +
        """"refresh_token":"$refresh","user":{"id":"$userId","email":"$email"}}"""

internal fun json(
    codice: Int,
    corpo: String,
) = MockResponse().setResponseCode(codice).setHeader("Content-Type", "application/json").setBody(corpo)

/** Un file v2 minimo ma con la forma giusta: il server finto non lo guarda, il test confronta i byte. */
internal const val FILE_V2 = """{"formatVersion":2,"sportId":"padel","matchId":"3f2a9c1e-5b7d-4e8a-9c01-2d4f6a8b0c1e","players":[],"timeline":[]}"""

internal const val UUID_PARTITA = "3f2a9c1e-5b7d-4e8a-9c01-2d4f6a8b0c1e"

internal fun rispostaDiInvio(
    stato: String = "pending",
    giaInviata: Boolean = false,
) = json(
    200,
    """{"item":{"id":"i-1","group_id":"g-1","external_id":"match:$UUID_PARTITA","status":"$stato","match_id":null,""" +
        """"created_at":"2026-10-07T10:00:00+00:00"},"already_submitted":$giaInviata}""",
)

internal fun erroreRpc(
    codice: Int,
    sqlstate: String,
    messaggio: String,
    dettagli: String? = null,
) = json(codice, """{"code":"$sqlstate","details":${dettagli?.let { "\"$it\"" } ?: "null"},"hint":null,"message":"$messaggio"}""")

/** Un server finto con una coda di risposte e il registro delle richieste, per percorso. */
internal class ServerFinto(
    private val risposte: (RecordedRequest, Int) -> MockResponse,
) {
    val server = MockWebServer()
    val richieste = mutableListOf<RecordedRequest>()
    private val contatori = HashMap<String, AtomicInteger>()

    init {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val percorso = request.path.orEmpty().substringBefore('?')
                    val n = contatori.getOrPut(percorso) { AtomicInteger() }.getAndIncrement()
                    synchronized(richieste) { richieste += request }
                    return risposte(request, n)
                }
            }
        server.start()
    }

    fun config() = server.configurazione()

    fun quante(percorsoIniziaCon: String) = synchronized(richieste) { richieste.count { it.path.orEmpty().startsWith(percorsoIniziaCon) } }

    fun chiudi() = server.shutdown()
}

internal fun preferenze(
    context: Context,
    nome: String,
) = context.getSharedPreferences(nome, Context.MODE_PRIVATE).also { it.edit().clear().commit() }
