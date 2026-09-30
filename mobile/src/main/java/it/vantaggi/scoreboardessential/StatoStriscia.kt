package it.vantaggi.scoreboardessential

import android.content.Context
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.SportCapabilities
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import java.util.Locale

/**
 * Cosa dice la striscia dell'ultima azione e cosa succede se la si tocca.
 *
 * @property testo il testo base, su una riga. Cambia il testo, mai l'altezza della striscia.
 * @property daAttribuire il gol senza marcatore che il tocco apre nel dialogo, o null se la
 *   striscia non offre niente da toccare.
 */
internal data class StatoStriscia(
    val testo: String,
    val daAttribuire: MatchEvent? = null,
)

/**
 * Il testo base della striscia, dal primo punto del registro (il registro e' in ordine cronologico
 * inverso, quindi il primo SCORE e' l'ultimo segnato).
 *
 * Nel calcio e' «GOL ROSSI · MARCO B.», oppure «GOL ROSSI · CHI HA SEGNATO? ›» quando il marcatore
 * manca: in quel caso il gol e' anche [StatoStriscia.daAttribuire], la stessa cosa che apre la riga
 * del registro. Negli sport senza marcatore e' «PUNTO ROSSI · 40-30», con i primari del display.
 * Senza punti: «NESSUN GOL» o «NESSUN PUNTO». Dopo un ANNULLA il punto tolto non e' piu' nel
 * registro, quindi la striscia torna da sola al precedente. Sta fuori dall'Activity perche' sotto
 * Robolectric MainActivity non si monta: cosi' il testo si prova da solo.
 *
 * Le capacita' non ancora arrivate valgono il calcio, come per ANNULLA.
 */
internal fun statoDellaStriscia(
    context: Context,
    eventi: List<MatchEvent>?,
    capacita: SportCapabilities?,
    display: ScoreDisplay?,
    nomeSquadra1: String,
    nomeSquadra2: String,
): StatoStriscia {
    val conMarcatore = capacita?.attributesScorer != false
    val ultimo = eventi.orEmpty().firstOrNull { it.type == MatchEventType.SCORE }
    if (ultimo == null) {
        return StatoStriscia(context.getString(if (conMarcatore) R.string.strip_none_goal else R.string.strip_none_point))
    }
    val squadra = (if (ultimo.team == 2) nomeSquadra2 else nomeSquadra1).maiuscolo()
    if (!conMarcatore) {
        val punteggio = display?.let { "${it.side1Primary}-${it.side2Primary}" }.orEmpty()
        return StatoStriscia(context.getString(R.string.strip_point, squadra, punteggio))
    }
    if (ultimo.playerId != null) {
        return StatoStriscia(context.getString(R.string.strip_goal_by, squadra, ultimo.player.orEmpty().maiuscolo()))
    }
    // Senza indice del motore il gol non si puo' attribuire: il registro non lo offre, e nemmeno la striscia.
    val daAttribuire = ultimo.takeIf { it.engineIndex != null }
    val testo =
        if (daAttribuire != null) {
            context.getString(R.string.strip_goal_unattributed, squadra)
        } else {
            context.getString(R.string.strip_goal_by, squadra, ultimo.player.orEmpty().maiuscolo())
        }
    return StatoStriscia(testo, daAttribuire)
}

private fun String.maiuscolo(): String = uppercase(Locale.getDefault())
