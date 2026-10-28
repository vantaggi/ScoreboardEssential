package it.vantaggi.scoreboardessential.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.core.SportRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La classifica delle partite vinte di uno sport (passo 6), letta dalle formazioni salvate. Rosso se
 * conta le partite di un altro sport o quella ancora in corso, o se sbaglia chi ha vinto.
 */
@RunWith(RobolectricTestRunner::class)
class PartiteVinteDelloSportTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            db =
                Room
                    .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            db.teamDao().insertWithId(Team(id = 1, name = "Rossi", color = 0, logoUri = null))
            db.teamDao().insertWithId(Team(id = 2, name = "Blu", color = 0, logoUri = null))
            listOf("Anna", "Bruno", "Carla", "Dario").forEachIndexed { i, nome ->
                db.playerDao().insert(Player(playerId = i + 1, playerName = nome, appearances = 0, goals = 0))
            }
        }

    @After
    fun tearDown() {
        db.close()
    }

    /** Anna e Bruno col lato 1, Carla e Dario col lato 2. */
    private fun partita(
        sport: String,
        punteggio: Pair<Int, Int>,
        inCorso: Boolean = false,
    ) = runBlocking {
        val id =
            db
                .matchDao()
                .insert(
                    Match(
                        team1Id = 1,
                        team2Id = 2,
                        team1Score = punteggio.first,
                        team2Score = punteggio.second,
                        timestamp = 1_790_193_000_000L,
                        sportId = sport,
                        isActive = inCorso,
                    ),
                ).toInt()
        db.matchDao().insertMatchPlayerCrossRefs(
            listOf(1 to 1, 2 to 1, 3 to 2, 4 to 2).map { (giocatore, lato) -> MatchPlayerCrossRef(id, giocatore, lato) },
        )
    }

    @Test
    fun `per ogni giocatore le partite chiuse dello sport, giocate e vinte`() =
        runBlocking {
            partita(SportRegistry.PADEL, 1 to 0)
            partita(SportRegistry.PADEL, 1 to 0)
            partita(SportRegistry.PADEL, 0 to 1)
            // Non contano: un altro sport e la partita in corso.
            partita(SportRegistry.FOOTBALL, 0 to 5)
            partita(SportRegistry.PADEL, 0 to 1, inCorso = true)

            val righe = db.matchDao().getPlayerRecords(SportRegistry.PADEL, 10).first()

            assertEquals(
                listOf(
                    PlayerRecord(1, "Anna", played = 3, won = 2),
                    PlayerRecord(2, "Bruno", played = 3, won = 2),
                    PlayerRecord(3, "Carla", played = 3, won = 1),
                    PlayerRecord(4, "Dario", played = 3, won = 1),
                ),
                righe,
            )
            assertEquals(emptyList<PlayerRecord>(), db.matchDao().getPlayerRecords(SportRegistry.TENNIS, 10).first())
        }

    @Test
    fun `le partite di ogni giocatore restano separate per sport`() =
        runBlocking {
            partita(SportRegistry.PADEL, 1 to 0)
            partita(SportRegistry.PADEL, 0 to 1)
            partita(SportRegistry.FOOTBALL, 3 to 1)
            // Non conta: la partita in corso.
            partita(SportRegistry.FOOTBALL, 0 to 5, inCorso = true)

            val conteggi = db.matchDao().getPlayerSportCounts().first()

            assertEquals(
                setOf(
                    PlayerSportCount(1, SportRegistry.PADEL, played = 2, won = 1),
                    PlayerSportCount(1, SportRegistry.FOOTBALL, played = 1, won = 1),
                    PlayerSportCount(3, SportRegistry.PADEL, played = 2, won = 1),
                    PlayerSportCount(3, SportRegistry.FOOTBALL, played = 1, won = 0),
                ),
                conteggi.filter { it.playerId == 1 || it.playerId == 3 }.toSet(),
            )
            assertEquals(mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 1), conteggi.playedIn(SportRegistry.FOOTBALL))
            assertEquals(mapOf(1 to 2, 2 to 2, 3 to 2, 4 to 2), conteggi.playedIn(SportRegistry.PADEL))
            assertEquals(mapOf(1 to 1, 2 to 1, 3 to 0, 4 to 0), conteggi.wonIn(SportRegistry.FOOTBALL))
            assertEquals(emptyMap<Int, Int>(), conteggi.playedIn(SportRegistry.TENNIS))
        }
}
