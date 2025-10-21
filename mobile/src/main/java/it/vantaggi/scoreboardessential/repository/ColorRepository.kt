package it.vantaggi.scoreboardessential.repository

import android.content.Context
import androidx.core.content.ContextCompat
import it.vantaggi.scoreboardessential.R

class ColorRepository(
    private val context: Context,
) {
    fun getTeam1DefaultColor(): Int = ContextCompat.getColor(context, R.color.team_spray_yellow)

    fun getTeam2DefaultColor(): Int = ContextCompat.getColor(context, R.color.team_electric_green)
}
