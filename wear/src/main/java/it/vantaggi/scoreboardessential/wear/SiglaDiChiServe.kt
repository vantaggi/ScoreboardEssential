package it.vantaggi.scoreboardessential.wear

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView

/** Quanto resta la sigla di chi serve a fine game, come sul telefono. */
internal const val DURATA_SIGLA_DI_CHI_SERVE_MS = 3_000L

/**
 * A fine game la sigla di chi serve ("ROS") prende per [durataMs] il posto dei pallini, dalla parte
 * di chi serve, poi tornano i pallini. Lo scambio e' una dissolvenza in duration_fast, secca col
 * movimento ridotto. Funzione sulle viste e non sull'Activity: il test la chiama sul layout gonfiato.
 */
internal fun mostraLaSigla(
    radice: View,
    lato: Int,
    sigla: String,
    durataMs: Long = DURATA_SIGLA_DI_CHI_SERVE_MS,
) {
    togliLaSigla(radice, subito = true)
    val (pallini, testo) = viste(radice, lato) ?: return
    testo.text = sigla
    testo.contentDescription = radice.context.getString(R.string.cd_serve_next, sigla)
    testo.visibility = View.VISIBLE
    if (MovimentoRidotto.attivo(radice.context)) {
        testo.alpha = 1f
        pallini.forEach { it.alpha = 0f }
    } else {
        val durata = radice.resources.getInteger(R.integer.duration_fast).toLong()
        val curva = AnimationUtils.loadInterpolator(radice.context, R.interpolator.ease_standard)
        testo.alpha = 0f
        testo
            .animate()
            .alpha(1f)
            .setDuration(durata)
            .setInterpolator(curva)
        pallini.forEach {
            it
                .animate()
                .alpha(0f)
                .setDuration(durata)
                .setInterpolator(curva)
        }
    }
    val fine = Runnable { togliLaSigla(radice, subito = false) }
    // Dal looper principale e non dalla vista: una vista non agganciata tiene i post in coda.
    val principale = Handler(Looper.getMainLooper())
    testo.setTag(R.id.team1ServeNext, principale to fine)
    principale.postDelayed(fine, durataMs)
}

/** Rimette i pallini al posto delle sigle: con la dissolvenza alla fine dei tre secondi, [subito] se interrotta. */
internal fun togliLaSigla(
    radice: View,
    subito: Boolean = true,
) {
    listOf(1, 2).forEach { lato ->
        val (pallini, testo) = viste(radice, lato) ?: return@forEach
        (testo.getTag(R.id.team1ServeNext) as? Pair<*, *>)?.let { (gestore, fine) ->
            (gestore as? Handler)?.removeCallbacks(fine as? Runnable ?: return@let)
        }
        testo.setTag(R.id.team1ServeNext, null)
        if (testo.visibility != View.VISIBLE) return@forEach
        testo.animate().cancel()
        pallini.forEach { it.animate().cancel() }
        if (subito || MovimentoRidotto.attivo(radice.context)) {
            testo.visibility = View.GONE
            pallini.forEach { it.alpha = 1f }
        } else {
            val durata = radice.resources.getInteger(R.integer.duration_fast).toLong()
            val curva = AnimationUtils.loadInterpolator(radice.context, R.interpolator.ease_standard)
            pallini.forEach {
                it
                    .animate()
                    .alpha(1f)
                    .setDuration(durata)
                    .setInterpolator(curva)
            }
            testo
                .animate()
                .alpha(0f)
                .setDuration(durata)
                .setInterpolator(curva)
                .withEndAction { testo.visibility = View.GONE }
        }
    }
}

private fun viste(
    radice: View,
    lato: Int,
): Pair<List<View>, TextView>? {
    val ids =
        if (lato == 2) {
            Triple(R.id.team2ServeDot, R.id.team2ServeDotSecond, R.id.team2ServeNext)
        } else {
            Triple(R.id.team1ServeDot, R.id.team1ServeDotSecond, R.id.team1ServeNext)
        }
    val testo = radice.findViewById<TextView>(ids.third) ?: return null
    val pallini = listOfNotNull(radice.findViewById<View>(ids.first), radice.findViewById(ids.second))
    return pallini to testo
}
