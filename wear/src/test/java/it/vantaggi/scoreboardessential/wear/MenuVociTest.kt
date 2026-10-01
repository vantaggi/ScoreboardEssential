package it.vantaggi.scoreboardessential.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I blocchi del menu partita, a tabella: JVM puro, nessun Android.
 *
 * Il caso base e' un telefono raggiungibile, nessun punto in coda, padel a partita non cominciata:
 * ogni test cambia UNA cosa, cosi' il blocco che scatta e' quello che il nome dice.
 */
class MenuVociTest {
    private fun input(
        inCoda: Int = 0,
        collegato: Boolean = true,
        partitaIniziata: Boolean = false,
        calcioConV2: Boolean = false,
        haElencoSport: Boolean = true,
        sport: String = "Padel",
        risultato: String = "3–2",
    ) = InputMenu(inCoda, collegato, partitaIniziata, calcioConV2, haElencoSport, sport, risultato)

    private fun voce(
        id: IdVoce,
        input: InputMenu,
    ) = MenuVoci.calcola(input).first { it.id == id }

    @Test
    fun `senza blocchi le due voci sono attive e dicono sport e risultato`() {
        val voci = MenuVoci.calcola(input())

        assertEquals(listOf(IdVoce.SPORT, IdVoce.FINE_PARTITA), voci.map { it.id })
        assertTrue(voci.all { it.attiva })
        assertEquals(SottotitoloVoce.SportInUso("Padel"), voci[0].sottotitolo)
        assertEquals(SottotitoloVoce.SalvaRisultato("3–2"), voci[1].sottotitolo)
    }

    @Test
    fun `con la coda piena FINE PARTITA e' disattivata e dice quanti punti`() {
        val fine = voce(IdVoce.FINE_PARTITA, input(inCoda = 2))

        assertFalse(fine.attiva)
        assertEquals(SottotitoloVoce.PrimaConsegna(2), fine.sottotitolo)
    }

    @Test
    fun `da scollegati FINE PARTITA e' disattivata e chiede il telefono`() {
        val fine = voce(IdVoce.FINE_PARTITA, input(collegato = false))

        assertFalse(fine.attiva)
        assertEquals(SottotitoloVoce.ServeIlTelefono, fine.sottotitolo)
    }

    @Test
    fun `nel calcio col v2 FINE PARTITA e' disattivata finche' L4 non e' corretto`() {
        val fine = voce(IdVoce.FINE_PARTITA, input(calcioConV2 = true))

        assertFalse(fine.attiva)
        assertEquals(SottotitoloVoce.ChiudiDalTelefono, fine.sottotitolo)
    }

    @Test
    fun `la coda vince sul telefono assente, e il telefono assente sul calcio`() {
        // Il caso tipico: scollegati con dei punti in coda. Il numero e' piu' utile del motivo generico.
        assertEquals(
            SottotitoloVoce.PrimaConsegna(3),
            voce(IdVoce.FINE_PARTITA, input(inCoda = 3, collegato = false, calcioConV2 = true)).sottotitolo,
        )
        assertEquals(
            SottotitoloVoce.ServeIlTelefono,
            voce(IdVoce.FINE_PARTITA, input(collegato = false, calcioConV2 = true)).sottotitolo,
        )
    }

    @Test
    fun `con la partita cominciata SPORT e' disattivata`() {
        val sport = voce(IdVoce.SPORT, input(partitaIniziata = true))

        assertFalse(sport.attiva)
        assertEquals(SottotitoloVoce.PartitaInCorso, sport.sottotitolo)
    }

    @Test
    fun `con la coda piena SPORT e' disattivata e dice quanti punti`() {
        // L'arretrato non dice di che sport e': cambiarlo dal polso lo mescolerebbe con un altro.
        val sport = voce(IdVoce.SPORT, input(inCoda = 1))

        assertFalse(sport.attiva)
        assertEquals(SottotitoloVoce.PrimaConsegna(1), sport.sottotitolo)
    }

    @Test
    fun `SPORT resta attiva da scollegati e nel calcio, i blocchi sono solo di FINE PARTITA`() {
        // Il controllo dei test sopra: un blocco non deve allargarsi alla voce accanto.
        val sport = voce(IdVoce.SPORT, input(collegato = false, calcioConV2 = true, sport = "Calcio"))

        assertTrue(sport.attiva)
        assertEquals(SottotitoloVoce.SportInUso("Calcio"), sport.sottotitolo)
    }

    @Test
    fun `FINE PARTITA non guarda se la partita e' cominciata`() {
        // A partita finita il registro non e' vuoto, ed e' proprio il momento di chiuderla.
        assertTrue(voce(IdVoce.FINE_PARTITA, input(partitaIniziata = true)).attiva)
    }

    @Test
    fun `senza un elenco di sport la voce SPORT non compare`() {
        val voci = MenuVoci.calcola(input(haElencoSport = false))

        assertEquals(listOf(IdVoce.FINE_PARTITA), voci.map { it.id })
    }

    // --- La conferma sul posto ---

    @Test
    fun `il primo tocco arma, e non chiude niente`() {
        val conferma = ConfermaSulPosto()

        assertEquals(ConfermaSulPosto.Esito.ARMATA, conferma.tocca(1_000))
        assertTrue(conferma.armata)
    }

    @Test
    fun `un secondo tocco prima di 600 ms non conta e non riarma`() {
        val conferma = ConfermaSulPosto()
        conferma.tocca(1_000)

        assertEquals(ConfermaSulPosto.Esito.IGNORATO, conferma.tocca(1_599))
        // Non e' ripartita la finestra: a 600 ms dal PRIMO tocco il secondo vale.
        assertEquals(ConfermaSulPosto.Esito.CONFERMATA, conferma.tocca(1_600))
    }

    @Test
    fun `il secondo tocco vale fra 600 ms e 5 secondi, estremi compresi`() {
        listOf(600L, 2_000L, 5_000L).forEach { dopo ->
            val conferma = ConfermaSulPosto()
            conferma.tocca(10_000)
            assertEquals("a $dopo ms", ConfermaSulPosto.Esito.CONFERMATA, conferma.tocca(10_000 + dopo))
            assertFalse(conferma.armata)
        }
    }

    @Test
    fun `dopo 5 secondi la card e' tornata com'era e il tocco e' di nuovo il primo`() {
        val conferma = ConfermaSulPosto()
        conferma.tocca(10_000)

        assertEquals(ConfermaSulPosto.Esito.ARMATA, conferma.tocca(15_001))
        // E si riparte da li': un secondo tocco subito dopo e' ancora troppo presto.
        assertEquals(ConfermaSulPosto.Esito.IGNORATO, conferma.tocca(15_100))
    }

    @Test
    fun `disarma riporta al primo tocco`() {
        val conferma = ConfermaSulPosto()
        conferma.tocca(0)
        conferma.disarma()

        assertFalse(conferma.armata)
        assertEquals(ConfermaSulPosto.Esito.ARMATA, conferma.tocca(2_000))
    }
}
