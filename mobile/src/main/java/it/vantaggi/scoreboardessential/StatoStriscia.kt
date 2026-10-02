package it.vantaggi.scoreboardessential

import android.content.Context
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.ScoringEvent
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
 * @property terminaPartita a partita finita il tocco apre il dialogo di fine partita: la striscia
 *   e' il comando che conclude, e sta lontana dalle zone + dove il pollice ha appena segnato.
 */
internal data class StatoStriscia(
    val testo: String,
    val daAttribuire: MatchEvent? = null,
    val terminaPartita: Boolean = false,
)

/**
 * Il testo base della striscia, dall'ultima azione del registro (il registro e' in ordine
 * cronologico inverso, quindi il primo SCORE e' l'ultimo segnato).
 *
 * Nel calcio e' «GOL ROSSI · MARCO B.», oppure «GOL ROSSI · CHI HA SEGNATO? ›» quando il marcatore
 * manca: in quel caso il gol e' anche [StatoStriscia.daAttribuire], la stessa cosa che apre la riga
 * del registro. Se il marcatore non e' noto e non c'e' niente da attribuire resta «GOL ROSSI».
 * Negli sport senza marcatore e' «PUNTO ROSSI · 40-30», con i primari del display, oppure
 * «GAME ROSSI · 5-3» al punto che chiude un game (i primari sono gia' tornati a 0-0).
 * Senza punti: «NESSUN GOL» o «NESSUN PUNTO». Se dopo l'ultimo gol c'e' una correzione (il -1), la
 * striscia dice la correzione e non si tocca: il gol tolto non e' piu' da attribuire. Dopo un
 * ANNULLA il punto tolto non e' piu' nel registro, quindi la striscia torna da sola al precedente.
 * Sta fuori dall'Activity perche' sotto Robolectric MainActivity non si monta: cosi' il testo si
 * prova da solo.
 *
 * A partita dichiarata finita (display.matchOver) il testo base e' sempre «PARTITA FINITA ·
 * TERMINA ›», qualunque sia l'ultima azione, e la striscia apre il dialogo di fine partita.
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
    if (display?.matchOver == true) {
        return StatoStriscia(context.getString(R.string.strip_match_over_end), terminaPartita = true)
    }
    val conMarcatore = capacita?.attributesScorer != false
    val registro = eventi.orEmpty()
    // Una correzione dopo l'ultimo gol e' l'ultima azione: il gol tolto dal -1 non e' piu' quello
    // che si vede, e attribuirlo darebbe un gol a un giocatore in una partita 0-0. Gli avvii e le
    // altre righe senza indice del motore non sono azioni di punteggio e non contano.
    val ultimaDelMotore = registro.firstOrNull { it.engineIndex != null }
    if (ultimaDelMotore != null && ultimaDelMotore.type != MatchEventType.SCORE) {
        val nome = (if (ultimaDelMotore.team == 2) nomeSquadra2 else nomeSquadra1).maiuscolo()
        return StatoStriscia(context.getString(R.string.strip_msg_correction, nome))
    }
    val ultimo = registro.firstOrNull { it.type == MatchEventType.SCORE }
    if (ultimo == null) {
        return StatoStriscia(context.getString(if (conMarcatore) R.string.strip_none_goal else R.string.strip_none_point))
    }
    val nomeDellaSquadra = if (ultimo.team == 2) nomeSquadra2 else nomeSquadra1
    val squadra = nomeDellaSquadra.maiuscolo()
    if (!conMarcatore) return StatoStriscia(testoDelPuntoARacchetta(context, squadra, display))
    // Il marcatore e' noto solo se c'e' un nome che non sia quello della squadra: senza marcatore, o
    // con un giocatore uscito dalla rosa, il registro scrive il nome della squadra al suo posto.
    val marcatore = ultimo.player?.takeIf { it.isNotBlank() && !it.equals(nomeDellaSquadra, ignoreCase = true) }
    if (ultimo.playerId != null) return StatoStriscia(golDi(context, squadra, marcatore))
    // Senza indice del motore il gol non si puo' attribuire: il registro non lo offre, e nemmeno la striscia.
    val daAttribuire = ultimo.takeIf { it.engineIndex != null }
    val testo =
        if (daAttribuire != null) {
            context.getString(R.string.strip_goal_unattributed, squadra)
        } else {
            golDi(context, squadra, marcatore)
        }
    return StatoStriscia(testo, daAttribuire)
}

/** «GOL ROSSI · MARCO B.», o solo «GOL ROSSI» quando il marcatore non e' noto. */
private fun golDi(
    context: Context,
    squadra: String,
    marcatore: String?,
): String =
    if (marcatore == null) {
        context.getString(R.string.strip_goal, squadra)
    } else {
        context.getString(R.string.strip_goal_by, squadra, marcatore.maiuscolo())
    }

/**
 * «PUNTO ROSSI · 40-30» con i primari del display. Al punto che chiude un game i primari tornano a
 * 0-0: mostrarli direbbe «PUNTO ROSSI · 0-0», quindi si dice il game appena chiuso, «GAME ROSSI ·
 * 5-3», dai secondari. Un punto che lascia i primari a 0-0 ha chiuso un game, e i game del set in
 * corso possono essere 0-0 solo se quel game ha chiuso il set: allora il game e' l'ultimo dei set
 * chiusi, il penultimo dei secondari.
 */
private fun testoDelPuntoARacchetta(
    context: Context,
    squadra: String,
    display: ScoreDisplay?,
): String {
    val game = display?.takeIf { it.side1Primary == "0" && it.side2Primary == "0" }?.let(::ultimoGameChiuso)
    return if (game != null) {
        context.getString(R.string.strip_game, squadra, game)
    } else {
        val primari = display?.let { "${it.side1Primary}-${it.side2Primary}" }.orEmpty()
        context.getString(R.string.strip_point, squadra, primari)
    }
}

private fun ultimoGameChiuso(display: ScoreDisplay): String? {
    val parti = display.side1Secondary?.split(SEPARATORE_DEI_SET).orEmpty()
    val ultima = parti.lastOrNull() ?: return null
    return if (ultima == "0-0" && parti.size > 1) parti[parti.lastIndex - 1] else ultima
}

// Il separatore dei set nei secondari di :core (RacketRules.SEPARATOR, privato): middot fra spazi.
internal const val SEPARATORE_DEI_SET = " · "

private fun String.maiuscolo(): String = uppercase(Locale.getDefault())

/**
 * ANNULLA chiede conferma solo dove l'azione da annullare puo' essere un gol con un marcatore da
 * perdere: nel calcio. Nel padel e nel tennis e' un tocco solo (DESIGN.md, Decisioni prese,
 * Telefono 3): un punto si rimette con un altro tocco. Le capacita' non ancora arrivate valgono il
 * calcio, come per ANNULLA.
 */
internal fun annullaChiedeConferma(capacita: SportCapabilities?): Boolean = capacita?.attributesScorer != false

/** Il tempo entro cui un secondo tocco su ANNULLA e' un rimbalzo del dito e non una scelta. */
internal const val FINESTRA_TOCCHI_ANNULLA_MS = 500L

/**
 * Nel padel e nel tennis ANNULLA e' un tocco solo, senza il dialogo che faceva da filtro: due tocchi
 * rapidi toglievano due punti. Il secondo entro [FINESTRA_TOCCHI_ANNULLA_MS] dal precedente si ignora.
 * [precedente] e' l'istante dell'ultimo tocco accettato, null se non ce ne sono stati.
 */
internal fun toccoAnnullaRipetuto(
    adesso: Long,
    precedente: Long?,
): Boolean = precedente != null && adesso - precedente < FINESTRA_TOCCHI_ANNULLA_MS

/**
 * Il messaggio di 3 secondi dopo ANNULLA: cosa e' stato tolto e a chi, «ANNULLATO: PUNTO ROSSI».
 * Una correzione tolta (il -1 del calcio) e' una «CORREZIONE», non un gol.
 */
internal fun testoDellAnnullamento(
    context: Context,
    tolto: ScoringEvent,
    capacita: SportCapabilities?,
    nomeSquadra1: String,
    nomeSquadra2: String,
): String {
    val squadra = (if (tolto.side == 2) nomeSquadra2 else nomeSquadra1).maiuscolo()
    val formato =
        when {
            tolto is ScoringEvent.Correction -> R.string.strip_msg_undone_correction
            capacita?.attributesScorer != false -> R.string.strip_msg_undone_goal
            else -> R.string.strip_msg_undone_point
        }
    return context.getString(formato, squadra)
}
