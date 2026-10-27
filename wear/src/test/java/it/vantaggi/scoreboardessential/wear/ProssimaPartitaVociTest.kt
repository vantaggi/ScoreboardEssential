package it.vantaggi.scoreboardessential.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La voce "Prossima partita" del menu e la sequenza condivisa: JVM puro.
 *
 * Rosso se la voce compare a partita cominciata (il telefono rifiuterebbe), se scavalca la coda o il
 * telefono assente, o se i due mittenti del polso possono mandare una sequenza che non supera l'altra.
 */
class ProssimaPartitaVociTest {
    private fun input(
        inCoda: Int = 0,
        collegato: Boolean = true,
        partitaIniziata: Boolean = false,
        prossima: String = "VAN/GLO – ROS/BIA",
        rifiutati: Int = 0,
    ) = InputMenu(inCoda, collegato, partitaIniziata, true, "Padel", "0–0", rifiutati, prossima)

    @Test
    fun `a partita ferma la prossima partita e' la prima voce e dice le coppie`() {
        val voci = MenuVoci.calcola(input())
        assertEquals(listOf(IdVoce.PROSSIMA_PARTITA, IdVoce.SPORT, IdVoce.FINE_PARTITA), voci.map { it.id })
        assertTrue(voci[0].attiva)
        assertEquals(SottotitoloVoce.CoppieProposte("VAN/GLO – ROS/BIA"), voci[0].sottotitolo)
    }

    @Test
    fun `senza proposta o a partita cominciata la voce non c'e'`() {
        assertFalse(MenuVoci.calcola(input(prossima = "")).any { it.id == IdVoce.PROSSIMA_PARTITA })
        assertFalse(MenuVoci.calcola(input(partitaIniziata = true)).any { it.id == IdVoce.PROSSIMA_PARTITA })
    }

    @Test
    fun `la coda e il telefono assente spengono la voce e dicono perche'`() {
        val conCoda = MenuVoci.calcola(input(inCoda = 2, collegato = false)).first()
        assertFalse(conCoda.attiva)
        assertEquals(SottotitoloVoce.PrimaConsegna(2), conCoda.sottotitolo)

        val scollegato = MenuVoci.calcola(input(collegato = false)).first()
        assertFalse(scollegato.attiva)
        assertEquals(SottotitoloVoce.ServeIlTelefono, scollegato.sottotitolo)

        val rifiutata = MenuVoci.calcola(input(rifiutati = 1)).first()
        assertFalse(rifiutata.attiva)
        assertEquals(SottotitoloVoce.CodaRifiutata(1), rifiutata.sottotitolo)
    }

    @Test
    fun `la sequenza della schermata supera il quadrante e quella del quadrante supera la schermata`() {
        // Il quadrante parte da un seme nel futuro: la schermata deve superarlo, non fermarsi all'ora.
        val futuro = System.currentTimeMillis() + 60_000
        val quadrante = SequenzaDelPolso.delQuadrante(futuro)
        val schermata = SequenzaDelPolso.dellaSchermata()
        assertTrue(schermata > quadrante)
        // E il quadrante, col suo contatore rimasto indietro, deve superare la schermata.
        assertTrue(SequenzaDelPolso.delQuadrante(quadrante) > schermata)
    }
}
