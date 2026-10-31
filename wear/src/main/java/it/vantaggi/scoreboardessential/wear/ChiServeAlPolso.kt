package it.vantaggi.scoreboardessential.wear

import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.inizioDelSet

/*
 * Chi serve al polso (bozza A di Cioti, 31 ottobre 2026): a inizio set la capsula SERVE? apre la scelta
 * a tutto schermo, prima la coppia e poi, in ciascuna coppia, chi serve per primo; a partita in corso
 * la correzione sta nel menu; a fine game la sigla di chi serve prende per tre secondi il posto dei
 * pallini. Qui solo funzioni pure: le domande, le risposte e quando offrire, provate su JVM.
 */

/** Lo stato del telefono come lo legge :core: le stringhe vuote del protocollo sono i null del display. */
internal fun WearScoreState.comeDisplay(): ScoreDisplay =
    ScoreDisplay(
        side1Primary = side1Primary,
        side1Secondary = side1Secondary.ifEmpty { null },
        side2Primary = side2Primary,
        side2Secondary = side2Secondary.ifEmpty { null },
        periodLabel = periodLabel.ifEmpty { null },
        servingSide = servingSide.takeIf { it == 1 || it == 2 },
        servingPlayerSlot = servingSlot.takeIf { it == 1 || it == 2 },
        matchOver = matchOver,
    )

/**
 * Il polso offre SERVE?: siamo al primo punto di un set (la stessa domanda del telefono, [inizioDelSet])
 * e chi serve non e' ancora stato scelto per questo inizio, cioe' l'ultimo evento del registro non e'
 * una scelta. Fatta la scelta la capsula non torna, anche se il set non e' ancora cominciato.
 */
internal fun offreChiServe(stato: WearScoreState): Boolean {
    if (!inizioDelSet(stato.comeDisplay())) return false
    val ultimo = MatchLogCodec.decode(stato.eventLog)?.lastOrNull() ?: return true
    return ultimo.event !is ScoringEvent.ServeChoice
}

/**
 * Fra [prima] e [dopo] si e' chiuso un game: i punti ripartono da 0-0 e i game sono cambiati, nella
 * stessa partita e con qualcuno al servizio. Un annullamento che torna al game prima non lo e' (i punti
 * non sono 0-0), e nemmeno quello del primo punto di un game (i game non cambiano). E' il momento in
 * cui il telefono fa comparire la sigla di chi serve dopo.
 */
internal fun fineDelGame(
    prima: WearScoreState?,
    dopo: WearScoreState,
): Boolean {
    if (prima == null || dopo.matchOver || dopo.servingSide == 0) return false
    if (prima.matchUuid != dopo.matchUuid || prima.sportId != dopo.sportId) return false
    if (dopo.side1Primary != "0" || dopo.side2Primary != "0") return false
    return prima.side1Secondary != dopo.side1Secondary
}

/** Che cosa ha scelto chi tocca: la scelta di inizio set, o la correzione a partita in corso. */
sealed interface EsitoDelServizio {
    /** Serve la coppia [lato]; [slot1] e [slot2] sono chi serve per primo nella coppia 1 e nella 2. */
    data class Scelta(
        val lato: Int,
        val slot1: Int,
        val slot2: Int,
    ) : EsitoDelServizio

    /** Adesso serve il giocatore [slot] della coppia [lato]: l'altra coppia la decide il motore. */
    data class Correzione(
        val lato: Int,
        val slot: Int,
    ) : EsitoDelServizio
}

/** Una riga della scelta: il nome grande, la riga piccola sotto (vuota se non serve), la spunta. */
data class RigaDelServizio(
    val titolo: String,
    val dettaglio: String,
    val lato: Int,
    val slot: Int,
    val spunta: Boolean,
)

/** Quale domanda e' a schermo: il titolo lo traduce la schermata. */
sealed interface DomandaDelServizio {
    /** "Chi serve?": le due coppie. */
    data object Coppie : DomandaDelServizio

    /** "Chi serve per primo in ROS/BIA?": i due nomi della coppia [lato]. */
    data class Primo(
        val lato: Int,
    ) : DomandaDelServizio

    /** "Chi sta servendo adesso?": i quattro nomi, o le due coppie se i nomi non ci sono. */
    data object Correzione : DomandaDelServizio
}

/** I fatti della scelta, letti quando si apre. */
data class InputDelServizio(
    /** I nomi delle due coppie come li ha il quadrante ("ROS/BIA"). */
    val nomi: List<String>,
    /** Le due rose nell'ordine del servizio, dal telefono; vuote se non le manda. */
    val rose: List<List<String>>,
    /** La coppia che il tabellone da' al servizio adesso (1 o 2). */
    val lato: Int,
    /** Chi serve al prossimo turno di ciascuna coppia (1 o 2): la proposta. */
    val prossimi: List<Int>,
    /** Il giocatore che il tabellone da' al servizio adesso, 0 nel singolare. */
    val slotAttuale: Int,
    val correzione: Boolean,
) {
    /** Le due coppie hanno i loro due nomi: allora si sceglie anche il giocatore. */
    val conINomi: Boolean get() = rose.size == 2 && rose.all { it.size >= 2 } && slotAttuale != 0

    fun prossimo(lato: Int): Int = prossimi.getOrNull(lato - 1)?.takeIf { it == 1 || it == 2 } ?: 1
}

/**
 * La scelta a passi, come sul telefono: a inizio set la coppia e poi, se le coppie hanno i nomi, chi
 * serve per primo in ciascuna, a cominciare da quella che apre; la correzione e' una domanda sola.
 * Ogni domanda ha la spunta sulla proposta, cosi' chi non deve cambiare niente tocca la riga spuntata.
 */
class PassiDelServizio(
    private val input: InputDelServizio,
) {
    private val passi = ArrayDeque<DomandaDelServizio>()
    private var apre = input.lato
    private val primi = IntArray(2) { input.prossimo(it + 1) }

    init {
        passi.addLast(if (input.correzione) DomandaDelServizio.Correzione else DomandaDelServizio.Coppie)
    }

    val domanda: DomandaDelServizio get() = passi.last()

    fun righe(): List<RigaDelServizio> =
        when (val d = domanda) {
            DomandaDelServizio.Coppie -> {
                (1..2).map { lato ->
                    RigaDelServizio(nome(lato), giocatori(lato), lato, input.prossimo(lato), lato == input.lato)
                }
            }

            is DomandaDelServizio.Primo -> {
                input.rose[d.lato - 1].take(2).mapIndexed { i, nome ->
                    RigaDelServizio(nome, "", d.lato, i + 1, i + 1 == primi[d.lato - 1])
                }
            }

            DomandaDelServizio.Correzione -> {
                if (input.conINomi) {
                    (1..2).flatMap { lato ->
                        input.rose[lato - 1].take(2).mapIndexed { i, nome ->
                            RigaDelServizio(nome, nome(lato), lato, i + 1, lato == input.lato && i + 1 == input.slotAttuale)
                        }
                    }
                } else {
                    (1..2).map { lato ->
                        RigaDelServizio(nome(lato), giocatori(lato), lato, input.prossimo(lato), lato == input.lato)
                    }
                }
            }
        }

    /** Il tocco su [riga]: null se c'e' un'altra domanda (ora in [domanda]), altrimenti l'esito. */
    fun scegli(riga: RigaDelServizio): EsitoDelServizio? {
        when (val d = domanda) {
            DomandaDelServizio.Correzione -> {
                return EsitoDelServizio.Correzione(riga.lato, riga.slot)
            }

            DomandaDelServizio.Coppie -> {
                apre = riga.lato
                if (!input.conINomi) return EsitoDelServizio.Scelta(apre, primi[0], primi[1])
                passi.addLast(DomandaDelServizio.Primo(apre))
            }

            is DomandaDelServizio.Primo -> {
                primi[d.lato - 1] = riga.slot
                if (d.lato != apre) return EsitoDelServizio.Scelta(apre, primi[0], primi[1])
                passi.addLast(DomandaDelServizio.Primo(3 - apre))
            }
        }
        return null
    }

    /** Indietro: la domanda prima, se c'e'. Falso alla prima, e allora si esce. */
    fun indietro(): Boolean {
        if (passi.size <= 1) return false
        passi.removeLast()
        return true
    }

    private fun nome(lato: Int): String = input.nomi.getOrNull(lato - 1).orEmpty()

    private fun giocatori(lato: Int): String =
        input.rose
            .getOrNull(lato - 1)
            .orEmpty()
            .joinToString(", ")
}
