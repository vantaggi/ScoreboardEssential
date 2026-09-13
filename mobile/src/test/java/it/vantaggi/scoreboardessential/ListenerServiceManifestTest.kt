package it.vantaggi.scoreboardessential

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Il servizio di ascolto del Data Layer deve poter essere raggiunto da Google Play Services.
 *
 * Visto su emulatore il 13 settembre: l'orologio riceveva DATA_CHANGED dal telefono un secondo
 * dopo l'invio, ma non poteva consegnarlo all'app -- "Permission Denial ... requires
 * android.permission.BIND_WEARABLE_LISTENER_SERVICE". Play Services quel permesso non ce l'ha:
 * dichiararlo sul servizio lo rende irraggiungibile proprio a chi deve chiamarlo, e gli
 * aggiornamenti in tempo reale muoiono in silenzio. Lo stesso attributo stava su questo servizio.
 */
class ListenerServiceManifestTest {
    @Test
    fun `il servizio di ascolto non chiede un permesso che Play Services non ha`() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("manifest non trovato in ${manifest.absolutePath}", manifest.exists())

        val permessoSulServizio =
            Regex("""android:permission\s*=\s*"android\.permission\.BIND_WEARABLE_LISTENER_SERVICE"""")

        assertFalse(
            "con questo permesso Play Services non puo' collegarsi al servizio e i messaggi non arrivano",
            permessoSulServizio.containsMatchIn(manifest.readText()),
        )
    }
}
