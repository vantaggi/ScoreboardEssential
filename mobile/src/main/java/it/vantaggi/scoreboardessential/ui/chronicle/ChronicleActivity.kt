package it.vantaggi.scoreboardessential.ui.chronicle

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
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
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.ChipGroup
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import it.vantaggi.scoreboardessential.core.GameStat
import it.vantaggi.scoreboardessential.core.MatchPlayer
import it.vantaggi.scoreboardessential.core.MatchStats
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.sportLabel
import it.vantaggi.scoreboardessential.utils.MatchExportUtils
import it.vantaggi.scoreboardessential.utils.etichettaDiSquadra
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * La Cronaca di una partita chiusa: la stessa della dashboard di Padel Elite, sui dati del
 * telefono e senza rete (`docs/dashboard/SCOREBOARD_CRONACA_APP.md`, sezione 4).
 *
 * Il calcolo e' tutto in [MatchStats]; qui ci sono solo impaginazione e parole. Ogni sezione ha
 * il suo stato vuoto e lo dice: una partita salvata senza ordine di servizio o senza tempi non
 * deve sembrare una partita in cui nessuno ha servito o durata zero.
 *
 * Schermata di contorno: asfalto, card in cemento, colori delle squadre solo come riempimento
 * con l'inchiostro di TeamInk o come grafica resa leggibile sulla card (DESIGN.md).
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
        // Il colore con cui si e' giocato; i predefiniti solo se la squadra non c'e' piu'.
        val paints =
            listOf(
                riga.team1?.color ?: color(R.color.team_spray_yellow),
                riga.team2?.color ?: color(R.color.team_electric_green),
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
        val card = findViewById<View>(id)
        card.findViewById<TextView>(R.id.section_title).setText(title)
        return card.findViewById<LinearLayout>(R.id.section_body).also { it.removeAllViews() }
    }

    // 1. Tabellone: coppie, set con il tie-break in apice, "Interrotta", punti e game.
    private fun scoreboard(
        body: LinearLayout,
        s: MatchStats,
        teams: List<String>,
        paints: List<Int>,
    ) {
        if (s.points.isEmpty()) return empty(body, R.string.chronicle_no_points)
        // Il set in corso si mostra solo se ha almeno un game: uno 0-0 non racconta niente.
        val columns =
            s.sets.map { it.games to it.tieBreak } +
                listOfNotNull(s.current?.takeIf { it.games.sum() > 0 }?.let { it.games to null })
        val table = TableLayout(this).apply { setColumnStretchable(0, true) }
        for (side in 1..2) {
            val row = TableRow(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val label =
                text(teams[side - 1], size = 16f, bold = true).apply {
                    etichettaDiSquadra(paints[side - 1])
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
            // L'etichetta resta della sua misura: si allarga la colonna, non il colore.
            row.addView(
                FrameLayout(this).apply {
                    setPadding(0, dp(4), 0, dp(4))
                    addView(label, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START or Gravity.CENTER_VERTICAL))
                },
            )
            // Chi ha perso si legge senza il colore, come nello storico; interrotta, nessuno ha perso.
            val ink = if (s.ended && s.winnerTeam != side) R.color.sidewalk_gray else R.color.stencil_white
            columns.forEach { (games, tieBreak) ->
                row.addView(
                    text(setScore(games[side - 1], tieBreak?.get(side - 1)), ink, size = 24f, bold = true).apply {
                        gravity = Gravity.END
                        minWidth = dp(36)
                        fontFeatureSettings = TABULAR
                    },
                )
            }
            table.addView(row)
        }
        body.addView(table)

        val foot = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        if (!s.ended) {
            foot.addView(
                text(getString(R.string.chronicle_interrupted), size = 12f, bold = true).apply {
                    background = badge(color(R.color.graffiti_dark_gray))
                    setPadding(dp(8), dp(2), dp(8), dp(2))
                },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(8) },
            )
        }
        foot.addView(
            text(
                getString(R.string.chronicle_totals, s.pointsWon[0], s.pointsWon[1], s.gamesWon[0], s.gamesWon[1]),
                R.color.sidewalk_gray,
            ),
        )
        body.addView(foot, blockParams(top = 12))
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

    // 2. Andamento.
    private fun momentum(
        body: LinearLayout,
        s: MatchStats,
        teams: List<String>,
        paints: List<Int>,
    ) {
        if (s.points.isEmpty()) return empty(body, R.string.chronicle_no_points)
        val diffs = s.points.map { it.diff }
        val setEnds = s.points.indices.filter { s.points[it].setWon && !s.points[it].matchWon }
        val chart =
            MomentumView(this).apply {
                setData(diffs, setEnds, onCard(paints[0]), onCard(paints[1]))
                contentDescription =
                    getString(
                        R.string.chronicle_momentum_description,
                        teams[0],
                        maxOf(0, diffs.max()),
                        teams[1],
                        maxOf(0, -diffs.min()),
                    )
            }
        body.addView(chart, LinearLayout.LayoutParams(MATCH, dp(CHART_HEIGHT_DP)))
        body.addView(caption(getString(R.string.chronicle_momentum_caption, teams[0], teams[1])), blockParams(top = 8))
    }

    // 3. Game per game.
    private fun games(
        body: LinearLayout,
        s: MatchStats,
        teams: List<String>,
        paints: List<Int>,
    ) {
        if (s.games.isEmpty()) return empty(body, R.string.chronicle_no_games)
        s.games.groupBy { it.set }.forEach { (set, games) ->
            val closed = set < s.sets.size
            body.addView(caption(getString(if (closed) R.string.chronicle_set else R.string.chronicle_set_open, set + 1)))
            // ChipGroup solo come contenitore che va a capo: le celle sono etichette, non chip.
            val flow =
                ChipGroup(this).apply {
                    chipSpacingHorizontal = dp(6)
                    chipSpacingVertical = dp(6)
                }
            games.forEach { flow.addView(gameCell(it, teams, paints)) }
            body.addView(flow, blockParams(top = 4, bottom = 12))
        }
        // La B esiste solo se si sapeva chi serviva.
        body.addView(caption(getString(if (s.serve != null) R.string.chronicle_games_legend_breaks else R.string.chronicle_games_legend)))
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
        return text(shown, size = 14f, bold = true).apply {
            etichettaDiSquadra(paints[g.winner - 1])
            setPadding(dp(8), dp(4), dp(8), dp(4))
            fontFeatureSettings = TABULAR
            // Il colore non e' l'unico segno di chi ha vinto il game.
            contentDescription =
                getString(if (isBreak) R.string.chronicle_game_cd_break else R.string.chronicle_game_cd, label, teams[g.winner - 1])
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
        val serve = s.serve ?: return empty(body, R.string.chronicle_serve_unknown)
        val names = lineup.associate { it.localId to it.name }
        val order = Match.decodeServeOrder(match.serveOrder)
        val table = TableLayout(this).apply { setColumnStretchable(0, true) }
        table.addView(
            serveRow(
                caption(getString(R.string.chronicle_serve_header_player)),
                caption(getString(R.string.chronicle_serve_header_points)),
                caption(getString(R.string.chronicle_serve_header_games)),
            ),
        )
        // Per coppia, come nella dashboard; dentro la coppia, nell'ordine di servizio. Il lato e'
        // la posizione nell'ordine, la stessa regola con cui MatchStats sa chi serviva.
        serve.byPlayer
            .sortedBy { order.indexOf(it.playerId) % 2 }
            .forEach { p ->
                val side = order.indexOf(p.playerId) % 2 + 1
                val line = p.line
                val name = names[p.playerId] ?: getString(R.string.chronicle_player_unknown, p.playerId)
                table.addView(
                    serveRow(
                        withBar(text(name), paints[side - 1]),
                        text("${line.won}/${line.points}  ${percent(line.won, line.points)}").apply { fontFeatureSettings = TABULAR },
                        text("${line.held}/${line.games}").apply { fontFeatureSettings = TABULAR },
                    ),
                )
            }
        body.addView(table)
        for (side in 1..2) {
            val converted = s.breaks.converted[side - 1]
            val chances = s.breaks.chances[side - 1]
            body.addView(
                withBar(text(getString(R.string.chronicle_breaks, teams[side - 1], converted, chances)), paints[side - 1]),
                blockParams(top = 8),
            )
        }
    }

    private fun serveRow(vararg cells: View): TableRow =
        TableRow(this).apply {
            cells.forEachIndexed { i, cell ->
                if (i > 0 && cell is TextView) cell.gravity = Gravity.END
                cell.setPadding(if (i > 0) dp(12) else 0, dp(4), 0, dp(4))
                addView(cell)
            }
        }

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
        val t = s.times ?: return empty(body, R.string.chronicle_times_unknown)
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
            row.addView(text(label, R.color.sidewalk_gray), LinearLayout.LayoutParams(0, WRAP, 1f))
            row.addView(text(ChronicleText.duration(resources, ms), bold = true).apply { fontFeatureSettings = TABULAR })
            body.addView(row, blockParams(top = 2, bottom = 2))
        }
        t.longestGame?.let { g ->
            val played = g.score.sum()
            body.addView(
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
                blockParams(top = 8),
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
            body.addView(
                withBar(text(ChronicleText.moment(resources, m, teams)), side?.let { paints[it - 1] }),
                blockParams(top = 4, bottom = 4),
            )
        }
    }

    /** Il colore di squadra come grafica sulla card: barrette e linea dell'andamento. */
    private fun onCard(paint: Int): Int = ChronicleText.graphicOn(paint, color(R.color.concrete_gray))

    /** Una barretta da 4dp nel colore di squadra davanti al testo; senza colore resta lo spazio. */
    private fun withBar(
        label: TextView,
        paint: Int?,
    ): LinearLayout =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            val bar = View(this@ChronicleActivity)
            if (paint != null) bar.setBackgroundColor(onCard(paint))
            addView(bar, LinearLayout.LayoutParams(dp(4), dp(16)).apply { marginEnd = dp(8) })
            addView(label, LinearLayout.LayoutParams(0, WRAP, 1f))
        }

    private fun empty(
        body: LinearLayout,
        @StringRes message: Int,
    ) {
        body.addView(text(getString(message), R.color.sidewalk_gray))
    }

    private fun caption(value: CharSequence): TextView = text(value, R.color.sidewalk_gray, size = 12f)

    private fun text(
        value: CharSequence,
        @ColorRes ink: Int = R.color.stencil_white,
        size: Float = 14f,
        bold: Boolean = false,
    ): TextView =
        TextView(this).apply {
            text = value
            setTextColor(color(ink))
            textSize = size
            typeface = Typeface.create(CONDENSED, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun badge(fill: Int) =
        MaterialShapeDrawable(ShapeAppearanceModel.builder(this, R.style.ShapeAppearance_App_StreetBadge, 0).build()).apply {
            fillColor = ColorStateList.valueOf(fill)
        }

    private fun blockParams(
        top: Int = 0,
        bottom: Int = 0,
    ) = LinearLayout.LayoutParams(MATCH, WRAP).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun color(
        @ColorRes id: Int,
    ): Int = ContextCompat.getColor(this, id)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val EXTRA_MATCH_ID = "it.vantaggi.scoreboardessential.extra.MATCH_ID"
        private const val NO_MATCH = -1
        private const val DATE_PATTERN = "dd/MM/yyyy HH:mm"
        private const val CONDENSED = "sans-serif-condensed"
        private const val TABULAR = "tnum"
        private const val SUPERSCRIPT_SIZE = 0.55f
        private const val CHART_HEIGHT_DP = 160
        private const val PERCENT = 100
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        fun intent(
            context: Context,
            matchId: Int,
        ): Intent = Intent(context, ChronicleActivity::class.java).putExtra(EXTRA_MATCH_ID, matchId)
    }
}
