package it.vantaggi.scoreboardessential

import android.content.Context
import it.vantaggi.scoreboardessential.core.GameLine
import it.vantaggi.scoreboardessential.core.GameOutcome
import java.util.Locale

/**
 * I testi della riga di un game: tre righe a schermo e una frase sola per TalkBack.
 *
 * @property titolo «GAME ROSSI · 3-2»: chi ha vinto il game e i game del set, lato 1 per primo come
 *   il tabellone.
 * @property dettaglio «TENUTO», «BREAK» o «TIE-BREAK 7-5»; null quando non si sa chi serviva.
 * @property chiusura «SET ROSSI · 6-4» se il game chiude il set, «PARTITA ROSSI» se chiude la
 *   partita; null per un game qualsiasi.
 * @property descrizione la frase che TalkBack legge, con i numeri a voce e non come «3-2».
 */
internal data class TestiDelGame(
    val titolo: String,
    val dettaglio: String?,
    val chiusura: String?,
    val descrizione: String,
)

/** Sta fuori dall'adapter perche' e' una funzione di [game] e del nome, e si prova da sola. */
internal fun testiDelGame(
    context: Context,
    game: GameLine,
    nomeVincitore: String,
): TestiDelGame {
    val maiuscolo = nomeVincitore.uppercase(Locale.getDefault())
    val (lato1, lato2) = game.gamesAfter
    val inGame = "$lato1-$lato2"
    val tieBreak = game.tieBreakScore?.let { (a, b) -> Triple("$a-$b", a, b) }

    val dettaglio =
        when (game.outcome) {
            GameOutcome.HELD -> context.getString(R.string.log_game_held)
            GameOutcome.BROKEN -> context.getString(R.string.log_game_break)
            GameOutcome.TIE_BREAK -> tieBreak?.let { context.getString(R.string.log_game_tiebreak, it.first) }
            GameOutcome.UNKNOWN -> null
        }
    val chiusura =
        when {
            game.closesMatch -> context.getString(R.string.log_game_match, maiuscolo)
            game.closesSet -> context.getString(R.string.log_game_set, maiuscolo, inGame)
            else -> null
        }

    // Per il tie-break TalkBack dice anche il punteggio giocato («7 a 5»), oltre al 7-6 del set.
    val frasi =
        buildList {
            add(context.getString(R.string.cd_log_game, nomeVincitore, lato1, lato2))
            when (game.outcome) {
                GameOutcome.HELD -> {
                    add(context.getString(R.string.cd_log_game_held))
                }

                GameOutcome.BROKEN -> {
                    add(context.getString(R.string.cd_log_game_break))
                }

                GameOutcome.TIE_BREAK -> {
                    tieBreak?.let { (_, a, b) -> add(context.getString(R.string.cd_log_game_tiebreak, nomeVincitore, a, b)) }
                }

                GameOutcome.UNKNOWN -> {
                    Unit
                }
            }
            if (game.closesSet) add(context.getString(R.string.cd_log_game_set, nomeVincitore, lato1, lato2))
            if (game.closesMatch) add(context.getString(R.string.cd_log_game_match, nomeVincitore))
        }

    return TestiDelGame(
        titolo = context.getString(R.string.log_game, maiuscolo, inGame),
        dettaglio = dettaglio,
        chiusura = chiusura,
        descrizione = frasi.joinToString(". "),
    )
}
