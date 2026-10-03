package it.vantaggi.scoreboardessential

import it.vantaggi.scoreboardessential.core.MatchNarrative
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType

/**
 * Il registro a schermo del padel e del tennis: una riga per game al posto di una per punto.
 *
 * [registro] e' quello del ViewModel, in ordine cronologico inverso, con una riga per punto. Se
 * [narrativa] e' null (calcio) si restituisce com'e'. Altrimenti:
 *
 *  - i punti spariscono e le righe dei game vengono da [narrativa], cioe' dal registro del motore:
 *    un game esiste se il motore l'ha chiuso, e se un annullamento lo riapre la sua riga non c'e'
 *    piu', senza che nessuno la tolga;
 *  - le righe che il motore non conosce (partita pronta, ANNULLATO, partita ripresa...) restano,
 *    ognuna al suo posto. Il posto lo da' il punto del motore che le precede: la riga informativa
 *    si mette subito dopo l'ultima riga con un `engineIndex` piu' vecchia di lei, e la riga di un
 *    game sta all'indice del punto che l'ha chiuso. Ne' i punti ne' i game si cercano per
 *    posizione fra due liste che contengono cose diverse.
 *
 * Il game ancora aperto non ha riga: il tabellone e la striscia dicono gia' il punto in corso.
 * Sta fuori dal ViewModel perche' e' una funzione pura, e si prova da sola.
 */
internal fun righeDelRegistro(
    registro: List<MatchEvent>,
    narrativa: MatchNarrative?,
    nomeSquadra1: String,
    nomeSquadra2: String,
): List<MatchEvent> {
    if (narrativa == null) return registro

    // Chiave di posizione sull'asse degli indici del motore: i game e le righe del motore stanno
    // sull'intero, le righe informative a meta' fra l'indice che le precede e il successivo.
    val dellaPartita = ArrayList<Pair<Double, MatchEvent>>()
    var ultimoIndice = -1
    // Dal piu' vecchio al piu' nuovo, perche' una riga informativa guarda quella che la precede.
    for (riga in registro.asReversed()) {
        val indice = riga.engineIndex
        when {
            indice != null -> {
                ultimoIndice = indice
                // Il punto e' sostituito dalla riga del suo game; le altre righe del motore, come una
                // correzione, che nel padel non esiste, restano.
                if (riga.type != MatchEventType.SCORE) dellaPartita.add(indice.toDouble() to riga)
            }

            else -> dellaPartita.add(ultimoIndice + MEZZO to riga)
        }
    }
    for (game in narrativa.games) {
        val nome = if (game.winner == 1) nomeSquadra1 else nomeSquadra2
        dellaPartita.add(
            game.lastLogIndex.toDouble() to
                MatchEvent(
                    timestamp = "",
                    // Non si mostra: e' la chiave con cui DiffUtil riconosce la riga fra due liste.
                    event = "Game ${game.index + 1}",
                    team = game.winner,
                    player = nome,
                    type = MatchEventType.GAME,
                    engineIndex = game.lastLogIndex,
                    game = game,
                ),
        )
    }
    // sortedBy e' stabile: a parita' di chiave resta l'ordine di inserimento, cioe' il cronologico.
    return dellaPartita.sortedBy { it.first }.map { it.second }.asReversed()
}

private const val MEZZO = 0.5
