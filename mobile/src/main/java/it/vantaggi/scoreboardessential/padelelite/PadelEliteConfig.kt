package it.vantaggi.scoreboardessential.padelelite

import it.vantaggi.scoreboardessential.BuildConfig

/**
 * Dove sta il Supabase di Padel Elite e con quale chiave pubblica ("publishable") parlargli.
 *
 * I due valori vengono da `local.properties` (o dall'ambiente della CI) e arrivano qui via
 * `BuildConfig`: nel repository non ci sono. Se manca uno dei due la funzione e' **spenta**: nessun
 * comando compare nell'app, nessuna richiesta parte, l'app resta com'era senza l'invio.
 *
 * La chiave e' pubblica per costruzione (finisce in ogni APK): la sicurezza sta nelle RLS e nelle
 * RPC del database, non nel nasconderla.
 */
data class PadelEliteConfig(
    val supabaseUrl: String,
    val publishableKey: String,
) {
    /** L'URL senza la barra finale, pronto a ricevere `/auth/v1/...` e `/rest/v1/...`. */
    val baseUrl: String = supabaseUrl.trim().trimEnd('/')

    /** Funzione accesa solo con un URL http(s) e una chiave: un valore a meta' vale come spenta. */
    val isConfigured: Boolean =
        (baseUrl.startsWith("https://") || baseUrl.startsWith("http://")) && publishableKey.isNotBlank()

    companion object {
        /** La funzione spenta: il valore delle build senza `local.properties`. */
        val OFF = PadelEliteConfig("", "")

        fun fromBuildConfig() = PadelEliteConfig(BuildConfig.PADEL_ELITE_SUPABASE_URL, BuildConfig.PADEL_ELITE_SUPABASE_KEY)
    }
}
