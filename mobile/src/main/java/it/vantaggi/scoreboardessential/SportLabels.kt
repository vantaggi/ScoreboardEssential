package it.vantaggi.scoreboardessential

import android.content.Context
import it.vantaggi.scoreboardessential.core.SportRegistry

/**
 * Il nome leggibile di uno sport. Uno sport senza etichetta ripiega sul proprio id.
 *
 * Il registro delle regole vive in :core e puo' guadagnare uno sport prima che ne arrivi la
 * traduzione: in quel caso si legge "volley" invece di far crashare la schermata.
 *
 * Sta qui e non dentro una schermata perche' ora lo usano in due: le impostazioni, che lo mettono
 * nel menu a tendina, e [MainViewModel], che lo spedisce all'orologio -- il quale non puo'
 * tradurre niente, non conoscendo :core.
 */
fun sportLabel(
    context: Context,
    sportId: String,
): String =
    when (sportId) {
        SportRegistry.FOOTBALL -> context.getString(R.string.sport_football)
        SportRegistry.PADEL -> context.getString(R.string.sport_padel)
        SportRegistry.TENNIS -> context.getString(R.string.sport_tennis)
        else -> sportId
    }
