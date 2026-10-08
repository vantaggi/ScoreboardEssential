package it.vantaggi.scoreboardessential

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Passo 9, la parte che si vede: la card d'avviso in cima al foglio PARTITA e il badge sull'icona
 * dell'orologio. Lo stato che li accende (WatchNotice) si prova nel ViewModel; la Activity vera,
 * con l'icona collegata o no e la ricreazione, e' in MainActivityLayoutTest (strumentato).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class StatoOrologioTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private fun gonfiaIlFoglio(): View {
        val contesto = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)
        return LayoutInflater.from(contesto).inflate(R.layout.content_scoreboard_details, null)
    }

    /**
     * La card c'e' di norma nascosta: senza un arretrato rifiutato il foglio e' quello di prima. Sta
     * in cima, sopra le rose, col testo nuovo che descrive e non prescrive.
     */
    @Test
    fun laCardDellOrologio_e_nascosta_di_norma_e_sta_in_cima_al_foglio() {
        val foglio = gonfiaIlFoglio()
        val card = foglio.findViewById<View>(R.id.watch_notice_card)
        assertNotNull("manca la card dell'arretrato", card)
        assertEquals("di norma la card non si vede", View.GONE, card.visibility)

        val rose = foglio.findViewById<View>(R.id.rosters_card).layoutParams as ConstraintLayout.LayoutParams
        // Il gruppo Serata (R-2) sta fra la card e le rose: l'avviso resta il primo gruppo sotto la testata.
        val serata = foglio.findViewById<View>(R.id.serata_card).layoutParams as ConstraintLayout.LayoutParams
        assertEquals("il gruppo Serata sta sotto la card dell'orologio", R.id.watch_notice_card, serata.topToBottom)
        assertEquals("le rose stanno sotto il gruppo Serata", R.id.serata_card, rose.topToBottom)
        val params = card.layoutParams as ConstraintLayout.LayoutParams
        assertEquals("la card sta sotto la testata del foglio", R.id.match_sheet_close_button, params.topToBottom)

        assertEquals(
            app.getString(R.string.watch_batch_rejected),
            foglio.findViewById<TextView>(R.id.watch_notice_text).text.toString(),
        )
    }

    /** Il testo dell'avviso non e' un invito: «Chiudila» faceva salvare la partita senza quei punti. */
    @Test
    @Config(qualifiers = "it")
    fun laCard_descrive_e_non_invita() {
        val testo = gonfiaIlFoglio().findViewById<TextView>(R.id.watch_notice_text).text.toString()
        assertEquals("L'orologio ha mandato punti che non sono entrati in questa partita.", testo)
    }

    /** Il riepilogo nella striscia dice N punti dall'orologio, col singolare. */
    @Test
    @Config(qualifiers = "it")
    fun ilRiepilogo_dice_N_punti_dall_orologio() {
        assertEquals("1 punto dall'orologio", app.resources.getQuantityString(R.plurals.strip_msg_from_watch, 1, 1))
        assertEquals("3 punti dall'orologio", app.resources.getQuantityString(R.plurals.strip_msg_from_watch, 3, 3))
    }

    /**
     * Il badge e' disegnato: #FF1744 di 12dp in alto a destra, con un anello di 2dp #000000 fuori, e
     * il resto dell'icona resta trasparente (il glifo si vede sotto). E' il foreground dell'icona,
     * quindi non prende il tint grigio o chiaro del glifo.
     */
    @Test
    fun ilBadge_e_rosso_con_l_anello_nero_in_alto_a_destra() {
        val badge = ContextCompat.getDrawable(app, R.drawable.bg_watch_notice_badge)
        assertNotNull(badge)
        val densita = app.resources.displayMetrics.density
        val lato = (48 * densita).toInt()
        val bitmap = Bitmap.createBitmap(lato, lato, Bitmap.Config.ARGB_8888)
        badge!!.setBounds(0, 0, lato, lato)
        badge.draw(Canvas(bitmap))

        fun pixel(
            xDp: Float,
            yDp: Float,
        ) = bitmap.getPixel((xDp * densita).toInt(), (yDp * densita).toInt())

        // Centro del badge: 4dp dal bordo destro e dall'alto, 16dp di diametro totale.
        assertEquals("il centro e' elite_error", 0xFFE05252.toInt(), pixel(36f, 12f))
        assertEquals("l'anello e' elite_background", 0xFF0D0D0F.toInt(), pixel(36f + 7.5f, 12f))
        assertEquals("fuori dal badge e' trasparente", 0, Color.alpha(pixel(6f, 42f)))
        assertTrue("il badge sta in alto, non in basso", Color.alpha(pixel(36f, 12f)) == 255 && Color.alpha(pixel(36f, 40f)) == 0)
    }
}
