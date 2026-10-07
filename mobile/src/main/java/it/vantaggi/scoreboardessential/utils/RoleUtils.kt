package it.vantaggi.scoreboardessential.utils

/**
 * I ruoli si distinguono per la sigla e per il nome, mai per il colore: la categoria non ha piu' una
 * tinta (rosa, ciano, giallo e verde erano colori Street, e il colore da solo non dice un ruolo).
 */
object RoleUtils {
    fun getRoleAbbreviation(roleName: String): String =
        when (roleName) {
            // PORTA
            "Portiere" -> {
                "POR"
            }

            // DIFESA
            "Difensore Centrale" -> {
                "DC"
            }

            "Terzino Sinistro" -> {
                "TS"
            }

            "Terzino Destro" -> {
                "TD"
            }

            "Libero" -> {
                "LIB"
            }

            // CENTROCAMPO
            "Mediano" -> {
                "MED"
            }

            "Centrocampista Centrale" -> {
                "CC"
            }

            "Trequartista" -> {
                "TRQ"
            }

            "Esterno Sinistro" -> {
                "ES"
            }

            "Esterno Destro" -> {
                "ED"
            }

            // ATTACCO
            "Ala Sinistra" -> {
                "AS"
            }

            "Ala Destra" -> {
                "AD"
            }

            "Seconda Punta" -> {
                "SP"
            }

            "Centravanti" -> {
                "ATT"
            }

            else -> {
                // Fallback: prendi le prime 2-3 lettere maiuscole
                val words = roleName.split(" ").filter { it.isNotEmpty() }
                if (words.size > 1) {
                    words.map { it.first().uppercase() }.take(3).joinToString("")
                } else {
                    roleName.trim().take(3).uppercase()
                }
            }
        }
}
