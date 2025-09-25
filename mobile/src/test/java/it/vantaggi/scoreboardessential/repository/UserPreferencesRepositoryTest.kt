package it.vantaggi.scoreboardessential.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
class UserPreferencesRepositoryTest {

    private lateinit var repository: UserPreferencesRepository
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockContext: Context

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockContext = mock(Context::class.java)
        mockPrefs = mock(SharedPreferences::class.java)
        mockEditor = mock(SharedPreferences.Editor::class.java)

        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor)

        repository = UserPreferencesRepository(mockContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `hasSeenTutorial returns false by default`() = runTest {
        `when`(mockPrefs.getBoolean(eq("has_seen_tutorial"), eq(false))).thenReturn(false)

        val result = repository.hasSeenTutorial.first()

        assertEquals(false, result)
    }

    @Test
    fun `setHasSeenTutorial updates the preference`() = runTest {
        repository.setHasSeenTutorial(true)
        verify(mockEditor).putBoolean("has_seen_tutorial", true)
        verify(mockEditor).apply()
    }

    @Test
    fun `hasSeenTutorial returns true after being set`() = runTest {
        `when`(mockPrefs.getBoolean(eq("has_seen_tutorial"), eq(false))).thenReturn(true)

        val result = repository.hasSeenTutorial.first()

        assertEquals(true, result)
    }

    @Test
    fun `hasSeenTutorial flow emits new value when preference changes`() = runTest {
        val listenerCaptor: ArgumentCaptor<SharedPreferences.OnSharedPreferenceChangeListener> = ArgumentCaptor.forClass(SharedPreferences.OnSharedPreferenceChangeListener::class.java)

        val results = mutableListOf<Boolean>()
        `when`(mockPrefs.getBoolean(eq("has_seen_tutorial"), eq(false))).thenReturn(false)

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.hasSeenTutorial.collect { results.add(it) }
        }

        verify(mockPrefs).registerOnSharedPreferenceChangeListener(listenerCaptor.capture())
        assertEquals(listOf(false), results)

        `when`(mockPrefs.getBoolean(eq("has_seen_tutorial"), eq(false))).thenReturn(true)
        listenerCaptor.value.onSharedPreferenceChanged(mockPrefs, "has_seen_tutorial")

        assertEquals(listOf(false, true), results)

        job.cancel()
    }
}