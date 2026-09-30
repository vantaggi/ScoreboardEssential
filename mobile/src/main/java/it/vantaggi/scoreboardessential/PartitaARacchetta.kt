package it.vantaggi.scoreboardessential

import android.content.Context
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.SportRegistry
import java.util.Locale

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

/** I primari col vincitore per primo: «6-3» sia che vinca la squadra di sinistra sia quella di destra. */
private fun punteggioDelVincitore(
    display: ScoreDisplay,
    vincitore: Int,
): String =
    if (vincitore == 1) {
        "${display.side1Primary}-${display.side2Primary}"
    } else {
        "${display.side2Primary}-${display.side1Primary}"
    }

/**
 * Il testo della barra in alto a sinistra.
 *
 * Nel padel e nel tennis e' l'etichetta di periodo oppure, se il motore non ne da' (il padel a set
 * unico), il nome dello sport, piu' «· SERVE <NOME>» finche' si sa chi serve: «PADEL · SERVE ROSSI»,
 * «SET 2 · SERVE ANNA», «TIE-BREAK · SERVE BRUNO». A partita dichiarata finita diventa «VINCE ROSSI
 * · 6-3». Nel calcio non c'e' niente da dire e la barra resta vuota. Il testo cambia, la vista no.
 */
internal fun testoDellaBarra(
    context: Context,
    sportId: String,
    display: ScoreDisplay,
    nomeSquadra1: String,
    nomeSquadra2: String,
): String {
    val vincitore = vincitoreDellaPartita(display)
    if (vincitore != null) {
        val nome = if (vincitore == 1) nomeSquadra1 else nomeSquadra2
        return context.getString(R.string.bar_winner, nome, punteggioDelVincitore(display, vincitore)).maiuscolo()
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
    return testo.maiuscolo()
}

/**
 * Il dialogo di TERMINA: titolo, messaggio e se il bottone positivo e' SALVA.
 *
 * Il messaggio e' scritto dal display e non dagli interi di testata: nel padel a set unico i
 * primari sono i punti del game, e il dialogo che scriveva «0 / 0» sul 5-3 faceva sembrare l'app
 * rotta. Calcio: «ROSSI 2-1 LUPI». Racchetta in corso: «ROSSI – BIANCHI · game 5-3 · punto 30-15»,
 * con i set chiusi davanti se ce ne sono. A partita chiusa il titolo e' «PARTITA FINITA», il
 * messaggio «VINCE ROSSI · 6-3» e il positivo SALVA.
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
): TestoDelDialogoDiFine {
    val nome1 = nomeSquadra1.maiuscolo()
    val nome2 = nomeSquadra2.maiuscolo()
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
            // Dove i primari SONO i game (modalita' a game) il punto ripeterebbe il game.
            if (game != punto) add(context.getString(R.string.end_part_point, punto))
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
 * un game chiuso cambia i secondari (side1Secondary), la fine partita vince su tutto.
 */
internal fun riscontroDelPunto(
    prima: ScoreDisplay?,
    dopo: ScoreDisplay?,
    sportAGame: Boolean,
): RiscontroDelPunto =
    when {
        !sportAGame || prima == null || dopo == null || prima.matchOver -> RiscontroDelPunto.NESSUNO
        dopo.matchOver -> RiscontroDelPunto.PARTITA_FINITA
        dopo.side1Secondary != prima.side1Secondary -> RiscontroDelPunto.GAME_CHIUSO
        else -> RiscontroDelPunto.NESSUNO
    }

/**
 * Il tocco inerte a partita finita: tre tick brevi (off 0, on 30, off 60, on 30, off 60, on 30),
 * per dichiarare «ti ho sentito, ma e' finita» invece del silenzio di un'app che sembra bloccata.
 */
internal val TRE_TICK = longArrayOf(0, 30, 60, 30, 60, 30)

private fun String.maiuscolo(): String = uppercase(Locale.getDefault())
