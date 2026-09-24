package it.vantaggi.scoreboardessential.utils

import android.content.res.ColorStateList
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
 */
fun TextView.etichettaDiSquadra(colore: Int) {
    val forma = ShapeAppearanceModel.builder(context, R.style.ShapeAppearance_App_StreetBadge, 0).build()
    background = MaterialShapeDrawable(forma).apply { fillColor = ColorStateList.valueOf(colore) }
    setTextColor(TeamInk.on(colore))
}
