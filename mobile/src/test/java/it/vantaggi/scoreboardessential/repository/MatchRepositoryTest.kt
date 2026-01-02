package it.vantaggi.scoreboardessential.repository

import android.content.Context
import android.content.SharedPreferences
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
class MatchRepositoryTest {

    private lateinit var matchRepository: MatchRepository
    private lateinit var matchDao: MatchDao
    private lateinit var context: Context
    private lateinit var colorRepository: ColorRepository
    private lateinit var sharedPreferences: SharedPreferences

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        matchDao = mock(MatchDao::class.java)
        context = mock(Context::class.java)
        colorRepository = mock(ColorRepository::class.java)
        sharedPreferences = mock(SharedPreferences::class.java)

        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences)
        `when`(colorRepository.getTeam1DefaultColor()).thenReturn(100)
        `when`(colorRepository.getTeam2DefaultColor()).thenReturn(200)

        // Default behavior for SharedPreferences getInt
        `when`(sharedPreferences.getInt("team1_color", 100)).thenReturn(100)
        `when`(sharedPreferences.getInt("team2_color", 200)).thenReturn(200)

        matchRepository = MatchRepository(matchDao, context, colorRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `team1Color initialization defaults`() = runTest {
        // Assert initial value matches default from ColorRepository
        assertEquals(100, matchRepository.team1Color.first())
    }

    @Test
    fun `team2Color initialization defaults`() = runTest {
        // Assert initial value matches default from ColorRepository
        assertEquals(200, matchRepository.team2Color.first())
    }

    @Test
    fun `team1Color initialization from prefs`() = runTest {
        // Setup prefs to return a different color
        `when`(sharedPreferences.getInt("team1_color", 100)).thenReturn(101)

        // Re-initialize repository to pick up new prefs
        matchRepository = MatchRepository(matchDao, context, colorRepository)

        assertEquals(101, matchRepository.team1Color.first())
    }

    @Test
    fun `team2Color initialization from prefs`() = runTest {
        // Setup prefs to return a different color
        `when`(sharedPreferences.getInt("team2_color", 200)).thenReturn(201)

        // Re-initialize repository to pick up new prefs
        matchRepository = MatchRepository(matchDao, context, colorRepository)

        assertEquals(201, matchRepository.team2Color.first())
    }

    @Test
    fun `team1Color updates on pref change`() = runTest {
        val listenerCaptor = ArgumentCaptor.forClass(SharedPreferences.OnSharedPreferenceChangeListener::class.java)
        verify(sharedPreferences).registerOnSharedPreferenceChangeListener(listenerCaptor.capture())

        val results = mutableListOf<Int>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            matchRepository.team1Color.collect { results.add(it) }
        }

        // Trigger change
        `when`(sharedPreferences.getInt("team1_color", 100)).thenReturn(102)
        listenerCaptor.value.onSharedPreferenceChanged(sharedPreferences, "team1_color")

        assertEquals(listOf(100, 102), results)
        job.cancel()
    }

    @Test
    fun `team2Color updates on pref change`() = runTest {
        val listenerCaptor = ArgumentCaptor.forClass(SharedPreferences.OnSharedPreferenceChangeListener::class.java)
        verify(sharedPreferences).registerOnSharedPreferenceChangeListener(listenerCaptor.capture())

        val results = mutableListOf<Int>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            matchRepository.team2Color.collect { results.add(it) }
        }

        // Trigger change
        `when`(sharedPreferences.getInt("team2_color", 200)).thenReturn(202)
        listenerCaptor.value.onSharedPreferenceChanged(sharedPreferences, "team2_color")

        assertEquals(listOf(200, 202), results)
        job.cancel()
    }

    @Test
    fun `deleteMatch calls dao`() = runTest {
        val match = mock(Match::class.java)
        matchRepository.deleteMatch(match)
        verify(matchDao).delete(match)
    }

    @Test
    fun `close unregisters listener`() {
        matchRepository.close()
        // We can't easily capture the *exact* listener instance passed to unregister
        // without keeping a reference to it in the test setup if we cared about identity,
        // but here verifying that *any* listener was unregistered is a decent proxy,
        // or we can capture it again.

        // Since the listener is private val in repo, we can capture what was registered first
        val listenerCaptor = ArgumentCaptor.forClass(SharedPreferences.OnSharedPreferenceChangeListener::class.java)
        verify(sharedPreferences).registerOnSharedPreferenceChangeListener(listenerCaptor.capture())

        verify(sharedPreferences).unregisterOnSharedPreferenceChangeListener(listenerCaptor.value)
    }
}
