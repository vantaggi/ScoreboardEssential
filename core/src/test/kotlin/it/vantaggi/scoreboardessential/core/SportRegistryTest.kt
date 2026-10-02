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
    fun `i giocatori per lato: nessuno nel calcio, due nel padel, uno nel tennis senza rose`() {
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
    fun `ogni sport del registro produce uno stato iniziale valido`() {
        SportRegistry.selectable().forEach { rules ->
            val iniziale = rules.initial()
            assertNotNull("${rules.id}: stato iniziale", iniziale)
            assertEquals("${rules.id}: si parte da zero a zero", 0 to 0, iniziale.headline())
            assertEquals("${rules.id}: nessun vincitore all'inizio", null, iniziale.wonBy)
        }
    }
}
