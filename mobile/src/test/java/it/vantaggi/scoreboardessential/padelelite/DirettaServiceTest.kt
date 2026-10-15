package it.vantaggi.scoreboardessential.padelelite

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import it.vantaggi.scoreboardessential.MainActivity
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import it.vantaggi.scoreboardessential.core.ExportOrigin
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchPlayer
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.repository.ColorRepository
import it.vantaggi.scoreboardessential.repository.MatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId

/** Come [DirettaService] ma negato dal sistema: l'app e' in secondo piano e `startForeground` lancia. */
class DirettaServiceNegato : DirettaService() {
    override fun entraInPrimoPiano(notifica: Notification): Boolean = false
}

/**
 * Il servizio in primo piano e la sua notifica (D-3): testo, canale, azioni, aggiornamento al secondo, tetto delle 6 ore
 * di Android 15 e avvio negato in secondo piano. La diretta e' quella vera, col server finto; il tempo del servizio e'
 * virtuale (`advanceTimeBy`), quello della diretta no.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class DirettaServiceTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val context: Context get() = app
    private var finto: ServerFinto? = null
    private val scheduler = TestCoroutineScheduler()
    private val scopeServizio = CoroutineScope(StandardTestDispatcher(scheduler) + SupervisorJob())
    private lateinit var servizi: PadelEliteServices

    @Before
    fun prepara() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build())
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun chiudi() {
        scopeServizio.cancel()
        finto?.chiudi()
    }

    private fun creaServizi(risposte: (RecordedRequest, Int) -> MockResponse = { _, _ -> rispostaDiPubblicazione() }): PadelEliteServices {
        val s =
            ServerFinto { richiesta, n ->
                if (richiesta.path!!.contains("refresh_token")) json(400, """{"error":"invalid_grant"}""") else risposte(richiesta, n)
            }.also { finto = it }
        val sessione = SessionStore(preferenze(app, "sessione_service"), SoftwareSecretBox())
        sessione.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), sessione)
        account.selectGroup(PadelEliteGroup("g-1", "Padel", "admin"))
        return PadelEliteServices(
            app,
            s.config(),
            MatchRepository(mock(it.vantaggi.scoreboardessential.database.MatchDao::class.java), app, ColorRepository(app)),
            accountFactory = { account },
            invioStoreFactory = { InvioStore(preferenze(app, "invii_service")) },
            direttaStoreFactory = { DirettaStore(preferenze(app, "diretta_service")) },
            direttaMinIntervalMs = 40,
            direttaAvviatore = NullAvviatore,
        ).also { servizi = it }
    }

    private object NullAvviatore : DirettaAvviatore {
        override fun avvia() = true
    }

    private fun foto(punti: Int) =
        DirettaFoto(
            UUID_PARTITA,
            SportRegistry.forMatch(SportRegistry.PADEL, listOf(1, 2, 3, 4)),
            List(punti) { LoggedEvent(ScoringEvent.Point(side = 1), it * 30_000L) },
            listOf(MatchPlayer(1, "Marco", 1), MatchPlayer(2, "Anna", 2), MatchPlayer(3, "Luca", 1), MatchPlayer(4, "Sara", 2)),
            ExportOrigin(UUID_PARTITA, 1_790_190_240_000L, ZoneId.of("Europe/Rome"), "1.0"),
        )

    private fun attendi(
        che: String,
        entroMs: Long = 6_000,
        condizione: () -> Boolean,
    ) {
        val limite = System.currentTimeMillis() + entroMs
        while (!condizione()) {
            check(System.currentTimeMillis() < limite) { "non e' successo: $che" }
            Thread.sleep(10)
        }
    }

    /** Il servizio avviato come lo avvia il sistema: onCreate, i campi di prova, onStartCommand. */
    private fun avviaIlServizio(classe: Class<out DirettaService> = DirettaService::class.java): DirettaService {
        val controller = Robolectric.buildService(classe)
        val servizio = controller.create().get()
        servizio.servizi = servizi
        servizio.scope = scopeServizio
        controller.startCommand(0, 1)
        return servizio
    }

    private fun passa(ms: Long) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    private fun notificaInCorso(): Notification? =
        shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(DirettaService.ID_NOTIFICA)

    private fun titolo(n: Notification) = n.extras.getString(Notification.EXTRA_TITLE)

    private fun testo(n: Notification) = n.extras.getString(Notification.EXTRA_TEXT)

    // --- Notifica ---

    @Test
    fun `parte in primo piano con la notifica In diretta su gruppo e il punteggio`() {
        creaServizi()
        servizi.diretta.accendi("g-1", "Padel", foto(2))
        attendi("il punteggio") {
            servizi.diretta.punteggio.value
                .isNotEmpty()
        }

        val servizio = avviaIlServizio()

        val ombra = shadowOf(servizio)
        val notifica = ombra.lastForegroundNotification
        assertNotNull(notifica)
        assertEquals(DirettaService.ID_NOTIFICA, ombra.lastForegroundNotificationId)
        assertEquals(app.getString(R.string.diretta_live, "Padel"), titolo(notifica))
        assertEquals(servizi.diretta.punteggio.value, testo(notifica))
        assertTrue(testo(notifica)!!.startsWith("30-0"))
        assertEquals(ScoreboardEssentialApplication.CHANNEL_ID_DIRETTA, notifica.channelId)
        assertEquals(R.drawable.notifica_sensors, notifica.smallIcon.resId)
        assertTrue("fissa: non si toglie con uno swipe", notifica.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    @Config(qualifiers = "it")
    fun `in italiano dice In diretta su e Interrompi diretta`() {
        creaServizi()
        servizi.diretta.accendi("g-1", "Padel", foto(1))
        attendi("il punteggio") {
            servizi.diretta.punteggio.value
                .isNotEmpty()
        }

        val notifica = shadowOf(avviaIlServizio()).lastForegroundNotification

        assertEquals("In diretta su Padel", titolo(notifica))
        assertEquals(
            "Interrompi diretta",
            notifica.actions
                .single()
                .title
                .toString(),
        )
    }

    @Test
    fun `il canale e' a importanza bassa, senza suono ne' vibrazione`() {
        val canale =
            app
                .getSystemService(
                    NotificationManager::class.java,
                ).getNotificationChannel(ScoreboardEssentialApplication.CHANNEL_ID_DIRETTA)

        assertNotNull(canale)
        assertEquals(NotificationManager.IMPORTANCE_LOW, canale.importance)
        assertNull(canale.sound)
        assertFalse(canale.shouldVibrate())
    }

    @Test
    fun `il tocco apre l'app sul foglio e Interrompi diretta porta alla conferma`() {
        creaServizi()
        servizi.diretta.accendi("g-1", "Padel", foto(1))
        val notifica = shadowOf(avviaIlServizio()).lastForegroundNotification

        val tocco = shadowOf(notifica.contentIntent).savedIntent
        val interrompi = shadowOf(notifica.actions.single().actionIntent).savedIntent

        assertEquals(MainActivity::class.java.name, tocco.component!!.className)
        assertEquals(DirettaAzione.APRI, DirettaIntent.azioneDi(tocco))
        assertEquals(MainActivity::class.java.name, interrompi.component!!.className)
        assertEquals(DirettaAzione.INTERROMPI, DirettaIntent.azioneDi(interrompi))
        // Con SINGLE_TOP + CLEAR_TOP la schermata gia' aperta riceve onNewIntent invece di raddoppiare.
        assertTrue(interrompi.flags and android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(interrompi.flags and android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        // Le due azioni sono intenti distinti (gli extra non bastano a distinguerli).
        assertFalse(tocco.filterEquals(interrompi))
    }

    @Test
    fun `l'intento di Interrompi si legge una volta e lo spegni confermato chiude senza risultato con la chiusura in coda`() {
        creaServizi()
        servizi.diretta.accendi("g-1", "Padel", foto(1))
        val interrompi =
            shadowOf(
                shadowOf(avviaIlServizio())
                    .lastForegroundNotification.actions
                    .single()
                    .actionIntent,
            ).savedIntent

        // L'activity legge l'azione e mostra la conferma: finche' non si conferma, niente cambia.
        assertEquals(DirettaAzione.INTERROMPI, DirettaIntent.azioneDi(interrompi))
        assertTrue(servizi.diretta.acceso)
        // Rotazione: l'azione si consuma una volta sola e il dialogo non ricompare.
        DirettaIntent.consuma(interrompi)
        assertNull(DirettaIntent.azioneDi(interrompi))

        // «Interrompi» nel dialogo e' lo stesso spegni() dell'interruttore (MainViewModel.spegniLaDiretta).
        servizi.diretta.spegni()

        assertEquals(DirettaFase.CHIUSA, servizi.diretta.stato.value.fase)
        attendi("la chiusura in coda") {
            WorkManager
                .getInstance(app)
                .getWorkInfosForUniqueWork(DirettaChiusuraWorker.workName(UUID_PARTITA))
                .get()
                .size == 1
        }
        assertEquals(ChiusuraFase.IN_CORSO, runBlocking { servizi.chiusuraDellaDiretta(UUID_PARTITA).first() })
    }

    @Test
    fun `l'azione della notifica non si riesegue se l'activity e' ricreata o riaperta dalla cronologia`() {
        val interrompi = DirettaIntent.per(context, DirettaAzione.INTERROMPI)

        assertEquals(DirettaAzione.INTERROMPI, DirettaIntent.azioneDaEseguire(interrompi, ricreata = false))
        // Ricreata (processo morto, rotazione): l'intento e' quello di prima.
        assertNull(DirettaIntent.azioneDaEseguire(interrompi, ricreata = true))
        // Riaperta dalle app recenti: l'intento e' quello di prima.
        val dallaCronologia =
            DirettaIntent.per(context, DirettaAzione.INTERROMPI).addFlags(android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(DirettaIntent.azioneDaEseguire(dallaCronologia, ricreata = false))
        assertNull(DirettaIntent.azioneDaEseguire(null, ricreata = false))
        assertNull(DirettaIntent.azioneDaEseguire(android.content.Intent(), ricreata = false))
    }

    @Test
    fun `quando la diretta non trasmette la notifica dice il motivo`() {
        creaServizi { _, _ -> json(500, "{}") }
        servizi.diretta.accendi("g-1", "Padel", foto(1))
        attendi("l'attesa") { servizi.diretta.stato.value.fase == DirettaFase.IN_ATTESA }

        val notifica = shadowOf(avviaIlServizio()).lastForegroundNotification

        assertEquals(app.getString(R.string.diretta_live, "Padel"), titolo(notifica))
        assertEquals(app.getString(R.string.diretta_wait_network), testo(notifica))
    }

    // --- Aggiornamento ---

    @Test
    fun `la notifica si aggiorna al piu' una volta al secondo e non si riscrive se il testo e' lo stesso`() {
        creaServizi()
        servizi.diretta.accendi("g-1", "Padel", foto(1))
        attendi("il primo punteggio") {
            servizi.diretta.punteggio.value
                .startsWith("15-0")
        }
        avviaIlServizio()
        passa(0)
        assertTrue(testo(notificaInCorso()!!)!!.startsWith("15-0"))

        servizi.diretta.cambiata(foto(2))
        attendi("il secondo punteggio") {
            servizi.diretta.punteggio.value
                .startsWith("30-0")
        }
        passa(999)
        assertTrue("prima del secondo non cambia", testo(notificaInCorso()!!)!!.startsWith("15-0"))
        passa(1)
        assertTrue("passato il secondo si aggiorna", testo(notificaInCorso()!!)!!.startsWith("30-0"))

        // Altri due punti in fila nello stesso secondo: ne arriva uno, l'ultimo.
        servizi.diretta.cambiata(foto(3))
        attendi("il terzo punteggio") {
            servizi.diretta.punteggio.value
                .startsWith("40-0")
        }
        passa(500)
        assertTrue(testo(notificaInCorso()!!)!!.startsWith("30-0"))
        passa(500)
        assertTrue(testo(notificaInCorso()!!)!!.startsWith("40-0"))
    }

    // --- Quando si ferma ---

    @Test
    fun `si ferma da solo quando la diretta si chiude`() {
        creaServizi()
        servizi.diretta.accendi("g-1", "Padel", foto(1))
        val servizio = avviaIlServizio()
        passa(1)
        assertFalse(shadowOf(servizio).isStoppedBySelf)

        servizi.diretta.chiudi(conRisultato = true)
        // Subito, senza aspettare la pausa di un secondo fra due notifiche.
        passa(1)

        assertTrue(shadowOf(servizio).isStoppedBySelf)
        assertTrue(shadowOf(servizio).isForegroundStopped)
    }

    @Test
    fun `se la diretta si e' chiusa prima che il servizio partisse, entra in primo piano e si ferma subito`() {
        creaServizi()
        servizi.diretta.accendi("g-1", "Padel", foto(1))
        servizi.diretta.spegni()

        val servizio = avviaIlServizio()

        // Il sistema vuole comunque startForeground dopo startForegroundService: lo fa, e subito dopo il servizio se ne va.
        assertTrue(shadowOf(servizio).isForegroundStopped)
        assertTrue(shadowOf(servizio).isStoppedBySelf)
    }

    @Test
    fun `una diretta fermata prima che il servizio parta non ha uno stopService e il servizio se ne va da solo`() {
        creaServizi()
        val applicazione = app
        // Il controllo con l'avviatore vero, sincrono: vede l'accensione e subito dopo la chiusura, come in pochi millisecondi reali.
        val controllo =
            DirettaServizioControllo(
                servizi.diretta.stato,
                DirettaServiceAvviatore(context),
                CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            )

        servizi.diretta.accendi("g-1", "Padel", foto(1))
        servizi.diretta.chiudi(conRisultato = false)

        assertEquals(DirettaServizioFase.SPENTO, controllo.fase.value)
        val ombraApp = shadowOf(applicazione)
        assertNotNull("l'avvio e' stato chiesto", ombraApp.peekNextStartedService())
        assertNull("nessuno ferma il servizio da fuori: crollerebbe l'app", ombraApp.nextStoppedService)

        // Quando il sistema lo avvia, entra in primo piano (obbligatorio) e subito dopo si ferma da solo.
        val servizio = avviaIlServizio()
        assertTrue(shadowOf(servizio).isForegroundStopped)
        assertTrue(shadowOf(servizio).isStoppedBySelf)
    }

    // --- Android 15: il tetto delle 6 ore ---

    @Test
    fun `onTimeout ferma il servizio, scrive lo stato e la diretta continua`() {
        creaServizi()
        servizi.diretta.accendi("g-1", "Padel", foto(1))
        attendi("l'avvio dal controllo") { servizi.direttaServizio.fase.value == DirettaServizioFase.ATTIVO }
        val servizio = avviaIlServizio()

        servizio.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        assertTrue("il sistema vuole che si fermi subito", shadowOf(servizio).isStoppedBySelf)
        assertTrue(shadowOf(servizio).isForegroundStopped)
        assertEquals(DirettaServizioFase.SCADUTO, servizi.direttaServizio.fase.value)
        assertTrue("la diretta non si ferma", servizi.diretta.acceso)
        assertEquals(DirettaFase.IN_CORSO, servizi.diretta.stato.value.fase)
        // Il foglio lo dice con una parola, e chiede di tenere l'app aperta.
        val riga = direttaLook(context, servizi.diretta.stato.value, servizio = servizi.direttaServizio.fase.value)!!
        assertEquals(app.getString(R.string.diretta_live_service_timeout, "Padel"), riga.text)
    }

    // --- Dopo il tetto delle 6 ore: l'avviso ordinario (debito 15) ---

    private fun avviso(): Notification? =
        shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(DirettaAvvisoNotifica.ID_AVVISO)

    @Test
    fun `dopo il tetto delle 6 ore compare una notifica ordinaria che dice di tenere l'app aperta`() {
        creaServizi()
        servizi.diretta.accendi("g-1", "Padel", foto(1))
        attendi("l'avvio dal controllo") { servizi.direttaServizio.fase.value == DirettaServizioFase.ATTIVO }
        val servizio = avviaIlServizio()
        assertNull("prima del tetto non c'e'", avviso())

        servizio.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        val n = avviso()
        assertNotNull(n)
        assertEquals(app.getString(R.string.diretta_live_service_timeout, "Padel"), testo(n!!))
        assertEquals(app.getString(R.string.diretta_live, "Padel"), titolo(n))
        assertEquals(
            "stesso canale della notifica fissa: importanza bassa, nessun suono",
            ScoreboardEssentialApplication.CHANNEL_ID_DIRETTA,
            n.channelId,
        )
        assertEquals(R.drawable.notifica_sensors, n.smallIcon.resId)
        assertEquals("ordinaria: si puo' scorrere via", 0, n.flags and Notification.FLAG_ONGOING_EVENT)
        assertEquals("la notifica fissa e' sparita", null, notificaInCorso())
        assertNotNull("tocco = apre l'app", n.contentIntent)
    }

    @Test
    fun `l'avviso si toglie quando la diretta finisce`() {
        creaServizi()
        servizi.diretta.accendi("g-1", "Padel", foto(1))
        attendi("l'avvio dal controllo") { servizi.direttaServizio.fase.value == DirettaServizioFase.ATTIVO }
        avviaIlServizio().onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        assertNotNull(avviso())

        servizi.diretta.spegni()

        attendi("l'avviso tolto") { avviso() == null }
    }

    @Test
    fun `l'avviso si toglie quando il servizio riparte`() {
        creaServizi()
        servizi.diretta.accendi("g-1", "Padel", foto(1))
        attendi("l'avvio dal controllo") { servizi.direttaServizio.fase.value == DirettaServizioFase.ATTIVO }
        avviaIlServizio().onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        assertNotNull(avviso())

        val riavviato = avviaIlServizio()

        assertNull(avviso())
        assertNotNull("la notifica fissa e' tornata", shadowOf(riavviato).lastForegroundNotification)
    }

    // --- Avvio negato in secondo piano ---

    @Test
    fun `se startForeground e' negato il servizio rinuncia senza crash e la diretta prosegue`() {
        creaServizi()
        servizi.diretta.accendi("g-1", "Padel", foto(1))
        attendi("l'avvio dal controllo") { servizi.direttaServizio.fase.value == DirettaServizioFase.ATTIVO }

        val servizio = avviaIlServizio(DirettaServiceNegato::class.java)

        assertTrue(shadowOf(servizio).isStoppedBySelf)
        assertEquals(DirettaServizioFase.NON_AVVIATO, servizi.direttaServizio.fase.value)
        assertTrue(servizi.diretta.acceso)
        val riga = direttaLook(context, servizi.diretta.stato.value, servizio = DirettaServizioFase.NON_AVVIATO)!!
        assertEquals(app.getString(R.string.diretta_live_no_service, "Padel"), riga.text)
    }

    @Test
    fun `senza il permesso per le notifiche il servizio parte lo stesso`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        creaServizi()
        servizi.diretta.accendi("g-1", "Padel", foto(1))

        val servizio = avviaIlServizio()

        assertNotNull(shadowOf(servizio).lastForegroundNotification)
        assertFalse(shadowOf(servizio).isStoppedBySelf)
    }
}
