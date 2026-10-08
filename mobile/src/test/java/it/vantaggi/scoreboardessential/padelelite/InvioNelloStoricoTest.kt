package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import it.vantaggi.scoreboardessential.MatchHistoryAdapter
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.ui.MatchHistoryUiState
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
import org.robolectric.annotation.Config

/**
 * Il comando e lo stato di invio sullo storico, e la funzione spenta: senza configurazione non
 * compare niente. Ogni stato si dice con una parola e un'icona.
 */
@RunWith(AndroidJUnit4::class)
class InvioNelloStoricoTest {
    private val base: Context get() = ApplicationProvider.getApplicationContext()
    private val tema: Context get() = ContextThemeWrapper(base, R.style.Theme_ScoreboardEssential)
    private var finto: ServerFinto? = null

    @Before
    fun avvia() {
        WorkManagerTestInitHelper.initializeTestWorkManager(base, Configuration.Builder().build())
    }

    @After
    fun chiudi() {
        finto?.chiudi()
    }

    private fun partita(
        sport: String = SportRegistry.PADEL,
        uuid: String? = UUID_PARTITA,
        registro: Boolean = true,
    ) = Match(
        team1Id = 1,
        team2Id = 2,
        team1Score = 0,
        team2Score = 0,
        timestamp = 1_790_193_000_000L,
        sportId = sport,
        eventLog = if (registro) MatchLogCodec.encode(List(4) { LoggedEvent(ScoringEvent.Point(side = 1)) }) else "",
        matchUuid = uuid,
    )

    private fun stato(
        match: Match = partita(),
        invio: InvioInfo? = null,
        acceso: Boolean = true,
        entrato: Boolean = true,
    ) = MatchHistoryUiState(MatchWithTeams(match, null, null, emptyList()), "", null, invio, acceso, entrato)

    private fun scheda(stato: MatchHistoryUiState): View {
        val vista = LayoutInflater.from(tema).inflate(R.layout.match_item, FrameLayout(tema), false)
        MatchHistoryAdapter.MatchViewHolder(vista, {}, {}, {}).bind(stato)
        return vista
    }

    @Test
    fun `con la funzione spenta non compare nessun comando e nessuno stato`() {
        // Anche se per assurdo la partita avesse uno stato salvato.
        val vista = scheda(stato(invio = InvioInfo(InvioState.SENT), acceso = false))

        assertEquals(View.GONE, vista.findViewById<View>(R.id.send_match_button).visibility)
        assertEquals(View.GONE, vista.findViewById<View>(R.id.send_status_textview).visibility)
        assertFalse(stato(acceso = false).canSendToPadelElite)
    }

    @Test
    fun `senza configurazione la funzione e' spenta e il comando non fa niente`() {
        val servizi = PadelEliteServices(base, PadelEliteConfig.OFF, mock(MatchRepository::class.java))

        assertFalse(servizi.isEnabled)
        assertEquals(SendOutcome.DISABLED, servizi.send(UUID_PARTITA))
        assertEquals(
            0,
            WorkManager
                .getInstance(base)
                .getWorkInfosForUniqueWork(InvioWorker.workName(UUID_PARTITA))
                .get()
                .size,
        )
    }

    @Test
    fun `una configurazione a meta' vale come spenta`() {
        assertFalse(PadelEliteConfig("https://x.supabase.co", "").isConfigured)
        assertFalse(PadelEliteConfig("", "chiave").isConfigured)
        assertFalse(PadelEliteConfig("x.supabase.co", "chiave").isConfigured)
        assertTrue(PadelEliteConfig("https://x.supabase.co/", "chiave").isConfigured)
        assertEquals("https://x.supabase.co", PadelEliteConfig("https://x.supabase.co/", "chiave").baseUrl)
    }

    @Test
    fun `il comando c'e' solo per il padel chiuso con registro e identificativo`() {
        assertTrue(stato().canSendToPadelElite)
        assertFalse(stato(partita(sport = SportRegistry.FOOTBALL)).canSendToPadelElite)
        assertFalse(stato(partita(sport = SportRegistry.TENNIS)).canSendToPadelElite)
        assertFalse(stato(partita(uuid = null)).canSendToPadelElite)
        assertFalse(stato(partita(registro = false)).canSendToPadelElite)
    }

    @Test
    fun `il comando "Invia a Padel Elite" torna solo dove l'invio e' fallito`() {
        val altri = InvioState.entries - listOf(InvioState.UNSENDABLE, InvioState.LOGIN_AGAIN)
        for (s in altri) {
            assertFalse("$s", InvioInfo(s).canSend)
        }
        for (s in listOf(InvioState.UNSENDABLE, InvioState.LOGIN_AGAIN)) {
            assertTrue("$s", InvioInfo(s).canSend)
            assertTrue("$s", stato(invio = InvioInfo(s)).canSendToPadelElite)
        }
    }

    @Test
    @Config(qualifiers = "it")
    fun `ogni stato ha una parola e un'icona sulla card`() {
        val stati =
            listOf(
                InvioInfo(InvioState.QUEUED) to "In coda",
                InvioInfo(InvioState.QUEUED, InvioReason.INBOX_FULL) to "casella del gruppo e' piena",
                InvioInfo(InvioState.SENT) to "in attesa di un admin",
                InvioInfo(InvioState.UPDATED) to "Aggiornata nella casella",
                InvioInfo(InvioState.PRESENT) to "nella casella",
                InvioInfo(InvioState.IMPORTED) to "Importata",
                InvioInfo(InvioState.DISCARDED) to "Scartata",
                InvioInfo(InvioState.LOGIN_AGAIN) to "Accedi di nuovo",
                InvioInfo(InvioState.UNSENDABLE, InvioReason.INVALID_PAYLOAD, "matchId") to "(matchId)",
                InvioInfo(InvioState.UNSENDABLE, InvioReason.TOO_LARGE) to "troppo grande",
                InvioInfo(InvioState.UNSENDABLE, InvioReason.NOT_MEMBER) to "non sei piu' nel gruppo",
            )
        for ((info, frammento) in stati) {
            val riga = scheda(stato(invio = info)).findViewById<TextView>(R.id.send_status_textview)
            assertEquals(View.VISIBLE, riga.visibility)
            assertTrue("${riga.text}", riga.text.contains(frammento))
            // Parola E icona: il colore da solo non basta mai.
            assertNotNull("$info", riga.compoundDrawablesRelative[0])
        }
    }

    @Test
    fun `l'importata e l'errore non si distinguono solo dal colore`() {
        val importata = scheda(stato(invio = InvioInfo(InvioState.IMPORTED))).findViewById<TextView>(R.id.send_status_textview)
        val errore =
            scheda(
                stato(invio = InvioInfo(InvioState.UNSENDABLE, InvioReason.TOO_LARGE)),
            ).findViewById<TextView>(R.id.send_status_textview)

        assertTrue(importata.text != errore.text)
        assertTrue(importata.compoundDrawablesRelative[0].constantState != errore.compoundDrawablesRelative[0].constantState)
    }

    @Test
    @Config(qualifiers = "it")
    fun `una voce in attesa ha "Invia di nuovo" nello stesso bottone, importata e scartata niente`() {
        for (s in listOf(InvioState.SENT, InvioState.UPDATED, InvioState.PRESENT)) {
            val vista = scheda(stato(invio = InvioInfo(s)))
            val bottone = vista.findViewById<android.widget.Button>(R.id.send_match_button)
            assertEquals("$s", View.VISIBLE, bottone.visibility)
            assertEquals("$s", "Invia di nuovo", bottone.text.toString())
            assertTrue("$s", stato(invio = InvioInfo(s)).canSendAgain)
        }
        for (s in listOf(InvioState.IMPORTED, InvioState.DISCARDED, InvioState.QUEUED)) {
            val vista = scheda(stato(invio = InvioInfo(s)))
            assertEquals("$s", View.GONE, vista.findViewById<View>(R.id.send_match_button).visibility)
            assertFalse("$s", stato(invio = InvioInfo(s)).canSendAgain)
        }
        // Dove l'invio e' fallito resta il comando di prima, con la sua parola.
        val fallita = scheda(stato(invio = InvioInfo(InvioState.UNSENDABLE, InvioReason.INVALID_PAYLOAD)))
        assertEquals("Invia a Padel Elite", fallita.findViewById<android.widget.Button>(R.id.send_match_button).text.toString())
    }

    @Test
    fun `senza accesso, o con la funzione spenta, "Invia di nuovo" non c'e'`() {
        val inAttesa = InvioInfo(InvioState.SENT)

        assertFalse(stato(invio = inAttesa, entrato = false).canSendAgain)
        assertFalse(stato(invio = inAttesa, acceso = false, entrato = true).canSendAgain)
        assertEquals(View.GONE, scheda(stato(invio = inAttesa, entrato = false)).findViewById<View>(R.id.send_match_button).visibility)
        // Una partita che non si puo' esportare (non padel, senza registro) non ha il comando.
        assertFalse(stato(partita(sport = SportRegistry.TENNIS), invio = inAttesa).canSendAgain)
        assertFalse(stato(partita(registro = false), invio = inAttesa).canSendAgain)
    }

    @Test
    fun `il comando compare sulla card di una partita inviabile`() {
        val vista = scheda(stato())

        assertEquals(View.VISIBLE, vista.findViewById<View>(R.id.send_match_button).visibility)
        assertEquals(View.GONE, vista.findViewById<View>(R.id.send_status_textview).visibility)
    }

    private fun servizi(): Pair<PadelEliteServices, PadelEliteAccount> {
        val s = ServerFinto { _, _ -> json(200, rispostaDiSessione("A1", "R1", Long.MAX_VALUE / 2)) }.also { finto = it }
        val store = SessionStore(preferenze(base, "sessione_storico"), SoftwareSecretBox())
        val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), store)
        val servizi =
            PadelEliteServices(
                base,
                s.config(),
                mock(MatchRepository::class.java),
                accountFactory = { account },
                invioStoreFactory = { InvioStore(preferenze(base, "invii_storico")) },
            )
        return servizi to account
    }

    @Test
    fun `il comando senza accesso porta all'accesso, senza gruppo al gruppo, poi mette in coda`() =
        runBlocking {
            val (servizi, account) = servizi()
            assertEquals(SendOutcome.NEED_LOGIN, servizi.send(UUID_PARTITA))

            account.signIn("a@b.it", "x")
            assertEquals(SendOutcome.NEED_GROUP, servizi.send(UUID_PARTITA))
            assertEquals(
                0,
                WorkManager
                    .getInstance(base)
                    .getWorkInfosForUniqueWork(InvioWorker.workName(UUID_PARTITA))
                    .get()
                    .size,
            )

            account.selectGroup(PadelEliteGroup("g-1", "Padel", "member"))
            assertEquals(SendOutcome.QUEUED, servizi.send(UUID_PARTITA))
            assertEquals(
                1,
                WorkManager
                    .getInstance(base)
                    .getWorkInfosForUniqueWork(InvioWorker.workName(UUID_PARTITA))
                    .get()
                    .size,
            )
            assertEquals(InvioState.QUEUED, servizi.invii.get(UUID_PARTITA)?.state)
        }
}
