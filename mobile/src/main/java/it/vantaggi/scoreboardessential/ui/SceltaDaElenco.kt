package it.vantaggi.scoreboardessential.ui

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import it.vantaggi.scoreboardessential.R

/**
 * Il foglio di una riga delle Impostazioni: le scelte una sotto l'altra, la spunta su quella in uso.
 * Toccarne una la sceglie e chiude; Annulla chiude senza scegliere.
 */
fun Context.scegliDaElenco(
    titolo: String,
    voci: List<String>,
    corrente: Int,
    allaScelta: (Int) -> Unit,
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(titolo)
        .setSingleChoiceItems(voci.toTypedArray(), corrente) { dialogo, posizione ->
            dialogo.dismiss()
            allaScelta(posizione)
        }.setNegativeButton(getString(R.string.cancel), null)
        .show()
}
