package com.example.scoreboardessential.repository

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.scoreboardessential.R

class ColorRepository(private val context: Context) {

    fun getTeam1DefaultColor(): Int {
        return ContextCompat.getColor(context, R.color.vibrant_orange)
    }

    fun getTeam2DefaultColor(): Int {
        return ContextCompat.getColor(context, R.color.electric_lime)
    }
}
