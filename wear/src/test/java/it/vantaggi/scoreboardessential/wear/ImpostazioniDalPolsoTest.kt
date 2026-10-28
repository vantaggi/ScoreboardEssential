package it.vantaggi.scoreboardessential.wear

import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le impostazioni essenziali del menu (funzione pura), la vibrazione dei punti e le durate del portiere.
 *
 * Rosso se la regola del 40-40 si offre a partita cominciata o fuori dagli sport col 40-40, se il
 * portiere compare dove non c'e', se la vibrazione spenta tace anche gli avvisi (un punto in coda o non
 * confermato passerebbe in silenzio) o non tace le conferme, se la durata in uso sparisce dalla lista.
 */
class ImpostazioniDalPolsoTest {
    private val padelFermo =
        InputMenu(
            inCoda = 0,
            collegato = true,
            partitaIniziata = false,
            haElencoSport = true,
            sport = "Padel",
            risultato = "0–0",
            regola40 = "Killer point",
        )

    @Test
    fun `nel padel a partita ferma il 40-40 si sceglie, il portiere non c'e'`() {
        val voci = MenuVoci.impostazioni(padelFermo)

        assertEquals(listOf(IdVoce.REGOLA_40, IdVoce.VIBRAZIONE), voci.map { it.id })
        assertTrue(voci[0].attiva)
        assertEquals(SottotitoloVoce.Valore("Killer point"), voci[0].sottotitolo)
        assertEquals(SottotitoloVoce.Vibrazione(true), voci[1].sottotitolo)
    }

    @Test
    fun `a partita cominciata, con la coda o senza telefono il 40-40 e' spento e dice quale vale e perche'`() {
        assertEquals(
            Voce(IdVoce.REGOLA_40, false, SottotitoloVoce.ValoreBloccato("Killer point", SottotitoloVoce.PartitaInCorso)),
            MenuVoci.impostazioni(padelFermo.copy(partitaIniziata = true)).first(),
        )
        assertEquals(
            Voce(IdVoce.REGOLA_40, false, SottotitoloVoce.ValoreBloccato("Killer point", SottotitoloVoce.PrimaConsegna(2))),
            MenuVoci.impostazioni(padelFermo.copy(inCoda = 2)).first(),
        )
        assertEquals(
            Voce(IdVoce.REGOLA_40, false, SottotitoloVoce.ValoreBloccato("Killer point", SottotitoloVoce.ServeIlTelefono)),
            MenuVoci.impostazioni(padelFermo.copy(collegato = false)).first(),
        )
    }

    @Test
    fun `nel calcio c'e' il portiere, anche a partita in corso, e la vibrazione si cambia sempre`() {
        val calcio = padelFermo.copy(sport = "Calcio", regola40 = "", portiere = "30 s", partitaIniziata = true)
        val voci = MenuVoci.impostazioni(calcio)

        assertEquals(listOf(IdVoce.VIBRAZIONE, IdVoce.PORTIERE), voci.map { it.id })
        assertEquals(Voce(IdVoce.PORTIERE, true, SottotitoloVoce.Valore("30 s")), voci[1])

        val scollegato = MenuVoci.impostazioni(calcio.copy(collegato = false, vibrazionePunti = false))
        assertEquals(Voce(IdVoce.VIBRAZIONE, true, SottotitoloVoce.Vibrazione(false)), scollegato[0])
        assertEquals(Voce(IdVoce.PORTIERE, false, SottotitoloVoce.ServeIlTelefono), scollegato[1])
    }

    private class Registro : WearHaptics {
        val suonati = mutableListOf<LongArray>()
        var tick = 0

        override fun suona(pattern: LongArray) {
            suonati += pattern
        }

        override fun annulla() = Unit

        override fun tick() {
            tick++
        }
    }

    @Test
    fun `accesa la vibrazione suona tutto come prima`() {
        val registro = Registro()
        val aptica = HapticsDelPolso(registro) { true }

        aptica.tick()
        aptica.suona(WearPatterns.CONFERMA_DESTRA)
        aptica.suona(WearPatterns.IN_CODA_SINISTRA)

        assertEquals(1, registro.tick)
        assertArrayEquals(WearPatterns.CONFERMA_DESTRA, registro.suonati[0])
        assertArrayEquals(WearPatterns.IN_CODA_SINISTRA, registro.suonati[1])
    }

    @Test
    fun `spenta tacciono le conferme e restano gli avvisi`() {
        val registro = Registro()
        val aptica = HapticsDelPolso(registro) { false }

        aptica.tick()
        aptica.suona(WearPatterns.CONFERMA_SINISTRA)
        aptica.suona(WearPatterns.CONFERMA_DESTRA)
        aptica.suona(WearPatterns.ANNULLAMENTO)
        aptica.suona(HapticFeedbackManager.PATTERN_CONFIRM)
        assertEquals(0, registro.tick)
        assertTrue(registro.suonati.isEmpty())

        aptica.suona(WearPatterns.IN_CODA_DESTRA)
        aptica.suona(WearPatterns.NON_CONFERMATO)
        aptica.suona(HapticFeedbackManager.PATTERN_ALERT)
        assertEquals(3, registro.suonati.size)
        assertArrayEquals(HapticsDelPolso.SOLO_AVVISO_CODA, registro.suonati[0])
        assertArrayEquals(WearPatterns.NON_CONFERMATO, registro.suonati[1])
        assertArrayEquals(HapticFeedbackManager.PATTERN_ALERT, registro.suonati[2])
    }

    @Test
    fun `la durata in uso resta nella lista del portiere, in ordine e una volta sola`() {
        assertEquals(listOf(0L, 60L, 120L, 180L, 240L, 270L, 300L), DurataPortiere.scelte(270L).take(7))
        assertEquals(15, DurataPortiere.scelte(300L).size)
    }

    @Test
    fun `la lista del portiere e a minuti, parte da Off e va fino a mezz'ora`() {
        val lista = DurataPortiere.scelte(300L)
        assertEquals(0L, lista.first())
        assertEquals(1800L, lista.last())
        // Il cambio si fa ogni 4-10 minuti: tutti presenti.
        assertEquals(true, lista.containsAll(listOf(240L, 300L, 360L, 600L)))
        // Solo minuti: i 30 s di prima non sono una scelta, nemmeno quando il telefono li ha in uso.
        assertEquals(false, DurataPortiere.scelte(300L).contains(30L))
        assertEquals(false, DurataPortiere.scelte(30L).contains(30L))
        // Una durata in uso piu' lunga di un minuto, anche non intera, resta in lista.
        assertEquals(true, DurataPortiere.scelte(270L).contains(270L))
    }
}
