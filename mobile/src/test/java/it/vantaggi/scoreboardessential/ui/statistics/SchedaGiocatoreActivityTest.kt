package it.vantaggi.scoreboardessential.ui.statistics

import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.room.Room
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchPlayerCrossRef
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.Team
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * La scheda del giocatore (bozza B): un blocco per sport con titolo, partite, vinte e (nel calcio) i
 * gol; si apre toccando una riga delle Statistiche. Rosso se la scheda somma gli sport, mostra uno
 * sport mai giocato, o la riga delle Statistiche non apre la scheda di quel giocatore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it")
class SchedaGiocatoreActivityTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        istanza(db)
        runBlocking {
            db.teamDao().insertWithId(Team(id = 1, name = "Rossi", color = 0, logoUri = null))
            db.teamDao().insertWithId(Team(id = 2, name = "Blu", color = 0, logoUri = null))
            db.playerDao().insert(Player(playerId = 1, playerName = "Marco", appearances = 99, goals = 3))
            db.playerDao().insert(Player(playerId = 2, playerName = "Sara", appearances = 0, goals = 0))
        }
    }

    @After
    fun tearDown() {
        db.close()
        istanza(null)
    }

    private fun istanza(valore: AppDatabase?) {
        val campo = AppDatabase::class.java.getDeclaredField("instance")
        campo.isAccessible = true
        campo.set(null, valore)
    }

    /** Una partita chiusa vinta dal lato 1, con [lato1] contro [lato2]. */
    private fun partita(
        sport: String,
        lato1: List<Int>,
        lato2: List<Int> = emptyList(),
    ) = runBlocking {
        val match = Match(team1Id = 1, team2Id = 2, team1Score = 1, team2Score = 0, timestamp = 1L, sportId = sport)
        val id = db.matchDao().insert(match).toInt()
        db.matchDao().insertMatchPlayerCrossRefs(
            lato1.map { MatchPlayerCrossRef(id, it, 1) } + lato2.map { MatchPlayerCrossRef(id, it, 2) },
        )
    }

    private fun attendi(
        cosa: String,
        condizione: () -> Boolean,
    ) {
        val fine = System.currentTimeMillis() + 5_000
        while (!condizione()) {
            check(System.currentTimeMillis() < fine) { "troppo tardi: $cosa" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun apri(playerId: Int) =
        Robolectric
            .buildActivity(
                SchedaGiocatoreActivity::class.java,
                Intent(RuntimeEnvironment.getApplication(), SchedaGiocatoreActivity::class.java)
                    .putExtra(SchedaGiocatoreActivity.EXTRA_PLAYER_ID, playerId),
            ).setup()
            .get()

    /** Per ogni blocco: «Titolo: Nome valore, Nome valore». */
    private fun testi(scheda: SchedaGiocatoreActivity): List<String> {
        val contenuto = scheda.findViewById<LinearLayout>(R.id.sheet_content)
        return (0 until contenuto.childCount).map { i ->
            val blocco = contenuto.getChildAt(i)
            val righe = blocco.findViewById<LinearLayout>(R.id.block_rows)
            val valori =
                (0 until righe.childCount)
                    .map { righe.getChildAt(it) }
                    .filter { it.id == R.id.card_player_stat }
                    .map { "${it.findViewById<TextView>(R.id.text_player_name).text} ${it.findViewById<TextView>(R.id.text_goals).text}" }
            "${blocco.findViewById<TextView>(R.id.block_title).text}: ${valori.joinToString(", ")}"
        }
    }

    @Test
    fun `un blocco per sport giocato, con i numeri di quello sport`() {
        repeat(2) { partita(SportRegistry.FOOTBALL, listOf(1)) }
        repeat(3) { partita(SportRegistry.PADEL, listOf(1, 2)) }
        val scheda = apri(1)
        attendi("la scheda non e' arrivata") { testi(scheda).size == 2 }

        assertEquals(
            listOf("Calcio: Partite 2, Vinte 2, Gol 3", "Padel: Partite 3, Vinte 3"),
            testi(scheda),
        )
        assertEquals("Marco", scheda.supportActionBar?.title?.toString())
        assertEquals(View.GONE, scheda.findViewById<View>(R.id.empty_state).visibility)
    }

    @Test
    fun `senza partite c'e' lo stato vuoto`() {
        val scheda = apri(2)
        attendi("lo stato vuoto non e' arrivato") { scheda.findViewById<View>(R.id.empty_state).visibility == View.VISIBLE }

        assertEquals(View.GONE, scheda.findViewById<View>(R.id.sheet_scroll).visibility)
        assertEquals("Sara", scheda.supportActionBar?.title?.toString())
    }
}
