package it.vantaggi.scoreboardessential

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.ScoreState
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.core.SportRules
import it.vantaggi.scoreboardessential.core.TeamInk
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
    fun `al 6-3 la barra dice VINCE con il punteggio del vincitore per primo`() {
        assertEquals("VINCE ROSSI · 6-3", barra(padel, padelFinito(1)))
        assertEquals("VINCE BIANCHI · 6-3", barra(padel, padelFinito(2)))
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
    fun `nel tennis il dialogo mette i set chiusi davanti al game`() {
        var s = tennis.initial()
        repeat(6) { s = tennis.game(s, 1) }
        val testo = testoDelDialogoDiFine(context, tennis.id, tennis.display(s), "Rossi", "Bianchi")
        assertEquals("ROSSI – BIANCHI · set 6-0 · game 0-0", testo.messaggio)
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
    fun `il tocco inerte e' una sequenza di tre tick brevi`() {
        // Forma [pausa, tick, pausa, tick, ...]: i tick stanno agli indici dispari.
        val tick = TRE_TICK.filterIndexed { i, _ -> i % 2 == 1 }
        assertEquals(3, tick.size)
        assertTrue("ogni tick e' breve", tick.all { it <= 30 })
        assertTrue("tutta la sequenza sta sotto il quarto di secondo", TRE_TICK.sum() < 250)
    }
}
