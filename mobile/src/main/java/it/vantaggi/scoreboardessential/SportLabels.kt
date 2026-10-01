package it.vantaggi.scoreboardessential

import android.content.Context
import it.vantaggi.scoreboardessential.core.ClockMode
import it.vantaggi.scoreboardessential.core.DeuceRule
import it.vantaggi.scoreboardessential.core.RacketRules
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

/**
 * La riga delle regole che il motore applica, per sapere PRIMA del primo 40-40 cosa succede: «Set
 * unico · punto secco sul 40-40 · tie-break a 7», «Cronometro · cambio portiere».
 *
 * Si compone da [SportRegistry] e dalle capacita' dello sport, non da un testo per sport: la riga
 * non puo' dire una regola diversa da quella che il motore arbitra, e un quarto sport con
 * racchetta la ha gia' scritta. Le parole sono risorse, i numeri e i simboli no.
 */
fun sportRulesLine(
    context: Context,
    sportId: String,
): String {
    val regole = SportRegistry.byId(sportId)
    val pezzi = mutableListOf<String>()
    if (regole is RacketRules) {
        val config = regole.config
        pezzi.add(
            if (config.sets <= 1) {
                context.getString(R.string.rules_single_set)
            } else {
                context.resources.getQuantityString(R.plurals.rules_best_of, config.sets, config.sets)
            },
        )
        pezzi.add(
            context.getString(
                when (config.deuce) {
                    DeuceRule.GOLDEN_POINT -> R.string.rules_golden_point
                    DeuceRule.KILLER_POINT -> R.string.rules_killer_point
                    DeuceRule.ADVANTAGE -> R.string.rules_advantage
                },
            ),
        )
        if (config.tieBreak) pezzi.add(context.getString(R.string.rules_tiebreak_to, config.tieBreakTo))
    } else {
        if (regole.capabilities.clock == ClockMode.COUNT_UP) pezzi.add(context.getString(R.string.rules_stopwatch))
        if (regole.capabilities.hasAuxCountdown) pezzi.add(context.getString(R.string.rules_keeper_change))
    }
    return pezzi.joinToString(" · ")
}
