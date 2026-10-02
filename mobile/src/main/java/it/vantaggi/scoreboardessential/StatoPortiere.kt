package it.vantaggi.scoreboardessential

import android.content.Context

/**
 * Che cosa dice lo slot del portiere. SCADUTO viene dall'evento di scadenza del service e dura
 * finche' qualcuno non tocca o non azzera: non si deduce dal conto a zero, che e' lo stesso di una
 * pausa (L8).
 */
internal enum class StatoPortiere { FERMO, IN_CORSO, SCADUTO }

/** Un conto in corso e' sempre IN_CORSO: ripartire cancella anche un SCADUTO rimasto. */
internal fun statoDelPortiere(
    inCorso: Boolean,
    scaduto: Boolean,
): StatoPortiere =
    when {
        inCorso -> StatoPortiere.IN_CORSO
        scaduto -> StatoPortiere.SCADUTO
        else -> StatoPortiere.FERMO
    }

/** Lo stato e l'azione del tocco, per TalkBack: lo slot non ha altro testo che il conto. */
internal fun descrizioneDelPortiere(
    context: Context,
    stato: StatoPortiere,
    conto: String,
): String =
    when (stato) {
        StatoPortiere.FERMO -> context.getString(R.string.cd_keeper_stopped, conto)
        StatoPortiere.IN_CORSO -> context.getString(R.string.cd_keeper_running, conto)
        StatoPortiere.SCADUTO -> context.getString(R.string.cd_keeper_expired)
    }

/**
 * Il tocco parte sempre dalla durata piena, mai da un residuo: davanti a un residuo il service
 * riprenderebbe da li'. Quindi un conto in corso (il cambio e' avvenuto) o fermo a meta', in pausa
 * dall'orologio ([residuo] fra 0 e [durata], esclusi), va azzerato prima di ripartire. Da fermo
 * a durata piena, o scaduto a zero, non c'e' niente da azzerare.
 */
internal fun toccoDelPortiereAzzera(
    inCorso: Boolean,
    residuo: Long,
    durata: Long,
): Boolean = inCorso || residuo in 1 until durata
