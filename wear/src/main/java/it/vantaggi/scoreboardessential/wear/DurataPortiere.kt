package it.vantaggi.scoreboardessential.wear

import android.content.Context

/**
 * Le durate del portiere fra cui si sceglie dal polso: sul tondo non si scrive un numero, si sceglie
 * da una lista corta. Il cambio si fa ogni 4-10 minuti, quindi la lista va a minuti; 0 e' Off (niente
 * conto del portiere). La durata in uso c'e' sempre, anche se dal telefono e' stata scritta un'altra.
 */
object DurataPortiere {
    private val SCELTE = listOf(0L, 60L, 120L, 180L, 240L, 300L, 360L, 420L, 480L, 540L, 600L, 720L, 900L, 1200L, 1800L)

    fun scelte(attuale: Long): List<Long> = (SCELTE + attuale).filter { it >= 0 }.distinct().sorted()
}

/** "Off", "45 s", "2 min", "1 min 30 s". */
internal fun etichettaPortiere(
    context: Context,
    secondi: Long,
): String {
    if (secondi <= 0) return context.getString(R.string.wear_keeper_off)
    val minuti = (secondi / 60).toInt()
    val resto = (secondi % 60).toInt()
    return when {
        minuti == 0 -> context.getString(R.string.wear_keeper_seconds, resto)
        resto == 0 -> context.getString(R.string.wear_keeper_minutes, minuti)
        else -> context.getString(R.string.wear_keeper_minutes_seconds, minuti, resto)
    }
}
