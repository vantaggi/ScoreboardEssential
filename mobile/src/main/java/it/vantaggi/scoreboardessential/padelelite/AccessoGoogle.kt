package it.vantaggi.scoreboardessential.padelelite

import android.net.Uri
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * "Accedi con Google" come nella dashboard: lo stesso provider Google gia' configurato nel progetto
 * Supabase, aperto nel browser, e il ritorno nell'app con un deep link.
 *
 * Il flusso e' OAuth con PKCE (RFC 7636): l'app crea un verificatore casuale e manda a Supabase solo
 * la sua impronta SHA-256 ([sfida]); Supabase passa da Google e torna su [RITORNO] con un codice, che
 * vale solo insieme al verificatore (`POST /auth/v1/token?grant_type=pkce`). Un'altra app che
 * intercettasse il deep link avrebbe il codice ma non il verificatore.
 *
 * Nessun client OAuth nuovo e niente in Google Cloud: Google torna a Supabase, non all'app. Serve solo
 * che [RITORNO] sia fra le "Redirect URLs" ammesse da Supabase Auth; se non c'e', Supabase rimanda al
 * sito della dashboard e l'app non riceve niente.
 */
object AccessoGoogle {
    /** Il deep link di ritorno: lo stesso valore va nel manifest e nelle Redirect URLs di Supabase. */
    const val RITORNO = "scoreboardessential://padel-elite/accesso"

    /** 32 byte casuali in base64url senza `=`: 43 caratteri, dentro i 43..128 di RFC 7636. */
    fun nuovoVerificatore(random: SecureRandom = SecureRandom()): String {
        val byte = ByteArray(VERIFIER_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(byte)
    }

    /** `BASE64URL(SHA256(verificatore))`, il metodo `s256`. */
    fun sfida(verificatore: String): String {
        val impronta = MessageDigest.getInstance("SHA-256").digest(verificatore.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(impronta)
    }

    /** L'indirizzo da aprire nel browser: `/auth/v1/authorize` di Supabase con provider, ritorno e sfida. */
    fun indirizzo(
        config: PadelEliteConfig,
        sfida: String,
    ): String =
        config.baseUrl + "/auth/v1/authorize?provider=google" +
            "&redirect_to=" + URLEncoder.encode(RITORNO, "UTF-8") +
            "&code_challenge=" + sfida +
            "&code_challenge_method=s256"

    private const val VERIFIER_BYTES = 32
}

/** Cosa porta il deep link di ritorno da Google. */
sealed interface RitornoGoogle {
    data class Codice(
        val code: String,
    ) : RitornoGoogle

    /** L'utente ha chiuso o rifiutato la pagina di Google: non e' un errore da mostrare. */
    data object Annullato : RitornoGoogle

    /** Supabase ha risposto con un errore, o il ritorno non ha ne' codice ne' errore. */
    data object Errore : RitornoGoogle

    companion object {
        /**
         * Il ritorno letto da [uri], o null se [uri] non e' il deep link di accesso. Supabase mette
         * il codice nella query; un errore puo' stare nella query o dopo il `#`.
         */
        fun da(uri: Uri?): RitornoGoogle? {
            val atteso = Uri.parse(AccessoGoogle.RITORNO)
            if (uri == null || uri.scheme != atteso.scheme || uri.host != atteso.host || uri.path != atteso.path) return null
            uri.getQueryParameter("code")?.takeIf { it.isNotBlank() }?.let { return Codice(it) }
            val errore =
                uri.getQueryParameter("error")
                    ?: uri.fragment?.let { Uri.parse("x://ritorno?$it").getQueryParameter("error") }
            return if (errore == "access_denied") Annullato else Errore
        }
    }
}
