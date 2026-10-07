package it.vantaggi.scoreboardessential.utils

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.core.TeamInk

/**
 * Pulsante dipinto col colore della squadra, con scritta e icona leggibili sopra.
 *
 * Cambiare solo lo sfondo lasciava testo e icona nel bianco sporco del tema: sul giallo e sul
 * verde predefiniti facevano 1,07 e 1,01:1, cioe' TEAM 1 COLOR e TEAM 2 COLOR non si leggevano.
 * L'inchiostro lo decide [TeamInk], la stessa regola dell'orologio.
 */
fun MaterialButton.dipingiDiSquadra(colore: Int) {
    setBackgroundColor(colore)
    val inchiostro = TeamInk.on(colore)
    setTextColor(inchiostro)
    iconTint = ColorStateList.valueOf(inchiostro)
}

/**
 * Etichetta di squadra come tag StreetBadge pieno del colore della squadra, testo in [TeamInk].
 *
 * Prima il colore della squadra era il colore del TESTO su #1E1E1E: con un blu notte l'etichetta
 * spariva (1,26:1). Come riempimento il colore resta riconoscibile e il testo sopra regge sempre
 * almeno 4,58:1.
 *
 * Il riempimento deve anche staccarsi dal fondo su cui sta ([sfondo], di norma la card #1E1E1E):
 * sotto 3:1 (WCAG 1.4.11) un blu notte diventa una macchia nel grigio, e allora prende un
 * contorno da 1dp in testo secondario. Il colore resta quello scelto, e' il bordo a farlo vedere.
 */
fun TextView.etichettaDiSquadra(
    colore: Int,
    sfondo: Int = context.getColor(R.color.elite_surface_raised),
) {
    riempiDiSquadra(colore, sfondo)
    setTextColor(TeamInk.on(colore))
}

/**
 * Il nome di una squadra nel foglio PARTITA (rose, coppie, formazioni): testo primario e il colore della
 * squadra solo come barretta di 4dp sul bordo iniziale (G-6). Niente blocco pieno con il testo sopra:
 * il colore sta nella grafica e la leggibilita' non dipende da lui. La barretta e' il colore scelto, o
 * [TeamInk.graphicOn] se sul fondo del gruppo non arriva a 3:1.
 */
fun TextView.etichettaConBarretta(
    colore: Int,
    sfondo: Int = context.getColor(R.color.elite_surface),
) {
    val densita = resources.displayMetrics.density
    val barretta =
        GradientDrawable().apply {
            setColor(TeamInk.graphicOn(colore, sfondo))
            cornerRadius = 2 * densita
        }
    background =
        LayerDrawable(arrayOf(barretta)).apply {
            setLayerWidth(0, (4 * densita).toInt())
            setLayerGravity(0, Gravity.START or Gravity.FILL_VERTICAL)
        }
    setPaddingRelative((12 * densita).toInt(), (2 * densita).toInt(), 0, (2 * densita).toInt())
    setTextColor(context.getColor(R.color.elite_text_primary))
}

/**
 * Il riempimento StreetBadge nel colore della squadra, col contorno se non si stacca da [sfondo].
 * Lo usano le etichette e le bande del PDF: chi ci scrive sopra sceglie l'inchiostro con [TeamInk].
 */
fun View.riempiDiSquadra(
    colore: Int,
    sfondo: Int,
) {
    val forma = ShapeAppearanceModel.builder(context, R.style.ShapeAppearance_App_Badge, 0).build()
    background =
        MaterialShapeDrawable(forma).apply {
            fillColor = ColorStateList.valueOf(colore)
            if (TeamInk.contrast(colore, sfondo) < CONTRASTO_GRAFICA) {
                setStroke(resources.displayMetrics.density, context.getColor(R.color.elite_text_secondary))
            }
        }
}

/**
 * L'anteprima del selettore di colore: un punteggio di esempio e il nome della squadra sul colore
 * che si sta muovendo, con l'inchiostro che avranno davvero. Cosi' l'utente vede come si
 * leggera' il colore prima della partita, non a bordo campo.
 *
 * Il fondo del dialogo e' la superficie rialzata, come la scheda dello storico: il contorno si decide contro quella.
 */
fun TextView.anteprimaDiSquadra(
    colore: Int,
    nome: String,
) {
    etichettaDiSquadra(colore, context.getColor(R.color.elite_surface_raised))
    text = context.getString(R.string.color_preview_text, nome)
}

/** Sotto questo contrasto una grafica non si stacca dal fondo (WCAG 1.4.11). */
private const val CONTRASTO_GRAFICA = 3.0
