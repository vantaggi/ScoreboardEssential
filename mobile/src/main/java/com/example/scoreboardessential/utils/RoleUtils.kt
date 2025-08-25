package com.example.scoreboardessential.utils

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.scoreboardessential.R

object RoleUtils {

    fun getCategoryColor(context: Context, category: String): Int {
        return when (category.uppercase()) {
            "ATTACCO" -> ContextCompat.getColor(context, R.color.role_attack)
            "CENTROCAMPO" -> ContextCompat.getColor(context, R.color.role_midfield)
            "DIFESA" -> ContextCompat.getColor(context, R.color.role_defense)
            "PORTA" -> ContextCompat.getColor(context, R.color.role_goalkeeper)
            else -> ContextCompat.getColor(context, R.color.on_surface_variant_light)
        }
    }

    fun getRoleAbbreviation(roleName: String): String {
        return when (roleName) {
            // PORTA
            "Portiere" -> "POR"

            // DIFESA
            "Difensore Centrale" -> "DC"
            "Terzino Sinistro" -> "TS"
            "Terzino Destro" -> "TD"
            "Libero" -> "LIB"

            // CENTROCAMPO
            "Mediano" -> "MED"
            "Centrocampista Centrale" -> "CC"
            "Trequartista" -> "TRQ"
            "Esterno Sinistro" -> "ES"
            "Esterno Destro" -> "ED"

            // ATTACCO
            "Ala Sinistra" -> "AS"
            "Ala Destra" -> "AD"
            "Seconda Punta" -> "SP"
            "Centravanti" -> "ATT"

            else -> {
                // Fallback: prendi le prime 2-3 lettere maiuscole
                val words = roleName.split(" ")
                if (words.size > 1) {
                    words.map { it.first().uppercase() }.take(3).joinToString("")
                } else {
                    roleName.take(3).uppercase()
                }
            }
        }
    }
}
