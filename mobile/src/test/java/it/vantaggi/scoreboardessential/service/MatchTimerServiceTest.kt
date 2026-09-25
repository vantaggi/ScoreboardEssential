package it.vantaggi.scoreboardessential.service

import android.content.Context
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.timeout
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyBlocking
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPowerManager

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class MatchTimerServiceTest {
    private lateinit var service: MatchTimerService

    @Mock
    private lateinit var mockConnectionManager: OptimizedWearDataSync

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Create the service using Robolectric
        service = Robolectric.buildService(MatchTimerService::class.java).create().get()

        // Use reflection to replace the private connectionManager
        val connectionManagerField = MatchTimerService::class.java.getDeclaredField("connectionManager")
        connectionManagerField.isAccessible = true
        connectionManagerField.set(service, mockConnectionManager)
    }

    @Test
    fun `startTimer sends data only once (Optimized)`() =
        runTest {
            // Start the timer
            service.startTimer()

            // Advance time to allow the loop to run a few times
            Thread.sleep(2500)

            // Verify sendData is called EXACTLY ONCE
            verify(mockConnectionManager, times(1)).sendData(
                path = anyString(),
                data = anyMap(),
                urgent = anyBoolean(),
            )

            service.stopTimer()
        }

    @Test
    fun `startTimer sends data periodically (with shortened interval)`() =
        runTest {
            // Set short interval for testing
            val originalInterval = MatchTimerService.SYNC_INTERVAL
            MatchTimerService.SYNC_INTERVAL = 1000L

            try {
                service.startTimer()

                // Wait enough for at least 1 periodic sync (Start + >1s)
                Thread.sleep(2500)

                // Verify: Should be called multiple times (Start + Periodic)
                verify(mockConnectionManager, atLeast(2)).sendData(
                    path = anyString(),
                    data = anyMap(),
                    urgent = anyBoolean(),
                )
            } finally {
                MatchTimerService.SYNC_INTERVAL = originalInterval
                service.stopTimer()
            }
        }

    /** Aspetta una condizione scritta da un thread del service, entro un limite. */
    private fun aspetta(
        limiteMs: Long = 3_000,
        condizione: () -> Boolean,
    ) {
        val fine = System.currentTimeMillis() + limiteMs
        while (!condizione() && System.currentTimeMillis() < fine) Thread.sleep(20)
    }

    private fun prefs() = service.getSharedPreferences("MatchTimerPrefs", Context.MODE_PRIVATE)

    /**
     * L8: alla scadenza il ramo metteva "fermo" ma non rilasciava il PARTIAL_WAKE_LOCK (senza
     * timeout) ne' il primo piano, e non salvava: un riavvio ripartiva da "in corso".
     */
    @Test
    fun `alla scadenza del portiere il service rilascia wake lock e primo piano e salva`() {
        service.startKeeperTimer(50L)
        val wakeLock = ShadowPowerManager.getLatestWakeLock()
        assertTrue("il conto deve tenere il wake lock", wakeLock.isHeld)

        // Rilascio e salvataggio avvengono sul thread del service, uno dopo l'altro: si aspettano tutti e due.
        aspetta { !wakeLock.isHeld && !prefs().getBoolean("keeper_running", true) }

        assertFalse("wake lock ancora tenuto dopo la scadenza", wakeLock.isHeld)
        assertTrue("primo piano non tolto dopo la scadenza", shadowOf(service).isForegroundStopped)
        assertFalse("stato salvato ancora in corso", prefs().getBoolean("keeper_running", true))
    }

    /**
     * L8: la scadenza e' un evento a se'. Pausa e azzeramento mettono "fermo" come lei, e il
     * ViewModel che deduceva la scadenza da li' scriveva "Keeper timer expired!" anche allora.
     */
    @Test
    fun `l'evento di scadenza parte solo alla scadenza, non su pausa ne' azzeramento`() {
        val scadenze = mutableListOf<Unit>()
        // Unconfined: l'iscrizione avviene subito, prima di ogni emissione.
        val ascolto = CoroutineScope(Dispatchers.Unconfined)
        ascolto.launch { service.keeperTimerExpired.collect { scadenze.add(it) } }
        try {
            service.startKeeperTimer(10_000L)
            service.pauseKeeperTimer()
            service.startKeeperTimer(10_000L)
            service.resetKeeperTimer()
            Thread.sleep(300)
            assertEquals("pausa e azzeramento non sono scadenze", 0, scadenze.size)

            service.startKeeperTimer(50L)
            aspetta { scadenze.isNotEmpty() }
            assertEquals(1, scadenze.size)
        } finally {
            ascolto.cancel()
        }
    }

    /**
     * L8: in pausa e alla ripresa il telefono manda il RESIDUO in keeper_millis, che dall'altra
     * parte diventava la durata. La durata configurata viaggia ora a parte e il residuo non la
     * sostituisce mai; keeper_millis resta com'era, per l'orologio non aggiornato.
     */
    @Test
    fun `pausa e ripresa mandano il residuo in keeper_millis e la durata configurata a parte`() {
        service.startKeeperTimer(300_000L)
        Thread.sleep(1_200)
        service.pauseKeeperTimer()
        // Il ViewModel riprende passando la durata configurata: il service riparte dal residuo.
        service.startKeeperTimer(300_000L)

        val messaggi = argumentCaptor<Map<String, Any>>()
        verifyBlocking(mockConnectionManager, timeout(2_000).times(3)) {
            sendData(eq(WearConstants.PATH_KEEPER_TIMER), messaggi.capture(), any())
        }
        // Gli invii partono in coroutine separate: si riconoscono dal contenuto, non dall'ordine.
        val tutti = messaggi.allValues
        val pausa = tutti.single { it[WearConstants.KEY_KEEPER_RUNNING] == false }
        val partenze = tutti.filter { it[WearConstants.KEY_KEEPER_RUNNING] == true }
        assertEquals(2, partenze.size)
        assertTrue("l'avvio manda la durata piena", partenze.any { it[WearConstants.KEY_KEEPER_MILLIS] == 300_000L })
        assertTrue("in pausa keeper_millis e' il residuo", (pausa[WearConstants.KEY_KEEPER_MILLIS] as Long) < 300_000L)
        assertTrue(
            "alla ripresa keeper_millis e' il residuo",
            partenze.any { (it[WearConstants.KEY_KEEPER_MILLIS] as Long) < 300_000L },
        )
        tutti.forEach { assertEquals(300_000L, it[WearConstants.KEY_KEEPER_DURATION]) }
        service.resetKeeperTimer()
    }
}
