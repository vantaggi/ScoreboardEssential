package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SportRegistryTest {
    @Test
    fun `il registro espone calcio, padel e tennis`() {
        val ids = SportRegistry.selectable().map { it.id }
        assertEquals(listOf("football", "padel", "tennis"), ids)
    }

    @Test
    fun `uno sport sconosciuto degrada sul calcio invece di lanciare`() {
        // Il caso reale: una riga scritta da una versione piu' recente, o uno sport rimosso.
        // Aprire la cronologia non deve far crashare l'app.
        assertSame(FootballRules, SportRegistry.byId("cricket"))
        assertSame(FootballRules, SportRegistry.byId(""))
    }

    @Test
    fun `padel e tennis differiscono SOLO per configurazione`() {
        // E' il test di estensibilita' del modello: se un giorno questo smettesse di valere,
        // vorrebbe dire che aggiungere uno sport con racchetta richiede di toccare RacketRules.
        val padel = SportRegistry.byId("padel")
        val tennis = SportRegistry.byId("tennis")
        assertTrue(padel is RacketRules)
        assertTrue(tennis is RacketRules)
        // Le coppie sono l'unica capacita' che segue la configurazione: il padel e' sempre in
        // coppia, il tennis senza rose e' un singolare.
        assertEquals(padel.capabilities, tennis.capabilities.copy(playersPerSide = 2))
        assertEquals(DeuceRule.GOLDEN_POINT, padel.config.deuce)
        assertEquals(DeuceRule.ADVANTAGE, tennis.config.deuce)
        assertEquals(1, padel.config.sets)
        assertEquals(3, tennis.config.sets)
    }

    /**
     * Il padel nasce al killer point per chi non ha mai scelto, ma il punto secco della configurazione
     * resta la regola delle partite salvate senza `deuceRule`. Rosso se defaultDeuce torna al punto
     * secco, o se si cambia la configurazione invece di defaultDeuce (le partite vecchie cambierebbero).
     */
    @Test
    fun `il predefinito del 40-40 e' il killer point nel padel e la configurazione altrove`() {
        assertEquals(DeuceRule.KILLER_POINT, SportRegistry.defaultDeuce("padel"))
        assertEquals(DeuceRule.ADVANTAGE, SportRegistry.defaultDeuce("tennis"))
        assertEquals(DeuceRule.GOLDEN_POINT, (SportRegistry.byId("padel") as RacketRules).config.deuce)
        assertEquals(DeuceRule.GOLDEN_POINT, (SportRegistry.forMatch("padel") as RacketRules).config.deuce)
    }

    @Test
    fun `il calcio conserva le capacita' che l'interfaccia attuale si aspetta`() {
        val caps = FootballRules.capabilities
        assertEquals(ClockMode.COUNT_UP, caps.clock)
        assertTrue("il timer portiere deve restare", caps.hasAuxCountdown)
        assertTrue("le formazioni restano", caps.hasFormations)
        assertNull("il calcio non ha coppie", caps.playersPerSide)
        assertTrue("il dialogo del marcatore resta", caps.attributesScorer)
    }

    @Test
    fun `gli sport con racchetta spengono cronometro, ruoli e marcatore`() {
        val caps = SportRegistry.byId("padel").capabilities
        assertEquals(ClockMode.NONE, caps.clock)
        assertTrue(!caps.hasAuxCountdown && !caps.hasFormations && !caps.attributesScorer)
        assertTrue("il meno annulla, non corregge", caps.decrementIsUndo)
    }

    @Test
    fun `i giocatori per lato, nessuno nel calcio, due nel padel, uno nel tennis senza rose`() {
        assertNull(SportRegistry.byId("football").capabilities.playersPerSide)
        assertEquals(2, SportRegistry.byId("padel").capabilities.playersPerSide)
        assertEquals(1, SportRegistry.byId("tennis").capabilities.playersPerSide)
    }

    @Test
    fun `il tennis con l'ordine di servizio a quattro e' un doppio, con meno no`() {
        assertEquals(2, SportRegistry.forMatch("tennis", listOf(1, 2, 3, 4)).capabilities.playersPerSide)
        assertEquals(1, SportRegistry.forMatch("tennis", listOf(1, 2)).capabilities.playersPerSide)
        assertEquals(1, SportRegistry.forMatch("tennis", emptyList()).capabilities.playersPerSide)
        // Il padel e' in coppia con o senza nomi.
        assertEquals(2, SportRegistry.forMatch("padel", emptyList()).capabilities.playersPerSide)
    }

    @Test
    fun `unificare isSingles con le capacita' non cambia nessun esito rispetto al criterio di prima`() {
        for (sport in listOf("football", "padel", "tennis", "cricket")) {
            for (ordine in listOf(emptyList(), listOf(1, 2), listOf(1, 2, 3), listOf(1, 2, 3, 4), listOf(1, 2, 3, 4, 5))) {
                // Il criterio di prima, scritto a mano: tennis e ordine diverso da quattro.
                val diPrima = sport == "tennis" && ordine.size != 4
                assertEquals("$sport $ordine", diPrima, MatchStats.isSingles(sport, ordine))
                assertEquals("$sport $ordine", diPrima, SportRegistry.forMatch(sport, ordine).capabilities.playersPerSide == 1)
            }
        }
    }

    @Test
    fun `forMatch innesta l'ordine di servizio senza toccare il registro`() {
        val ordine = listOf(11, 22, 33, 44)
        val conOrdine = SportRegistry.forMatch("padel", ordine)
        assertEquals(ordine, conOrdine.config.serveOrder)
        // Il registro resta pulito: la configurazione con l'ordine e' una copia per la partita.
        assertTrue(
            SportRegistry
                .byId("padel")
                .config.serveOrder
                .isEmpty(),
        )
    }

    @Test
    fun `forMatch ignora l'ordine di servizio per il calcio`() {
        val rules = SportRegistry.forMatch("football", listOf(1, 2, 3, 4))
        assertSame(FootballRules, rules)
    }

    @Test
    fun `forMatch innesta la regola del 40-40 e il padel la gioca`() {
        val killer = SportRegistry.forMatch("padel", listOf(1, 2, 3, 4), DeuceRule.KILLER_POINT)
        assertEquals(DeuceRule.KILLER_POINT, killer.config.deuce)
        assertEquals(listOf(1, 2, 3, 4), killer.config.serveOrder)
        // Il registro resta a punto secco.
        assertEquals(DeuceRule.GOLDEN_POINT, SportRegistry.byId("padel").config.deuce)
        // 40-40, vantaggio, di nuovo 40-40: col killer point il game non e' ancora chiuso, e il
        // secondo 40-40 e' punto secco per entrambi.
        val engine = MatchEngine(killer)
        repeat(3) {
            engine.apply(ScoringEvent.Point(side = 1))
            engine.apply(ScoringEvent.Point(side = 2))
        }
        engine.apply(ScoringEvent.Point(side = 1))
        assertEquals("AV", killer.display(engine.state).side1Primary)
        engine.apply(ScoringEvent.Point(side = 2))
        assertEquals("PV", killer.display(engine.state).side1Primary)
        assertEquals("PV", killer.display(engine.state).side2Primary)
        engine.apply(ScoringEvent.Point(side = 2))
        assertEquals(listOf(0, 1), (engine.state as RacketScore).gamesInSet)
    }

    @Test
    fun `forMatch senza regola o con quella dello sport non crea una copia`() {
        assertSame(SportRegistry.byId("padel"), SportRegistry.forMatch("padel"))
        assertSame(SportRegistry.byId("padel"), SportRegistry.forMatch("padel", deuce = DeuceRule.GOLDEN_POINT))
        assertSame(FootballRules, SportRegistry.forMatch("football", deuce = DeuceRule.ADVANTAGE))
        assertEquals(DeuceRule.ADVANTAGE, SportRegistry.forMatch("padel", deuce = DeuceRule.ADVANTAGE).config.deuce)
    }

    @Test
    fun `daNome legge i nomi salvati e ripiega su null`() {
        DeuceRule.entries.forEach { assertEquals(it, DeuceRule.daNome(it.name)) }
        assertNull(DeuceRule.daNome(""))
        assertNull(DeuceRule.daNome(null))
        assertNull(DeuceRule.daNome("SUDDEN_DEATH"))
    }

    @Test
    fun `ogni sport del registro produce uno stato iniziale valido`() {
        SportRegistry.selectable().forEach { rules ->
            val iniziale = rules.initial()
            assertNotNull("${rules.id}: stato iniziale", iniziale)
            assertEquals("${rules.id}: si parte da zero a zero", 0 to 0, iniziale.headline())
            assertEquals("${rules.id}: nessun vincitore all'inizio", null, iniziale.wonBy)
        }
    }
}
