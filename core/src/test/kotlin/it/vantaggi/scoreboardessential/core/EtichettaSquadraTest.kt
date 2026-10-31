package it.vantaggi.scoreboardessential.core

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

    /** Rosso se torna "PRO/PRO - PRO/PRO", visto al giro sugli emulatori con i giocatori di prova. */
    @Test
    fun `le sigle che si ripetono fra chi e' in campo si distinguono`() {
        assertEquals(listOf("PRO1", "PRO2", "PRO3", "PRO4"), EtichettaSquadra.sigle(listOf("Prova 1", "Prova 2", "Prova 3", "Prova 4")))
        assertEquals(listOf("ROSS", "ROSE", "VAN"), EtichettaSquadra.sigle(listOf("Rossi", "Rosetti", "Vantaggi")))
        assertEquals(listOf("ROSM", "ROSL"), EtichettaSquadra.sigle(listOf("Rossi Mario", "Rossetti Luca")))
        // Senza seconda parola resta la quarta lettera; due nomi identici restano uguali.
        assertEquals(listOf("BIAN", "BIAN"), EtichettaSquadra.sigle(listOf("Bianchi", "Bianco")))
        assertEquals(listOf("VAN", "GLO"), EtichettaSquadra.sigle(listOf("Vantaggi", "Glorio")))
    }

    /** Rosso se le sigle di una coppia ignorano gli avversari: "Prova 1/Prova 2" contro "Prova 3/Prova 4". */
    @Test
    fun `la coppia si distingue anche dagli avversari`() {
        assertEquals("PRO1/PRO2", EtichettaSquadra.di("TEAM 1", listOf("Prova 1", "Prova 2"), listOf("Prova 3", "Prova 4")))
        assertEquals("VAN/GLO", EtichettaSquadra.di("TEAM 1", listOf("Vantaggi", "Glorio"), listOf("Rossi", "Bianchi")))
        assertEquals("VANT/GLO", EtichettaSquadra.di("TEAM 1", listOf("Vantaggi", "Glorio"), listOf("Vanni", "Bianchi")))
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
