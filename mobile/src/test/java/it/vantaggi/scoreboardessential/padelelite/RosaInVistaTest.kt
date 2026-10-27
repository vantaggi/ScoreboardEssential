package it.vantaggi.scoreboardessential.padelelite

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La rosa del gruppo divisa per la schermata (passo 4): i collegati sempre in vista e in ordine di
 * nome, gli altri dietro la ricerca, i nomi uguali da confermare sempre in vista. Rosso se tornano
 * tutti i "Non collegato" a campo vuoto, se i collegati finiscono in ordine sparso, o se la ricerca
 * distingue maiuscole o accenti.
 */
class RosaInVistaTest {
    private var prossimo = 0

    private fun riga(
        nome: String,
        collegato: String? = null,
        proposto: String? = null,
    ) = RosaRow(RemotePlayer(++prossimo, nome), collegato, proposto)

    private val rosa =
        listOf(
            riga("Vantaggi", collegato = "Mattia"),
            riga("Àlvarez"),
            riga("Serrone", collegato = "Serrone"),
            riga("Rossi", proposto = "Rossi"),
            riga("Bianchi"),
            riga("Alberti"),
            riga("Ragni", collegato = "Ragni"),
        )

    private fun nomi(righe: List<RosaRow>) = righe.map { it.remote.name }

    @Test
    fun `a campo vuoto ci sono i collegati in ordine e i soli nomi da confermare`() {
        val vista = RosaInVista.di(rosa, "")

        assertEquals(listOf("Ragni", "Serrone", "Vantaggi"), nomi(vista.collegati))
        assertEquals(listOf("Rossi"), nomi(vista.daCollegare))
        assertEquals(4, vista.totaleDaCollegare)
    }

    @Test
    fun `la ricerca trova i da collegare senza badare a maiuscole e accenti`() {
        val vista = RosaInVista.di(rosa, "  AL ")

        assertEquals(listOf("Rossi", "Alberti", "Àlvarez"), nomi(vista.daCollegare))
        assertEquals(3, vista.collegati.size)
        assertEquals(4, vista.totaleDaCollegare)
    }

    @Test
    fun `la ricerca non tocca i collegati e senza risultati restano i nomi da confermare`() {
        val vista = RosaInVista.di(rosa, "vantaggi")

        assertEquals(listOf("Rossi"), nomi(vista.daCollegare))
        assertEquals(listOf("Ragni", "Serrone", "Vantaggi"), nomi(vista.collegati))
    }
}
