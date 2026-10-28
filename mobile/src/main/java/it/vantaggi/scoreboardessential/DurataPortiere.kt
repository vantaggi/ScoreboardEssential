package it.vantaggi.scoreboardessential

/**
 * La durata del portiere come la scrive chi gioca: in minuti ("5", "4,5"), mentre le impostazioni la
 * tengono in secondi. Zero secondi vuol dire portiere spento.
 */
object DurataPortiere {
    /** Il cambio portiere di tutti i giorni: cinque minuti. */
    const val PREDEFINITA_SECONDI = 300L

    private const val MINIMO_SECONDI = 5L
    private const val MASSIMO_SECONDI = 3600L

    /** 300 -> "5", 270 -> "4,5", 90 -> "1,5". */
    fun minutiDi(secondi: Long): String {
        val interi = secondi / 60
        val resto = secondi % 60
        return if (resto == 0L) {
            interi.toString()
        } else {
            val decimali = (resto * 100 / 60).toString().padStart(2, '0').trimEnd('0')
            "$interi,$decimali"
        }
    }

    /** I minuti scritti (virgola o punto) in secondi; null se non e' un numero o e' fuori da 5 s - 60 min. */
    fun secondiDi(testo: String): Long? {
        val minuti = testo.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val secondi = Math.round(minuti * 60)
        return if (secondi in MINIMO_SECONDI..MASSIMO_SECONDI) secondi else null
    }
}
