package it.vantaggi.scoreboardessential

import android.content.Context
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportCapabilities
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType

/**
 * Cosa dice la striscia dell'ultima azione e cosa succede se la si tocca.
 *
 * @property testo il testo base, su una riga, o su due quando chiede il marcatore («Gol Rossi» sopra,
 *   «Chi ha segnato? ›» sotto: bozza A1 di Cioti, perche' su una riga a 360dp si tagliava). Cambia il
 *   testo, mai l'altezza della striscia: due righe a 16dp stanno nei 56dp.
 * @property daAttribuire il gol senza marcatore che il tocco apre nel dialogo, o null se la
 *   striscia non offre niente da toccare.
 * @property terminaPartita a partita finita il tocco apre il dialogo di fine partita: la striscia
 *   e' il comando che conclude, e sta lontana dalle zone + dove il pollice ha appena segnato.
 * @property sceltaDelServizio a inizio set la striscia propone chi serve («Serve ROS · ROS/BIA», a
 *   capo, «Cambia ›») e il tocco apre la scelta. Se la proposta va bene basta segnare: col primo
 *   punto del set la striscia torna a dire l'ultima azione.
 */
internal data class StatoStriscia(
    val testo: String,
    val daAttribuire: MatchEvent? = null,
    val terminaPartita: Boolean = false,
    val sceltaDelServizio: Boolean = false,
) {
    /** Quante righe ha il testo: la vista le mostra tutte, e non di piu'. */
    val righe: Int get() = if ('\n' in testo) 2 else 1
}

/**
 * Il testo base della striscia, dall'ultima azione del registro (il registro e' in ordine
 * cronologico inverso, quindi il primo SCORE e' l'ultimo segnato).
 *
 * Nel calcio e' «Gol Rossi · Marco B.», oppure «Gol Rossi», a capo, «Chi ha segnato? ›» quando il
 * marcatore manca: in quel caso il gol e' anche [StatoStriscia.daAttribuire], la stessa cosa che apre la riga
 * del registro. Se il marcatore non e' noto e non c'e' niente da attribuire resta «Gol Rossi».
 * Negli sport senza marcatore e' «Punto Rossi · 40-30», con i primari del display, oppure
 * «Game Rossi · 5-3» al punto che chiude un game (i primari sono gia' tornati a 0-0).
 * Senza punti: «Nessun gol» o «Nessun punto». Se dopo l'ultimo gol c'e' una correzione (il -1), la
 * striscia dice la correzione e non si tocca: il gol tolto non e' piu' da attribuire. Dopo un
 * ANNULLA il punto tolto non e' piu' nel registro, quindi la striscia torna da sola al precedente.
 * Sta fuori dall'Activity perche' sotto Robolectric MainActivity non si monta: cosi' il testo si
 * prova da solo.
 *
 * A partita dichiarata finita (display.matchOver) il testo base e' sempre «Partita finita ·
 * termina ›», qualunque sia l'ultima azione, e la striscia apre il dialogo di fine partita.
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
    giocatori1: List<String> = emptyList(),
    giocatori2: List<String> = emptyList(),
): StatoStriscia {
    if (display?.matchOver == true) {
        return StatoStriscia(context.getString(R.string.strip_match_over_end), terminaPartita = true)
    }
    if (display != null && inizioDelSet(display)) {
        val coppia = if (display.servingSide == 2) nomeSquadra2 else nomeSquadra1
        val sigla = siglaDiChiServe(display, giocatori1, giocatori2)
        val testo =
            if (sigla == null) {
                context.getString(R.string.strip_serve_proposal_team, coppia)
            } else {
                context.getString(R.string.strip_serve_proposal, sigla, coppia)
            }
        return StatoStriscia(testo, sceltaDelServizio = true)
    }
    val conMarcatore = capacita?.attributesScorer != false
    val registro = eventi.orEmpty()
    // Una correzione dopo l'ultimo gol e' l'ultima azione: il gol tolto dal -1 non e' piu' quello
    // che si vede, e attribuirlo darebbe un gol a un giocatore in una partita 0-0. Gli avvii e le
    // altre righe senza indice del motore non sono azioni di punteggio e non contano.
    val ultimaDelMotore = registro.firstOrNull { it.engineIndex != null }
    // Una correzione di chi serve a set in corso: la striscia dice chi serve adesso.
    if (ultimaDelMotore?.type == MatchEventType.SERVE && display != null) {
        val chi =
            siglaDiChiServe(display, giocatori1, giocatori2)
                ?: if (display.servingSide == 2) nomeSquadra2 else nomeSquadra1
        return StatoStriscia(context.getString(R.string.strip_msg_serve, chi))
    }
    if (ultimaDelMotore != null && ultimaDelMotore.type != MatchEventType.SCORE) {
        val nome = if (ultimaDelMotore.team == 2) nomeSquadra2 else nomeSquadra1
        return StatoStriscia(context.getString(R.string.strip_msg_correction, nome))
    }
    val ultimo = registro.firstOrNull { it.type == MatchEventType.SCORE }
    if (ultimo == null) {
        return StatoStriscia(context.getString(if (conMarcatore) R.string.strip_none_goal else R.string.strip_none_point))
    }
    val nomeDellaSquadra = if (ultimo.team == 2) nomeSquadra2 else nomeSquadra1
    val squadra = nomeDellaSquadra
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

/**
 * Siamo al primo punto di un set, in uno sport che modella il servizio: i punti del game sono 0-0 e
 * cosi' i game del set in corso (l'ultima parte dei secondari, che manca prima del primo game). Nel
 * tie-break i punti ripartono da 0-0 ma i game sono 6-6, quindi non e' un inizio di set.
 */
internal fun inizioDelSet(display: ScoreDisplay): Boolean {
    if (display.matchOver || display.servingSide == null) return false
    if (display.side1Primary != "0" || display.side2Primary != "0") return false
    val gameDelSet = display.side1Secondary?.split(SEPARATORE_DEI_SET)?.lastOrNull() ?: return true
    return gameDelSet == "0-0"
}

/**
 * Le prime tre lettere del cognome di chi serve, come nel resto dell'app quando si accorcia, o null
 * se non si sa chi e' (nessuna rosa, o una rosa piu' corta del giocatore che serve). Le rose sono
 * nell'ordine del servizio: il primo giocatore di ciascuna e' il primo che serve. Le sigle si
 * distinguono su tutti e quattro, come nei nomi delle coppie.
 */
internal fun siglaDiChiServe(
    display: ScoreDisplay,
    giocatori1: List<String>,
    giocatori2: List<String>,
): String? {
    val lato = display.servingSide ?: return null
    val squadra = if (lato == 2) giocatori2 else giocatori1
    val indice = if (squadra.size >= 2) (display.servingPlayerSlot ?: 1) - 1 else 0
    if (indice !in squadra.indices) return null
    val sigle = EtichettaSquadra.sigle(giocatori1 + giocatori2)
    val posizione = (if (lato == 2) giocatori1.size else 0) + indice
    return sigle.getOrNull(posizione)?.takeIf { it.isNotEmpty() }
}

/** «Gol Rossi · Marco B.», o solo «Gol Rossi» quando il marcatore non e' noto. */
private fun golDi(
    context: Context,
    squadra: String,
    marcatore: String?,
): String =
    if (marcatore == null) {
        context.getString(R.string.strip_goal, squadra)
    } else {
        context.getString(R.string.strip_goal_by, squadra, marcatore)
    }

/**
 * «Punto Rossi · 40-30» con i primari del display. Al punto che chiude un game i primari tornano a
 * 0-0: mostrarli direbbe «Punto Rossi · 0-0», quindi si dice il game appena chiuso, «Game Rossi ·
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
 * Il messaggio di 3 secondi dopo ANNULLA: cosa e' stato tolto e a chi, «Annullato: punto Rossi».
 * Una correzione tolta (il -1 del calcio) e' una «correzione», non un gol.
 */
internal fun testoDellAnnullamento(
    context: Context,
    tolto: ScoringEvent,
    capacita: SportCapabilities?,
    nomeSquadra1: String,
    nomeSquadra2: String,
): String {
    val squadra = if (tolto.side == 2) nomeSquadra2 else nomeSquadra1
    val formato =
        when {
            tolto is ScoringEvent.Correction -> R.string.strip_msg_undone_correction
            tolto is ScoringEvent.ServeChoice -> R.string.strip_msg_undone_serve
            capacita?.attributesScorer != false -> R.string.strip_msg_undone_goal
            else -> R.string.strip_msg_undone_point
        }
    return context.getString(formato, squadra)
}
