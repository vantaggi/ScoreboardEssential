package it.vantaggi.scoreboardessential

import android.content.ComponentName
import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.database.Team
import it.vantaggi.scoreboardessential.service.MatchTimerService
import it.vantaggi.scoreboardessential.ui.MatchHistoryUiState
import it.vantaggi.scoreboardessential.ui.chronicle.ChronicleActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** La Cronaca si apre dallo storico, dalla card di una partita con racchetta chiusa. */
@RunWith(RobolectricTestRunner::class)
class CronacaDalloStoricoTest {
    private lateinit var database: AppDatabase

    private val registro =
        MatchLogCodec.encode(List(24) { LoggedEvent(ScoringEvent.Point(side = 1)) })

    private val chiusa =
        Match(
            team1Id = 1,
            team2Id = 2,
            team1Score = 1,
            team2Score = 0,
            timestamp = 1_790_193_000_000L,
            sportId = SportRegistry.PADEL,
            eventLog = registro,
        )

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        setDatabaseInstance(database)

        // Lo storico crea un MainViewModel, che si lega al cronometro: gli si da' un servizio
        // finto, come in MainViewModelTest.
        val app = RuntimeEnvironment.getApplication()
        val binder = mock(MatchTimerService.MatchTimerBinder::class.java)
        val service = mock(MatchTimerService::class.java)
        whenever(binder.getService()).thenReturn(service)
        whenever(service.matchTimerValue).thenReturn(MutableStateFlow(0L))
        whenever(service.isMatchTimerRunning).thenReturn(MutableStateFlow(false))
        whenever(service.keeperTimerValue).thenReturn(MutableStateFlow(0L))
        whenever(service.isKeeperTimerRunning).thenReturn(MutableStateFlow(false))
        whenever(service.keeperTimerExpired).thenReturn(MutableSharedFlow())
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

    @Test
    fun `dallo storico la card di una partita di padel chiusa apre la sua Cronaca`() {
        val id =
            runBlocking {
                database.teamDao().insertWithId(Team(id = 1, name = "Rossi", color = 0xFFFFD600.toInt(), logoUri = null))
                database.teamDao().insertWithId(Team(id = 2, name = "Blu", color = 0xFF1A237E.toInt(), logoUri = null))
                database.matchDao().insert(chiusa).toInt()
            }
        val storico = Robolectric.buildActivity(MatchHistoryActivity::class.java).setup().get()
        val lista = storico.findViewById<RecyclerView>(R.id.match_history_recyclerview)

        // La lista arriva da un Flow di Room: si fa girare il looper finche' la card compare.
        val limite = System.currentTimeMillis() + 5_000
        val larga = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val alta = View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY)
        var comando: View? = null
        while (comando == null && System.currentTimeMillis() < limite) {
            shadowOf(Looper.getMainLooper()).idle()
            lista.measure(larga, alta)
            lista.layout(0, 0, 1080, 2000)
            comando = lista.findViewHolderForAdapterPosition(0)?.itemView?.findViewById(R.id.chronicle_match_button)
            if (comando == null) Thread.sleep(10)
        }
        assertNotNull("la card della partita non e' comparsa nello storico", comando)
        assertEquals(View.VISIBLE, comando!!.visibility)

        comando.performClick()

        val aperta = shadowOf(storico).nextStartedActivity
        assertEquals(ChronicleActivity::class.java.name, aperta.component?.className)
        assertEquals(id, aperta.getIntExtra(ChronicleActivity.EXTRA_MATCH_ID, -1))
    }

    /** Padel e tennis, chiuse, con un registro: la Cronaca si rigioca dai punti. */
    @Test
    fun `la Cronaca si offre solo per la racchetta chiusa con registro`() {
        fun offre(match: Match) = MatchHistoryUiState(MatchWithTeams(match, null, null, emptyList()), "").canOpenChronicle
        assertTrue(offre(chiusa))
        assertTrue("tennis", offre(chiusa.copy(sportId = SportRegistry.TENNIS)))
        assertFalse("calcio", offre(chiusa.copy(sportId = SportRegistry.FOOTBALL)))
        assertFalse("sport sconosciuto", offre(chiusa.copy(sportId = "curling")))
        assertFalse("partita ancora viva", offre(chiusa.copy(isActive = true)))
        assertFalse("solo punteggio finale", offre(chiusa.copy(eventLog = "")))
    }
}
