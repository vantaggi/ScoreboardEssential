package it.vantaggi.scoreboardessential

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Il testo base della striscia dell'ultima azione, dato il registro. Il registro e' in ordine
 * cronologico inverso: il primo SCORE e' l'ultimo punto segnato. Il tocco sulla striscia e il
 * dialogo che apre si provano su emulatore (MainActivityLayoutTest).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "it")
class StrisciaTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val calcio = SportRegistry.byId(SportRegistry.FOOTBALL).capabilities
    private val padel = SportRegistry.byId(SportRegistry.PADEL).capabilities

    private fun gol(
        squadra: Int,
        indice: Int,
        marcatore: String? = null,
        idGiocatore: Int? = null,
    ) = MatchEvent(
        timestamp = "12'",
        event = "Goal",
        team = squadra,
        // Senza marcatore `player` porta il nome della squadra: e' il contratto di MatchEvent.
        player = marcatore ?: if (squadra == 1) "Rossi" else "Blu",
        type = MatchEventType.SCORE,
        engineIndex = indice,
        playerId = idGiocatore,
    )

    private val avvio = MatchEvent("0'", "New match ready")
    private val correzione = MatchEvent("13'", "Score correction for Rossi", team = 1, engineIndex = 1)

    private fun striscia(
        eventi: List<MatchEvent>?,
        capacita: it.vantaggi.scoreboardessential.core.SportCapabilities? = calcio,
        display: ScoreDisplay? = null,
    ) = statoDellaStriscia(context, eventi, capacita, display, "Rossi", "Blu")

    @Test
    fun `nel calcio senza marcatore la striscia chiede chi ha segnato e si puo toccare`() {
        val stato = striscia(listOf(gol(1, 0), avvio))

        assertEquals("Gol Rossi · chi ha segnato? ›", stato.testo)
        assertNotNull("il gol senza marcatore e' la scorciatoia", stato.daAttribuire)
        assertEquals("e porta l'indice del motore di quel gol", 0, stato.daAttribuire?.engineIndex)
    }

    @Test
    fun `la striscia segue l'ultimo gol e la squadra che l'ha segnato`() {
        val stato = striscia(listOf(gol(2, 1), gol(1, 0), avvio))

        assertEquals("Gol Blu · chi ha segnato? ›", stato.testo)
        assertEquals(1, stato.daAttribuire?.engineIndex)
        assertEquals(2, stato.daAttribuire?.team)
    }

    @Test
    fun `nel calcio con il marcatore la striscia lo nomina e non offre niente da toccare`() {
        val stato = striscia(listOf(gol(1, 0, marcatore = "Marco B.", idGiocatore = 7), avvio))

        assertEquals("Gol Rossi · Marco B.", stato.testo)
        assertNull(stato.daAttribuire)
    }

    @Test
    fun `un avvio in testa non nasconde l'ultimo gol`() {
        val stato = striscia(listOf(avvio, gol(1, 0)))

        assertEquals("Gol Rossi · chi ha segnato? ›", stato.testo)
    }

    @Test
    fun `dopo un -1 la striscia dice la correzione e il gol tolto non si tocca`() {
        // Il -1 ha tolto quel gol: attribuirlo darebbe un gol a un giocatore in una partita 0-0.
        val stato = striscia(listOf(correzione, gol(1, 0), avvio))

        assertEquals("Correzione −1 Rossi", stato.testo)
        assertNull("la correzione non e' un bersaglio", stato.daAttribuire)
    }

    @Test
    fun `un gol dopo la correzione torna a essere l'ultima azione`() {
        val stato = striscia(listOf(gol(2, 2), correzione, gol(1, 0), avvio))

        assertEquals("Gol Blu · chi ha segnato? ›", stato.testo)
        assertEquals(2, stato.daAttribuire?.engineIndex)
    }

    @Test
    fun `senza punti la striscia dice che non c'e niente`() {
        assertEquals("Nessun gol", striscia(null).testo)
        assertEquals("Nessun gol", striscia(emptyList()).testo)
        assertEquals("Nessun gol", striscia(listOf(avvio)).testo)
        assertEquals("Nessun punto", striscia(listOf(avvio), capacita = padel).testo)
    }

    @Test
    fun `dopo ANNULLA la striscia torna al gol precedente e poi a nessun gol`() {
        // Annullare toglie la riga del gol dal registro: la striscia non ha memoria propria.
        val dopoIlPrimo = striscia(listOf(gol(2, 1, marcatore = "Anna", idGiocatore = 3), gol(1, 0), avvio))
        assertEquals("Gol Blu · Anna", dopoIlPrimo.testo)

        val dopoAnnulla = striscia(listOf(gol(1, 0), avvio))
        assertEquals("Gol Rossi · chi ha segnato? ›", dopoAnnulla.testo)

        val dopoAncora = striscia(listOf(avvio))
        assertEquals("Nessun gol", dopoAncora.testo)
        assertNull(dopoAncora.daAttribuire)
    }

    @Test
    fun `nel padel la striscia mostra il punto e il punteggio del display e non si tocca`() {
        val display = ScoreDisplay(side1Primary = "40", side2Primary = "30")
        val stato = striscia(listOf(gol(1, 0), avvio), capacita = padel, display = display)

        assertEquals("Punto Rossi · 40-30", stato.testo)
        assertNull("negli sport senza marcatore non c'e' scorciatoia", stato.daAttribuire)
    }

    /** Il display vero del motore dopo [punti] punti a [lato], non un display scritto a mano. */
    private fun displayDopo(
        sport: String,
        punti: Int,
        lato: Int = 1,
    ): ScoreDisplay {
        val regole = SportRegistry.byId(sport)
        var stato = regole.initial()
        repeat(punti) { stato = regole.apply(stato, ScoringEvent.Point(side = lato)) }
        return regole.display(stato)
    }

    @Test
    fun `al punto che chiude un game la striscia dice il game e non 0-0`() {
        // Padel, punto secco: quattro punti chiudono il game, e i primari tornano a 0-0.
        val display = displayDopo(SportRegistry.PADEL, punti = 4)
        assertEquals("0", display.side1Primary)

        val stato = striscia(listOf(gol(1, 3), avvio), capacita = padel, display = display)

        assertEquals("Game Rossi · 1-0", stato.testo)
        assertNull(stato.daAttribuire)
    }

    @Test
    fun `al game che chiude il set la striscia dice il game del set chiuso`() {
        // Tennis: sei game a zero chiudono il primo set. I secondari diventano «6-0 · 0-0»: il game
        // da dire e' l'ultimo del set chiuso, non i game del set nuovo.
        val display = displayDopo(SportRegistry.TENNIS, punti = 24, lato = 2)
        assertEquals("0-6 · 0-0", display.side1Secondary)

        val stato = striscia(listOf(gol(2, 23), avvio), capacita = SportRegistry.byId(SportRegistry.TENNIS).capabilities, display = display)

        assertEquals("Game Blu · 0-6", stato.testo)
    }

    @Test
    fun `a meta game la striscia dice ancora il punto`() {
        val display = displayDopo(SportRegistry.PADEL, punti = 2)

        assertEquals("Punto Rossi · 30-0", striscia(listOf(gol(1, 1), avvio), capacita = padel, display = display).testo)
    }

    @Test
    fun `senza marcatore noto la striscia dice solo il gol`() {
        // Senza indice del motore e senza giocatore: il registro scrive la squadra al posto del nome.
        assertEquals("Gol Rossi", striscia(listOf(gol(1, 0).copy(engineIndex = null))).testo)
        // Giocatore uscito dalla rosa: e' ancora attribuito, ma la riga porta il nome della squadra.
        assertEquals("Gol Rossi", striscia(listOf(gol(1, 0, marcatore = "Rossi", idGiocatore = 7))).testo)
    }

    @Test
    fun `nel padel dopo ANNULLA senza altri punti torna nessun punto`() {
        val display = ScoreDisplay(side1Primary = "0", side2Primary = "0")

        assertEquals("Nessun punto", striscia(listOf(avvio), capacita = padel, display = display).testo)
    }

    @Test
    fun `a partita finita la striscia dice TERMINA e apre il dialogo di fine partita`() {
        val display = ScoreDisplay(side1Primary = "6", side1Secondary = "6-3", side2Primary = "3", matchOver = true)
        val stato =
            striscia(listOf(MatchEvent("20'", "Point", team = 1, type = MatchEventType.SCORE, engineIndex = 9), avvio), padel, display)

        assertEquals("Partita finita · termina ›", stato.testo)
        assertTrue("toccarla apre TERMINA", stato.terminaPartita)
        assertNull("e non e' la scorciatoia del marcatore", stato.daAttribuire)
        // Anche senza registro: a partita finita la striscia e' sempre il comando che conclude.
        assertTrue(striscia(null, padel, display).terminaPartita)
    }

    @Test
    fun `in corso la striscia non e' il comando di TERMINA`() {
        val display = ScoreDisplay(side1Primary = "30", side1Secondary = "5-3", side2Primary = "15", matchOver = false)
        assertEquals(false, striscia(listOf(avvio), padel, display).terminaPartita)
        assertEquals(false, striscia(listOf(gol(1, 0), avvio)).terminaPartita)
    }

    @Test
    fun `finche le capacita non arrivano vale il calcio`() {
        assertTrue(striscia(listOf(gol(1, 0)), capacita = null).testo.contains("chi ha segnato"))
    }

    @Test
    fun `senza indice del motore il gol non si offre da attribuire`() {
        val vecchio = gol(1, 0).copy(engineIndex = null)

        assertNull(striscia(listOf(vecchio)).daAttribuire)
    }

    @Test
    @Config(qualifiers = "en")
    fun `in inglese il testo segue la lingua`() {
        assertEquals("Goal Rossi · who scored? ›", striscia(listOf(gol(1, 0))).testo)
        assertEquals("No goals yet", striscia(emptyList()).testo)
    }

    private val tennis = SportRegistry.byId(SportRegistry.TENNIS).capabilities

    @Test
    fun `ANNULLA chiede conferma solo nel calcio`() {
        assertTrue("il calcio tiene il dialogo", annullaChiedeConferma(calcio))
        assertEquals("il padel e' un tocco solo", false, annullaChiedeConferma(padel))
        assertEquals("il tennis e' un tocco solo", false, annullaChiedeConferma(tennis))
        assertTrue("finche' le capacita' non arrivano vale il calcio", annullaChiedeConferma(null))
    }

    @Test
    fun `un secondo tocco su ANNULLA entro mezzo secondo e un rimbalzo`() {
        assertEquals("il primo tocco passa", false, toccoAnnullaRipetuto(adesso = 10_000L, precedente = null))
        assertTrue("100 ms dopo e' un rimbalzo", toccoAnnullaRipetuto(adesso = 10_100L, precedente = 10_000L))
        assertTrue("499 ms dopo e' ancora un rimbalzo", toccoAnnullaRipetuto(adesso = 10_499L, precedente = 10_000L))
        assertEquals("a 500 ms e' una scelta", false, toccoAnnullaRipetuto(adesso = 10_500L, precedente = 10_000L))
    }

    @Test
    fun `il messaggio di annullamento dice cosa e' stato tolto e a chi`() {
        fun testo(
            tolto: ScoringEvent,
            capacita: it.vantaggi.scoreboardessential.core.SportCapabilities?,
        ) = testoDellAnnullamento(context, tolto, capacita, "Rossi", "Blu")

        assertEquals("Annullato: punto Rossi", testo(ScoringEvent.Point(side = 1), padel))
        assertEquals("Annullato: punto Blu", testo(ScoringEvent.Point(side = 2), tennis))
        assertEquals("Annullato: gol Blu", testo(ScoringEvent.Point(side = 2), calcio))
        assertEquals("Annullato: correzione Rossi", testo(ScoringEvent.Correction(side = 1), calcio))
    }

    @Test
    @Config(qualifiers = "en")
    fun `in inglese il messaggio di annullamento segue la lingua`() {
        assertEquals("Undone: point Rossi", testoDellAnnullamento(context, ScoringEvent.Point(side = 1), padel, "Rossi", "Blu"))
    }
}
