package it.vantaggi.scoreboardessential

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.MatchPlayer
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.padelelite.DirettaChiusuraWorker
import it.vantaggi.scoreboardessential.padelelite.DirettaFase
import it.vantaggi.scoreboardessential.padelelite.DirettaSalvata
import it.vantaggi.scoreboardessential.padelelite.DirettaStore
import it.vantaggi.scoreboardessential.padelelite.InvioStore
import it.vantaggi.scoreboardessential.padelelite.PadelEliteAccount
import it.vantaggi.scoreboardessential.padelelite.PadelEliteApi
import it.vantaggi.scoreboardessential.padelelite.PadelEliteConfig
import it.vantaggi.scoreboardessential.padelelite.PadelEliteGroup
import it.vantaggi.scoreboardessential.padelelite.PadelEliteServices
import it.vantaggi.scoreboardessential.padelelite.PadelEliteSession
import it.vantaggi.scoreboardessential.padelelite.ServerFinto
import it.vantaggi.scoreboardessential.padelelite.SessionStore
import it.vantaggi.scoreboardessential.padelelite.SoftwareSecretBox
import it.vantaggi.scoreboardessential.padelelite.UUID_PARTITA
import it.vantaggi.scoreboardessential.padelelite.json
import it.vantaggi.scoreboardessential.padelelite.preferenze
import it.vantaggi.scoreboardessential.padelelite.rispostaDiPubblicazione
import it.vantaggi.scoreboardessential.repository.ColorRepository
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import it.vantaggi.scoreboardessential.repository.UserPreferencesRepository
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * La diretta dentro `MainViewModel` (D-2): un punto la pubblica, anche arrivato dall'orologio (stesso imbuto),
 * la fine partita la chiude con lo stesso identificativo, lo scarto la chiude senza risultato, e dopo la chiusura
 * non parte piu' niente. Il ripristino la riprende. Server finto, DAO finti.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class DirettaNelViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var viewModel: MainViewModel
    private var finto: ServerFinto? = null
    private lateinit var diretta: DirettaStore
    private val matchDao = mock(MatchDao::class.java)

    @Before
    fun prepara() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().build())
    }

    @After
    fun chiudi() {
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
        finto?.chiudi()
    }

    private fun servizi(
        ruolo: String = "admin",
        risposta: (RecordedRequest, Int) -> MockResponse = { _, _ -> rispostaDiPubblicazione() },
    ): PadelEliteServices {
        val s = ServerFinto(risposta).also { finto = it }
        val sessione = SessionStore(preferenze(application, "sessione_vm_diretta"), SoftwareSecretBox())
        sessione.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), sessione)
        account.selectGroup(PadelEliteGroup("g-1", "Padel", ruolo))
        diretta = DirettaStore(preferenze(application, "diretta_vm"))
        val repository = MatchRepository(mock(MatchDao::class.java), application, ColorRepository(application))
        return PadelEliteServices(
            application,
            s.config(),
            repository,
            accountFactory = { account },
            invioStoreFactory = { InvioStore(preferenze(application, "invii_vm_diretta")) },
            direttaStoreFactory = { diretta },
            direttaMinIntervalMs = 50,
        )
    }

    private fun costruisci(padelElite: PadelEliteServices?) {
        val connessione = mock(OptimizedWearDataSync::class.java)
        whenever(connessione.connectionState).thenReturn(MutableStateFlow(ConnectionState.Disconnected))
        val repository = mock(MatchRepository::class.java).apply { whenever(allMatches).thenReturn(emptyFlow()) }
        val preferenze = mock(UserPreferencesRepository::class.java).apply { whenever(hasSeenTutorial).thenReturn(emptyFlow()) }
        val impostazioni = mock(MatchSettingsRepository::class.java).apply { whenever(getSettingsFlow()).thenReturn(emptyFlow()) }
        val giocatori =
            mock(PlayerDao::class.java).apply {
                whenever(getAllPlayers()).thenReturn(emptyFlow())
            }
        viewModel =
            if (padelElite != null) {
                MainViewModel(
                    repository,
                    preferenze,
                    impostazioni,
                    application,
                    playerDao = giocatori,
                    matchDao = matchDao,
                    connectionManager = connessione,
                    padelElite = padelElite,
                )
            } else {
                MainViewModel(
                    repository,
                    preferenze,
                    impostazioni,
                    application,
                    playerDao = giocatori,
                    matchDao = matchDao,
                    connectionManager = connessione,
                )
            }
    }

    private fun giocatore(
        id: Int,
        nome: String,
    ) = PlayerWithRoles(Player(playerId = id, playerName = nome, appearances = 0, goals = 0), emptyList())

    /** Padel con quattro giocatori in rosa: Marco e Luca contro Anna e Sara. */
    private fun partitaDiPadel() {
        viewModel.selectSport(SportRegistry.PADEL)
        viewModel.addPlayerToTeam(giocatore(1, "Marco"), 1)
        viewModel.addPlayerToTeam(giocatore(3, "Luca"), 1)
        viewModel.addPlayerToTeam(giocatore(2, "Anna"), 2)
        viewModel.addPlayerToTeam(giocatore(4, "Sara"), 2)
    }

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

    private fun pubblicazioni() =
        finto!!
            .richieste
            .filter { it.path == "/rest/v1/rpc/publish_live_from_app" }
            .map { JSONObject(it.body.clone().readUtf8()).getJSONObject("p_payload") }

    private fun lavoriDiChiusura(matchId: String) =
        WorkManager.getInstance(application).getWorkInfosForUniqueWork(DirettaChiusuraWorker.workName(matchId)).get()

    @Test
    fun `all'avvio il ruolo si rilegge una volta, l'interruttore compare e il foglio e' avvertito`() {
        val gruppi = """[{"group_id":"g-1","role":"admin","groups":{"name":"Padel"}}]"""
        costruisci(servizi(ruolo = "") { _, _ -> json(200, gruppi) })
        val avvisi =
            java.util.concurrent.atomic
                .AtomicInteger()
        val ascolto =
            kotlinx.coroutines
                .CoroutineScope(Dispatchers.Unconfined)
                .launch { viewModel.ruoloDirettaAggiornato.collect { avvisi.incrementAndGet() } }
        try {
            viewModel.selectSport(SportRegistry.PADEL)
            assertFalse("prima della rilettura: nessun ruolo, nessun comando", viewModel.direttaDisponibile())

            attendi("il ruolo riletto") {
                testDispatcher.scheduler.advanceUntilIdle()
                viewModel.direttaDisponibile()
            }

            assertEquals(1, avvisi.get())
            assertEquals(1, finto!!.quante("/rest/v1/group_members"))
        } finally {
            ascolto.cancel()
        }
    }

    @Test
    fun `senza configurazione non c'e' il comando e non parte niente`() {
        costruisci(null)
        viewModel.selectSport(SportRegistry.PADEL)

        assertFalse(viewModel.direttaDisponibile())
        assertFalse(viewModel.accendiLaDiretta())
        viewModel.addScore(1)
        assertEquals(DirettaFase.SPENTA, viewModel.direttaStato.value.fase)
    }

    @Test
    fun `un member non vede il comando`() {
        costruisci(servizi(ruolo = "member"))
        partitaDiPadel()

        assertFalse(viewModel.direttaDisponibile())
        assertFalse(viewModel.accendiLaDiretta())
        assertEquals(0, finto!!.richieste.size)
    }

    @Test
    fun `accesa prima della partita pubblica la timeline vuota, poi un punto la aggiorna`() {
        costruisci(servizi())
        partitaDiPadel()

        assertTrue(viewModel.direttaDisponibile())
        assertTrue(viewModel.accendiLaDiretta())
        val id = viewModel.currentMatchUuid
        assertNotNull(id)
        attendi("la prima pubblicazione") { pubblicazioni().isNotEmpty() }
        assertEquals(0, pubblicazioni().first().getJSONArray("timeline").length())
        assertEquals(id, pubblicazioni().first().getString("matchId"))

        viewModel.addScore(1)
        viewModel.addScore(2)
        attendi("i due punti") { pubblicazioni().last().getJSONArray("timeline").length() == 2 }

        // Lo stesso identificativo di prima: nasce con l'accensione e lo tiene la riga viva.
        assertEquals(id, viewModel.currentMatchUuid)
        assertEquals(id, pubblicazioni().last().getString("matchId"))
        assertEquals(DirettaSalvata(id!!, "g-1", "Padel"), diretta.load())
    }

    @Test
    fun `fine partita chiude la diretta col file finale e dopo non parte piu' niente`() {
        costruisci(servizi())
        partitaDiPadel()
        viewModel.accendiLaDiretta()
        viewModel.addScore(1)
        val id = viewModel.currentMatchUuid!!
        attendi("il punto") { pubblicazioni().isNotEmpty() }

        assertTrue(viewModel.endMatch())
        val prima = pubblicazioni().size
        Thread.sleep(300)

        assertEquals(DirettaFase.CHIUSA, viewModel.direttaStato.value.fase)
        attendi("il lavoro di chiusura") { lavoriDiChiusura(id).size == 1 }
        assertNull(diretta.load())
        assertEquals(prima, pubblicazioni().size)
    }

    @Test
    fun `la fine partita chiude la riga con lo stesso identificativo della diretta`() {
        costruisci(servizi())
        partitaDiPadel()
        viewModel.accendiLaDiretta()
        viewModel.addScore(1)
        val id = viewModel.currentMatchUuid!!

        assertTrue(viewModel.endMatch())
        testDispatcher.scheduler.advanceUntilIdle()

        val chiusa = org.mockito.kotlin.argumentCaptor<Match>()
        kotlinx.coroutines.runBlocking {
            org.mockito.kotlin
                .verify(
                    matchDao,
                ).closeMatch(chiusa.capture(), any<List<Int>>(), any<List<Int>>())
        }
        assertEquals(id, chiusa.firstValue.matchUuid)
    }

    @Test
    fun `accesa e spenta prima del primo punto la partita si scorda l'id, la prossima accensione ne ha uno nuovo`() {
        costruisci(servizi())
        partitaDiPadel()
        assertTrue(viewModel.accendiLaDiretta())
        val primo = viewModel.currentMatchUuid
        assertNotNull(primo)

        viewModel.spegniLaDiretta()

        assertNull(viewModel.currentMatchUuid)
        assertTrue(viewModel.accendiLaDiretta())
        val secondo = viewModel.currentMatchUuid
        assertNotNull(secondo)
        assertFalse(primo == secondo)
    }

    @Test
    fun `accesa e scartata prima del primo punto la partita si scorda l'id`() {
        costruisci(servizi())
        partitaDiPadel()
        viewModel.accendiLaDiretta()

        assertFalse(viewModel.discardMatch())

        assertNull(viewModel.currentMatchUuid)
        assertEquals(DirettaFase.CHIUSA, viewModel.direttaStato.value.fase)
        assertTrue(viewModel.accendiLaDiretta())
    }

    @Test
    fun `scartare la partita chiude la diretta senza risultato`() {
        costruisci(servizi())
        partitaDiPadel()
        viewModel.accendiLaDiretta()
        viewModel.addScore(1)
        val id = viewModel.currentMatchUuid!!

        assertTrue(viewModel.discardMatch())

        assertEquals(DirettaFase.CHIUSA, viewModel.direttaStato.value.fase)
        attendi("il lavoro di chiusura") { lavoriDiChiusura(id).size == 1 }
        assertNull(diretta.load())
    }

    @Test
    fun `spegnerla a mano la chiude e per la stessa partita non si riaccende`() {
        costruisci(servizi())
        partitaDiPadel()
        viewModel.accendiLaDiretta()
        viewModel.addScore(1)

        viewModel.spegniLaDiretta()

        assertEquals(DirettaFase.CHIUSA, viewModel.direttaStato.value.fase)
        assertFalse(viewModel.accendiLaDiretta())
    }

    @Test
    fun `al ripristino la diretta salvata per la partita ripresa riprende e pubblica i punti`() {
        val servizi = servizi()
        val registro = MatchLogCodec.encode(List(3) { LoggedEvent(ScoringEvent.Point(side = 1), it * 30_000L) })
        runBlockingTest {
            whenever(matchDao.getActiveMatchOnce()).thenReturn(
                Match(
                    matchId = 5,
                    team1Id = 1,
                    team2Id = 2,
                    team1Score = 0,
                    team2Score = 0,
                    timestamp = 1_790_193_000_000L,
                    isActive = true,
                    sportId = SportRegistry.PADEL,
                    eventLog = registro,
                    serveOrder = "1,2,3,4",
                    startedAt = 1_790_190_240_000L,
                    matchUuid = UUID_PARTITA,
                ),
            )
            whenever(matchDao.getMatchLineup(5)).thenReturn(
                listOf(MatchPlayer(1, "Marco", 1), MatchPlayer(3, "Luca", 1), MatchPlayer(2, "Anna", 2), MatchPlayer(4, "Sara", 2)),
            )
        }
        diretta.save(DirettaSalvata(UUID_PARTITA, "g-1", "Padel"))
        val giocatori =
            mock(PlayerDao::class.java).apply {
                whenever(getAllPlayers()).thenReturn(emptyFlow())
            }
        runBlockingTest {
            whenever(giocatori.getPlayersWithRoles(listOf(1, 3, 2, 4))).thenReturn(
                listOf(giocatore(1, "Marco"), giocatore(3, "Luca"), giocatore(2, "Anna"), giocatore(4, "Sara")),
            )
        }

        val connessione = mock(OptimizedWearDataSync::class.java)
        whenever(connessione.connectionState).thenReturn(MutableStateFlow(ConnectionState.Disconnected))
        viewModel =
            MainViewModel(
                mock(MatchRepository::class.java).apply { whenever(allMatches).thenReturn(emptyFlow()) },
                mock(UserPreferencesRepository::class.java).apply { whenever(hasSeenTutorial).thenReturn(emptyFlow()) },
                mock(MatchSettingsRepository::class.java).apply { whenever(getSettingsFlow()).thenReturn(emptyFlow()) },
                application,
                playerDao = giocatori,
                matchDao = matchDao,
                connectionManager = connessione,
                padelElite = servizi,
            )
        testDispatcher.scheduler.advanceUntilIdle()

        attendi("la ripresa") { pubblicazioni().isNotEmpty() }
        assertEquals(3, pubblicazioni().last().getJSONArray("timeline").length())
        assertEquals(UUID_PARTITA, pubblicazioni().last().getString("matchId"))
        assertEquals(DirettaFase.IN_CORSO, viewModel.direttaStato.value.fase)
    }

    private fun runBlockingTest(blocco: suspend () -> Unit) = kotlinx.coroutines.runBlocking { blocco() }
}
