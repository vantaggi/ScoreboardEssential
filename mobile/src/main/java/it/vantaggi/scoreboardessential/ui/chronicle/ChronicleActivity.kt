package it.vantaggi.scoreboardessential.ui.chronicle

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isNotEmpty
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.ChipGroup
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import it.vantaggi.scoreboardessential.core.GameStat
import it.vantaggi.scoreboardessential.core.MatchPlayer
import it.vantaggi.scoreboardessential.core.MatchStats
import it.vantaggi.scoreboardessential.core.ServeLine
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.sportLabel
import it.vantaggi.scoreboardessential.utils.MatchExportUtils
import it.vantaggi.scoreboardessential.utils.etichettaConBarretta
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import it.vantaggi.scoreboardessential.shared.R as SharedR

/**
 * La Cronaca di una partita chiusa: la stessa della dashboard di Padel Elite, sui dati del
 * telefono e senza rete (`docs/dashboard/SCOREBOARD_CRONACA_APP.md`, sezione 4).
 *
 * Il calcolo e' tutto in [MatchStats]; qui ci sono solo impaginazione e parole. Ogni sezione ha
 * il suo stato vuoto e lo dice (che cosa manca, perche', cosa fare): una partita salvata senza
 * ordine di servizio o senza tempi non deve sembrare una partita in cui nessuno ha servito o
 * durata zero.
 *
 * Schermata di contorno (G-5): ogni sezione e' un gruppo tonale (surface su canvas) con righe
 * separate da linee sottili rientrate al testo, niente card e niente ombre. I colori sono i
 * ruoli `elite_*`; quelli dei lati sono il colore con cui si e' giocato (lime e ciano, chart-1 e
 * chart-2, se l'utente non li ha cambiati), schiarito da TeamInk se non regge 3:1 sul gruppo, e
 * sono sempre grafica (barrette, tratti, linea): il testo resta nel testo primario. Tutti i
 * numeri sono Inter con cifre tabulari. Nessun maiuscolo.
 */
class ChronicleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chronicle)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Edge-to-edge: gli insets di sistema diventano padding del contenitore radice, come nello storico.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.chronicle_root)) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            windowInsets
        }

        val matchId = intent.getIntExtra(EXTRA_MATCH_ID, NO_MATCH)
        lifecycleScope.launch {
            val dao = (application as ScoreboardEssentialApplication).database.matchDao()
            val riga = dao.getMatchWithTeams(matchId)
            // Rigiocare il registro costa qualche millisecondo anche per una partita lunga: non
            // serve un altro thread.
            val stats = riga?.let { MatchExportUtils.savedEngine(it.match) }?.let { MatchStats.of(it) }
            if (riga == null || stats == null) {
                findViewById<View>(R.id.chronicle_unavailable).visibility = View.VISIBLE
                return@launch
            }
            render(riga, dao.getMatchLineup(matchId), stats)
        }
    }

    /** Si torna allo storico da cui si e' arrivati, senza ricrearlo. */
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun render(
        riga: MatchWithTeams,
        lineup: List<MatchPlayer>,
        s: MatchStats,
    ) {
        val teams =
            listOf(
                riga.team1?.name ?: getString(R.string.label_team_1),
                riga.team2?.name ?: getString(R.string.label_team_2),
            )
        // Il colore con cui si e' giocato; i predefiniti (lime e ciano) solo se la squadra non c'e' piu'.
        val paints =
            listOf(
                riga.team1?.color ?: color(R.color.team_side_1),
                riga.team2?.color ?: color(R.color.team_side_2),
            )
        supportActionBar?.subtitle = subtitle(riga.match, s)
        findViewById<View>(R.id.chronicle_sections).visibility = View.VISIBLE

        scoreboard(section(R.id.section_scoreboard, R.string.chronicle_section_scoreboard), s, teams, paints)
        momentum(section(R.id.section_momentum, R.string.chronicle_section_momentum), s, teams, paints)
        games(section(R.id.section_games, R.string.chronicle_section_games), s, teams, paints)
        serve(section(R.id.section_serve, R.string.chronicle_section_serve), s, teams, paints, riga.match, lineup)
        times(section(R.id.section_times, R.string.chronicle_section_times), s, teams)
        moments(section(R.id.section_moments, R.string.chronicle_section_moments), s, teams, paints)
    }

    /** "Padel · 23/09/2026 21:04 · 1 h 12 min": la durata solo se il tabellone l'ha misurata. */
    private fun subtitle(
        match: Match,
        s: MatchStats,
    ): String {
        val date = SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(Date(match.startedAt ?: match.timestamp))
        return listOfNotNull(
            sportLabel(this, match.sportId),
            date,
            s.times?.let { ChronicleText.duration(resources, it.totalMs) },
        ).joinToString(" · ")
    }

    private fun section(
        id: Int,
        @StringRes title: Int,
    ): LinearLayout {
        val group = findViewById<View>(id)
        group.findViewById<TextView>(R.id.section_title).setText(title)
        return group.findViewById<LinearLayout>(R.id.section_body).also { it.removeAllViews() }
    }

    // 1. Tabellone: coppie, set con il tie-break in apice, "Interrotta", punti e game.
    private fun scoreboard(
        body: LinearLayout,
        s: MatchStats,
        teams: List<String>,
        paints: List<Int>,
    ) {
        if (s.points.isEmpty()) return empty(body, R.string.chronicle_no_points, R.string.chronicle_no_points_reason)
        // Il set in corso si mostra solo se ha almeno un game: uno 0-0 non racconta niente.
        val columns =
            s.sets.map { it.games to it.tieBreak } +
                listOfNotNull(s.current?.takeIf { it.games.sum() > 0 }?.let { it.games to null })
        val table = TableLayout(this).apply { setColumnStretchable(0, true) }
        for (side in 1..2) {
            val label =
                text(teams[side - 1], size = BODY_SP, weight = WEIGHT_MEDIUM).apply {
                    etichettaConBarretta(paints[side - 1], color(R.color.elite_surface))
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
            // Chi ha perso si legge senza il colore: testo secondario e peso 400 (il colore non e'
            // l'unico segno); interrotta, nessuno ha perso.
            val lost = s.ended && s.winnerTeam != side
            val cells =
                columns.map { (games, tieBreak) ->
                    text(
                        setScore(games[side - 1], tieBreak?.get(side - 1)),
                        if (lost) R.color.elite_text_secondary else R.color.elite_text_primary,
                        size = SCORE_SP,
                        weight = if (lost) WEIGHT_REGULAR else WEIGHT_SEMIBOLD,
                    ).apply { minWidth = dimen(R.dimen.space_32) }
                }
            // L'etichetta resta della sua misura: si allarga la colonna, non il colore.
            val first =
                FrameLayout(this).apply {
                    addView(
                        label,
                        FrameLayout.LayoutParams(
                            WRAP,
                            WRAP,
                            Gravity.START or Gravity.CENTER_VERTICAL,
                        ),
                    )
                }
            addTableRow(table, first, *cells.toTypedArray())
        }
        body.addView(table)

        val foot = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        if (!s.ended) {
            foot.addView(
                text(
                    getString(R.string.chronicle_interrupted),
                    R.color.elite_text_secondary,
                    size = CAPTION_SP,
                    weight = WEIGHT_SEMIBOLD,
                ).apply {
                    background = badge()
                    setPaddingRelative(dimen(R.dimen.space_8), dimen(R.dimen.space_4), dimen(R.dimen.space_8), dimen(R.dimen.space_4))
                },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dimen(R.dimen.space_8) },
            )
        }
        foot.addView(
            text(
                getString(R.string.chronicle_totals, s.pointsWon[0], s.pointsWon[1], s.gamesWon[0], s.gamesWon[1]),
                R.color.elite_text_secondary,
            ),
            LinearLayout.LayoutParams(0, WRAP, 1f),
        )
        addRow(body, foot)
    }

    private fun setScore(
        games: Int,
        tieBreak: Int?,
    ): CharSequence {
        if (tieBreak == null) return games.toString()
        return SpannableStringBuilder(games.toString()).apply {
            val start = length
            append(tieBreak.toString())
            setSpan(SuperscriptSpan(), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(SUPERSCRIPT_SIZE), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    // 2. Andamento: una domanda sola, chi e' stato avanti nei punti vinti.
    private fun momentum(
        body: LinearLayout,
        s: MatchStats,
        teams: List<String>,
        paints: List<Int>,
    ) {
        if (s.points.isEmpty()) return empty(body, R.string.chronicle_no_points, R.string.chronicle_momentum_empty_reason)
        val diffs = s.points.map { it.diff }
        val setEnds = s.points.indices.filter { s.points[it].setWon && !s.points[it].matchWon }
        val chart =
            MomentumView(this).apply {
                setData(diffs, setEnds, onSurface(paints[0]), onSurface(paints[1]), teams[0], teams[1])
                contentDescription =
                    getString(
                        R.string.chronicle_momentum_description,
                        teams[0],
                        maxOf(0, diffs.max()),
                        teams[1],
                        maxOf(0, -diffs.min()),
                    )
            }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(chart, LinearLayout.LayoutParams(MATCH, WRAP))
        box.addView(caption(getString(R.string.chronicle_momentum_caption)), blockParams(top = dimen(R.dimen.space_8)))
        addRow(body, box)
    }

    // 3. Game per game: un riquadro per game, la barretta dice chi l'ha vinto (a sinistra il lato 1).
    private fun games(
        body: LinearLayout,
        s: MatchStats,
        teams: List<String>,
        paints: List<Int>,
    ) {
        if (s.games.isEmpty()) return empty(body, R.string.chronicle_no_games, R.string.chronicle_no_games_reason)
        s.games.groupBy { it.set }.forEach { (set, games) ->
            val closed = set < s.sets.size
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            box.addView(caption(getString(if (closed) R.string.chronicle_set else R.string.chronicle_set_open, set + 1)))
            // ChipGroup solo come contenitore che va a capo: le celle sono etichette, non chip.
            val flow =
                ChipGroup(this).apply {
                    chipSpacingHorizontal = dimen(R.dimen.space_8)
                    chipSpacingVertical = dimen(R.dimen.space_8)
                }
            games.forEach { flow.addView(gameCell(it, teams, paints)) }
            box.addView(flow, blockParams(top = dimen(R.dimen.space_8)))
            addRow(body, box)
        }
        // La B esiste solo se si sapeva chi serviva.
        val legend = if (s.serve != null) R.string.chronicle_games_legend_breaks else R.string.chronicle_games_legend
        addRow(body, caption(getString(legend, teams[0], teams[1])))
    }

    private fun gameCell(
        g: GameStat,
        teams: List<String>,
        paints: List<Int>,
    ): TextView {
        val isBreak = g.hold == false
        val label =
            if (g.tieBreak) {
                getString(R.string.chronicle_tiebreak_cell, g.score[g.winner - 1], g.score[2 - g.winner])
            } else {
                "${g.gamesAfter[0]}-${g.gamesAfter[1]}"
            }
        val shown = if (isBreak) "$label ${getString(R.string.chronicle_break_mark)}" else label
        val atStart = g.winner == 1
        val bar = dimen(R.dimen.space_16)
        val plain = dimen(R.dimen.space_12)
        return text(shown, size = BODY_SP, weight = WEIGHT_MEDIUM).apply {
            background = cellBackground(ChronicleText.graphicOn(paints[g.winner - 1], color(R.color.elite_surface_raised)), atStart)
            setPaddingRelative(if (atStart) bar else plain, dimen(R.dimen.space_8), if (atStart) plain else bar, dimen(R.dimen.space_8))
            // Il colore non e' l'unico segno di chi ha vinto il game: c'e' la posizione della barretta e il nome.
            contentDescription =
                getString(if (isBreak) R.string.chronicle_game_cd_break else R.string.chronicle_game_cd, label, teams[g.winner - 1])
        }
    }

    /**
     * Il riquadro di un game: `background-elevated` con raggio dei controlli e la barretta del
     * vincitore da 4dp, a sinistra per il lato 1 e a destra per il lato 2.
     */
    private fun cellBackground(
        ink: Int,
        atStart: Boolean,
    ): Drawable {
        val density = resources.displayMetrics.density
        val base =
            GradientDrawable().apply {
                setColor(color(R.color.elite_surface_raised))
                cornerRadius = resources.getDimension(R.dimen.radius_control)
            }
        val bar = GradientDrawable().apply { setColor(ink) }
        return LayerDrawable(arrayOf(base, bar)).apply {
            val inset = (CELL_BAR_INSET_DP * density).roundToInt()
            setLayerWidth(1, (CELL_BAR_DP * density).roundToInt())
            setLayerGravity(1, (if (atStart) Gravity.START else Gravity.END) or Gravity.FILL_VERTICAL)
            setLayerInsetTop(1, inset)
            setLayerInsetBottom(1, inset)
            if (atStart) setLayerInsetLeft(1, inset) else setLayerInsetRight(1, inset)
        }
    }

    // 4. Servizio.
    private fun serve(
        body: LinearLayout,
        s: MatchStats,
        teams: List<String>,
        paints: List<Int>,
        match: Match,
        lineup: List<MatchPlayer>,
    ) {
        val order = Match.decodeServeOrder(match.serveOrder)
        // Nel singolare le rose a due non c'entrano: il servizio si ricava dall'alternanza.
        val singles = MatchStats.isSingles(match.sportId, order)
        val serve =
            s.serve ?: return if (singles) {
                empty(body, R.string.chronicle_serve_unknown_singles, R.string.chronicle_serve_unknown_singles_reason)
            } else {
                empty(body, R.string.chronicle_serve_unknown, R.string.chronicle_serve_unknown_reason)
            }
        val names = lineup.associate { it.localId to it.name }
        val table = TableLayout(this).apply { setColumnStretchable(0, true) }
        addTableRow(
            table,
            caption(getString(R.string.chronicle_serve_header_player)),
            caption(getString(R.string.chronicle_serve_header_points)),
            caption(getString(R.string.chronicle_serve_header_games)),
        )
        // Nel singolare non si sa chi serviva come persona: una riga per lato, col nome della squadra.
        serve.bySide.takeIf { serve.byPlayer.isEmpty() }?.forEachIndexed { i, line ->
            addTableRow(table, *serveLineCells(withBar(text(teams[i]), paints[i]), line))
        }
        // Per coppia, come nella dashboard; dentro la coppia, nell'ordine di servizio. Il lato e'
        // la posizione nell'ordine, la stessa regola con cui MatchStats sa chi serviva.
        serve.byPlayer
            .sortedBy { order.indexOf(it.playerId) % 2 }
            .forEach { p ->
                val side = order.indexOf(p.playerId) % 2 + 1
                val name = names[p.playerId] ?: getString(R.string.chronicle_player_unknown, p.playerId)
                addTableRow(table, *serveLineCells(withBar(text(name), paints[side - 1]), p.line))
            }
        body.addView(table)
        for (side in 1..2) {
            val converted = s.breaks.converted[side - 1]
            val chances = s.breaks.chances[side - 1]
            addRow(body, withBar(text(getString(R.string.chronicle_breaks, teams[side - 1], converted, chances)), paints[side - 1]))
        }
    }

    private fun serveLineCells(
        label: View,
        line: ServeLine,
    ): Array<View> =
        arrayOf(
            label,
            text("${line.won}/${line.points}  ${percent(line.won, line.points)}", weight = WEIGHT_MEDIUM),
            text("${line.held}/${line.games}", weight = WEIGHT_MEDIUM),
        )

    private fun percent(
        won: Int,
        played: Int,
    ): String =
        if (played == 0) {
            getString(R.string.chronicle_no_value)
        } else {
            getString(R.string.chronicle_percent, (won * PERCENT / played.toDouble()).roundToInt())
        }

    // 5. Tempi.
    private fun times(
        body: LinearLayout,
        s: MatchStats,
        teams: List<String>,
    ) {
        val t = s.times ?: return empty(body, R.string.chronicle_times_unknown, R.string.chronicle_times_unknown_reason)
        val rows =
            listOf(getString(R.string.chronicle_times_total) to t.totalMs) +
                s.sets.zip(t.setDurationsMs) { set, ms ->
                    getString(R.string.chronicle_times_set, set.index + 1, set.games[0], set.games[1]) to ms
                } +
                listOf(
                    getString(R.string.chronicle_times_point) to t.avgPointMs,
                    getString(R.string.chronicle_times_game) to t.avgGameMs,
                )
        rows.forEach { (label, ms) ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(label, R.color.elite_text_secondary), LinearLayout.LayoutParams(0, WRAP, 1f))
            row.addView(text(ChronicleText.duration(resources, ms), weight = WEIGHT_SEMIBOLD))
            addRow(body, row)
        }
        t.longestGame?.let { g ->
            val played = g.score.sum()
            addRow(
                body,
                caption(
                    resources.getQuantityString(
                        R.plurals.chronicle_times_longest,
                        played,
                        ChronicleText.duration(resources, g.durationMs),
                        played,
                        g.set + 1,
                        teams[g.winner - 1],
                        g.gamesAfter[0],
                        g.gamesAfter[1],
                    ),
                ),
            )
        }
    }

    // 6. Momenti chiave: le frasi le scrive ChronicleText dai dati tipizzati.
    private fun moments(
        body: LinearLayout,
        s: MatchStats,
        teams: List<String>,
        paints: List<Int>,
    ) {
        if (s.moments.isEmpty()) return empty(body, R.string.chronicle_moments_none)
        s.moments.forEach { m ->
            val side = ChronicleText.side(m)
            addRow(body, withBar(text(ChronicleText.moment(resources, m, teams)), side?.let { paints[it - 1] }))
        }
    }

    /** Il colore di squadra come grafica sul gruppo: barrette e linea dell'andamento. */
    private fun onSurface(paint: Int): Int = ChronicleText.graphicOn(paint, color(R.color.elite_surface))

    /** Una barretta da 4dp nel colore di squadra davanti al testo; senza colore resta lo spazio. */
    private fun withBar(
        label: TextView,
        paint: Int?,
    ): LinearLayout =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            val bar = View(this@ChronicleActivity)
            if (paint != null) bar.setBackgroundColor(onSurface(paint))
            addView(
                bar,
                LinearLayout.LayoutParams(dimen(R.dimen.space_4), dimen(R.dimen.space_16)).apply {
                    marginEnd =
                        dimen(R.dimen.space_8)
                },
            )
            addView(label, LinearLayout.LayoutParams(0, WRAP, 1f))
        }

    /**
     * Lo stato vuoto di una sezione: che cosa manca, e se serve perche' e cosa fare. Il titolo e'
     * una frase senza punto, il motivo la spiegazione in testo secondario.
     */
    private fun empty(
        body: LinearLayout,
        @StringRes title: Int,
        @StringRes reason: Int? = null,
    ) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text(getString(title), size = BODY_SP, weight = WEIGHT_MEDIUM))
        reason?.let { box.addView(text(getString(it), R.color.elite_text_secondary), blockParams(top = dimen(R.dimen.space_4))) }
        addRow(body, box)
    }

    /** Una riga del gruppo: space_16 ai lati, space_12 sopra e sotto, e la linea sottile rientrata prima di ogni riga tranne la prima. */
    private fun addRow(
        body: LinearLayout,
        content: View,
    ) {
        if (body.isNotEmpty()) body.addView(divider(LinearLayout.LayoutParams(MATCH, dimen(R.dimen.border_width))))
        val row =
            FrameLayout(this).apply {
                setPaddingRelative(dimen(R.dimen.space_16), dimen(R.dimen.space_12), dimen(R.dimen.space_16), dimen(R.dimen.space_12))
                addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
            }
        body.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    /**
     * Una riga di tabella con le stesse misure di [addRow], cosi' le colonne di numeri restano in
     * fila: la prima cella a sinistra, le altre a destra.
     */
    private fun addTableRow(
        table: TableLayout,
        vararg cells: View,
    ) {
        if (table.isNotEmpty()) table.addView(divider(TableLayout.LayoutParams(MATCH, dimen(R.dimen.border_width))))
        val row =
            TableRow(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                cells.forEachIndexed { i, cell ->
                    if (i > 0 && cell is TextView) cell.gravity = Gravity.END
                    cell.setPaddingRelative(
                        dimen(if (i == 0) R.dimen.space_16 else R.dimen.space_12),
                        dimen(R.dimen.space_12),
                        if (i == cells.lastIndex) dimen(R.dimen.space_16) else 0,
                        dimen(R.dimen.space_12),
                    )
                    addView(cell)
                }
            }
        table.addView(row)
    }

    /** `border-subtle` di 1dp rientrata di space_16 (il bordo del testo); non e' letta da TalkBack. */
    private fun divider(params: LinearLayout.LayoutParams): View =
        View(this).apply {
            setBackgroundColor(color(R.color.elite_border_strong))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            params.marginStart = dimen(R.dimen.space_16)
            layoutParams = params
        }

    /** Il badge: solo bordo e testo, raggio 6, senza sfondo (come i ruoli dei giocatori). */
    private fun badge() =
        GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dimen(R.dimen.border_width), color(R.color.elite_outline))
            cornerRadius = resources.getDimension(R.dimen.radius_badge)
        }

    private fun caption(value: CharSequence): TextView = text(value, R.color.elite_text_secondary, size = CAPTION_SP)

    /**
     * Tutto il testo della Cronaca e' Inter con cifre tabulari (`tnum`): i numeri stanno in fila e
     * non ballano. Tre pesi in tutta la schermata: 400, 500 e 600. Mai maiuscolo.
     */
    private fun text(
        value: CharSequence,
        @ColorRes ink: Int = R.color.elite_text_primary,
        size: Float = BODY_SP,
        weight: Int = WEIGHT_REGULAR,
    ): TextView =
        TextView(this).apply {
            text = value
            setTextColor(color(ink))
            textSize = size
            typeface = Typeface.create(font(SharedR.font.inter), weight, false)
            fontFeatureSettings = TABULAR
        }

    /** Il carattere di res/font (modulo shared). Se manca, Typeface.DEFAULT: meglio un testo storto che nessun testo. */
    private fun font(
        @FontRes id: Int,
    ): Typeface = ResourcesCompat.getFont(this, id) ?: Typeface.DEFAULT

    private fun blockParams(top: Int = 0) =
        LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = top
        }

    private fun color(
        @ColorRes id: Int,
    ): Int = ContextCompat.getColor(this, id)

    private fun dimen(
        @DimenRes id: Int,
    ): Int = resources.getDimensionPixelSize(id)

    companion object {
        const val EXTRA_MATCH_ID = "it.vantaggi.scoreboardessential.extra.MATCH_ID"
        private const val NO_MATCH = -1
        private const val DATE_PATTERN = "dd/MM/yyyy HH:mm"
        private const val WEIGHT_REGULAR = 400
        private const val WEIGHT_MEDIUM = 500
        private const val WEIGHT_SEMIBOLD = 600

        // I corpi della scala M3 in sp: caption 12, body medio 14, body 16, i set del tabellone 22.
        private const val CAPTION_SP = 12f
        private const val BODY_SP = 14f
        private const val SCORE_SP = 22f
        private const val TABULAR = "tnum"
        private const val SUPERSCRIPT_SIZE = 0.55f
        private const val CELL_BAR_DP = 4
        private const val CELL_BAR_INSET_DP = 6
        private const val PERCENT = 100
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        fun intent(
            context: Context,
            matchId: Int,
        ): Intent = Intent(context, ChronicleActivity::class.java).putExtra(EXTRA_MATCH_ID, matchId)
    }
}
