package it.vantaggi.scoreboardessential

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.card.MaterialCardView
import it.vantaggi.scoreboardessential.core.MatchEngine
import it.vantaggi.scoreboardessential.core.RacketRules
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.utils.NumberRoll
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Le immagini del README (vetrina), non un controllo: la colonna di gioco vera, gonfiata col tema
 * dell'app e riempita con i dati finti di una partita di padel giocata dal motore, salvata in
 * `mobile/build/vetrina/`. Gira solo con `VETRINA=1` (`scripts/vetrina.sh`). MainActivity sotto
 * Robolectric non si monta: qui si usano le stesse funzioni che lei chiama per servizio e zone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h923dp-xhdpi")
class VetrinaDelTelefonoTest {
    // Nomi finti: nessun giocatore vero nelle immagini pubbliche.
    private val rose = listOf(listOf("Rossi", "Bianchi"), listOf("Verdi", "Neri"))

    private fun gonfia(): View {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val contesto = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)
        val radice = LayoutInflater.from(contesto).inflate(R.layout.content_scoreboard_live, null)
        radice.layoutDirection = View.LAYOUT_DIRECTION_LTR
        return radice
    }

    private fun gioca(eventi: List<ScoringEvent>) =
        (SportRegistry.byId(SportRegistry.PADEL) as RacketRules).let { regole ->
            val motore = MatchEngine(regole)
            eventi.forEach { motore.apply(it) }
            regole.display(motore.state)
        }

    private fun game(lato: Int) = List(4) { ScoringEvent.Point(lato) }

    private fun riempi(
        radice: View,
        eventi: List<ScoringEvent>,
    ) {
        val c = radice.context
        val d = gioca(eventi)
        val nomi = listOf("ROS/BIA", "VER/NER")
        radice.findViewById<TextView>(R.id.team1_score_textview).text = d.side1Primary
        radice.findViewById<TextView>(R.id.team2_score_textview).text = d.side2Primary
        mostraGliSlotDelServizio(radice, sportConServizio = true)
        mostraIlServizio(radice, d, nomi[0], nomi[1], rose)
        val zone =
            listOf(
                listOf(R.id.team1_add_button_card, R.id.team1_plus_icon, R.id.team1_color_bar, R.id.team1_zone_bar, R.color.team_side_1),
                listOf(R.id.team2_add_button_card, R.id.team2_plus_icon, R.id.team2_color_bar, R.id.team2_zone_bar, R.color.team_side_2),
            )
        zone.forEach { (zona, glifo, barretta, barra, colore) ->
            applicaStatoDellaZona(
                radice.findViewById<MaterialCardView>(zona),
                radice.findViewById<ImageView>(glifo),
                radice.findViewById(barretta),
                radice.findViewById(barra),
                ContextCompat.getColor(c, colore),
                finita = false,
            )
        }
        // L'orologio collegato, come in aggiornaIconaOrologio.
        radice.findViewById<ImageView>(R.id.wear_status_icon).apply {
            setImageResource(R.drawable.ic_watch)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(c, R.color.text_primary))
        }
        listOf(R.id.timer_start_button, R.id.keeper_slot).forEach { radice.findViewById<View>(it).visibility = View.GONE }
        val alServizio = if (d.servingSide == 2) nomi[1] else nomi[0]
        radice.findViewById<TextView>(R.id.match_period_textview).apply {
            visibility = View.VISIBLE
            text = c.getString(R.string.score_period_serving, "Padel", alServizio)
        }
        // Come bindScoreDetail: il dettaglio letto dalla squadra di sinistra.
        radice.findViewById<TextView>(R.id.score_detail_value).text = d.side1Secondary.orEmpty()
        radice.findViewById<TextView>(R.id.score_detail_caption).text = c.getString(R.string.caption_game)
        radice.findViewById<View>(R.id.score_detail_container).visibility = View.VISIBLE
        val ultimo = (eventi.lastOrNull() as? ScoringEvent.Point)?.let { nomi[it.side - 1] } ?: nomi[0]
        radice.findViewById<TextView>(R.id.last_action_text).text =
            c.getString(R.string.strip_point, ultimo, "${d.side1Primary}-${d.side2Primary}")
    }

    private fun misura(radice: View) {
        val densita = radice.resources.displayMetrics.density
        radice.measure(
            View.MeasureSpec.makeMeasureSpec((411 * densita).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((923 * densita).toInt(), View.MeasureSpec.EXACTLY),
        )
        radice.layout(0, 0, radice.measuredWidth, radice.measuredHeight)
    }

    /** Come MainActivity.dimensionaINumeri: il token piu' largo della racchetta nella meta' di colonna. */
    private fun dimensionaINumeri(radice: View) {
        val token = "AV"
        val riga = radice.findViewById<View>(R.id.score_row)
        val uno = radice.findViewById<TextView>(R.id.team1_score_textview)
        val due = radice.findViewById<TextView>(R.id.team2_score_textview)
        val r = radice.resources
        val prova = Paint(uno.paint).apply { textSize = 100f }
        val metriche = prova.fontMetrics
        val disponibile = riga.width / 2f - 16 * r.displayMetrics.density
        val pixel =
            minOf(
                r.getDimension(R.dimen.score_text_max),
                100f * disponibile / prova.measureText(token),
                100f * riga.height / (metriche.descent - metriche.ascent),
            ).coerceAtLeast(r.getDimension(R.dimen.score_text_min))
        listOf(uno, due).forEach {
            it.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixel)
            NumberRoll.fissaLaScatola(it, token)
        }
    }

    private fun cattura(
        radice: View,
        nome: String,
    ) {
        misura(radice)
        dimensionaINumeri(radice)
        misura(radice)
        val immagine = Bitmap.createBitmap(radice.measuredWidth, radice.measuredHeight, Bitmap.Config.ARGB_8888)
        Canvas(immagine).apply {
            drawColor(ContextCompat.getColor(radice.context, R.color.background_watch))
            radice.draw(this)
        }
        val file = File("build/vetrina/$nome.png")
        file.parentFile!!.mkdirs()
        FileOutputStream(file).use { immagine.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun partita() {
        assumeTrue("solo con VETRINA=1", System.getenv("VETRINA") == "1")
        val p1 = ScoringEvent.Point(1)
        val p2 = ScoringEvent.Point(2)
        val base = game(1) + game(2) + game(1) + game(2) + game(1)
        val copertina = gonfia()
        riempi(copertina, base + listOf(p2, p1, p1))
        cattura(copertina, "telefono-copertina")
        val giro = listOf(p1, p2, p1, p1, p2, p2, p1, p1)
        var eventi = base
        for (i in 0..giro.size) {
            if (i > 0) eventi = eventi + giro[i - 1]
            val radice = gonfia()
            riempi(radice, eventi)
            cattura(radice, "telefono-teaser-%02d".format(i))
        }
    }
}
