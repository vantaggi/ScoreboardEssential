package it.vantaggi.scoreboardessential.padelelite

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import it.vantaggi.scoreboardessential.MainActivity
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import it.vantaggi.scoreboardessential.core.ExportOrigin
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchPlayer
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.util.UUID

/**
 * La diretta su un telefono vero (debito 12 di D-3): il servizio in primo piano `dataSync` e la sua notifica, il comando
 * «Interrompi diretta» che apre la conferma e il tetto di `dataSync`. L'avvio negato in secondo piano qui non si prova: un
 * processo strumentato resta a importanza 125 (servizio in primo piano) anche dopo HOME, e il sistema lo lascia partire. Padel Elite punta
 * a un indirizzo muto ([it.vantaggi.scoreboardessential.ProvaApplicationStrumentata]): la diretta resta in attesa per la
 * rete e nessuna richiesta esce dal telefono.
 *
 * Argomenti (`-Pandroid.testInstrumentationRunnerArguments.<nome>=...`): `pausaMs` tiene la notifica a schermo per una
 * foto; `tetto=1` prova il tetto, dopo aver accorciato il limite con `device_config` (vedi DESIGN.md).
 */
@RunWith(AndroidJUnit4::class)
class DirettaSulDispositivoTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val app get() = context as ScoreboardEssentialApplication
    private val servizi get() = app.padelElite
    private val strumentazione get() = InstrumentationRegistry.getInstrumentation()
    private val argomenti get() = InstrumentationRegistry.getArguments()

    @Before
    fun prepara() {
        strumentazione.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        // Niente presentazione ne' guida: coprirebbero la schermata e il dialogo.
        context
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_completed", true)
            .commit()
        context
            .getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("has_seen_tutorial", true)
            .commit()
    }

    @After
    fun chiudi() {
        servizi.diretta.spegni()
        attendi("il servizio fermo", 10_000) { notifica(DirettaService.ID_NOTIFICA) == null }
        // ActivityScenario non segue l'activity dopo onNewIntent (il comando della notifica): si chiude a mano.
        strumentazione.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED).flatMap { monitor.getActivitiesInStage(it) }.forEach { it.finish() }
        }
    }

    private fun foto() =
        DirettaFoto(
            UUID.randomUUID().toString(),
            SportRegistry.forMatch(SportRegistry.PADEL, listOf(1, 2, 3, 4)),
            List(3) { LoggedEvent(ScoringEvent.Point(side = 1), it * 30_000L) },
            listOf(MatchPlayer(1, "Marco", 1), MatchPlayer(2, "Anna", 2), MatchPlayer(3, "Luca", 1), MatchPlayer(4, "Sara", 2)),
            ExportOrigin(UUID.randomUUID().toString(), System.currentTimeMillis(), ZoneId.of("Europe/Rome"), "1.0"),
        )

    private fun notifica(id: Int): Notification? =
        context
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .firstOrNull { it.id == id }
            ?.notification

    private fun attendi(
        che: String,
        entroMs: Long = 15_000,
        condizione: () -> Boolean,
    ) {
        val limite = SystemClock.elapsedRealtime() + entroMs
        while (!condizione()) {
            check(SystemClock.elapsedRealtime() < limite) { "non e' successo: $che" }
            Thread.sleep(100)
        }
    }

    private fun pausaPerLaFoto() {
        argomenti.getString("pausaMs")?.toLongOrNull()?.let { Thread.sleep(it) }
    }

    private fun shell(comando: String) {
        strumentazione.uiAutomation.executeShellCommand(comando).close()
    }

    /** La schermata in primo piano (l'avvio del servizio e' consentito) e la diretta accesa, in attesa per la rete. */
    private fun accendi() {
        // Come dal launcher: in un task suo, dove arrivano anche gli intenti della notifica.
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        attendi("la schermata in primo piano") {
            var aperta = false
            strumentazione.runOnMainSync {
                aperta = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).isNotEmpty()
            }
            aperta
        }
        // Il ripristino di MainViewModel e' asincrono e chiude una diretta di un'altra partita (Diretta.riprendi): la diretta
        // del test si accende dopo. Nessun segnale pubblico dice quando ha finito; due secondi bastano a una lettura del database.
        Thread.sleep(2_000)
        assertTrue(servizi.diretta.accendi("g-1", "Padel", foto()))
        attendi("la diretta in attesa per la rete") {
            servizi.diretta.stato.value.fase == DirettaFase.IN_ATTESA
        }
        attendi("il servizio in primo piano") { servizi.direttaServizio.fase.value == DirettaServizioFase.ATTIVO }
        attendi("la notifica fissa") { notifica(DirettaService.ID_NOTIFICA) != null }
    }

    @Test
    fun laNotificaFissaDiceIlGruppoEIlMotivoDellAttesa() {
        accendi()

        val n = notifica(DirettaService.ID_NOTIFICA)!!
        assertEquals(context.getString(R.string.diretta_live, "Padel"), n.extras.getString(Notification.EXTRA_TITLE))
        attendi("il testo dell'attesa") {
            notifica(DirettaService.ID_NOTIFICA)?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ==
                context.getString(R.string.diretta_notif_waiting, context.getString(R.string.diretta_notif_why_network))
        }
        assertTrue("fissa", n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(
            context.getString(R.string.diretta_notif_stop),
            n.actions
                .single()
                .title
                .toString(),
        )
        assertEquals(ScoreboardEssentialApplication.CHANNEL_ID_DIRETTA, n.channelId)
        pausaPerLaFoto()

        servizi.diretta.spegni()
        attendi("il servizio se ne va da solo") { notifica(DirettaService.ID_NOTIFICA) == null }
    }

    @Test
    fun interrompiDallaNotificaChiedeConfermaEContinuaLasciaTutto() {
        accendi()
        val interrompi = notifica(DirettaService.ID_NOTIFICA)!!.actions.single().actionIntent

        interrompi.send()
        onView(withText(R.string.diretta_stop_title)).inRoot(isDialog()).check(matches(isDisplayed()))
        pausaPerLaFoto()
        onView(withText(R.string.diretta_stop_keep)).inRoot(isDialog()).perform(click())
        assertTrue("Continua lascia la diretta accesa", servizi.diretta.acceso)
        assertNotNull(notifica(DirettaService.ID_NOTIFICA))

        interrompi.send()
        onView(withText(R.string.diretta_stop_confirm)).inRoot(isDialog()).perform(click())
        attendi("la diretta spenta") { !servizi.diretta.acceso }
        attendi("la notifica tolta") { notifica(DirettaService.ID_NOTIFICA) == null }
    }

    @Test
    fun alTettoDiDataSyncIlServizioSiFermaLaDirettaNoELAvvisoCompare() {
        assumeTrue("solo con tetto=1 e il limite accorciato", argomenti.getString("tetto") == "1")
        accendi()
        shell("input keyevent KEYCODE_HOME")

        attendi("il tetto del servizio", 240_000) { servizi.direttaServizio.fase.value == DirettaServizioFase.SCADUTO }

        attendi("la notifica fissa tolta") { notifica(DirettaService.ID_NOTIFICA) == null }
        assertTrue("la diretta continua", servizi.diretta.acceso)
        val avviso = notifica(DirettaAvvisoNotifica.ID_AVVISO)
        assertNotNull("l'avviso del tetto", avviso)
        assertEquals(context.getString(R.string.diretta_avviso_text), avviso!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        pausaPerLaFoto()

        servizi.diretta.spegni()
        attendi("l'avviso tolto a fine diretta") { notifica(DirettaAvvisoNotifica.ID_AVVISO) == null }
        assertNull(notifica(DirettaService.ID_NOTIFICA))
    }
}
