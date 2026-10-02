package it.vantaggi.scoreboardessential.service

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyBlocking
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPowerManager

/**
 * Il service gira qui su un dispatcher di test e su un orologio che legge il tempo virtuale:
 * `advanceTimeBy` fa passare i secondi senza aspettarli. Prima i test dormivano davvero
 * (`Thread.sleep`) e contavano gli invii di una coroutine su Dispatchers.Default, che nessuno
 * governava.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class MatchTimerServiceTest {
    private lateinit var service: MatchTimerService
    private lateinit var scheduler: TestCoroutineScheduler

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

        // Tempo virtuale: il service legge l'ora da qui e le sue coroutine girano sullo stesso scheduler.
        scheduler = TestCoroutineScheduler()
        service.clock = { ORA_DI_PARTENZA + scheduler.currentTime }
        service.scope = CoroutineScope(StandardTestDispatcher(scheduler) + SupervisorJob())
    }

    @After
    fun tearDown() {
        service.scope.cancel()
    }

    /** Fa passare il tempo virtuale ed esegue tutto cio' che scade entro l'istante finale compreso. */
    private fun passa(millisecondi: Long) {
        scheduler.advanceTimeBy(millisecondi)
        scheduler.runCurrent()
    }

    @Test
    fun `startTimer sends data only once (Optimized)`() {
        service.startTimer()

        // Il sincronismo periodico e' a 60 secondi: in due secondi e mezzo parte solo l'invio di avvio.
        passa(2_500)

        verifyBlocking(mockConnectionManager, times(1)) {
            sendData(
                path = anyString(),
                data = anyMap(),
                urgent = anyBoolean(),
            )
        }

        service.stopTimer()
    }

    @Test
    fun `startTimer sends data periodically (with shortened interval)`() {
        // Set short interval for testing
        val originalInterval = MatchTimerService.SYNC_INTERVAL
        MatchTimerService.SYNC_INTERVAL = 1000L

        try {
            service.startTimer()

            // Avvio a 0, poi un invio a ogni secondo che passa: 1000 e 2000.
            passa(2_500)

            verifyBlocking(mockConnectionManager, times(3)) {
                sendData(
                    path = anyString(),
                    data = anyMap(),
                    urgent = anyBoolean(),
                )
            }
        } finally {
            MatchTimerService.SYNC_INTERVAL = originalInterval
            service.stopTimer()
        }
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

        // Prima del controllo successivo (un secondo) il conto e' ancora in corso.
        passa(500)
        assertTrue("il conto deve tenere il wake lock finche' non scade", wakeLock.isHeld)

        passa(1_000)

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
            // Oltre la durata del conto interrotto: se un job fosse rimasto vivo sarebbe scaduto ora.
            passa(11_000)
            assertEquals("pausa e azzeramento non sono scadenze", 0, scadenze.size)

            service.startKeeperTimer(50L)
            passa(1_500)
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
        passa(1_200)
        service.pauseKeeperTimer()
        // Il ViewModel riprende passando la durata configurata: il service riparte dal residuo.
        service.startKeeperTimer(300_000L)
        scheduler.runCurrent()

        val messaggi = argumentCaptor<Map<String, Any>>()
        verifyBlocking(mockConnectionManager, times(3)) {
            sendData(eq(WearConstants.PATH_KEEPER_TIMER), messaggi.capture(), any())
        }
        // Gli invii partono in coroutine separate: si riconoscono dal contenuto, non dall'ordine.
        val tutti = messaggi.allValues
        val pausa = tutti.single { it[WearConstants.KEY_KEEPER_RUNNING] == false }
        val partenze = tutti.filter { it[WearConstants.KEY_KEEPER_RUNNING] == true }
        assertEquals(2, partenze.size)
        assertTrue("l'avvio manda la durata piena", partenze.any { it[WearConstants.KEY_KEEPER_MILLIS] == 300_000L })
        // Il conto ha fatto due passi (a 0 e a 1000) prima della pausa: il residuo e' esattamente 299 secondi.
        assertEquals("in pausa keeper_millis e' il residuo", 299_000L, pausa[WearConstants.KEY_KEEPER_MILLIS])
        assertTrue(
            "alla ripresa keeper_millis e' il residuo",
            partenze.any { it[WearConstants.KEY_KEEPER_MILLIS] == 299_000L },
        )
        tutti.forEach { assertEquals(300_000L, it[WearConstants.KEY_KEEPER_DURATION]) }
        service.resetKeeperTimer()
    }

    /**
     * Passo 13: lo stato SCADUTO dello slot nasce solo dall'evento di scadenza, quindi dopo un
     * riavvio non ne deve nascere uno finto. Un conto che era in corso e la cui fine e' passata mentre
     * il processo non c'era riparte FERMO a zero: nessun conto da riprendere, nessun wake lock
     * (e quindi nessuna scadenza da annunciare, ne' al registro ne' allo slot).
     */
    @Test
    fun `dopo un riavvio con la fine del conto gia' passata il service riparte fermo e senza scadenza`() {
        prefs()
            .edit()
            .putBoolean("keeper_running", true)
            .putLong("keeper_end_time", System.currentTimeMillis() - 10_000L)
            .commit()

        val riavviato = Robolectric.buildService(MatchTimerService::class.java).create().get()
        val scadenze = mutableListOf<Unit>()
        // Unconfined: l'iscrizione avviene subito, prima di ogni emissione.
        val ascolto = CoroutineScope(Dispatchers.Unconfined)
        ascolto.launch { riavviato.keeperTimerExpired.collect { scadenze.add(it) } }
        try {
            assertFalse("non c'e' un conto da riprendere", riavviato.isKeeperTimerRunning.value)
            assertEquals(0L, riavviato.keeperTimerValue.value)
            val wakeLock = ShadowPowerManager.getLatestWakeLock()
            assertTrue("il riavvio non deve tenere acceso un wake lock per un conto finito", wakeLock == null || !wakeLock.isHeld)
            assertEquals("il riavvio non annuncia nessuna scadenza", 0, scadenze.size)
        } finally {
            ascolto.cancel()
            riavviato.scope.cancel()
        }
    }

    /**
     * Il cambio dal telefono riparte da capo con un solo messaggio. Con azzeramento e avvio come due
     * operazioni i due invii partivano da due coroutine sullo stesso path e l'ultimo a scrivere vinceva:
     * l'orologio poteva restare con l'azzeramento. Parte da un conto fermo a meta' (pausa), perche' e'
     * il caso in cui un avvio semplice riprenderebbe dal residuo.
     */
    @Test
    fun `restartKeeperTimer riparte dalla durata piena con un solo messaggio e senza scadenze`() {
        val scadenze = mutableListOf<Unit>()
        val ascolto = CoroutineScope(Dispatchers.Unconfined)
        ascolto.launch { service.keeperTimerExpired.collect { scadenze.add(it) } }
        try {
            service.startKeeperTimer(300_000L)
            passa(1_200)
            service.pauseKeeperTimer()
            scheduler.runCurrent()
            org.mockito.Mockito.clearInvocations(mockConnectionManager)

            service.restartKeeperTimer(300_000L)
            scheduler.runCurrent()

            val messaggi = argumentCaptor<Map<String, Any>>()
            verifyBlocking(mockConnectionManager, times(1)) {
                sendData(eq(WearConstants.PATH_KEEPER_TIMER), messaggi.capture(), any())
            }
            val messaggio = messaggi.firstValue
            assertEquals("riparte dalla durata piena, non dal residuo", 300_000L, messaggio[WearConstants.KEY_KEEPER_MILLIS])
            assertEquals(true, messaggio[WearConstants.KEY_KEEPER_RUNNING])
            assertEquals(300_000L, messaggio[WearConstants.KEY_KEEPER_DURATION])
            assertTrue("il conto e' in corso", service.isKeeperTimerRunning.value)

            passa(2_000)
            assertEquals("il conto scende dalla durata piena", 298_000L, service.keeperTimerValue.value)
            assertEquals("nessuna scadenza dal riavvio", 0, scadenze.size)
            service.resetKeeperTimer()
        } finally {
            ascolto.cancel()
        }
    }

    private fun notificaDiScadenza() =
        shadowOf(service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .getNotification(null, KEEPER_TIMER_EXPIRED_NOTIFICATION_ID)

    /**
     * L'avviso di scadenza resta in vista finche' qualcuno non lo chiude: il tocco su CAMBIO (un
     * nuovo avvio) e l'azzeramento tolgono lo SCADUTO dallo slot e devono togliere anche lui.
     */
    @Test
    fun `l'avvio e l'azzeramento del portiere tolgono l'avviso di scadenza`() {
        shadowOf(service.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        service.startKeeperTimer(50L)
        passa(1_500)
        assertNotNull("la scadenza deve mostrare l'avviso", notificaDiScadenza())
        service.restartKeeperTimer(300_000L)
        assertNull("il tocco su CAMBIO deve togliere l'avviso", notificaDiScadenza())
        service.resetKeeperTimer()

        service.startKeeperTimer(50L)
        passa(1_500)
        assertNotNull("la scadenza deve mostrare l'avviso", notificaDiScadenza())
        service.resetKeeperTimer()
        assertNull("l'azzeramento deve togliere l'avviso", notificaDiScadenza())
    }

    private companion object {
        const val ORA_DI_PARTENZA = 1_700_000_000_000L

        // Lo stesso numero di KEEPER_TIMER_EXPIRED_NOTIFICATION_ID, che e' privato nel service.
        const val KEEPER_TIMER_EXPIRED_NOTIFICATION_ID = 2
    }
}
