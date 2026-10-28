package it.vantaggi.scoreboardessential.ui.statistics

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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
 * Le Statistiche seguono lo sport in uso (passo 6). Nel padel niente schede da calcio (Generale,
 * Attacco, Difesa) e niente gol: la classifica delle partite vinte. Nel calcio, con tutti a zero gol,
 * lo stato vuoto e basta. In cima si sceglie lo sport senza passare dalle Impostazioni, e i numeri
 * sono di quello sport: chi gioca anche a padel non ha nel calcio le sue partite di padel.
 * Rosso se nel padel tornano le schede o i gol, se lo zero si mostra, se il chip dello sport non
 * cambia la classifica, o se il calcio conta le partite o i giocatori di un altro sport.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it")
class StatistichePerSportTest {
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
            listOf("Anna", "Bruno", "Carla", "Dario").forEachIndexed { i, nome ->
                db.playerDao().insert(Player(playerId = i + 1, playerName = nome, appearances = 1, goals = 0))
            }
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

    private fun sport(id: String) {
        RuntimeEnvironment
            .getApplication()
            .getSharedPreferences("match_settings_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("active_sport", id)
            .commit()
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

    private fun testiDelleRighe(lista: RecyclerView): List<String> =
        (0 until lista.childCount).map { i ->
            val riga = lista.getChildAt(i)
            "${riga.findViewById<TextView>(R.id.text_player_name).text} ${riga.findViewById<TextView>(R.id.text_goals).text}"
        }

    /** Una partita chiusa: [lato1] contro [lato2], vince chi ha il punteggio piu' alto. */
    private fun partita(
        sport: String,
        lato1: List<Int>,
        lato2: List<Int>,
        punteggio: Pair<Int, Int> = 1 to 0,
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
                        timestamp = 1L,
                        sportId = sport,
                    ),
                ).toInt()
        db.matchDao().insertMatchPlayerCrossRefs(
            lato1.map { MatchPlayerCrossRef(id, it, 1) } + lato2.map { MatchPlayerCrossRef(id, it, 2) },
        )
    }

    private fun chip(
        statistiche: StatisticsActivity,
        testo: String,
    ): Chip =
        statistiche
            .findViewById<ChipGroup>(R.id.sport_chip_group)
            .let { gruppo -> (0 until gruppo.childCount).map { gruppo.getChildAt(it) as Chip } }
            .first { it.text.toString() == testo }

    private fun chipScelto(statistiche: StatisticsActivity): String? {
        val gruppo = statistiche.findViewById<ChipGroup>(R.id.sport_chip_group)
        return (0 until gruppo.childCount)
            .map { gruppo.getChildAt(it) as Chip }
            .firstOrNull { it.isChecked }
            ?.text
            ?.toString()
    }

    private fun righe(lista: RecyclerView): List<View> {
        lista.measure(0, 0)
        lista.layout(0, 0, 1080, 2000)
        return (0 until lista.childCount).map { lista.getChildAt(it) }
    }

    @Test
    fun `nel calcio le partite e i giocatori sono solo di calcio`() {
        sport(SportRegistry.FOOTBALL)
        runBlocking { db.playerDao().update(Player(playerId = 1, playerName = "Anna", appearances = 99, goals = 2)) }
        // Anna, Bruno e Carla giocano a calcio; Dario solo a padel, e Anna anche a padel.
        partita(SportRegistry.FOOTBALL, listOf(1, 2), listOf(3), punteggio = 2 to 0)
        repeat(3) { partita(SportRegistry.PADEL, listOf(1, 4), listOf(2, 3)) }
        val statistiche = Robolectric.buildActivity(StatisticsActivity::class.java).setup().get()
        val lista = statistiche.findViewById<RecyclerView>(R.id.recycler_stats)
        attendi("la classifica non e' arrivata") { righe(lista).size == 3 }

        val descrizioni = righe(lista).map { it.contentDescription.toString() }
        // Una sola presenza: la partita di calcio. Le tre di padel non si sommano.
        assertEquals("1. Anna, 2 gol, 1 presenza", descrizioni.first())
        assertEquals(emptyList<String>(), descrizioni.filter { it.contains("Dario") })
        assertEquals("Calcio", chipScelto(statistiche))
    }

    @Test
    fun `il chip dello sport cambia la classifica senza toccare le impostazioni`() {
        sport(SportRegistry.FOOTBALL)
        runBlocking { db.playerDao().update(Player(playerId = 1, playerName = "Anna", appearances = 1, goals = 2)) }
        partita(SportRegistry.FOOTBALL, listOf(1), listOf(2), punteggio = 2 to 0)
        partita(SportRegistry.PADEL, listOf(1, 2), listOf(3, 4))
        val statistiche = Robolectric.buildActivity(StatisticsActivity::class.java).setup().get()
        val lista = statistiche.findViewById<RecyclerView>(R.id.recycler_stats)
        attendi("i gol non sono arrivati") {
            righe(lista).isNotEmpty() && statistiche.findViewById<View>(R.id.tabLayout).visibility == View.VISIBLE
        }

        chip(statistiche, "Padel").performClick()

        attendi("la classifica del padel non e' arrivata") {
            righe(lista).size == 4 && statistiche.findViewById<View>(R.id.tabLayout).visibility == View.GONE
        }
        assertEquals("Partite vinte", statistiche.findViewById<TextView>(R.id.stats_heading).text.toString())
        assertEquals("Padel", chipScelto(statistiche))
        // Lo sport in uso nelle Impostazioni e' ancora il calcio.
        assertEquals(
            SportRegistry.FOOTBALL,
            RuntimeEnvironment
                .getApplication()
                .getSharedPreferences("match_settings_prefs", Context.MODE_PRIVATE)
                .getString("active_sport", null),
        )

        chip(statistiche, "Calcio").performClick()

        attendi("i gol non sono tornati") {
            statistiche.findViewById<View>(R.id.tabLayout).visibility == View.VISIBLE && righe(lista).size == 2
        }
        assertEquals(View.GONE, statistiche.findViewById<View>(R.id.stats_heading).visibility)
    }

    @Test
    fun `toccando una riga si apre la scheda di quel giocatore`() {
        sport(SportRegistry.PADEL)
        partita(SportRegistry.PADEL, listOf(1, 2), listOf(3, 4))
        val statistiche = Robolectric.buildActivity(StatisticsActivity::class.java).setup().get()
        val lista = statistiche.findViewById<RecyclerView>(R.id.recycler_stats)
        attendi("la classifica non e' arrivata") { righe(lista).size == 4 }

        righe(lista).first().performClick()

        val aperta = shadowOf(statistiche).nextStartedActivity
        assertEquals(SchedaGiocatoreActivity::class.java.name, aperta.component?.className)
        assertEquals(1, aperta.getIntExtra(SchedaGiocatoreActivity.EXTRA_PLAYER_ID, -1))
    }

    @Test
    fun `nel padel la classifica e' delle partite vinte, senza schede`() {
        sport(SportRegistry.PADEL)
        runBlocking {
            val partita = Match(team1Id = 1, team2Id = 2, team1Score = 1, team2Score = 0, timestamp = 1L, sportId = SportRegistry.PADEL)
            val id = db.matchDao().insert(partita)
            db.matchDao().insertMatchPlayerCrossRefs(
                listOf(1 to 1, 2 to 1, 3 to 2, 4 to 2).map { (giocatore, lato) -> MatchPlayerCrossRef(id.toInt(), giocatore, lato) },
            )
        }
        val statistiche = Robolectric.buildActivity(StatisticsActivity::class.java).setup().get()
        val lista = statistiche.findViewById<RecyclerView>(R.id.recycler_stats)
        attendi("la classifica non e' arrivata") {
            lista.measure(0, 0)
            lista.layout(0, 0, 1080, 2000)
            lista.childCount == 4
        }

        assertEquals(View.GONE, statistiche.findViewById<View>(R.id.tabLayout).visibility)
        assertEquals("Partite vinte", statistiche.findViewById<TextView>(R.id.stats_heading).text.toString())
        assertEquals("Padel", chipScelto(statistiche))
        assertEquals(
            listOf("Anna 1 vinta su 1", "Bruno 1 vinta su 1", "Carla 0 vinte su 1", "Dario 0 vinte su 1"),
            testiDelleRighe(lista),
        )
        // Due in testa a pari merito: nessun primo, nessun lime.
        val lime = statistiche.getColor(R.color.elite_lime)
        val colori = (0 until lista.childCount).map { lista.getChildAt(it).findViewById<TextView>(R.id.text_goals).currentTextColor }
        assertEquals(emptyList<Int>(), colori.filter { it == lime })
    }

    @Test
    fun `nel calcio con tutti a zero gol c'e' lo stato vuoto`() {
        sport(SportRegistry.FOOTBALL)
        val statistiche = Robolectric.buildActivity(StatisticsActivity::class.java).setup().get()
        attendi("lo stato vuoto non e' arrivato") {
            statistiche.findViewById<View>(R.id.empty_state).visibility == View.VISIBLE
        }

        assertEquals(View.GONE, statistiche.findViewById<View>(R.id.stats_group).visibility)
        assertEquals(View.VISIBLE, statistiche.findViewById<View>(R.id.tabLayout).visibility)
    }
}
