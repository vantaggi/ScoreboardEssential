package it.vantaggi.scoreboardessential

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.os.Looper
import android.view.Window
import androidx.room.Room
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.repository.ColorRepository
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.service.MatchTimerService
import it.vantaggi.scoreboardessential.ui.MatchSettingsActivity
import it.vantaggi.scoreboardessential.ui.onboarding.OnboardingActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.fakes.RoboMenuItem

/**
 * Storico, onboarding e impostazioni si aprono sopra la partita in corso: non devono costruire
 * un secondo MainViewModel ne' distruggere quello che c'e'.
 */
@RunWith(RobolectricTestRunner::class)
class NavigazioneTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        // Come in AddEditPlayerActivityTest: un database in memoria al posto di quello vero.
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        setDatabaseInstance(database)

        // Un service finto da legare, come in MainViewModelTest: se una schermata costruisce un
        // MainViewModel, il test deve fallire sull'asserzione e non sul binder nullo di Robolectric.
        val app = RuntimeEnvironment.getApplication()
        val service = mock(MatchTimerService::class.java)
        whenever(service.matchTimerValue).thenReturn(MutableStateFlow(0L))
        whenever(service.isMatchTimerRunning).thenReturn(MutableStateFlow(false))
        whenever(service.keeperTimerValue).thenReturn(MutableStateFlow(0L))
        whenever(service.isKeeperTimerRunning).thenReturn(MutableStateFlow(false))
        whenever(service.keeperTimerExpired).thenReturn(MutableSharedFlow())
        val binder = mock(MatchTimerService.MatchTimerBinder::class.java)
        whenever(binder.getService()).thenReturn(service)
        shadowOf(app).setComponentNameAndServiceForBindService(ComponentName(app, MatchTimerService::class.java), binder)
    }

    @After
    fun tearDown() {
        database.close()
        setDatabaseInstance(null)
    }

    private fun setDatabaseInstance(instance: AppDatabase?) {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, instance)
    }

    /**
     * Il primo gesto di un MainViewModel e' legare MatchTimerService: nessuna di queste schermate
     * lega altro, quindi un legame qualsiasi vuol dire che ne e' nato uno.
     */
    private fun legamiAlService() = shadowOf(RuntimeEnvironment.getApplication()).boundServiceConnections

    private fun lasciaGirare() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `aprire lo storico non costruisce un MainViewModel`() {
        Robolectric.buildActivity(MatchHistoryActivity::class.java).setup()
        lasciaGirare()

        assertTrue("lo storico ha costruito un MainViewModel: ${legamiAlService()}", legamiAlService().isEmpty())
    }

    @Test
    fun `chiudere l'onboarding scrive la preferenza senza costruire un MainViewModel`() {
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java).setup().get()

        activity.findViewById<android.view.View>(R.id.skipButton).performClick()
        lasciaGirare()

        assertTrue("l'onboarding ha costruito un MainViewModel", legamiAlService().isEmpty())
        assertTrue(activity.isFinishing)
        // Le stesse preferenze e la stessa chiave che MainViewModel legge all'avvio.
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        assertTrue("la preferenza non e' stata scritta", prefs.getBoolean("onboarding_completed", false))
    }

    /**
     * La freccia della barra arriva all'Activity come la voce android.R.id.home. Senza una
     * risposta, AppCompat naviga verso parentActivityName e ricrea MainActivity (launchMode
     * standard), distruggendo il ViewModel della partita.
     */
    private fun frecciaSu(activity: Activity) {
        activity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, RoboMenuItem(android.R.id.home))
        lasciaGirare()
    }

    @Test
    fun `la freccia su dello storico chiude la schermata senza riaprire MainActivity`() {
        val activity = Robolectric.buildActivity(MatchHistoryActivity::class.java).setup().get()

        frecciaSu(activity)

        assertNull("la freccia ha avviato un'altra schermata", shadowOf(activity).nextStartedActivity)
        assertTrue("la freccia non ha chiuso lo storico", activity.isFinishing)
    }

    @Test
    fun `la freccia su delle impostazioni chiude la schermata senza riaprire MainActivity`() {
        val activity = Robolectric.buildActivity(MatchSettingsActivity::class.java).setup().get()

        frecciaSu(activity)

        assertNull("la freccia ha avviato un'altra schermata", shadowOf(activity).nextStartedActivity)
        assertTrue("la freccia non ha chiuso le impostazioni", activity.isFinishing)
    }

    /**
     * Lo storico legge le sole partite chiuse. La riga viva cancellata da li' faceva aggiornare
     * zero righe a ogni punto, e END MATCH rispondeva "salvata" per una partita che non c'era.
     */
    @Test
    fun `lo storico non mostra la partita in corso`() =
        runBlocking {
            val app = RuntimeEnvironment.getApplication()
            val chiusa = Match(team1Id = 1, team2Id = 2, team1Score = 3, team2Score = 1, timestamp = 1L, eventLog = "1|1,1,1,2")
            val viva = chiusa.copy(team1Score = 2, timestamp = 2L, isActive = true, eventLog = "1|1,1,2")
            val idChiusa = database.matchDao().insert(chiusa).toInt()
            database.matchDao().insert(viva)

            val storico = MatchRepository(database.matchDao(), app, ColorRepository(app)).allMatches.first()

            assertEquals(listOf(idChiusa), storico.map { it.match.matchId })
        }
}
