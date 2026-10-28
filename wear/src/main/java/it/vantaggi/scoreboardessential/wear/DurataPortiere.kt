package it.vantaggi.scoreboardessential.wear

import android.content.Context
import java.text.NumberFormat

/**
 * Le durate del portiere fra cui si sceglie dal polso: sul tondo non si scrive un numero, si sceglie
 * da una lista corta. Il cambio si fa ogni 4-10 minuti, quindi la lista va a minuti; 0 e' Off (niente
 * conto del portiere). La durata in uso c'e' sempre, anche se dal telefono e' stata scritta un'altra.
 */
object DurataPortiere {
    private val SCELTE = listOf(0L, 60L, 120L, 180L, 240L, 300L, 360L, 420L, 480L, 540L, 600L, 720L, 900L, 1200L, 1800L)

    /**
     * Sotto il minuto non si offre niente: i secondi di un tempo che non e' piu' il predefinito (30 s)
     * restavano in lista come voce in uso. Se il telefono ne ha uno, il menu lo mostra comunque come valore.
     */
    fun scelte(attuale: Long): List<Long> = (SCELTE + attuale).filter { it == 0L || it >= MINUTO }.distinct().sorted()

    private const val MINUTO = 60L
}

/**
 * "Off", "45 s", "2 min", "4,5 min", "1 min 20 s". Un decimo di minuto esatto si scrive coi decimali, come
 * nel campo del telefono: 4,5 scritto li' si leggeva "4 min 30 s" sul polso.
 */
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
        resto % SECONDI_PER_DECIMO == 0 -> {
            val formato = NumberFormat.getNumberInstance(context.resources.configuration.locales[0])
            formato.maximumFractionDigits = 1
            context.getString(R.string.wear_keeper_minutes_decimal, formato.format(secondi / 60.0))
        }
        else -> context.getString(R.string.wear_keeper_minutes_seconds, minuti, resto)
    }
}

private const val SECONDI_PER_DECIMO = 6
