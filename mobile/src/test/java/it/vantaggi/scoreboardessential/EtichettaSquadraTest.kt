package it.vantaggi.scoreboardessential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EtichettaSquadraTest {
    @Test
    fun `nome predefinito e coppia composta danno le sigle dei cognomi`() {
        // Rosso se torna "TEAM 1": era il caso da cui e' nata la regola.
        assertEquals("VAN/GLO", EtichettaSquadra.di("TEAM 1", listOf("Vantaggi", "Glorio")))
        assertEquals("VAN/GLO", EtichettaSquadra.di("Team 1", listOf("vantaggi", "glorio")))
        assertEquals("VAN/GLO", EtichettaSquadra.di("Squadra 1", listOf("Vantaggi", "Glorio")))
        assertEquals("VAN/GLO", EtichettaSquadra.di("", listOf("Vantaggi", "Glorio")))
    }

    @Test
    fun `il singolare mostra una sigla sola`() {
        assertEquals("VAN", EtichettaSquadra.di("TEAM 2", listOf("Vantaggi")))
    }

    @Test
    fun `un nome scelto vince sempre`() {
        assertEquals("Leoni", EtichettaSquadra.di("Leoni", listOf("Vantaggi", "Glorio")))
        assertEquals("Team Rocket", EtichettaSquadra.di("Team Rocket", listOf("Vantaggi", "Glorio")))
    }

    @Test
    fun `senza giocatori o con piu' di due resta il nome`() {
        assertEquals("TEAM 1", EtichettaSquadra.di("TEAM 1", emptyList()))
        assertEquals("TEAM 1", EtichettaSquadra.di("TEAM 1", listOf("Rossi", "Bianchi", "Verdi")))
    }

    @Test
    fun `la sigla viene dal cognome, la prima parola, solo lettere`() {
        // Rosso se torna l'ultima parola: "Ferrario Stefano" dava STE, il nome di battesimo.
        assertEquals("FER", EtichettaSquadra.sigla("Ferrario Stefano"))
        assertEquals("VAN", EtichettaSquadra.sigla("Vantaggi"))
        assertEquals("DAM", EtichettaSquadra.sigla("D'Amico"))
        assertEquals("LI", EtichettaSquadra.sigla("Li"))
        assertEquals("", EtichettaSquadra.sigla("  "))
    }

    @Test
    fun `un giocatore senza lettere lascia il nome`() {
        assertEquals("TEAM 1", EtichettaSquadra.di("TEAM 1", listOf("Vantaggi", "42")))
    }

    @Test
    fun `predefinito riconosce solo i nomi generici`() {
        assertTrue(EtichettaSquadra.predefinito(" team 2 "))
        assertFalse(EtichettaSquadra.predefinito("Team Rocket"))
    }

    /** Rosso se il predefinito dei dati ("Team 1") torna a schermo cosi' com'e', come nello storico. */
    @Test
    fun `mostrato traduce solo il predefinito`() {
        assertEquals("Squadra 1", EtichettaSquadra.mostrato("Team 1", "Squadra 1"))
        assertEquals("Squadra 2", EtichettaSquadra.mostrato(null, "Squadra 2"))
        assertEquals("Squadra 1", EtichettaSquadra.mostrato("", "Squadra 1"))
        assertEquals("Team Rocket", EtichettaSquadra.mostrato("Team Rocket", "Squadra 1"))
    }
}
