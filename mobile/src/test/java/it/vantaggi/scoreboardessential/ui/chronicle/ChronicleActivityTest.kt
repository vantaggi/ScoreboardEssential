package it.vantaggi.scoreboardessential.ui.chronicle

import android.os.Looper
import android.text.Spanned
import android.text.style.SuperscriptSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.room.Room
import com.google.android.material.card.MaterialCardView
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchPlayerCrossRef
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.Team
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    /** Il grigio dei testi di prima, il vecchio stencil_white, che non e' piu' un colore dell'app. */
    private val grigioDiPrima = 0xFFE0E0E0.toInt()

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
            // I predefiniti del tabellone (G-6): lato 1 lime e lato 2 ciano, chart-1 e chart-2.
            database.teamDao().insertWithId(Team(id = 3, name = "Lime", color = 0xFFC8F135.toInt(), logoUri = null))
            database.teamDao().insertWithId(Team(id = 4, name = "Ciano", color = 0xFF00E5FF.toInt(), logoUri = null))
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
        sportId: String = SportRegistry.PADEL,
        team1Id: Int = 1,
        team2Id: Int = 2,
    ): Int =
        runBlocking {
            val registro = sides.mapIndexed { i, side -> LoggedEvent(ScoringEvent.Point(side), stepMs?.let { i * it }) }
            val partita =
                Match(
                    team1Id = team1Id,
                    team2Id = team2Id,
                    team1Score = 1,
                    team2Score = 0,
                    timestamp = 1_790_193_000_000L,
                    sportId = sportId,
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

        // Lo stato vuoto dice che cosa manca (la frase e' quella della dashboard) e poi perche' e
        // cosa fare: nell'app l'ordine di servizio nasce dalle rose e non si imposta a mano.
        assertEquals(
            listOf(
                "Il tabellone non sapeva chi serviva",
                "Con due giocatori per squadra nelle rose prima del primo punto, qui vedrai punti e game vinti al servizio da ciascuno.",
            ),
            sezione(cronaca, R.id.section_serve),
        )
        assertEquals(
            listOf(
                "Il tabellone non ha registrato i tempi di questa partita",
                "I punti di questa partita non hanno l'ora. Le partite nuove la registrano.",
            ),
            sezione(cronaca, R.id.section_times),
        )
        // Il resto si calcola lo stesso: il tabellone e la striscia non dipendono da chi serviva.
        assertTrue(sezione(cronaca, R.id.section_scoreboard).contains("Punti vinti 24 – 0 · Game 6 – 0"))
        assertEquals(listOf("24 punti di fila per Rossi nel 1° set"), sezione(cronaca, R.id.section_moments))
        // Senza servitore non si sa cos'e' un break: la legenda non ne parla.
        assertTrue(
            sezione(cronaca, R.id.section_games)
                .contains("La barretta dice chi ha vinto il game: Rossi a sinistra, Blu a destra. Il numero è il punteggio dopo il game."),
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

    /**
     * Nel singolare l'ordine di servizio non esiste e non serve: il servizio si legge per squadra,
     * senza la frase delle rose a due. Rossi serve il game 0 e lo perde, Blu serve il game 1 e lo
     * perde, Rossi tiene il game 2: 4 punti su 8 e 1 game su 2 per Rossi, 0 su 4 e 0 su 1 per Blu.
     */
    @Test
    fun `nel singolare il servizio e le palle break si leggono per squadra`() {
        val id = salva(game(2) + game(1) + game(1), serveOrder = "", stepMs = null, sportId = SportRegistry.TENNIS)

        val cronaca = apri(id)

        val servizio = sezione(cronaca, R.id.section_serve)
        assertEquals(
            listOf(
                "Al servizio",
                "Punti vinti",
                "Game tenuti",
                "Rossi",
                "4/8  50%",
                "1/2",
                "Blu",
                "0/4  0%",
                "0/1",
                "Rossi: palle break convertite 1 su 1",
                "Blu: palle break convertite 1 su 1",
            ),
            servizio,
        )
        assertTrue(sezione(cronaca, R.id.section_games).any { it.contains("B = break") })
        // La B sta sui game vinti da chi riceveva: Blu vince il game 0 che serve Rossi e Rossi il game 1 che serve Blu.
        assertEquals(listOf("0-1 B", "1-1 B", "2-1"), sezione(cronaca, R.id.section_games).filter { Regex("""\d-\d.*""").matches(it) })
    }

    /** Senza punti il singolare non suggerisce le rose: non c'entrano. */
    @Test
    fun `nel singolare senza punti la frase non parla di rose`() {
        val id = salva(emptyList(), serveOrder = "", stepMs = null, sportId = SportRegistry.TENNIS)

        val cronaca = apri(id)

        val servizio = sezione(cronaca, R.id.section_serve)
        assertEquals(2, servizio.size)
        assertFalse(servizio.any { it.contains("rose") })
        assertTrue(servizio.first().startsWith("Servizio e palle break si ricavano dai punti"))
    }

    @Test
    fun `una partita che non c'e' piu' dice che la cronaca non e' disponibile`() {
        val cronaca = apri(matchId = 999)

        assertEquals(View.VISIBLE, cronaca.findViewById<View>(R.id.chronicle_unavailable).visibility)
        assertEquals(View.GONE, cronaca.findViewById<View>(R.id.chronicle_sections).visibility)
    }

    // --- G-5: l'aspetto sui ruoli della UI Constitution ---

    private fun tutteLeViste(view: View): List<View> =
        listOf(view) + ((view as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { tutteLeViste(g.getChildAt(it)) } } ?: emptyList())

    private fun testiDellaCronaca(activity: ChronicleActivity): List<TextView> =
        tutteLeViste(activity.findViewById(R.id.chronicle_sections)).filterIsInstance<TextView>()

    /** Cifre tabulari: il testo con un numero ha `tnum`, cosi' i numeri stanno in fila. */
    private fun cifreNonTabulari(testi: List<TextView>): List<String> =
        testi
            .filter { v -> v.text.any { it.isDigit() } && v.fontFeatureSettings?.contains("tnum") != true }
            .map { it.text.toString() }

    /** Nessun maiuscolo forzato e nessun colore fuori dai due livelli di testo. */
    private fun testiFuoriRegola(
        testi: List<TextView>,
        ctx: android.content.Context,
    ): List<String> {
        val ammessi = setOf(ctx.getColor(R.color.elite_text_primary), ctx.getColor(R.color.elite_text_secondary))
        return testi
            .filter { it.isAllCaps || it.currentTextColor !in ammessi }
            .map { "${it.text} (maiuscolo=${it.isAllCaps}, colore=${Integer.toHexString(it.currentTextColor)})" }
    }

    private fun tieBreakDiRossi(): Int {
        val games = (1..6).flatMap { game(1) + game(2) }
        val tieBreak = List(5) { listOf(1, 2) }.flatten() + listOf(1, 1)
        return salva(games + tieBreak, serveOrder = "1,3,2,4", stepMs = 20_000L)
    }

    private fun graficoDi(activity: ChronicleActivity): MomentumView =
        tutteLeViste(activity.findViewById(R.id.section_momentum)).filterIsInstance<MomentumView>().single()

    @Test
    fun `i numeri della Cronaca sono tabulari e il testo e' sui due livelli senza maiuscolo`() {
        val cronaca = apri(tieBreakDiRossi())
        val testi = testiDellaCronaca(cronaca)

        assertTrue("pochi testi: ${testi.size}", testi.size > 40)
        assertEquals(emptyList<String>(), cifreNonTabulari(testi))
        assertEquals(emptyList<String>(), testiFuoriRegola(testi, cronaca))
        // Falsificazione: un testo con un numero senza `tnum`, o col grigio di prima, o in maiuscolo, viene trovato.
        val storto =
            TextView(cronaca).apply {
                text = "24"
                setTextColor(grigioDiPrima)
            }
        assertEquals(listOf("24"), cifreNonTabulari(listOf(storto)))
        assertEquals(1, testiFuoriRegola(listOf(storto), cronaca).size)
        val maiuscolo =
            TextView(cronaca).apply {
                isAllCaps = true
                setTextColor(cronaca.getColor(R.color.elite_text_primary))
            }
        assertEquals(1, testiFuoriRegola(listOf(maiuscolo), cronaca).size)
    }

    @Test
    fun `le sei sezioni sono gruppi tonali nell'ordine della dashboard, senza ombra`() {
        val cronaca = apri(salva((1..6).flatMap { game(1) }, serveOrder = "", stepMs = null))
        val sezioni = cronaca.findViewById<ViewGroup>(R.id.chronicle_sections)

        val titoli =
            (0 until sezioni.childCount).map {
                sezioni
                    .getChildAt(it)
                    .findViewById<TextView>(R.id.section_title)
                    .text
                    .toString()
            }
        assertEquals(listOf("Tabellone", "Andamento", "Game per game", "Servizio", "Tempi", "Momenti chiave"), titoli)
        for (i in 0 until sezioni.childCount) {
            val gruppo = tutteLeViste(sezioni.getChildAt(i)).filterIsInstance<MaterialCardView>().single()
            assertEquals(cronaca.getColor(R.color.elite_surface), gruppo.cardBackgroundColor.defaultColor)
            assertEquals(cronaca.getColor(R.color.elite_border), gruppo.strokeColor)
            assertEquals("la sezione $i ha un'ombra", 0f, gruppo.cardElevation, 0f)
        }
    }

    /** Righe e linee: la linea sta fra una riga e l'altra, rientrata al testo, e non c'e' ne' prima ne' dopo. */
    @Test
    fun `fra le righe di un gruppo ci sono linee sottili rientrate e non prima ne' dopo`() {
        val cronaca = apri(tieBreakDiRossi())
        val densita = cronaca.resources.displayMetrics.density
        val sottile = cronaca.resources.getDimensionPixelSize(R.dimen.border_width)
        val tempi = cronaca.findViewById<View>(R.id.section_times).findViewById<ViewGroup>(R.id.section_body)

        val figli = (0 until tempi.childCount).map { tempi.getChildAt(it) }
        assertTrue("righe e linee alternate: ${figli.size}", figli.size >= 5 && figli.size % 2 == 1)
        figli.forEachIndexed { i, v ->
            val linea = i % 2 == 1
            assertEquals("figlio $i", linea, v.layoutParams.height == sottile && v !is ViewGroup)
            if (linea) {
                assertEquals(16f, (v.layoutParams as ViewGroup.MarginLayoutParams).marginStart / densita, 0.5f)
            }
        }
        // Falsificazione: la riga di un gruppo non e' alta come una linea.
        assertNotEquals(sottile, figli.first().layoutParams.height)
    }

    @Test
    fun `ogni stato vuoto dice che cosa manca e perche', senza punti esclamativi`() {
        val cronaca = apri(salva((1..6).flatMap { game(1) }, serveOrder = "", stepMs = null))

        for (id in listOf(R.id.section_serve, R.id.section_times)) {
            val vuoto = sezione(cronaca, id)
            assertEquals("titolo e motivo: $vuoto", 2, vuoto.size)
            assertFalse(vuoto.any { it.contains("!") })
            assertFalse("il titolo non e' una frase chiusa dal punto: ${vuoto[0]}", vuoto[0].endsWith("."))
        }
        // Senza nessun punto, tabellone, andamento e game dicono che cosa manca e perche'.
        val senzaPunti = apri(salva(emptyList(), serveOrder = "", stepMs = null))
        for (id in listOf(R.id.section_scoreboard, R.id.section_momentum, R.id.section_games)) {
            assertEquals("sezione $id: ${sezione(senzaPunti, id)}", 2, sezione(senzaPunti, id).size)
        }
        assertEquals("Nessun punto registrato", sezione(senzaPunti, R.id.section_scoreboard).first())
    }

    /** I lati col colore con cui si e' giocato: lime e ciano (chart-1 e chart-2) restano tali; gli altri si schiariscono a 3:1. */
    @Test
    fun `il grafico dei lati e' lime e ciano con i predefiniti e leggibile con gli altri colori`() {
        val gruppo = 0xFF161618.toInt()
        val punti = (1..3).flatMap { game(1) } + (1..2).flatMap { game(2) }

        val predefiniti = apri(salva(punti, serveOrder = "", stepMs = null, team1Id = 3, team2Id = 4))
        assertEquals(0xFFC8F135.toInt() to 0xFF00E5FF.toInt(), graficoDi(predefiniti).sideColors)

        // Colori scelti dall'utente: il giallo regge da solo, il blu notte si schiarisce fino a 3:1 sul gruppo.
        val scelti = apri(salva(punti, serveOrder = "", stepMs = null))
        val (giallo, blu) = graficoDi(scelti).sideColors
        assertEquals(0xFFFFD600.toInt(), giallo)
        assertTrue(TeamInk.contrast(blu, gruppo) >= 3.0)
        assertNotEquals(0xFF1A237E.toInt(), blu)
    }

    @Test
    fun `il tie-break ha l'apice nel tabellone e il grafico nomina i due lati`() {
        val cronaca = apri(tieBreakDiRossi())

        val conApice =
            testiDellaCronaca(cronaca).filter { v ->
                (v.text as? Spanned)?.getSpans(0, v.text.length, SuperscriptSpan::class.java)?.isNotEmpty() == true
            }
        // Un apice per lato nel set del tie-break: 7 con 7 in apice e 6 con 5 in apice.
        assertEquals(listOf("77", "65"), conApice.map { it.text.toString() })
        // Il grafico nomina i due lati: il secondo segno oltre al colore.
        assertEquals(listOf("Rossi", "Blu"), graficoDi(cronaca).labels.map { it.substringBefore(" +") })
    }
}
