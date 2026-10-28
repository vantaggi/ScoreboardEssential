package it.vantaggi.scoreboardessential.padelelite

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La rosa del gruppo divisa per la schermata (passo 4): i collegati in ordine di nome, poi i nomi
 * uguali da confermare e tutti gli altri, che la ricerca stringe. Rosso se a campo vuoto i "Non
 * collegato" spariscono (Cioti li tocca per collegarli), se i collegati finiscono in ordine sparso,
 * o se la ricerca distingue maiuscole o accenti.
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
    fun `a campo vuoto ci sono tutti con i nomi da confermare in cima e gli altri in ordine`() {
        val vista = RosaInVista.di(rosa, "")

        assertEquals(listOf("Ragni", "Serrone", "Vantaggi"), nomi(vista.collegati))
        assertEquals(listOf("Rossi", "Alberti", "Àlvarez", "Bianchi"), nomi(vista.daCollegare))
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
    fun `la ricerca non tocca i collegati e senza risultati restano i soli nomi da confermare`() {
        val vista = RosaInVista.di(rosa, "vantaggi")

        assertEquals(listOf("Rossi"), nomi(vista.daCollegare))
        assertEquals(listOf("Ragni", "Serrone", "Vantaggi"), nomi(vista.collegati))
    }
}
