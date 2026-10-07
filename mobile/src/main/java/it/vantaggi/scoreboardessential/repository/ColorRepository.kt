package it.vantaggi.scoreboardessential.repository

import android.content.Context
import androidx.core.content.ContextCompat
import it.vantaggi.scoreboardessential.R

/**
 * I colori predefiniti delle due squadre: lime (lato 1) e ciano (lato 2), come la Cronaca e la
 * dashboard di Padel Elite (UI Constitution, G-6). Valgono solo finche' l'utente non sceglie un
 * colore: le preferenze salvano la scelta e mai il predefinito, quindi chi non ha mai scelto passa ai
 * nuovi da solo e chi ha scelto (anche il vecchio giallo o verde) li tiene.
 */
class ColorRepository(
    private val context: Context,
) {
    fun getTeam1DefaultColor(): Int = ContextCompat.getColor(context, R.color.team_side_1)

    fun getTeam2DefaultColor(): Int = ContextCompat.getColor(context, R.color.team_side_2)
}
