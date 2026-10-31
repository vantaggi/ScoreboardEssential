package it.vantaggi.scoreboardessential.wear

import it.vantaggi.scoreboardessential.core.MatchEngine
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.RacketRules
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo stato di una partita di padel (a set unico come quello dell'app, o a [set] set per provare gli
 * inizi di set) come lo manda il telefono, dagli eventi veri del motore: il display
 * e il registro sono quelli che il polso riceve, non stringhe scritte a mano.
 */
internal fun statoPadel(
    eventi: List<ScoringEvent>,
    uuid: String = "partita-1",
    set: Int = 1,
): WearScoreState {
    val padel = SportRegistry.byId(SportRegistry.PADEL) as RacketRules
    val regole = if (set == 1) padel else RacketRules(id = padel.id, config = padel.config.copy(sets = set))
    val motore = MatchEngine(regole)
    eventi.forEach { motore.apply(it) }
    val d = regole.display(motore.state)
    return WearScoreState(
        side1Primary = d.side1Primary,
        side1Secondary = d.side1Secondary.orEmpty(),
        side2Primary = d.side2Primary,
        side2Secondary = d.side2Secondary.orEmpty(),
        periodLabel = d.periodLabel.orEmpty(),
        hasClock = false,
        hasAuxTimer = false,
        attributesScorer = false,
        decrementIsUndo = true,
        sportId = SportRegistry.PADEL,
        sportLabel = "Padel",
        sportIds = emptyList(),
        sportLabels = emptyList(),
        matchInProgress = motore.log.isNotEmpty(),
        matchOver = d.matchOver,
        eventLog = MatchLogCodec.encode(motore.log),
        servingSide = d.servingSide ?: 0,
        servingSlot = d.servingPlayerSlot ?: 0,
        matchUuid = uuid,
    )
}

/** Un game vinto dal lato [lato]: quattro punti di fila. */
internal fun game(lato: Int): List<ScoringEvent> = List(4) { ScoringEvent.Point(lato) }

/** Un set 6-0 per il lato [lato]. */
internal fun set(lato: Int): List<ScoringEvent> = (1..6).flatMap { game(lato) }

/**
 * Chi serve al polso, le parti pure: quando offrire SERVE?, quando un game si e' chiuso, e le domande
 * della scelta a tutto schermo. Sono le regole della bozza A di Cioti (31 ottobre 2026).
 */
class ChiServeAlPolsoTest {
    // --- Quando si offre SERVE? ---

    /** Rosso se a inizio partita o dopo un set chiuso la capsula non si offre. */
    @Test
    fun `SERVE si offre al primo punto della partita e di ogni set`() {
        assertTrue("partita nuova", offreChiServe(statoPadel(emptyList())))
        assertTrue("dopo il primo set", offreChiServe(statoPadel(set(1), set = 3)))
    }

    /** Rosso se la capsula compare a meta' game, a meta' set o nel tie-break (6-6 non e' un inizio). */
    @Test
    fun `SERVE non si offre a meta' game, a meta' set ne' nel tie-break`() {
        assertFalse("15-0", offreChiServe(statoPadel(listOf(ScoringEvent.Point(1)))))
        assertFalse("1-0 nei game", offreChiServe(statoPadel(game(1))))
        val seiPari = (1..6).flatMap { game(1) + game(2) }
        assertFalse("tie-break a 6-6", offreChiServe(statoPadel(seiPari, set = 3)))
    }

    /** Rosso se, fatta la scelta, la capsula torna prima del primo punto. */
    @Test
    fun `fatta la scelta SERVE non torna, finche' il set non ricomincia`() {
        val scelta = ScoringEvent.ServeChoice(side = 2, slot1 = 1, slot2 = 2)
        assertFalse(offreChiServe(statoPadel(listOf(scelta))))
        assertTrue("il set dopo si offre di nuovo", offreChiServe(statoPadel(listOf(scelta) + set(2), set = 3)))
    }

    @Test
    fun `calcio e partita finita non offrono SERVE`() {
        val calcio = statoPadel(emptyList()).copy(sportId = "football", servingSide = 0, servingSlot = 0)
        assertFalse(offreChiServe(calcio))
        assertFalse(offreChiServe(statoPadel(set(1) + set(1), set = 3)))
        assertFalse("il padel dell'app e' a set unico: finito", offreChiServe(statoPadel(set(1))))
    }

    // --- Quando si chiude un game ---

    /** Rosso se la sigla non parte a fine game o a fine set, o parte per un punto qualunque. */
    @Test
    fun `un game chiuso si riconosce, un punto no`() {
        val prima = statoPadel(List(3) { ScoringEvent.Point(1) })
        val dopo = statoPadel(game(1))
        assertTrue(fineDelGame(prima, dopo))
        assertFalse("40-0 dopo 30-0", fineDelGame(statoPadel(List(2) { ScoringEvent.Point(1) }), prima))
        val setQuasi = statoPadel(set(1).dropLast(1), set = 3)
        assertTrue("anche il game che chiude il set", fineDelGame(setQuasi, statoPadel(set(1), set = 3)))
    }

    /** Rosso se un annullamento o una partita nuova fanno comparire la sigla. */
    @Test
    fun `annullamenti, la stessa schermata e un'altra partita non sono una fine game`() {
        val quindici = statoPadel(game(1) + ScoringEvent.Point(2))
        val zero = statoPadel(game(1))
        assertFalse("annullato il primo punto del game", fineDelGame(quindici, zero))
        assertFalse("annullato il punto che chiudeva il game", fineDelGame(zero, statoPadel(List(3) { ScoringEvent.Point(1) })))
        assertFalse("nessuno stato prima", fineDelGame(null, zero))
        assertFalse("partita nuova", fineDelGame(statoPadel(game(1).dropLast(1)), statoPadel(game(1), uuid = "partita-2")))
    }

    // --- La scelta a tutto schermo ---

    private fun input(
        correzione: Boolean = false,
        rose: List<List<String>> = listOf(listOf("Rossi", "Bianchi"), listOf("Verdi", "Neri")),
        slotAttuale: Int = 1,
    ) = InputDelServizio(
        nomi = listOf("ROS/BIA", "VER/NER"),
        rose = rose,
        lato = 1,
        prossimi = listOf(1, 2),
        slotAttuale = slotAttuale,
        correzione = correzione,
    )

    /**
     * La scelta di inizio set coi nomi: la coppia, poi chi serve per primo nella coppia che apre e
     * nell'altra. Rosso se l'ordine delle domande cambia, se la spunta non e' sulla proposta o se
     * l'esito non dice la coppia e i due primi.
     */
    @Test
    fun `coi nomi si sceglie la coppia, poi il primo di ciascuna a cominciare da chi apre`() {
        val passi = PassiDelServizio(input())
        assertEquals(DomandaDelServizio.Coppie, passi.domanda)
        val coppie = passi.righe()
        assertEquals(listOf("ROS/BIA", "VER/NER"), coppie.map { it.titolo })
        assertEquals(listOf("Rossi, Bianchi", "Verdi, Neri"), coppie.map { it.dettaglio })
        assertEquals("la proposta e' la coppia al servizio", listOf(true, false), coppie.map { it.spunta })

        assertNull(passi.scegli(coppie[1]))
        assertEquals(DomandaDelServizio.Primo(2), passi.domanda)
        val primi2 = passi.righe()
        assertEquals(listOf("Verdi", "Neri"), primi2.map { it.titolo })
        assertEquals("la proposta e' il prossimo della coppia 2", listOf(false, true), primi2.map { it.spunta })

        assertNull(passi.scegli(primi2[0]))
        assertEquals(DomandaDelServizio.Primo(1), passi.domanda)
        val esito = passi.scegli(passi.righe()[1])
        assertEquals(EsitoDelServizio.Scelta(lato = 2, slot1 = 2, slot2 = 1), esito)
    }

    /** Rosso se senza nomi (o nel singolare) la scelta chiede anche i giocatori. */
    @Test
    fun `senza nomi o nel singolare si sceglie solo la coppia e i primi restano la proposta`() {
        val senzaNomi = PassiDelServizio(input(rose = listOf(emptyList(), emptyList())))
        assertEquals(EsitoDelServizio.Scelta(lato = 2, slot1 = 1, slot2 = 2), senzaNomi.scegli(senzaNomi.righe()[1]))
        val singolare = PassiDelServizio(input(rose = listOf(listOf("Rossi"), listOf("Verdi")), slotAttuale = 0))
        assertEquals(EsitoDelServizio.Scelta(lato = 1, slot1 = 1, slot2 = 2), singolare.scegli(singolare.righe()[0]))
    }

    /** Rosso se la correzione non elenca i quattro con la coppia sotto e la spunta su chi serve adesso. */
    @Test
    fun `la correzione e' una domanda sola coi quattro nomi`() {
        val passi = PassiDelServizio(input(correzione = true, slotAttuale = 2))
        assertEquals(DomandaDelServizio.Correzione, passi.domanda)
        val righe = passi.righe()
        assertEquals(listOf("Rossi", "Bianchi", "Verdi", "Neri"), righe.map { it.titolo })
        assertEquals(listOf("ROS/BIA", "ROS/BIA", "VER/NER", "VER/NER"), righe.map { it.dettaglio })
        assertEquals(listOf(false, true, false, false), righe.map { it.spunta })
        assertEquals(EsitoDelServizio.Correzione(lato = 2, slot = 2), passi.scegli(righe[3]))
    }

    @Test
    fun `la correzione senza nomi sceglie la coppia, col suo prossimo`() {
        val passi = PassiDelServizio(input(correzione = true, rose = listOf(emptyList(), emptyList())))
        assertEquals(EsitoDelServizio.Correzione(lato = 2, slot = 2), passi.scegli(passi.righe()[1]))
    }

    /** Rosso se indietro non torna alla domanda prima o non dice di uscire dalla prima. */
    @Test
    fun `indietro torna alla domanda prima, dalla prima si esce`() {
        val passi = PassiDelServizio(input())
        passi.scegli(passi.righe()[0])
        assertEquals(DomandaDelServizio.Primo(1), passi.domanda)
        assertTrue(passi.indietro())
        assertEquals(DomandaDelServizio.Coppie, passi.domanda)
        assertFalse(passi.indietro())
    }
}
