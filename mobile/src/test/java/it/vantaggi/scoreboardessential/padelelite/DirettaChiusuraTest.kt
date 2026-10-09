package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
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
import okhttp3.mockwebserver.MockResponse
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La chiusura della diretta (`end_live_from_app`) e la visibilita' del comando. Database vero in memoria, servizi veri,
 * server finto. A partita finita si chiude COL FILE FINALE (vincitore), da scartata o interrotta senza.
 */
@RunWith(AndroidJUnit4::class)
class DirettaChiusuraTest {
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
            ids =
                listOf("Marco", "Luca", "Anna", "Sara")
                    .map { db.playerDao().insert(Player(playerName = it, appearances = 0, goals = 0)).toInt() }
                    .toIntArray()
            db.padelEliteLinkDao().link(PadelEliteLink(ids[0], "g-1", 101, "Marco D."))
        }
    }

    @After
    fun chiudi() {
        db.close()
        finto?.chiudi()
    }

    /** Salva la partita nello storico con [punti] punti al lato 1: 24 la chiudono (6-0, vincitore il lato 1). */
    private fun salvaPartita(punti: Int) =
        runBlocking {
            val registro = MatchLogCodec.encode(List(punti) { LoggedEvent(ScoringEvent.Point(side = 1), it * 30_000L) })
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

    private fun servizi(
        ruolo: String? = "admin",
        gruppo: Boolean = true,
        accesso: Boolean = true,
        config: PadelEliteConfig? = null,
        risposte: (Int) -> MockResponse = { rispostaDiChiusura() },
    ): PadelEliteServices {
        val s = ServerFinto { _, n -> risposte(n) }.also { finto = it }
        val store = SessionStore(preferenze(context, "sessione_chiusura_diretta"), SoftwareSecretBox())
        if (accesso) store.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), store)
        if (gruppo && ruolo != null) account.selectGroup(PadelEliteGroup("g-1", "Padel", ruolo))
        return PadelEliteServices(
            context,
            config ?: s.config(),
            repository,
            accountFactory = { account },
            invioStoreFactory = { InvioStore(preferenze(context, "invii_chiusura_diretta")) },
            direttaStoreFactory = { DirettaStore(preferenze(context, "diretta_chiusura")) },
        )
    }

    private fun chiusureAlServer() =
        finto!!
            .richieste
            .filter { it.path == "/rest/v1/rpc/end_live_from_app" }
            .map { JSONObject(it.body.clone().readUtf8()) }

    @Test
    fun `a partita finita si chiude col file finale e il vincitore`() =
        runBlocking {
            salvaPartita(24)
            val s = servizi()

            assertEquals(RunOutcome.DONE, s.chiusuraDiretta.run(UUID_PARTITA, "g-1", conRisultato = true, attempt = 0))

            val corpo = chiusureAlServer().single()
            assertEquals("g-1", corpo.getString("p_group"))
            assertEquals(UUID_PARTITA, corpo.getString("p_match_id"))
            val file = corpo.getJSONObject("p_payload")
            assertEquals(1, file.getInt("winnerTeam"))
            assertEquals(24, file.getJSONArray("timeline").length())
            assertEquals(1, file.getJSONArray("setScores").length())
            // Il file finale e' quello dell'invio: stessi collegamenti del gruppo.
            assertEquals(101, file.getJSONArray("players").getJSONObject(0).getInt("padelPlayerId"))
        }

    @Test
    fun `una partita scartata si chiude senza file`() =
        runBlocking {
            salvaPartita(24)
            val s = servizi()

            assertEquals(RunOutcome.DONE, s.chiusuraDiretta.run(UUID_PARTITA, "g-1", conRisultato = false, attempt = 0))

            val corpo = chiusureAlServer().single()
            assertTrue(corpo.isNull("p_payload"))
            assertEquals(UUID_PARTITA, corpo.getString("p_match_id"))
        }

    @Test
    fun `una partita interrotta senza vincitore si chiude senza file`() =
        runBlocking {
            salvaPartita(8)
            val s = servizi()

            assertEquals(RunOutcome.DONE, s.chiusuraDiretta.run(UUID_PARTITA, "g-1", conRisultato = true, attempt = 0))

            assertTrue(chiusureAlServer().single().isNull("p_payload"))
        }

    @Test
    fun `se la rete manca la chiusura si ritenta con lo stesso file finale`() =
        runBlocking {
            salvaPartita(24)
            val s = servizi { n -> if (n == 0) json(503, "") else rispostaDiChiusura() }

            assertEquals(RunOutcome.RETRY, s.chiusuraDiretta.run(UUID_PARTITA, "g-1", true, 0))
            assertEquals(RunOutcome.DONE, s.chiusuraDiretta.run(UUID_PARTITA, "g-1", true, 1))

            val corpi = chiusureAlServer()
            assertEquals(2, corpi.size)
            assertEquals(1, corpi.last().getJSONObject("p_payload").getInt("winnerTeam"))
        }

    @Test
    fun `un file finale rifiutato non blocca la chiusura, che riprova senza file`() =
        runBlocking {
            salvaPartita(24)
            val s = servizi { n -> if (n == 0) erroreRpc(500, "22023", "invalid_payload", "players") else rispostaDiChiusura() }

            assertEquals(RunOutcome.DONE, s.chiusuraDiretta.run(UUID_PARTITA, "g-1", true, 0))

            val corpi = chiusureAlServer()
            assertEquals(2, corpi.size)
            assertFalse(corpi[0].isNull("p_payload"))
            assertTrue(corpi[1].isNull("p_payload"))
        }

    @Test
    fun `se il file finale lo puo' scrivere solo chi ha aperto la diretta, un altro admin chiude senza file`() =
        runBlocking {
            salvaPartita(24)
            val s = servizi { n -> if (n == 0) erroreRpc(500, "42501", "not_authorized") else rispostaDiChiusura(scritto = false) }

            assertEquals(RunOutcome.DONE, s.chiusuraDiretta.run(UUID_PARTITA, "g-1", true, 0))

            val corpi = chiusureAlServer()
            assertEquals(2, corpi.size)
            assertFalse(corpi[0].isNull("p_payload"))
            // Il matchId del file e' quello della chiusura (il server li vuole uguali).
            assertEquals(UUID_PARTITA, corpi[0].getJSONObject("p_payload").getString("matchId"))
            assertTrue(corpi[1].isNull("p_payload"))
        }

    @Test
    fun `la risposta della chiusura dice se il file finale e' stato scritto`() =
        runBlocking {
            val s = servizi { rispostaDiChiusura() }
            val api = PadelEliteApi(s.config)

            val scritto = api.endLive("A1", "g-1", UUID_PARTITA, FILE_V2)
            finto!!.chiudi()
            finto = ServerFinto { _, _ -> rispostaDiChiusura(giaChiusa = true) }
            val gia = PadelEliteApi(finto!!.config()).endLive("A1", "g-1", UUID_PARTITA, FILE_V2)

            assertEquals(true, (scritto as LiveResult.Ended).written)
            assertEquals(false, scritto.alreadyEnded)
            assertEquals(true, (gia as LiveResult.Ended).alreadyEnded)
            assertEquals(false, gia.written)
        }

    @Test
    fun `la riga non ancora scritta si aspetta, poi si chiude senza risultato`() =
        runBlocking {
            val s = servizi()

            assertEquals(RunOutcome.RETRY, s.chiusuraDiretta.run(UUID_PARTITA, "g-1", true, 0))
            assertEquals(0, chiusureAlServer().size)
            assertEquals(RunOutcome.DONE, s.chiusuraDiretta.run(UUID_PARTITA, "g-1", true, DirettaChiusuraRunner.MAX_MISSING_ATTEMPTS))
            assertTrue(chiusureAlServer().single().isNull("p_payload"))
        }

    @Test
    fun `una diretta gia' chiusa o sparita non e' un errore`() =
        runBlocking {
            salvaPartita(24)
            val chiusa = servizi { rispostaDiChiusura(giaChiusa = true) }
            assertEquals(RunOutcome.DONE, chiusa.chiusuraDiretta.run(UUID_PARTITA, "g-1", true, 0))
            finto!!.chiudi()

            val sparita = servizi { erroreRpc(500, "P0002", "not_found") }
            assertEquals(RunOutcome.DONE, sparita.chiusuraDiretta.run(UUID_PARTITA, "g-1", false, 0))
        }

    @Test
    fun `la chiusura e' un solo lavoro per partita`() {
        val richiesta = DirettaChiusuraRichiesta(UUID_PARTITA, "g-1", conRisultato = true)

        DirettaChiusuraWorker.enqueue(context, richiesta)
        DirettaChiusuraWorker.enqueue(context, richiesta)
        DirettaChiusuraWorker.enqueue(context, richiesta.copy(conRisultato = false))

        val lavori = WorkManager.getInstance(context).getWorkInfosForUniqueWork(DirettaChiusuraWorker.workName(UUID_PARTITA)).get()
        assertEquals(1, lavori.size)
        val spec = DirettaChiusuraWorker.request(richiesta).workSpec
        assertEquals(androidx.work.NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertTrue(spec.input.getBoolean(DirettaChiusuraWorker.KEY_RESULT, false))
        assertEquals("g-1", spec.input.getString(DirettaChiusuraWorker.KEY_GROUP))
    }

    @Test
    fun `il comando c'e' per owner e admin del gruppo, nel padel`() {
        assertTrue(servizi(ruolo = "owner").puoTrasmettere(SportRegistry.PADEL))
        finto!!.chiudi()
        assertTrue(servizi(ruolo = "admin").puoTrasmettere(SportRegistry.PADEL))
    }

    @Test
    fun `il comando non c'e' per un member`() {
        assertFalse(servizi(ruolo = "member").puoTrasmettere(SportRegistry.PADEL))
    }

    @Test
    fun `il comando non c'e' senza un gruppo scelto ne' con un ruolo mai salvato`() {
        assertFalse(servizi(gruppo = false).puoTrasmettere(SportRegistry.PADEL))
        finto!!.chiudi()
        assertFalse(servizi(ruolo = null).puoTrasmettere(SportRegistry.PADEL))
    }

    @Test
    fun `il comando non c'e' in un altro sport`() {
        val s = servizi(ruolo = "owner")
        assertFalse(s.puoTrasmettere(SportRegistry.TENNIS))
        assertFalse(s.puoTrasmettere(SportRegistry.FOOTBALL))
    }

    @Test
    fun `il comando non c'e' senza accesso ne' con la funzione spenta`() {
        assertFalse(servizi(accesso = false).puoTrasmettere(SportRegistry.PADEL))
        finto!!.chiudi()
        assertFalse(servizi(config = PadelEliteConfig.OFF).puoTrasmettere(SportRegistry.PADEL))
    }

    @Test
    fun `senza permesso nessuna richiesta parte da solo`() {
        val s = servizi(ruolo = "member")

        assertFalse(s.puoTrasmettere(SportRegistry.PADEL))
        assertEquals(0, finto!!.richieste.size)
    }
}
