package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Le sigle delle coppie sopra i punteggi e quale di esse serve (bozza A di chi batte, 3 novembre 2026). */
class SigleDelleCoppieTest {
    /** Rosso se le coppie escono in un altro ordine o con sigle diverse da quelle dei nomi delle squadre. */
    @Test
    fun `due coppie danno le loro sigle, nell'ordine delle rose`() {
        assertEquals(
            listOf("VAN", "GLO") to listOf("ROS", "BIA"),
            sigleDelleCoppie(listOf("Vantaggi", "Glorio"), listOf("Rossi", "Bianchi")),
        )
    }

    /** Rosso se due cognomi che cominciano uguale danno la stessa sigla in campo. */
    @Test
    fun `le sigle si distinguono su tutti e quattro`() {
        assertEquals(
            listOf("ROSS", "MOR") to listOf("ROSE", "BIA"),
            sigleDelleCoppie(listOf("Rossi", "Mori"), listOf("Rosetti", "Bianchi")),
        )
    }

    /** Rosso se il singolare non ha una sigla per parte. */
    @Test
    fun `nel singolare una sigla per parte`() {
        assertEquals(listOf("VAN") to listOf("ROS"), sigleDelleCoppie(listOf("Vantaggi"), listOf("Rossi")))
    }

    /** Rosso se compaiono sigle senza rose, nel calcio o con un nome senza lettere. */
    @Test
    fun `senza rose, con piu' di due giocatori o con una sigla vuota niente sigle`() {
        assertNull(sigleDelleCoppie(emptyList(), listOf("Rossi")))
        assertNull(sigleDelleCoppie(listOf("A", "B", "C"), listOf("D", "E")))
        assertNull(sigleDelleCoppie(listOf("123"), listOf("Rossi")))
    }

    /** Rosso se la sigla accesa non e' quella del giocatore che serve, o se si accende dall'altra parte. */
    @Test
    fun `chi serve e' il primo o il secondo della coppia che serve`() {
        assertEquals(0, indiceDiChiServe(lato = 1, servingSide = 1, giocatore = 1, giocatoriDellaCoppia = 2))
        assertEquals(1, indiceDiChiServe(lato = 2, servingSide = 2, giocatore = 2, giocatoriDellaCoppia = 2))
        assertEquals("senza giocatore noto il primo", 0, indiceDiChiServe(1, 1, null, 2))
        assertEquals("nel singolare la sola sigla", 0, indiceDiChiServe(2, 2, 2, 1))
        assertNull("l'altra coppia", indiceDiChiServe(lato = 1, servingSide = 2, giocatore = 1, giocatoriDellaCoppia = 2))
        assertNull("nessuno serve", indiceDiChiServe(lato = 1, servingSide = null, giocatore = null, giocatoriDellaCoppia = 2))
    }
}
