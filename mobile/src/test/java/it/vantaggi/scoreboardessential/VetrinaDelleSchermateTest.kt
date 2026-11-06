package it.vantaggi.scoreboardessential

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.room.Room
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchEngine
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.RacketRules
import it.vantaggi.scoreboardessential.core.RacketScore
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchPlayerCrossRef
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.Team
import it.vantaggi.scoreboardessential.ui.chronicle.ChronicleActivity
import it.vantaggi.scoreboardessential.ui.statistics.StatisticsActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Le immagini del README (vetrina), non un controllo: Cronaca, Storico e Statistiche vere, su un
 * database in memoria con una serata finta di padel. Nomi inventati, partite giocate dal motore
 * con un caso fisso, cosi' le immagini escono uguali a ogni giro. Gira solo con `VETRINA=1`
 * (`scripts/vetrina.sh`) e scrive in `mobile/build/vetrina/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h923dp-xhdpi")
class VetrinaDelleSchermateTest {
    private lateinit var db: AppDatabase
    private val nomi = listOf("Marco", "Luca", "Giulia", "Sara", "Paolo", "Elena", "Davide", "Chiara")

    // Le coppie della serata finta: i posti 1-2 contro 3-4, ogni volta con un altro caso.
    private val partite =
        listOf(
            listOf(1, 2, 3, 4) to 0.56,
            listOf(5, 6, 7, 8) to 0.47,
            listOf(1, 3, 5, 7) to 0.58,
            listOf(2, 4, 6, 8) to 0.5,
            listOf(1, 6, 2, 5) to 0.53,
            listOf(3, 8, 4, 7) to 0.45,
        )

    @Before
    fun setUp() {
        assumeTrue("solo con VETRINA=1", System.getenv("VETRINA") == "1")
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        istanza(db)
        RuntimeEnvironment
            .getApplication()
            .getSharedPreferences("match_settings_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("active_sport", SportRegistry.PADEL)
            .commit()
        runBlocking {
            nomi.forEachIndexed {
                i,
                nome,
                ->
                db.playerDao().insert(Player(playerId = i + 1, playerName = nome, appearances = 0, goals = 0))
            }
        }
        partite.forEachIndexed { i, (posti, forza) -> salva(i, posti, forza) }
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        istanza(null)
    }

    private fun istanza(valore: AppDatabase?) {
        val campo = AppDatabase::class.java.getDeclaredField("instance")
        campo.isAccessible = true
        campo.set(null, valore)
    }

    private fun sigla(id: Int) = nomi[id - 1].take(3).uppercase()

    /**
     * Una partita a set unico giocata punto per punto: chi serve vince il punto un po' piu' spesso,
     * e [forza] sposta l'equilibrio verso la prima coppia. Ogni punto ha i suoi secondi.
     */
    private fun salva(
        indice: Int,
        posti: List<Int>,
        forza: Double,
    ): Int {
        val caso = Random(2026 + indice)
        val regole =
            RacketRules(
                id = SportRegistry.PADEL,
                config =
                    (
                        SportRegistry.byId(
                            SportRegistry.PADEL,
                        ) as RacketRules
                    ).config.copy(serveOrder = listOf(posti[0], posti[2], posti[1], posti[3])),
            )
        val motore = MatchEngine(regole)
        val registro = mutableListOf<LoggedEvent>()
        var tempo = 0L
        while (!regole.display(motore.state).matchOver) {
            val serve = regole.display(motore.state).servingSide ?: 1
            val p1 = forza + if (serve == 1) 0.08 else -0.08
            val punto = ScoringEvent.Point(if (caso.nextDouble() < p1) 1 else 2)
            motore.apply(punto)
            registro += LoggedEvent(punto, tempo)
            tempo += 18_000L + caso.nextLong(0, 30_000L)
        }
        val game = (motore.state as RacketScore).closedSets.last().games
        val inizio = 1_793_473_200_000L + indice * 45 * 60_000L
        return runBlocking {
            val lime = ContextCompat.getColor(RuntimeEnvironment.getApplication(), R.color.team_side_1)
            val ciano = ContextCompat.getColor(RuntimeEnvironment.getApplication(), R.color.team_side_2)
            db.teamDao().insertWithId(
                Team(id = indice * 2 + 1, name = "${sigla(posti[0])}/${sigla(posti[1])}", color = lime, logoUri = null),
            )
            db.teamDao().insertWithId(
                Team(id = indice * 2 + 2, name = "${sigla(posti[2])}/${sigla(posti[3])}", color = ciano, logoUri = null),
            )
            val id =
                db
                    .matchDao()
                    .insert(
                        Match(
                            team1Id = indice * 2 + 1,
                            team2Id = indice * 2 + 2,
                            team1Score = game[0],
                            team2Score = game[1],
                            timestamp = inizio + tempo,
                            sportId = SportRegistry.PADEL,
                            eventLog = MatchLogCodec.encode(registro),
                            serveOrder = listOf(posti[0], posti[2], posti[1], posti[3]).joinToString(","),
                            startedAt = inizio,
                        ),
                    ).toInt()
            db.matchDao().insertMatchPlayerCrossRefs(posti.mapIndexed { i, p -> MatchPlayerCrossRef(id, p, if (i < 2) 1 else 2) })
            id
        }
    }

    private fun attendi(condizione: () -> Boolean) {
        val fine = System.currentTimeMillis() + 5_000
        while (!condizione() && System.currentTimeMillis() < fine) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        repeat(5) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun cattura(
        activity: Activity,
        nome: String,
    ) {
        val decor = activity.window.decorView
        decor.requestLayout()
        shadowOf(Looper.getMainLooper()).idle()
        val immagine = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        Canvas(immagine).apply {
            drawColor(ContextCompat.getColor(activity, R.color.background_canvas))
            decor.draw(this)
        }
        val file = File("build/vetrina/$nome.png")
        file.parentFile!!.mkdirs()
        FileOutputStream(file).use { immagine.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun scorri(
        activity: Activity,
        dp: Int,
    ) {
        val lista = trova(activity.window.decorView) ?: return
        lista.scrollTo(0, (dp * activity.resources.displayMetrics.density).toInt())
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun trova(vista: View): NestedScrollView? {
        if (vista is NestedScrollView) return vista
        if (vista is ViewGroup) {
            for (i in 0 until vista.childCount) trova(vista.getChildAt(i))?.let { return it }
        }
        return null
    }

    @Test
    fun cronaca() {
        val activity =
            Robolectric
                .buildActivity(ChronicleActivity::class.java, ChronicleActivity.intent(RuntimeEnvironment.getApplication(), 1))
                .setup()
                .get()
        attendi { activity.findViewById<View>(R.id.chronicle_sections).visibility == View.VISIBLE }
        cattura(activity, "cronaca-1")
        scorri(activity, 700)
        cattura(activity, "cronaca-2")
        scorri(activity, 1400)
        cattura(activity, "cronaca-3")
    }

    @Test
    fun storico() {
        val activity = Robolectric.buildActivity(MatchHistoryActivity::class.java).setup().get()
        attendi { false }
        cattura(activity, "storico")
    }

    @Test
    fun statistiche() {
        val activity = Robolectric.buildActivity(StatisticsActivity::class.java).setup().get()
        attendi { false }
        cattura(activity, "statistiche")
    }
}
