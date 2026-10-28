package it.vantaggi.scoreboardessential

import it.vantaggi.scoreboardessential.core.Composizione
import it.vantaggi.scoreboardessential.core.Serata
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class CoppieAlPolsoTest {
    private val nomi = mapOf(1 to "Vantaggi", 2 to "Glorio", 3 to "Rossi", 4 to "Bianchi", 5 to "Verdi Luca")

    @Test
    fun `la prossima partita arriva coi nomi nell'ordine dei posti, le sigle, la base e la panchina`() {
        // Cinque presenti: gioca la rotazione dei primi quattro, il quinto e' in panchina.
        val serata = Serata.nuova(listOf(1, 2, 3, 4, 5))
        val bozza = serata.bozza!!

        val dati = CoppieAlPolso.dati(listOf("Vantaggi", "Glorio"), listOf("Rossi", "Bianchi"), serata) { nomi[it] }

        assertEquals("Vantaggi|Glorio", dati[WearConstants.KEY_TEAM1_ROSTER])
        assertEquals(1, dati[WearConstants.KEY_NEXT_NUMBER])
        assertEquals(bozza.posti.joinToString("|") { nomi.getValue(it) }, dati[WearConstants.KEY_NEXT_PLAYERS])
        assertEquals(bozza.squadra1.joinToString(",") + "/" + bozza.squadra2.joinToString(","), dati[WearConstants.KEY_NEXT_BASE])
        assertEquals("VAN/BIA|GLO/ROS", dati[WearConstants.KEY_NEXT_LABELS])
        assertEquals("Verdi Luca", dati[WearConstants.KEY_NEXT_BENCH])
        assertEquals(false, dati[WearConstants.KEY_NEXT_DELIVERED])
        assertEquals(true, CoppieAlPolso.dati(emptyList(), emptyList(), serata.consegna()) { nomi[it] }[WearConstants.KEY_NEXT_DELIVERED])
    }

    /** Rosso se il polso torna a leggere "PRO/PRO - PRO/PRO", come al giro sugli emulatori. */
    @Test
    fun `con nomi che cominciano uguale le sigle delle due coppie si distinguono`() {
        val prova = mapOf(1 to "Prova 1", 2 to "Prova 2", 3 to "Prova 3", 4 to "Prova 4")
        val serata = Serata.nuova(listOf(1, 2, 3, 4))
        val posti = serata.bozza!!.posti.map { prova.getValue(it) }

        val dati = CoppieAlPolso.dati(emptyList(), emptyList(), serata) { prova[it] }

        val sigle = posti.map { "PRO" + it.last() }
        assertEquals("${sigle[0]}/${sigle[1]}|${sigle[2]}/${sigle[3]}", dati[WearConstants.KEY_NEXT_LABELS])
    }

    @Test
    fun `senza serata, senza bozza o con un nome mancante non si propone niente`() {
        listOf(
            CoppieAlPolso.dati(emptyList(), emptyList(), null) { nomi[it] },
            CoppieAlPolso.dati(emptyList(), emptyList(), Serata.nuova(listOf(1, 2, 3))) { nomi[it] },
            // Il giocatore 9 non e' nella rosa caricata: meglio nessuna proposta che un posto senza nome.
            CoppieAlPolso.dati(emptyList(), emptyList(), Serata.nuova(listOf(1, 2, 3, 9))) { nomi[it] },
        ).forEach { dati ->
            assertEquals(0, dati[WearConstants.KEY_NEXT_NUMBER])
            assertEquals("", dati[WearConstants.KEY_NEXT_PLAYERS])
            assertEquals("", dati[WearConstants.KEY_NEXT_BASE])
        }
    }

    @Test
    fun `il separatore dentro un nome diventa uno spazio e un nome resta uno`() {
        val dati = CoppieAlPolso.dati(listOf("Rossi|Mario", "Glorio"), emptyList(), null) { null }
        assertEquals("Rossi Mario|Glorio", dati[WearConstants.KEY_TEAM1_ROSTER])
    }

    @Test
    fun `la base e' quella di SerataCodec`() {
        assertEquals("4,1/3,2", CoppieAlPolso.base(Composizione(listOf(4, 1), listOf(3, 2))))
    }
}
