package it.vantaggi.scoreboardessential.wear

import it.vantaggi.scoreboardessential.core.ClockMode
import it.vantaggi.scoreboardessential.core.MatchEngine
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.core.SportRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le fasce A e D contro il motore vero: la stringa dei set non si costruisce a mano, la scrive
 * RacketRules, e se il suo separatore cambiasse questi test lo direbbero prima del quadrante.
 */
class FaceTextTest {
    /** Lo stato che l'orologio riceverebbe: le stringhe del display del motore, e niente altro. */
    private fun statoDi(
        rules: SportRules,
        vararg game: Int,
        punti: List<Int> = emptyList(),
    ): WearScoreState {
        val motore = MatchEngine(rules)
        // Un game si vince a 0 con quattro punti di fila: nel padel col punto secco e nel tennis a
        // vantaggi, da 0-0, bastano quattro punti.
        game.forEach { lato -> repeat(4) { motore.apply(ScoringEvent.Point(side = lato)) } }
        punti.forEach { lato -> motore.apply(ScoringEvent.Point(side = lato)) }
        val display = rules.display(motore.state)
        return WearScoreState(
            side1Primary = display.side1Primary,
            side1Secondary = display.side1Secondary.orEmpty(),
            side2Primary = display.side2Primary,
            side2Secondary = display.side2Secondary.orEmpty(),
            periodLabel = display.periodLabel.orEmpty(),
            hasClock = rules.capabilities.clock != ClockMode.NONE,
            hasAuxTimer = rules.capabilities.hasAuxCountdown,
            attributesScorer = rules.capabilities.attributesScorer,
            decrementIsUndo = rules.capabilities.decrementIsUndo,
            sportId = rules.id,
            sportLabel = rules.id,
            sportIds = emptyList(),
            sportLabels = emptyList(),
            matchInProgress = motore.log.isNotEmpty(),
            matchOver = display.matchOver,
            eventLog = "",
            servingSide = display.servingSide ?: 0,
        )
    }

    private val tennis = SportRegistry.byId(SportRegistry.TENNIS)
    private val padel = SportRegistry.byId(SportRegistry.PADEL)

    // Set 1 al lato 1 per 6-4; set 2 al lato 2 per 6-3; set 3 al lato 1 per 7-5.
    private val primoSet = intArrayOf(1, 1, 1, 1, 2, 2, 2, 2, 1, 1)
    private val secondoSet = intArrayOf(2, 2, 2, 1, 1, 1, 2, 2, 2)
    private val terzoSet = intArrayOf(1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 1, 1)

    @Test
    fun `tennis in corso nel set 2 la A e' il game del set e la D il set e il set chiuso`() {
        // Set 1 chiuso 6-4, poi 4-3 nel secondo: 1, 2, 1, 2, 1, 2, 1.
        val stato = statoDi(tennis, *primoSet, 1, 2, 1, 2, 1, 2, 1)

        assertEquals("6-4 · 4-3", stato.side1Secondary)
        assertEquals("4 – 3" to "Set 2 · 6-4", FaceText.split(stato))
    }

    @Test
    fun `nel tie-break la D comincia con TIE-BREAK e porta i set chiusi`() {
        // Set 1 chiuso 6-4, poi 6-6 nel secondo: sei game alternati.
        val stato = statoDi(tennis, *primoSet, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2)

        assertEquals("Tie-break", stato.periodLabel)
        val (contesto, dettaglio) = FaceText.split(stato)
        assertEquals("6 – 6", contesto)
        assertTrue(dettaglio, dettaglio.startsWith("Tie-break"))
        assertEquals("Tie-break · 6-4", dettaglio)
    }

    @Test
    fun `nel padel a set unico la D e' vuota anche nel tie-break senza set chiusi`() {
        val inCorso = statoDi(padel, 1, 2, 1)
        assertEquals("2 – 1" to "", FaceText.split(inCorso))

        // 6-6 nel set unico: il periodo dice TIE-BREAK, nessun set chiuso da accodare.
        val tieBreak = statoDi(padel, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2)
        assertEquals("6 – 6" to "Tie-break", FaceText.split(tieBreak))
    }

    @Test
    fun `tennis finito la A e' vuota e la D ha i tre set`() {
        val stato = statoDi(tennis, *primoSet, *secondoSet, *terzoSet)

        assertTrue(stato.matchOver)
        assertEquals("2", stato.side1Primary)
        assertEquals("1", stato.side2Primary)
        assertEquals("6-4 · 3-6 · 7-5", stato.side1Secondary)
        assertEquals("" to "6-4 · 3-6 · 7-5", FaceText.split(stato))
    }

    @Test
    fun `padel finito la A e la D sono vuote perche' la riga ripete i primari`() {
        val stato = statoDi(padel, *primoSet)

        assertTrue(stato.matchOver)
        assertEquals("6", stato.side1Primary)
        assertEquals("4", stato.side2Primary)
        assertEquals("6-4", stato.side1Secondary)
        assertEquals("" to "", FaceText.split(stato))
    }

    @Test
    fun `una stringa senza separatore e' un segmento solo e la A la mostra intera`() {
        val base = statoDi(padel, 1)

        // Una coppia di game prende il trattino lungo.
        assertEquals("1 – 0" to "", FaceText.split(base))
        // Una stringa che non e' una coppia di game non si tocca: niente tagli, niente invenzioni.
        assertEquals("ABC" to "", FaceText.split(base.copy(side1Secondary = "ABC")))
    }

    @Test
    fun `senza riga dei set la A e' vuota e il periodo resta in D`() {
        val stato = statoDi(tennis).copy(side1Secondary = "", periodLabel = "Set 1")

        assertEquals("" to "Set 1", FaceText.split(stato))
    }

    @Test
    fun `nel calcio le fasce A e D non vengono da qui`() {
        val stato = statoDi(SportRegistry.byId(SportRegistry.FOOTBALL))

        assertEquals("" to "", FaceText.split(stato))
    }

    @Test
    fun `il separatore dei set e' quello del motore`() {
        // La copia di FaceText contro la stringa che RacketRules scrive davvero: se divergono,
        // la riga intera finirebbe in A.
        val stato = statoDi(tennis, *primoSet, 1)

        assertEquals(listOf("6-4", "1-0"), stato.side1Secondary.split(FaceText.SET_SEPARATOR))
    }

    @Test
    fun `il vincitore si legge dai primari solo a partita finita`() {
        assertEquals(1, FaceText.vincitore(statoDi(tennis, *primoSet, *secondoSet, *terzoSet)))
        assertEquals(2, FaceText.vincitore(statoDi(tennis, *secondoSet, *secondoSet)))
        assertEquals(1, FaceText.vincitore(statoDi(padel, *primoSet)))
        // In corso: nessuno, anche se un lato e' avanti.
        assertNull(FaceText.vincitore(statoDi(tennis, 1, 1, 1)))
        // Finita ma con primari che non si confrontano: nessuno, tutte e due bianche.
        assertNull(FaceText.vincitore(statoDi(padel, *primoSet).copy(side1Primary = "6", side2Primary = "6")))
        assertNull(FaceText.vincitore(statoDi(padel, *primoSet).copy(side1Primary = "AV", side2Primary = "40")))
    }
}
