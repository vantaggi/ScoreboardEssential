package it.vantaggi.scoreboardessential.ui.chronicle

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.room.Room
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchPlayerCrossRef
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.Team
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * La Cronaca aperta su partite vere, lette dal database: ogni sezione dice cio' che la partita
 * sa, e quando non lo sa lo dice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it")
class ChronicleActivityTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        // Come in AddEditPlayerActivityTest: un database in memoria al posto di quello vero.
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        setDatabaseInstance(database)
        runBlocking {
            database.teamDao().insertWithId(Team(id = 1, name = "Rossi", color = 0xFFFFD600.toInt(), logoUri = null))
            // Blu notte: sulla card sparirebbe, la Cronaca lo schiarisce come grafica.
            database.teamDao().insertWithId(Team(id = 2, name = "Blu", color = 0xFF1A237E.toInt(), logoUri = null))
            listOf("Anna", "Bruno", "Carla", "Dario").forEachIndexed { i, nome ->
                database.playerDao().insert(Player(playerId = i + 1, playerName = nome, appearances = 0, goals = 0))
            }
        }
    }

    @After
    fun tearDown() {
        database.close()
        setDatabaseInstance(null)
    }

    private fun setDatabaseInstance(instance: AppDatabase?) {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, instance)
    }

    /** Un game di padel col punto secco: quattro punti di fila. */
    private fun game(side: Int) = List(4) { side }

    /**
     * Una partita chiusa: le squadre 1 e 2, Anna e Bruno col lato 1, Carla e Dario col lato 2.
     * [sides] e' chi ha vinto ogni punto; con [stepMs] ogni punto ha il suo tempo, senza no.
     */
    private fun salva(
        sides: List<Int>,
        serveOrder: String,
        stepMs: Long?,
    ): Int =
        runBlocking {
            val registro = sides.mapIndexed { i, side -> LoggedEvent(ScoringEvent.Point(side), stepMs?.let { i * it }) }
            val partita =
                Match(
                    team1Id = 1,
                    team2Id = 2,
                    team1Score = 1,
                    team2Score = 0,
                    timestamp = 1_790_193_000_000L,
                    sportId = SportRegistry.PADEL,
                    eventLog = MatchLogCodec.encode(registro),
                    serveOrder = serveOrder,
                    startedAt = if (stepMs == null) null else 1_790_190_240_000L,
                )
            val id = database.matchDao().insert(partita).toInt()
            database.matchDao().insertMatchPlayerCrossRefs(
                listOf(1 to 1, 2 to 1, 3 to 2, 4 to 2).map { (player, side) -> MatchPlayerCrossRef(id, player, side) },
            )
            id
        }

    /**
     * Apre la Cronaca e aspetta che abbia caricato. Le query sospese di Room girano su un
     * esecutore suo, non sul Main finto: si fa girare il looper finche' le sezioni compaiono.
     */
    private fun apri(matchId: Int): ChronicleActivity {
        val activity =
            Robolectric
                .buildActivity(ChronicleActivity::class.java, ChronicleActivity.intent(RuntimeEnvironment.getApplication(), matchId))
                .setup()
                .get()
        val limite = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < limite) {
            shadowOf(Looper.getMainLooper()).idle()
            val caricata =
                activity.findViewById<View>(R.id.chronicle_sections).visibility == View.VISIBLE ||
                    activity.findViewById<View>(R.id.chronicle_unavailable).visibility == View.VISIBLE
            if (caricata) return activity
            Thread.sleep(10)
        }
        throw AssertionError("la Cronaca non ha caricato la partita $matchId")
    }

    private fun testi(view: View): List<String> =
        when (view) {
            is TextView -> listOf(view.text.toString())
            is ViewGroup -> (0 until view.childCount).flatMap { testi(view.getChildAt(it)) }
            else -> emptyList()
        }

    private fun sezione(
        activity: ChronicleActivity,
        id: Int,
    ): List<String> = testi(activity.findViewById<View>(id).findViewById(R.id.section_body))

    /**
     * La richiesta del brief: senza ordine di servizio e senza tempi la Cronaca lo dice, invece
     * di mostrare una tabella vuota o una durata di zero minuti.
     */
    @Test
    fun `senza ordine di servizio e senza tempi le due sezioni lo dicono`() {
        // 6-0: ventiquattro punti di fila per Rossi.
        val id = salva((1..6).flatMap { game(1) }, serveOrder = "", stepMs = null)

        val cronaca = apri(id)

        // La prima frase e' quella della dashboard; il consiglio dopo i due punti e' dell'app,
        // dove l'ordine di servizio nasce dalle rose e non si imposta a mano.
        assertEquals(
            listOf(
                "Il tabellone non sapeva chi serviva: con due giocatori per squadra nelle rose prima del primo " +
                    "punto, qui vedrai punti e game vinti al servizio da ciascuno.",
            ),
            sezione(cronaca, R.id.section_serve),
        )
        assertEquals(listOf("Il tabellone non ha registrato i tempi di questa partita."), sezione(cronaca, R.id.section_times))
        // Il resto si calcola lo stesso: il tabellone e la striscia non dipendono da chi serviva.
        assertTrue(sezione(cronaca, R.id.section_scoreboard).contains("Punti vinti 24 – 0 · Game 6 – 0"))
        assertEquals(listOf("24 punti di fila per Rossi nel 1° set"), sezione(cronaca, R.id.section_moments))
        // Senza servitore non si sa cos'e' un break: la legenda non ne parla.
        assertTrue(
            sezione(cronaca, R.id.section_games)
                .contains("Il colore dice chi ha vinto il game, il numero è il punteggio dopo il game."),
        )
    }

    /**
     * Con ordine e tempi le stesse sezioni si riempiono: il controllo che le frasi di prima
     * dipendano dal dato che manca e non siano scritte sempre.
     */
    @Test
    fun `con ordine di servizio e tempi servizio, tempi e tie-break ci sono`() {
        // Game alterni fino al 6-6, poi tie-break 7-5 per Rossi.
        val games = (1..6).flatMap { game(1) + game(2) }
        val tieBreak = List(5) { listOf(1, 2) }.flatten() + listOf(1, 1)
        // A1, B1, A2, B2: Anna, Carla, Bruno, Dario.
        val id = salva(games + tieBreak, serveOrder = "1,3,2,4", stepMs = 20_000L)

        val cronaca = apri(id)

        val servizio = sezione(cronaca, R.id.section_serve)
        assertFalse(servizio.any { it.startsWith("Il tabellone non sapeva") })
        assertTrue(servizio.containsAll(listOf("Al servizio", "Anna", "Bruno", "Carla", "Dario")))
        // Anna e Bruno prima: la tabella e' per coppia, poi nell'ordine di servizio.
        assertEquals(listOf("Anna", "Bruno", "Carla", "Dario"), servizio.filter { it in setOf("Anna", "Bruno", "Carla", "Dario") })

        val tempi = sezione(cronaca, R.id.section_times)
        assertFalse(tempi.any { it.startsWith("Il tabellone non ha registrato") })
        assertTrue(tempi.contains("Durata"))

        assertTrue(sezione(cronaca, R.id.section_moments).contains("Tie-break del 1° set a Rossi, 7-5"))
        assertTrue(sezione(cronaca, R.id.section_games).contains("TB 7-5"))
    }

    @Test
    fun `una partita che non c'e' piu' dice che la cronaca non e' disponibile`() {
        val cronaca = apri(matchId = 999)

        assertEquals(View.VISIBLE, cronaca.findViewById<View>(R.id.chronicle_unavailable).visibility)
        assertEquals(View.GONE, cronaca.findViewById<View>(R.id.chronicle_sections).visibility)
    }
}
