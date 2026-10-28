package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.io.File

/**
 * Durante la partita il quadrante di sistema mostra l'icona che riporta al tabellone (Ongoing
 * Activity). Il servizio in primo piano che la regge parte solo a partita cominciata e a schermo
 * acceso, e si ferma con la partita o quando chi gioca chiude l'app.
 *
 * Sotto Robolectric il sistema non esegue il servizio da solo: si prova l'attivita' che lo chiede
 * ([PartitaInCorsoServizio.imposta]) e, a parte, il servizio con la sua notifica.
 */
@RunWith(RobolectricTestRunner::class)
class PartitaInCorsoTest {
    private lateinit var app: Application
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var viewModel: WearViewModel

    @Before
    fun setup() {
        app = RuntimeEnvironment.getApplication()
        listOf("wear_pending_intents", "wear_last_known_match", "wear_partita_in_corso").forEach { nome ->
            app
                .getSharedPreferences(nome, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
        val telefono = Mockito.mock(OptimizedWearDataSync::class.java)
        Mockito.`when`(telefono.connectionState).thenReturn(MutableStateFlow(ConnectionState.Disconnected))
        viewModel = WearViewModel(app, telefono, orologio = { SystemClock.uptimeMillis() + 1_000_000L })
        controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.get().chiediPermessoNotifiche = false
        val fabbrica =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
            }
        ViewModelProvider(controller.get(), fabbrica)[WearViewModel::class.java]
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun stato(
        inCorso: Boolean = true,
        finita: Boolean = false,
    ) = WearScoreState(
        side1Primary = "40",
        side1Secondary = "",
        side2Primary = "15",
        side2Secondary = "",
        periodLabel = "Set 2",
        hasClock = false,
        hasAuxTimer = false,
        attributesScorer = false,
        decrementIsUndo = true,
        sportId = "tennis",
        sportLabel = "",
        sportIds = emptyList(),
        sportLabels = emptyList(),
        matchInProgress = inCorso,
        matchOver = finita,
        eventLog = "",
    )

    private fun applica(stato: WearScoreState) {
        viewModel.applyStateV2(stato)
        idle()
    }

    /** I servizi avviati finora, nell'ordine; svuota la coda. */
    private fun serviziAvviati(): List<String?> {
        val tutti = mutableListOf<String?>()
        while (true) tutti += shadowOf(app).nextStartedService?.component?.className ?: break
        return tutti
    }

    private fun serviziFermati(): List<String?> {
        val tutti = mutableListOf<String?>()
        while (true) tutti += shadowOf(app).nextStoppedService?.component?.className ?: break
        return tutti
    }

    private val nomeServizio = PartitaInCorsoServizio::class.java.name

    @Test
    fun `la partita cominciata a schermo acceso avvia il servizio dell'icona`() {
        controller.create().start().resume()
        idle()
        serviziAvviati()

        applica(stato())

        assertTrue(serviziAvviati().contains(nomeServizio))
    }

    @Test
    fun `senza partita in corso il servizio non parte`() {
        controller.create().start().resume()
        idle()

        applica(stato(inCorso = false))

        assertTrue(serviziAvviati().none { it == nomeServizio })
    }

    @Test
    fun `la partita finita ferma il servizio`() {
        controller.create().start().resume()
        applica(stato())
        serviziFermati()

        applica(stato(finita = true))

        assertTrue(serviziFermati().contains(nomeServizio))
    }

    @Test
    fun `a schermo spento una partita che comincia non lo avvia, onResume si`() {
        controller.create().start().resume()
        idle()
        controller.pause().stop()
        serviziAvviati()

        applica(stato())
        assertTrue("fermo in background il sistema rifiuta il servizio in primo piano", serviziAvviati().none { it == nomeServizio })

        controller.start().resume()
        idle()
        assertTrue(serviziAvviati().contains(nomeServizio))
    }

    @Test
    fun `a schermo spento la fine della partita toglie comunque l'icona`() {
        controller.create().start().resume()
        applica(stato())
        controller.pause().stop()
        serviziFermati()

        applica(stato(finita = true))

        assertTrue(serviziFermati().contains(nomeServizio))
    }

    @Test
    fun `chi chiude l'app ferma il servizio, il sistema che la distrugge no`() {
        controller.create().start().resume()
        applica(stato())
        serviziFermati()

        controller.pause().stop().destroy()
        assertTrue("distrutta senza finish: l'icona resta per riaprirla", serviziFermati().none { it == nomeServizio })

        val seconda = Robolectric.buildActivity(MainActivity::class.java)
        seconda.get().chiediPermessoNotifiche = false
        seconda.create().start().resume()
        seconda.get().finish()
        seconda.pause().stop().destroy()
        assertTrue(serviziFermati().contains(nomeServizio))
    }

    @Test
    fun `il permesso delle notifiche si chiede una volta sola`() {
        val attivita = Robolectric.buildActivity(MainActivity::class.java)
        val fabbrica =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
            }
        ViewModelProvider(attivita.get(), fabbrica)[WearViewModel::class.java]
        attivita.create().start().resume()
        idle()

        applica(stato())
        assertNotNull("la prima volta la finestra del permesso si apre", shadowOf(attivita.get()).nextStartedActivity)

        attivita.get().sincronizzaPartitaInCorso()
        idle()
        assertNull("la seconda no", shadowOf(attivita.get()).nextStartedActivity)
    }

    @Test
    fun `il servizio mette una notifica in corso, silenziosa, che riporta al tabellone`() {
        val servizio = Robolectric.buildService(PartitaInCorsoServizio::class.java).create()
        servizio.startCommand(0, 1)

        val notifica =
            app
                .getSystemService(NotificationManager::class.java)
                .activeNotifications
                .single { it.id == PartitaInCorsoServizio.ID_NOTIFICA }
                .notification
        assertTrue("in corso: non si toglie con uno swipe", notifica.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notifica.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
        assertEquals(PartitaInCorsoServizio.CANALE, notifica.channelId)
        assertEquals(Notification.CATEGORY_WORKOUT, notifica.category)
        assertEquals(app.getString(R.string.wear_ongoing_status), notifica.extras.getCharSequence(Notification.EXTRA_TEXT))

        val tocco = shadowOf(notifica.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, tocco.component?.className)
        assertTrue(tocco.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `il manifest dichiara il servizio non esportato e di tipo specialUse con i permessi`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val servizio = manifest.substringAfter("android:name=\".PartitaInCorsoServizio\"").substringBefore("</service>")

        assertTrue(servizio.contains("android:exported=\"false\""))
        assertTrue(servizio.contains("android:foregroundServiceType=\"specialUse\""))
        assertTrue(servizio.contains("FOREGROUND_SERVICE_TYPE_SPECIAL_USE_DESCRIPTION"))
        listOf("FOREGROUND_SERVICE", "FOREGROUND_SERVICE_SPECIAL_USE", "POST_NOTIFICATIONS").forEach {
            assertTrue(it, manifest.contains("android.permission.$it\""))
        }
    }
}
