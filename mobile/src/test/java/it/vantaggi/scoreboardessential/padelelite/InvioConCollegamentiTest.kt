package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.ExportResult
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchExporter
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
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R-1, i collegamenti usati nell'invio: il file che parte per un gruppo porta `padelPlayerId` dei
 * giocatori collegati IN QUEL gruppo, e solo quelli; il file da condividere non lo porta mai.
 * Database vero in memoria, `MatchRepository` e `PadelEliteServices.runner` veri, server finto.
 */
@RunWith(AndroidJUnit4::class)
class InvioConCollegamentiTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repository: MatchRepository
    private var finto: ServerFinto? = null
    private var ids = IntArray(4)

    @Before
    fun apri() {
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
                    .map {
                        db.playerDao().insert(Player(playerName = it, appearances = 0, goals = 0)).toInt()
                    }.toIntArray()
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

    /** Il corpo dell'RPC che il server finto ha ricevuto: `p_payload` dell'unica richiesta di invio. */
    private fun invia(gruppo: String): JSONObject {
        val s =
            ServerFinto { _, _ -> rispostaDiInvio() }.also {
                finto?.chiudi()
                finto = it
            }
        val store = SessionStore(preferenze(context, "sessione_collegamenti"), SoftwareSecretBox())
        store.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), store)
        val servizi =
            PadelEliteServices(
                context,
                s.config(),
                repository,
                accountFactory = { account },
                invioStoreFactory = { InvioStore(preferenze(context, "invii_collegamenti")) },
            )
        assertEquals(RunOutcome.DONE, runBlocking { servizi.runner.run(UUID_PARTITA, gruppo, 0) })
        val richiesta: RecordedRequest = s.richieste.single { it.path == "/rest/v1/rpc/submit_scoreboard_match" }
        val corpo = JSONObject(richiesta.body.readUtf8())
        assertEquals(gruppo, corpo.getString("p_group"))
        return corpo.getJSONObject("p_payload")
    }

    /** Nome del giocatore -> `padelPlayerId` nel file (assente = nessuna chiave). */
    private fun idNelFile(payload: JSONObject): Map<String, Int> {
        val giocatori = payload.getJSONArray("players")
        return (0 until giocatori.length())
            .map { giocatori.getJSONObject(it) }
            .filter { it.has("padelPlayerId") }
            .associate { it.getString("name") to it.getInt("padelPlayerId") }
    }

    private fun collega(
        gruppo: String,
        locale: Int,
        remoto: Int,
    ) = runBlocking { db.padelEliteLinkDao().link(PadelEliteLink(locale, gruppo, remoto, "n$remoto")) }

    @Test
    fun `l'invio a un gruppo porta padelPlayerId dei soli giocatori collegati in quel gruppo`() {
        collega("g-1", ids[0], 11) // Marco
        collega("g-1", ids[2], 13) // Anna
        collega("g-2", ids[0], 99) // Marco in un altro gruppo: un altro numero
        collega("g-2", ids[3], 98) // Sara solo nell'altro gruppo

        assertEquals(mapOf("Marco" to 11, "Anna" to 13), idNelFile(invia("g-1")))
    }

    @Test
    fun `lo stesso giocatore in un altro gruppo porta il numero di quel gruppo`() {
        collega("g-1", ids[0], 11)
        collega("g-2", ids[0], 99)
        collega("g-2", ids[3], 98)

        assertEquals(mapOf("Marco" to 99, "Sara" to 98), idNelFile(invia("g-2")))
    }

    @Test
    fun `un gruppo senza collegamenti manda il file di prima, senza il campo`() {
        collega("g-1", ids[0], 11)

        val payload = invia("g-3")

        assertTrue(idNelFile(payload).isEmpty())
        assertFalse(payload.toString(), payload.toString().contains("padelPlayerId"))
    }

    @Test
    fun `il file da condividere non porta mai padelPlayerId anche se i collegamenti ci sono`() =
        runBlocking {
            collega("g-1", ids[0], 11)
            val matchId = db.matchDao().getMatchByUuid(UUID_PARTITA)!!.matchId

            val file = MatchExporter.toJson((repository.buildSavedExport(matchId) as ExportResult.Ready).export)
            val aGruppo = MatchExporter.toJson((repository.buildSavedExport(matchId, "g-1") as ExportResult.Ready).export)

            assertFalse(file, file.contains("padelPlayerId"))
            assertTrue(aGruppo, aGruppo.contains("\"name\":\"Marco\",\"side\":1,\"padelPlayerId\":11"))
        }
}
