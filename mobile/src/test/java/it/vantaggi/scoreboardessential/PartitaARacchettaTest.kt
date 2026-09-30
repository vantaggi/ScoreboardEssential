package it.vantaggi.scoreboardessential

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.RacketRules
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.ScoreState
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.ScoringMode
import it.vantaggi.scoreboardessential.core.SportConfig
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.core.SportRules
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Barra, dialogo di fine partita, colore dello sconfitto e riscontri di padel e tennis (passo 8).
 *
 * I display sono quelli VERI del motore, giocati punto per punto: un display scritto a mano
 * potrebbe dire una cosa che il motore non dice mai (per esempio il periodo null nel padel a set
 * unico, che e' proprio il motivo per cui la barra non diceva chi serve). Il layout e i tocchi si
 * provano su emulatore (MainActivityLayoutTest).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "it")
class PartitaARacchettaTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val padel: SportRules = SportRegistry.byId(SportRegistry.PADEL)
    private val tennis: SportRules = SportRegistry.byId(SportRegistry.TENNIS)

    /** Gioca [punti] punti di fila al lato [lato]. */
    private fun SportRules.punti(
        stato: ScoreState,
        lato: Int,
        punti: Int,
    ): ScoreState = (1..punti).fold(stato) { acc, _ -> apply(acc, ScoringEvent.Point(side = lato)) }

    /** Un game da zero: il punto secco a 40-40 lo chiude in quattro punti. */
    private fun SportRules.game(
        stato: ScoreState,
        lato: Int,
    ): ScoreState = punti(stato, lato, 4)

    /** Padel al 5-3 per Rossi, con 30-15 nel game che si sta giocando. */
    private fun padelCinqueATre(): ScoreState {
        var s = padel.initial()
        repeat(3) {
            s = padel.game(s, 1)
            s = padel.game(s, 2)
        }
        repeat(2) { s = padel.game(s, 1) }
        s = padel.punti(s, 1, 2)
        return padel.punti(s, 2, 1)
    }

    private fun padelFinito(vince: Int): ScoreState {
        var s = padel.initial()
        repeat(3) { s = padel.game(s, if (vince == 1) 2 else 1) }
        repeat(6) { s = padel.game(s, vince) }
        return s
    }

    private fun barra(
        regole: SportRules,
        stato: ScoreState,
    ) = testoDellaBarra(context, regole.id, regole.display(stato), "Rossi", "Bianchi")

    @Test
    fun `nel padel a set unico la barra dice lo sport e chi serve fin dal primo punto`() {
        assertEquals("PADEL · SERVE ROSSI", barra(padel, padel.initial()))
        assertEquals("PADEL · SERVE ROSSI", barra(padel, padel.punti(padel.initial(), 2, 1)))
    }

    @Test
    fun `a ogni game il servizio passa all'altra squadra`() {
        assertEquals("PADEL · SERVE BIANCHI", barra(padel, padel.game(padel.initial(), 1)))
    }

    @Test
    fun `nel tennis la barra porta il set e dopo il primo set dice SET 2`() {
        assertEquals("SET 1 · SERVE ROSSI", barra(tennis, tennis.initial()))
        var s = tennis.initial()
        repeat(6) { s = tennis.game(s, 1) }
        assertEquals("SET 2", barra(tennis, s).substringBefore(" · "))
        assertTrue(barra(tennis, s), barra(tennis, s).contains("SERVE"))
    }

    @Test
    fun `nel tie-break la barra dice TIE-BREAK e il servente`() {
        var s = padel.initial()
        repeat(5) {
            s = padel.game(s, 1)
            s = padel.game(s, 2)
        }
        s = padel.game(s, 1)
        s = padel.game(s, 2)
        assertEquals("TIE-BREAK", barra(padel, s).substringBefore(" · "))
        assertTrue(barra(padel, s), barra(padel, s).contains("SERVE"))
    }

    @Test
    fun `nel calcio la barra resta vuota`() {
        val display = SportRegistry.byId(SportRegistry.FOOTBALL).let { it.display(it.initial()) }
        assertEquals("", testoDellaBarra(context, SportRegistry.FOOTBALL, display, "Rossi", "Lupi"))
    }

    @Test
    fun `al 6-3 la barra dice VINCE con il punteggio dal lato della squadra di sinistra`() {
        assertEquals("VINCE ROSSI · 6-3", barra(padel, padelFinito(1)))
        // Come lo schermo: a sinistra Rossi ha perso 3-6, e la barra non lo rigira in 6-3.
        assertEquals("VINCE BIANCHI · 3-6", barra(padel, padelFinito(2)))
    }

    /** Tennis a tre set vinto dalla squadra di destra: 6-4 3-6 7-5 per Bianchi, cioe' 4-6 6-3 5-7 per Rossi. */
    private fun tennisVintoDaBianchi(): ScoreState {
        var s = tennis.initial()
        repeat(4) {
            s = tennis.game(s, 1)
            s = tennis.game(s, 2)
        }
        repeat(2) { s = tennis.game(s, 2) }
        repeat(3) {
            s = tennis.game(s, 1)
            s = tennis.game(s, 2)
        }
        repeat(3) { s = tennis.game(s, 1) }
        repeat(5) {
            s = tennis.game(s, 1)
            s = tennis.game(s, 2)
        }
        repeat(2) { s = tennis.game(s, 2) }
        return s
    }

    @Test
    fun `nel tennis a piu' set la barra dice i set dalla parte della squadra di sinistra`() {
        val display = tennis.display(tennisVintoDaBianchi())
        assertTrue("la partita doveva finire: $display", display.matchOver)
        assertEquals("4-6 · 6-3 · 5-7", display.side1Secondary)
        assertEquals("VINCE BIANCHI · 4-6 6-3 5-7", barra(tennis, tennisVintoDaBianchi()))
    }

    @Test
    fun `con un nome lungo la barra accorcia il nome e lascia intero il punteggio`() {
        val display = padel.display(padelFinito(1))
        val lungo = "Maria Antonietta Della"
        assertEquals(22, lungo.length)
        // Una barra da 24 caratteri: «VINCE MARIA ANTONIETTA DELLA · 6-3» (34) non entra.
        val testo = testoDellaBarra(context, padel.id, display, lungo, "Bianchi") { it.length <= 24 }
        assertTrue("il punteggio deve restare: $testo", testo.endsWith(" · 6-3"))
        assertTrue("il nome e' accorciato con l'ellissi: $testo", testo.contains("\u2026"))
        assertTrue("il testo deve entrare: $testo", testo.length <= 24)
        assertTrue("accorciato il meno possibile: $testo", testo.length >= 23)
        // Se entra intero non si tocca.
        assertEquals("VINCE MARIA ANTONIETTA DELLA · 6-3", testoDellaBarra(context, padel.id, display, lungo, "Bianchi") { true })
    }

    @Test
    fun `il vincitore si legge dai primari e solo a partita finita`() {
        assertEquals(1, vincitoreDellaPartita(padel.display(padelFinito(1))))
        assertEquals(2, vincitoreDellaPartita(padel.display(padelFinito(2))))
        assertNull(vincitoreDellaPartita(padel.display(padelCinqueATre())))
        assertNull(vincitoreDellaPartita(ScoreDisplay(side1Primary = "3", side2Primary = "3", matchOver = true)))
        assertNull(vincitoreDellaPartita(null))
    }

    @Test
    fun `il dialogo sul 5-3 e 30-15 dice il punteggio del display e non 0-0`() {
        val testo = testoDelDialogoDiFine(context, padel.id, padel.display(padelCinqueATre()), "Rossi", "Bianchi")
        assertEquals("ROSSI – BIANCHI · game 5-3 · punto 30-15", testo.messaggio)
        assertEquals("Termina Partita?", testo.titolo)
        assertFalse("il positivo e' SALVA solo a partita chiusa", testo.salva)
    }

    @Test
    fun `nel tie-break 6-6 con i punti 6-6 il dialogo dice anche il punto`() {
        var s = padel.initial()
        repeat(6) {
            s = padel.game(s, 1)
            s = padel.game(s, 2)
        }
        repeat(6) {
            s = padel.punti(s, 1, 1)
            s = padel.punti(s, 2, 1)
        }
        val display = padel.display(s)
        assertEquals("6", display.side1Primary)
        assertEquals("6-6", display.side1Secondary)
        val testo = testoDelDialogoDiFine(context, padel.id, display, "Rossi", "Bianchi")
        assertEquals("ROSSI – BIANCHI · game 6-6 · punto 6-6", testo.messaggio)
    }

    @Test
    fun `in modalita' a game il dialogo non ha la riga del punto`() {
        val display = ScoreDisplay(side1Primary = "5", side1Secondary = "5-3", side2Primary = "3", side2Secondary = "3-5")
        val testo = testoDelDialogoDiFine(context, padel.id, display, "Rossi", "Bianchi", modalitaAGame = true)
        assertEquals("ROSSI – BIANCHI · game 5-3", testo.messaggio)
        assertTrue(modalitaAGame("padel").not())
        assertTrue(modalitaAGame(SportRegistry.FOOTBALL).not())
    }

    @Test
    fun `nel tennis il dialogo mette i set chiusi davanti al game`() {
        var s = tennis.initial()
        repeat(6) { s = tennis.game(s, 1) }
        val testo = testoDelDialogoDiFine(context, tennis.id, tennis.display(s), "Rossi", "Bianchi")
        assertEquals("ROSSI – BIANCHI · set 6-0 · game 0-0 · punto 0-0", testo.messaggio)
    }

    @Test
    fun `a partita chiusa il dialogo dice PARTITA FINITA, chi ha vinto e offre SALVA`() {
        val testo = testoDelDialogoDiFine(context, padel.id, padel.display(padelFinito(1)), "Rossi", "Bianchi")
        assertEquals("PARTITA FINITA", testo.titolo)
        assertEquals("VINCE ROSSI · 6-3", testo.messaggio)
        assertTrue(testo.salva)
        assertEquals("Salva", context.getString(R.string.btn_save_match))
    }

    @Test
    fun `nel calcio il dialogo scrive il risultato dal display`() {
        val display = ScoreDisplay(side1Primary = "2", side2Primary = "1")
        val testo = testoDelDialogoDiFine(context, SportRegistry.FOOTBALL, display, "Rossi", "Lupi")
        assertEquals("ROSSI 2-1 LUPI", testo.messaggio)
        assertFalse(testo.salva)
    }

    @Test
    fun `lo sconfitto passa al grigio, il vincitore e chi gioca ancora restano bianchi`() {
        val bianco = context.getColor(R.color.ink_white)
        val grigio = context.getColor(R.color.sidewalk_gray)
        val finita = padel.display(padelFinito(1))
        assertEquals(bianco, coloreDelNumero(1, finita, bianco, grigio))
        assertEquals(grigio, coloreDelNumero(2, finita, bianco, grigio))
        val vintaDaBianchi = padel.display(padelFinito(2))
        assertEquals(grigio, coloreDelNumero(1, vintaDaBianchi, bianco, grigio))
        assertEquals(bianco, coloreDelNumero(2, vintaDaBianchi, bianco, grigio))
        val inCorso = padel.display(padelCinqueATre())
        assertEquals(bianco, coloreDelNumero(1, inCorso, bianco, grigio))
        assertEquals(bianco, coloreDelNumero(2, inCorso, bianco, grigio))
        assertEquals(bianco, coloreDelNumero(2, null, bianco, grigio))
    }

    @Test
    fun `il grigio dello sconfitto si legge sul nero e SCARTA si legge sul dialogo`() {
        val grigio = context.getColor(R.color.sidewalk_gray)
        val bianco = context.getColor(R.color.ink_white)
        assertTrue("grigio su nero %.2f".format(TeamInk.contrast(grigio, TeamInk.NERO)), TeamInk.contrast(grigio, TeamInk.NERO) >= 4.5)
        val suNero = TeamInk.contrast(grigio, TeamInk.NERO)
        assertTrue("il grigio deve restare piu' spento del bianco", suNero < TeamInk.contrast(bianco, TeamInk.NERO))
        val scarta = context.getColor(R.color.error_text)
        val dialogo = context.getColor(R.color.graffiti_dark_gray)
        assertEquals(0xFFFF6E6E.toInt(), scarta)
        assertTrue("SCARTA su #2C2C2C %.2f".format(TeamInk.contrast(scarta, dialogo)), TeamInk.contrast(scarta, dialogo) >= 4.5)
    }

    @Test
    fun `un game chiuso da un tocco locale da' il doppio colpo, la fine partita il colpo pesante`() {
        val prima = padel.display(padel.punti(padel.initial(), 1, 3))
        val gameChiuso = padel.display(padel.game(padel.initial(), 1))
        assertEquals(RiscontroDelPunto.GAME_CHIUSO, riscontroDelPunto(padel.display(padel.initial()), gameChiuso, true))
        assertEquals(RiscontroDelPunto.GAME_CHIUSO, riscontroDelPunto(prima, gameChiuso, true))
        val ultimoPunto = padel.display(padel.punti(padelFinitoAUnPunto(), 1, 1))
        assertEquals(RiscontroDelPunto.PARTITA_FINITA, riscontroDelPunto(padel.display(padelFinitoAUnPunto()), ultimoPunto, true))
    }

    private fun padelFinitoAUnPunto(): ScoreState {
        var s = padel.initial()
        repeat(3) { s = padel.game(s, 2) }
        repeat(5) { s = padel.game(s, 1) }
        return padel.punti(s, 1, 3)
    }

    @Test
    fun `in modalita' a game un tocco non da' il doppio colpo, lo da' il set chiuso`() {
        val giochi = RacketRules(id = "tennis", config = SportConfig(mode = ScoringMode.GAMES, sets = 3))
        var s = giochi.initial()
        val display0 = giochi.display(s)
        s = giochi.apply(s, ScoringEvent.Point(side = 1))
        val display1 = giochi.display(s)
        assertTrue("in modalita' a game il secondario cambia a ogni tocco", display0.side1Secondary != display1.side1Secondary)
        assertEquals(RiscontroDelPunto.NESSUNO, riscontroDelPunto(display0, display1, true, modalitaAGame = true))
        // Con la regola dei punti quello stesso cambio sarebbe un game chiuso.
        assertEquals(RiscontroDelPunto.GAME_CHIUSO, riscontroDelPunto(display0, display1, true, modalitaAGame = false))
        repeat(4) { s = giochi.apply(s, ScoringEvent.Point(side = 1)) }
        val prima = giochi.display(s)
        assertEquals("5-0", prima.side1Secondary)
        val dopo = giochi.display(giochi.apply(s, ScoringEvent.Point(side = 1)))
        assertEquals("6-0 · 0-0", dopo.side1Secondary)
        assertEquals(RiscontroDelPunto.GAME_CHIUSO, riscontroDelPunto(prima, dopo, true, modalitaAGame = true))
    }

    @Test
    fun `un punto che non chiude niente, il calcio e la partita finita non danno riscontro`() {
        val zero = padel.display(padel.initial())
        val quindici = padel.display(padel.punti(padel.initial(), 1, 1))
        assertEquals(RiscontroDelPunto.NESSUNO, riscontroDelPunto(zero, quindici, true))
        assertEquals(RiscontroDelPunto.NESSUNO, riscontroDelPunto(zero, padel.display(padel.game(padel.initial(), 1)), false))
        val finita = padel.display(padelFinito(1))
        assertEquals(RiscontroDelPunto.NESSUNO, riscontroDelPunto(finita, finita, true))
        assertEquals(RiscontroDelPunto.NESSUNO, riscontroDelPunto(null, quindici, true))
    }

    @Test
    fun `il tocco inerte e' il colpo unico lungo, non i tre tick dell'annullamento`() {
        // Forma [pausa, colpo]: un solo impulso, da almeno mezzo decimo di secondo oltre un tick.
        assertArrayEquals(longArrayOf(0, 400), HapticFeedbackManager.PATTERN_INERT_TAP)
        assertEquals("un solo impulso", 1, HapticFeedbackManager.PATTERN_INERT_TAP.filterIndexed { i, _ -> i % 2 == 1 }.size)
        assertTrue("e lungo", HapticFeedbackManager.PATTERN_INERT_TAP[1] >= 300)
    }
}
