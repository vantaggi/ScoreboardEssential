package it.vantaggi.scoreboardessential

import android.app.Application
import android.app.Notification
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.service.PartitaInCorsoServizio
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * Il servizio che tiene vivo il processo durante la partita: col telefono in tasca, senza un servizio in primo
 * piano, Samsung congela l'app e il motore non risponde piu' all'orologio.
 */
@RunWith(RobolectricTestRunner::class)
class PartitaInCorsoServizioTest {
    private lateinit var app: Application
    private val avviatoPrima = PartitaInCorsoServizio.avvia
    private val fermatoPrima = PartitaInCorsoServizio.ferma
    private val avviati = mutableListOf<Intent>()
    private var arresti = 0

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        PartitaInCorsoServizio.dimentica()
        PartitaInCorsoServizio.avvia = { _, intent -> avviati += intent }
        PartitaInCorsoServizio.ferma = { _, _ -> arresti++ }
    }

    @After
    fun tearDown() {
        PartitaInCorsoServizio.avvia = avviatoPrima
        PartitaInCorsoServizio.ferma = fermatoPrima
        PartitaInCorsoServizio.dimentica()
    }

    @Test
    fun `una partita in corso accende il servizio una volta sola`() {
        PartitaInCorsoServizio.imposta(app, attiva = true)
        PartitaInCorsoServizio.imposta(app, attiva = true)
        PartitaInCorsoServizio.imposta(app, attiva = true)

        assertEquals(1, avviati.size)
        assertEquals(PartitaInCorsoServizio::class.java.name, avviati.single().component?.className)
    }

    @Test
    fun `senza partita il servizio si ferma, e fermarlo due volte non fa niente`() {
        PartitaInCorsoServizio.imposta(app, attiva = false)
        assertEquals("mai acceso: niente da fermare", 0, arresti)

        PartitaInCorsoServizio.imposta(app, attiva = true)
        PartitaInCorsoServizio.imposta(app, attiva = false)
        PartitaInCorsoServizio.imposta(app, attiva = false)

        assertEquals(1, arresti)
    }

    /** Partita cominciata dal polso a schermo spento: il sistema rifiuta l'avvio, e la richiesta dopo ci riprova. */
    @Test
    fun `se il sistema rifiuta l'avvio dal background si riprova alla richiesta successiva`() {
        var tentativi = 0
        PartitaInCorsoServizio.avvia = { _, _ ->
            tentativi++
            if (tentativi == 1) throw IllegalStateException("ForegroundServiceStartNotAllowedException")
        }

        PartitaInCorsoServizio.imposta(app, attiva = true)
        assertEquals(1, tentativi)

        PartitaInCorsoServizio.imposta(app, attiva = true)
        assertEquals("il rifiuto non e' stato ricordato come un avvio", 2, tentativi)

        PartitaInCorsoServizio.imposta(app, attiva = true)
        assertEquals("riuscito: basta", 2, tentativi)
    }

    @Test
    fun `un permesso negato non fa cadere il chiamante`() {
        PartitaInCorsoServizio.avvia = { _, _ -> throw SecurityException("FOREGROUND_SERVICE_SPECIAL_USE") }

        PartitaInCorsoServizio.imposta(app, attiva = true)
    }

    @Test
    fun `il servizio entra in primo piano con una notifica fissa e muta, e non e' sticky`() {
        val servizio = Robolectric.buildService(PartitaInCorsoServizio::class.java).create().get()

        val esito = servizio.onStartCommand(Intent(), 0, 1)

        assertEquals(android.app.Service.START_NOT_STICKY, esito)
        val notifica = shadowOf(servizio).lastForegroundNotification
        assertNotNull(notifica)
        assertEquals(PartitaInCorsoServizio.ID_NOTIFICA, shadowOf(servizio).lastForegroundNotificationId)
        assertTrue("fissa", notifica.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(ScoreboardEssentialApplication.CHANNEL_ID_PARTITA, notifica.channelId)
    }

    /** Il tipo specialUse e la sua descrizione stanno nel manifest, o Android 14 rifiuta il servizio. */
    @Test
    fun `il manifest dichiara il servizio non esportato di tipo specialUse con la descrizione`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val servizio = manifest.substringAfter("android:name=\".service.PartitaInCorsoServizio\"").substringBefore("</service>")

        assertTrue(servizio.contains("android:exported=\"false\""))
        assertTrue(servizio.contains("android:foregroundServiceType=\"specialUse\""))
        assertTrue(servizio.contains("FOREGROUND_SERVICE_TYPE_SPECIAL_USE_DESCRIPTION"))
    }
}
