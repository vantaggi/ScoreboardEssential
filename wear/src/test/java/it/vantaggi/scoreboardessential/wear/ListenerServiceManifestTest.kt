package it.vantaggi.scoreboardessential.wear

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Il servizio di ascolto del Data Layer deve poter essere raggiunto da Google Play Services.
 *
 * Visto su emulatore il 13 settembre: questo orologio riceveva DATA_CHANGED dal telefono un
 * secondo dopo l'invio, ma WearableService non poteva collegarsi a WearDataLayerService --
 * "Permission Denial ... requires android.permission.BIND_WEARABLE_LISTENER_SERVICE". Play
 * Services quel permesso non ce l'ha. E' per questo che il quadrante restava su un punteggio
 * vecchio: si aggiornava solo rileggendo i DataItem al risveglio, mai in tempo reale.
 */
class ListenerServiceManifestTest {
    @Test
    fun `il servizio di ascolto non chiede un permesso che Play Services non ha`() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("manifest non trovato in ${manifest.absolutePath}", manifest.exists())

        val permessoSulServizio =
            Regex("""android:permission\s*=\s*"android\.permission\.BIND_WEARABLE_LISTENER_SERVICE"""")

        assertFalse(
            "con questo permesso Play Services non puo' collegarsi al servizio e i dati non arrivano",
            permessoSulServizio.containsMatchIn(manifest.readText()),
        )
    }
}
