package it.vantaggi.scoreboardessential

import android.content.Context
import it.vantaggi.scoreboardessential.core.ScoreDisplay

/*
 * Chi serve, scelto a mano (bozza A di Cioti, 31 ottobre 2026): a inizio set la coppia e, in ciascuna
 * coppia, chi serve per primo; a partita in corso la correzione. Le scelte sono il foglio delle
 * Impostazioni (scegliDaElenco), un passo per domanda, con la spunta sulla proposta.
 *
 * Qui solo le voci e la spunta, funzioni pure: sotto Robolectric MainActivity non si monta, quindi
 * si provano da sole e l'Activity si limita ad aprire i fogli.
 */

/** Una voce della scelta: il testo e che cosa dice al motore. */
internal data class VoceDelServizio(
    val testo: String,
    val lato: Int,
    val slot: Int,
)

/** Le voci di un foglio e quale ha la spunta (-1: nessuna). */
internal data class FoglioDelServizio(
    val voci: List<VoceDelServizio>,
    val spunta: Int,
)

/** Le due coppie hanno ciascuna i suoi due nomi: allora si sceglie anche il giocatore. */
internal fun inCoppiaConINomi(rose: List<List<String>>): Boolean = rose.size == 2 && rose.all { it.size >= 2 }

/**
 * La correzione «Chi sta servendo adesso?»: i quattro giocatori, «Rossi · ROS/BIA», con la spunta su
 * chi il tabellone da' al servizio. Senza i nomi le voci sono le due coppie, e per la coppia scelta
 * serve chi la rotazione le da' al suo prossimo turno ([prossimo]).
 */
internal fun foglioDellaCorrezione(
    context: Context,
    display: ScoreDisplay,
    nomi: List<String>,
    rose: List<List<String>>,
    prossimo: (Int) -> Int?,
): FoglioDelServizio {
    val conINomi = inCoppiaConINomi(rose)
    val voci =
        (1..2).flatMap { lato ->
            if (conINomi) {
                rose[lato - 1].take(2).mapIndexed { i, nome ->
                    VoceDelServizio(context.getString(R.string.serve_correct_item, nome, nomi[lato - 1]), lato, i + 1)
                }
            } else {
                listOf(VoceDelServizio(nomi[lato - 1], lato, prossimo(lato) ?: 1))
            }
        }
    val spunta =
        voci.indexOfFirst {
            it.lato == display.servingSide && (!conINomi || it.slot == (display.servingPlayerSlot ?: 1))
        }
    return FoglioDelServizio(voci, spunta)
}

/**
 * Le domande della scelta a inizio set, dopo la coppia che apre ([apre]): per ciascuna coppia, a
 * cominciare da quella, «Chi serve per primo in ROS/BIA?» con i suoi due nomi e la spunta su chi la
 * rotazione propone. Vuota senza i nomi: allora si sceglie solo la coppia.
 */
internal fun domandeDelPrimo(
    context: Context,
    apre: Int,
    nomi: List<String>,
    rose: List<List<String>>,
    prossimo: (Int) -> Int?,
): List<Pair<String, FoglioDelServizio>> {
    if (!inCoppiaConINomi(rose)) return emptyList()
    return listOf(apre, 3 - apre).map { lato ->
        val voci = rose[lato - 1].take(2).mapIndexed { i, nome -> VoceDelServizio(nome, lato, i + 1) }
        context.getString(R.string.serve_choice_first, nomi[lato - 1]) to FoglioDelServizio(voci, (prossimo(lato) ?: 1) - 1)
    }
}
