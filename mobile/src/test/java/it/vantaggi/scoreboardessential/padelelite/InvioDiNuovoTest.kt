package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.PadelEliteLink
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.repository.ColorRepository
import it.vantaggi.scoreboardessential.repository.MatchRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Invia di nuovo": una partita gia' nella casella e ancora in attesa si rimanda, e il file si
 * rifa' al momento dell'invio con i collegamenti di adesso (R-1) del gruppo di destinazione. Un
 * lavoro solo per partita, lo stato e' per partita, e senza accesso non c'e' nessun comando.
 * Database vero in memoria, `MatchRepository` e `PadelEliteServices.runner` veri, server finto.
 */
@RunWith(AndroidJUnit4::class)
class InvioDiNuovoTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repository: MatchRepository
    private var finto: ServerFinto? = null
    private var ids = IntArray(4)

    @Before
    fun apri() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = MatchRepository(db.matchDao(), context, ColorRepository(context), db.padelEliteLinkDao())
        runBlocking {
            // Marco e Luca contro Anna e Sara, ordine di servizio Marco, Anna, Luca, Sara.
            ids =
                listOf("Marco", "Luca", "Anna", "Sara")
                    .map { db.playerDao().insert(Player(playerName = it, appearances = 0, goals = 0)).toInt() }
                    .toIntArray()
            val registro = MatchLogCodec.encode(List(8) { LoggedEvent(ScoringEvent.Point(side = 1), it * 30_000L) })
            val matchId =
                db
                    .matchDao()
                    .insert(
                        Match(
                            team1Id = 1,
                            team2Id = 2,
                            team1Score = 0,
                            team2Score = 0,
                            timestamp = 1_790_193_000_000L,
                            sportId = SportRegistry.PADEL,
                            eventLog = registro,
                            serveOrder = "${ids[0]},${ids[2]},${ids[1]},${ids[3]}",
                            startedAt = 1_790_190_240_000L,
                            matchUuid = UUID_PARTITA,
                        ),
                    ).toInt()
            db.matchDao().replaceLineup(matchId, listOf(ids[0], ids[1]), listOf(ids[2], ids[3]))
        }
    }

    @After
    fun chiudi() {
        db.close()
        finto?.chiudi()
    }

    private fun collega(
        gruppo: String,
        locale: Int,
        remoto: Int,
    ) = runBlocking { db.padelEliteLinkDao().link(PadelEliteLink(locale, gruppo, remoto, "n$remoto")) }

    /** I servizi con l'accesso fatto e il gruppo `g-1` scelto, o senza accesso. */
    private fun servizi(
        accesso: Boolean = true,
        risposte: (Int) -> okhttp3.mockwebserver.MockResponse = { rispostaDiInvio() },
    ): PadelEliteServices {
        val s = ServerFinto { _, n -> risposte(n) }.also { finto = it }
        val store = SessionStore(preferenze(context, "sessione_di_nuovo"), SoftwareSecretBox())
        if (accesso) store.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), store)
        if (accesso) account.selectGroup(PadelEliteGroup("g-1", "Padel", "member"))
        return PadelEliteServices(
            context,
            s.config(),
            repository,
            accountFactory = { account },
            invioStoreFactory = { InvioStore(preferenze(context, "invii_di_nuovo")) },
        )
    }

    private fun corpiDiInvio() =
        finto!!
            .richieste
            .filter { it.path == "/rest/v1/rpc/submit_scoreboard_match" }
            .map { JSONObject(it.body.readUtf8()) }

    private fun idNelFile(payload: JSONObject): Map<String, Int> {
        val giocatori = payload.getJSONArray("players")
        return (0 until giocatori.length())
            .map { giocatori.getJSONObject(it) }
            .filter { it.has("padelPlayerId") }
            .associate { it.getString("name") to it.getInt("padelPlayerId") }
    }

    /** Il gruppo scritto nei dati del lavoro in coda (WorkInfo non espone l'input: si legge dal database di WorkManager). */
    private fun gruppoDelLavoro(uuid: String = UUID_PARTITA): String? {
        val id = lavori(uuid).single().id.toString()
        val manager = WorkManager.getInstance(context) as androidx.work.impl.WorkManagerImpl
        return manager.workDatabase
            .workSpecDao()
            .getWorkSpec(id)
            ?.input
            ?.getString(InvioWorker.KEY_GROUP)
    }

    private fun lavori(uuid: String = UUID_PARTITA) =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(InvioWorker.workName(uuid)).get()

    @Test
    fun `invia di nuovo rifa' il file con i collegamenti di adesso del gruppo di destinazione`() =
        runBlocking {
            val servizi = servizi { n -> if (n == 0) rispostaDiInvio() else rispostaDiInvio(giaInviata = true, aggiornata = true) }
            // Primo invio: nessun collegamento.
            assertEquals(RunOutcome.DONE, servizi.runner.run(UUID_PARTITA, "g-1", 0))
            assertEquals(InvioState.SENT, servizi.invii.get(UUID_PARTITA)?.state)

            // Dopo la consegna l'utente collega Marco in g-1 (e un altro numero in g-2).
            collega("g-1", ids[0], 11)
            collega("g-2", ids[0], 99)
            assertEquals(SendOutcome.QUEUED, servizi.send(UUID_PARTITA))
            assertEquals(1, lavori().count { it.state == WorkInfo.State.ENQUEUED })
            assertEquals(
                InvioInfo(InvioState.QUEUED, group = "g-1", links = "", previous = InvioState.SENT),
                servizi.invii.get(UUID_PARTITA),
            )
            // Il lavoro che parte e' quello che il worker eseguira': stesso runner, gruppo scelto adesso.
            assertEquals(RunOutcome.DONE, servizi.runner.run(UUID_PARTITA, "g-1", 0))

            val (primo, secondo) = corpiDiInvio()
            assertTrue(idNelFile(primo.getJSONObject("p_payload")).isEmpty())
            assertEquals(mapOf("Marco" to 11), idNelFile(secondo.getJSONObject("p_payload")))
            assertEquals("g-1", secondo.getString("p_group"))
            assertEquals(InvioState.UPDATED, servizi.invii.get(UUID_PARTITA)?.state)
        }

    @Test
    fun `due richieste di invia di nuovo sono un solo lavoro e il secondo non ne crea un altro`() {
        val servizi = servizi()
        servizi.invii.set(UUID_PARTITA, InvioInfo(InvioState.SENT, group = "g-1"))

        assertEquals(SendOutcome.QUEUED, servizi.send(UUID_PARTITA))
        val primo = lavori().single().id
        // Un secondo tocco su una card non ancora aggiornata: lo stato era ancora "inviata".
        servizi.invii.set(UUID_PARTITA, InvioInfo(InvioState.SENT, group = "g-1"))
        assertEquals(SendOutcome.QUEUED, servizi.send(UUID_PARTITA))

        val vivi = lavori().filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        assertEquals("un solo lavoro attivo per la partita", 1, vivi.size)
        assertEquals(primo, vivi.single().id)
    }

    @Test
    fun `invia di nuovo va al gruppo della voce anche se adesso e' scelto un altro`() {
        val servizi = servizi()
        servizi.account.selectGroup(PadelEliteGroup("g-2", "Altro", "member"))
        servizi.invii.set(UUID_PARTITA, InvioInfo(InvioState.SENT, group = "g-1", links = ""))

        assertEquals(SendOutcome.QUEUED, servizi.send(UUID_PARTITA))

        assertEquals("g-1", gruppoDelLavoro())
        // Un invio nuovo (nessuna voce nella casella) va invece al gruppo scelto.
        servizi.invii.set("altra-partita-1234", InvioInfo(InvioState.UNSENDABLE, InvioReason.NO_FILE))
        assertEquals(SendOutcome.QUEUED, servizi.send("altra-partita-1234"))
        assertEquals("g-2", gruppoDelLavoro("altra-partita-1234"))
    }

    @Test
    fun `una voce in attesa di cui non si conosce il gruppo non si rimanda`() {
        val servizi = servizi()
        servizi.invii.set(UUID_PARTITA, InvioInfo(InvioState.SENT))

        assertEquals(SendOutcome.NOT_RESENDABLE, servizi.send(UUID_PARTITA))

        assertTrue(lavori().isEmpty())
        assertEquals(InvioInfo(InvioState.SENT), servizi.invii.get(UUID_PARTITA))
    }

    @Test
    fun `il primo invio resta KEEP, il comando ripetuto non cambia il lavoro in coda`() {
        val servizi = servizi()

        assertEquals(SendOutcome.QUEUED, servizi.send(UUID_PARTITA))
        val primo = lavori().single().id
        assertEquals(SendOutcome.QUEUED, servizi.send(UUID_PARTITA))

        assertEquals(primo, lavori().single().id)
    }

    @Test
    fun `lo stato e' per partita e rimandarne una non tocca le altre`() {
        val servizi = servizi()
        servizi.invii.set(UUID_PARTITA, InvioInfo(InvioState.SENT, group = "g-1"))
        servizi.invii.set("altra-partita-1234", InvioInfo(InvioState.IMPORTED, group = "g-1"))
        servizi.invii.set("terza-partita-1234", InvioInfo(InvioState.UPDATED, group = "g-1"))

        servizi.send(UUID_PARTITA)

        assertEquals(InvioState.QUEUED, servizi.invii.get(UUID_PARTITA)?.state)
        assertEquals(InvioState.IMPORTED, servizi.invii.get("altra-partita-1234")?.state)
        assertEquals(InvioState.UPDATED, servizi.invii.get("terza-partita-1234")?.state)
        assertTrue(lavori("altra-partita-1234").isEmpty())
        assertTrue(lavori("terza-partita-1234").isEmpty())
    }

    @Test
    fun `senza accesso non c'e' nessun comando e la richiesta porta all'accesso senza mettere in coda`() {
        val servizi = servizi(accesso = false)
        servizi.invii.set(UUID_PARTITA, InvioInfo(InvioState.SENT, group = "g-1"))

        assertFalse(servizi.hasAccess())
        assertEquals(SendOutcome.NEED_LOGIN, servizi.send(UUID_PARTITA))
        assertTrue(lavori().isEmpty())
        // Lo stato non cambia: la partita resta "inviata".
        assertEquals(InvioInfo(InvioState.SENT, group = "g-1"), servizi.invii.get(UUID_PARTITA))
        assertEquals(0, finto!!.richieste.size)
    }

    @Test
    fun `senza configurazione il comando non fa niente e non c'e' accesso`() {
        val servizi = PadelEliteServices(context, PadelEliteConfig.OFF, repository)

        assertFalse(servizi.hasAccess())
        assertEquals(SendOutcome.DISABLED, servizi.send(UUID_PARTITA))
        assertTrue(lavori().isEmpty())
    }

    @Test
    fun `senza gruppo scelto un invio nuovo porta al gruppo, un rimando va comunque alla voce`() {
        val servizi = servizi()
        servizi.account.clearSelectedGroup()

        assertTrue(servizi.hasAccess())
        assertEquals(SendOutcome.NEED_GROUP, servizi.send(UUID_PARTITA))
        assertTrue(lavori().isEmpty())

        servizi.invii.set(UUID_PARTITA, InvioInfo(InvioState.UPDATED, group = "g-1"))
        assertEquals(SendOutcome.QUEUED, servizi.send(UUID_PARTITA))
        assertEquals("g-1", gruppoDelLavoro())
    }
}
