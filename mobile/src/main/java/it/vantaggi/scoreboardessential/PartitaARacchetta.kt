package it.vantaggi.scoreboardessential

import android.content.Context
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.ScoringMode
import it.vantaggi.scoreboardessential.core.SportRegistry

/*
 * Barra, dialogo di fine partita e riscontri di padel e tennis (DESIGN.md, passo 8).
 *
 * Tutto quello che si decide qui e' funzione pura di [ScoreDisplay]: sotto Robolectric
 * MainActivity non si monta, quindi i testi, i colori e le vibrazioni si provano da soli e
 * l'Activity si limita ad applicarli.
 */

/**
 * Chi ha vinto la partita, 1 o 2, dal confronto dei primari interi; null se non e' finita.
 *
 * A partita chiusa i primari sono i game del set unico o i set vinti, quindi il maggiore e' il
 * vincitore. Un pari, o un primario che non e' un numero, non dichiara nessuno: meglio nessun
 * vincitore che quello sbagliato.
 */
internal fun vincitoreDellaPartita(display: ScoreDisplay?): Int? {
    if (display == null || !display.matchOver) return null
    val uno = display.side1Primary.toIntOrNull() ?: return null
    val due = display.side2Primary.toIntOrNull() ?: return null
    return when {
        uno > due -> 1
        due > uno -> 2
        else -> null
    }
}

/**
 * Il punteggio di una partita chiusa, come lo schermo: dal punto di vista della squadra di SINISTRA.
 *
 * Viene da side1Secondary, che a partita chiusa sono i set giocati («6-3» a set unico, «4-6 6-3
 * 5-7» a piu' set), e non dai primari: questi nel tennis sono i set vinti («2-1») e non dicono
 * com'e' finita. Il vincitore non va messo per primo: il tennis vinto dalla squadra di destra si
 * legge «4-6 6-3 5-7», come sul tabellone. Senza secondari (un display che non ne da') restano i primari.
 */
private fun punteggioDellaPartita(display: ScoreDisplay): String =
    display.side1Secondary?.split(SEPARATORE_DEI_SET)?.joinToString(" ")
        ?: "${display.side1Primary}-${display.side2Primary}"

/**
 * Il testo della barra in alto a sinistra.
 *
 * Nel padel e nel tennis e' l'etichetta di periodo oppure, se il motore non ne da' (il padel a set
 * unico), il nome dello sport, piu' «· serve <nome>» finche' si sa chi serve: «Padel · serve Rossi»,
 * «Set 2 · serve Anna», «Tie-break · serve Bruno». A partita dichiarata finita diventa «Vince Rossi
 * · 6-3». Nel calcio non c'e' niente da dire e la barra resta vuota. Il testo cambia, la vista no.
 *
 * [entra] dice se un testo candidato sta nella barra. La barra ha una riga e taglia con l'ellissi
 * in coda: con un nome lungo il taglio mangiava il punteggio, che e' la parte che conta. Se il testo
 * della vittoria non entra si accorcia il nome («Vince Maria Anto… · 6-3»), mai il punteggio.
 */
internal fun testoDellaBarra(
    context: Context,
    sportId: String,
    display: ScoreDisplay,
    nomeSquadra1: String,
    nomeSquadra2: String,
    entra: (String) -> Boolean = { true },
): String {
    val vincitore = vincitoreDellaPartita(display)
    if (vincitore != null) {
        val nome = if (vincitore == 1) nomeSquadra1 else nomeSquadra2
        val punteggio = punteggioDellaPartita(display)

        fun testo(nome: String) = context.getString(R.string.bar_winner, nome, punteggio)
        return testo(nome).takeIf(entra) ?: testo(nomeAccorciato(nome) { entra(testo(it)) })
    }
    val base = display.periodLabel ?: if (sportId == SportRegistry.FOOTBALL) null else sportLabel(context, sportId)
    val servente =
        when (display.servingSide) {
            1 -> nomeSquadra1
            2 -> nomeSquadra2
            else -> null
        }
    val testo =
        when {
            base == null -> ""
            servente.isNullOrBlank() -> base
            else -> context.getString(R.string.score_period_serving, base, servente)
        }
    return testo
}

/** Il nome accorciato con l'ellissi, il piu' lungo che fa entrare il testo; almeno una lettera. */
private fun nomeAccorciato(
    nome: String,
    entra: (String) -> Boolean,
): String {
    for (lunghezza in nome.length - 1 downTo 1) {
        val candidato = nome.take(lunghezza).trimEnd() + ELLISSI
        if (entra(candidato)) return candidato
    }
    return nome.take(1) + ELLISSI
}

private const val ELLISSI = "…"

/**
 * Il dialogo di TERMINA: titolo, messaggio e se il bottone positivo e' SALVA.
 *
 * Il messaggio e' scritto dal display e non dagli interi di testata: nel padel a set unico i
 * primari sono i punti del game, e il dialogo che scriveva «0 / 0» sul 5-3 faceva sembrare l'app
 * rotta. Calcio: «Rossi 2-1 Lupi». Racchetta in corso: «Rossi – Bianchi · game 5-3 · punto 30-15»,
 * con i set chiusi davanti se ce ne sono. A partita chiusa il titolo e' «Partita finita», il
 * messaggio «Vince Rossi · 6-3» e il positivo SALVA. In [modalitaAGame] i primari sono i game e
 * la riga «punto» non c'e'.
 */
internal data class TestoDelDialogoDiFine(
    val titolo: String,
    val messaggio: String,
    val salva: Boolean,
)

internal fun testoDelDialogoDiFine(
    context: Context,
    sportId: String,
    display: ScoreDisplay,
    nomeSquadra1: String,
    nomeSquadra2: String,
    modalitaAGame: Boolean = false,
): TestoDelDialogoDiFine {
    val nome1 = nomeSquadra1
    val nome2 = nomeSquadra2
    val vincitore = vincitoreDellaPartita(display)
    if (vincitore != null) {
        return TestoDelDialogoDiFine(
            titolo = context.getString(R.string.end_match_over_title),
            messaggio = testoDellaBarra(context, sportId, display, nomeSquadra1, nomeSquadra2),
            salva = true,
        )
    }
    val titolo = context.getString(R.string.end_match_title)
    if (sportId == SportRegistry.FOOTBALL) {
        val messaggio = context.getString(R.string.end_summary_football, nome1, display.side1Primary, display.side2Primary, nome2)
        return TestoDelDialogoDiFine(titolo, messaggio, salva = false)
    }
    val intestazione = context.getString(R.string.end_summary_racket, nome1, nome2)
    val parti = display.side1Secondary?.split(SEPARATORE_DEI_SET).orEmpty()
    val game = parti.lastOrNull()
    val chiusi = parti.dropLast(1)
    val punto = "${display.side1Primary}-${display.side2Primary}"
    val dettagli =
        buildList {
            if (chiusi.isNotEmpty()) add(context.getString(R.string.end_part_set, chiusi.joinToString(", ")))
            if (game != null) add(context.getString(R.string.end_part_game, game))
            // Dove i primari SONO i game (modalita' a game) il punto ripeterebbe il game. Lo si decide
            // dalla modalita' e non dall'uguaglianza dei testi: un tie-break 6-6 con punti 6-6 ha il
            // game uguale al punto e il punto va detto.
            if (!modalitaAGame) add(context.getString(R.string.end_part_point, punto))
        }
    return TestoDelDialogoDiFine(titolo, (listOf(intestazione) + dettagli).joinToString(SEPARATORE_DEI_SET), salva = false)
}

/**
 * Il colore del numero di un lato. A partita finita lo sconfitto passa dal bianco al grigio
 * (#9E9E9E, 7,84:1 sul nero: si legge chi ha perso senza spegnere la cifra); il vincitore e chi
 * non ha un vincitore restano bianchi. I due colori arrivano dal chiamante, che li legge dalle risorse.
 */
internal fun coloreDelNumero(
    lato: Int,
    display: ScoreDisplay?,
    bianco: Int,
    grigio: Int,
): Int {
    val vincitore = vincitoreDellaPartita(display)
    return if (vincitore != null && vincitore != lato) grigio else bianco
}

/** Che riscontro al polpastrello da' un punto dato col telefono, oltre al colpo del tocco. */
internal enum class RiscontroDelPunto { NESSUNO, GAME_CHIUSO, PARTITA_FINITA }

/**
 * Il riscontro di un tocco LOCALE su +, dal display di prima e di dopo. Va chiamata dal listener
 * della zona, con i due display letti attorno alla chiamata al ViewModel: i punti arrivati
 * dall'orologio non passano di qui e non fanno vibrare il telefono. Solo negli sport a racchetta:
 * un game chiuso cambia i secondari (side1Secondary), la fine partita vince su tutto. Il colpo del
 * tocco inerte a partita finita e' HapticFeedbackManager.PATTERN_INERT_TAP, in :shared.
 */
internal fun riscontroDelPunto(
    prima: ScoreDisplay?,
    dopo: ScoreDisplay?,
    sportAGame: Boolean,
    modalitaAGame: Boolean = false,
): RiscontroDelPunto =
    when {
        !sportAGame || prima == null || dopo == null || prima.matchOver -> RiscontroDelPunto.NESSUNO

        dopo.matchOver -> RiscontroDelPunto.PARTITA_FINITA

        // In modalita' a game ogni tocco chiude un game e cambia side1Secondary: il doppio colpo
        // sarebbe a ogni tocco e non direbbe piu' niente. Lo si da' solo se si chiude un set.
        modalitaAGame -> if (setInCorso(dopo) != setInCorso(prima)) RiscontroDelPunto.GAME_CHIUSO else RiscontroDelPunto.NESSUNO

        dopo.side1Secondary != prima.side1Secondary -> RiscontroDelPunto.GAME_CHIUSO

        else -> RiscontroDelPunto.NESSUNO
    }

/** Quanti set ha il display, compreso quello in corso: cresce di uno quando un set si chiude. */
private fun setInCorso(display: ScoreDisplay): Int = display.side1Secondary?.split(SEPARATORE_DEI_SET)?.size ?: 0

/** Lo sport gioca a game (un tocco = un game) invece che a punti? Dalle regole, non dal testo. */
internal fun modalitaAGame(sportId: String): Boolean = SportRegistry.byId(sportId).config.mode == ScoringMode.GAMES
