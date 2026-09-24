package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La regola del colore protegge qualsiasi colore l'utente scelga, non solo i predefiniti: per
 * questo il test non si fida di una manciata di esempi e scorre tutto l'RGB a passo 3 (86^3
 * colori, estremi compresi, perche' 255 = 3 x 85).
 */
class TeamInkTest {
    private fun argb(rgb: Int) = TeamInk.NERO or rgb

    private fun ogniColore(azione: (Int) -> Unit) {
        for (r in 0..255 step 3) {
            for (g in 0..255 step 3) {
                for (b in 0..255 step 3) {
                    azione(argb((r shl 16) or (g shl 8) or b))
                }
            }
        }
    }

    @Test
    fun `l'inchiostro sopra un colore da' almeno 4,5 su tutto l'RGB`() {
        var peggiore = 21.0
        var dove = 0
        ogniColore { colore ->
            val c = TeamInk.contrast(TeamInk.on(colore), colore)
            if (c < peggiore) {
                peggiore = c
                dove = colore
            }
        }
        assertTrue("minimo %.3f su #%06X".format(peggiore, dove and 0xFFFFFF), peggiore >= 4.5)
        // Il minimo teorico e' 4,58: se la scansione trova di meno, la soglia e' stata toccata.
        assertEquals(4.58, peggiore, 0.01)
    }

    @Test
    fun `la grafica su nero da' almeno 3 su tutto l'RGB`() {
        ogniColore { colore ->
            val grafica = TeamInk.graphicOnBlack(colore)
            val c = TeamInk.contrast(grafica, TeamInk.NERO)
            assertTrue("#%06X diventa #%06X a %.3f".format(colore and 0xFFFFFF, grafica and 0xFFFFFF, c), c >= 3.0)
        }
    }

    @Test
    fun `casi fissi dell'inchiostro`() {
        assertEquals(TeamInk.NERO, TeamInk.on(argb(0xFFD600)))
        assertEquals(14.87, TeamInk.contrast(TeamInk.NERO, argb(0xFFD600)), 0.01)
        assertEquals(TeamInk.BIANCO, TeamInk.on(argb(0x1A237E)))
        assertEquals(13.24, TeamInk.contrast(TeamInk.BIANCO, argb(0x1A237E)), 0.01)
        assertEquals(TeamInk.NERO, TeamInk.on(argb(0xF50057)))
        assertEquals(5.02, TeamInk.contrast(TeamInk.NERO, argb(0xF50057)), 0.01)
        assertEquals(TeamInk.NERO, TeamInk.on(argb(0xFF1744)))
        assertEquals(TeamInk.NERO, TeamInk.on(argb(0x76FF03)))
        // Il caso peggiore: sta appena sopra la soglia, e nero e bianco valgono entrambi 4,58.
        assertEquals(TeamInk.NERO, TeamInk.on(argb(0x5D60FF)))
        assertEquals(4.58, TeamInk.contrast(TeamInk.NERO, argb(0x5D60FF)), 0.01)
        assertEquals(4.58, TeamInk.contrast(TeamInk.BIANCO, argb(0x5D60FF)), 0.01)
    }

    /**
     * #8454F6 ha luminanza 0,1790000: sopra lo 0,179 che usava la pista dell'orologio, sotto la
     * soglia esatta 0,179129. Le due piste gli davano inchiostri opposti. Questo caso fissa l'esito
     * della regola unica, cosi' nessuno puo' reintrodurre un arrotondamento diverso senza vederlo.
     */
    @Test
    fun `un colore nella banda fra i due arrotondamenti ha inchiostro bianco`() {
        val l = TeamInk.luminance(argb(0x8454F6))
        assertTrue("luminanza $l fuori dalla banda", l > 0.179 && l < 0.17913)
        assertEquals(0.179129, TeamInk.SOGLIA, 0.000001)
        assertEquals(TeamInk.BIANCO, TeamInk.on(argb(0x8454F6)))
    }

    @Test
    fun `contrasti di riferimento`() {
        assertEquals(21.0, TeamInk.contrast(TeamInk.BIANCO, TeamInk.NERO), 0.001)
        assertEquals(21.0, TeamInk.contrast(TeamInk.NERO, TeamInk.BIANCO), 0.001)
        assertEquals(1.0, TeamInk.contrast(argb(0x123456), argb(0x123456)), 0.001)
        assertEquals(1.59, TeamInk.contrast(argb(0x1A237E), TeamInk.NERO), 0.01)
    }

    @Test
    fun `casi fissi della grafica su nero`() {
        // Chi ha gia' 3:1 resta com'e'.
        assertEquals(argb(0xFFD600), TeamInk.graphicOnBlack(argb(0xFFD600)))
        assertEquals(argb(0x76FF03), TeamInk.graphicOnBlack(argb(0x76FF03)))
        assertEquals(argb(0xF50057), TeamInk.graphicOnBlack(argb(0xF50057)))
        assertEquals(argb(0xFF0000), TeamInk.graphicOnBlack(argb(0xFF0000)))
        // Gli altri salgono verso il bianco quel tanto che basta, tinta compresa.
        assertEquals(argb(0x4F4FA7), TeamInk.graphicOnBlack(argb(0x000080)))
        assertEquals(argb(0x3333FF), TeamInk.graphicOnBlack(argb(0x0000FF)))
        assertEquals(argb(0x5C5C5C), TeamInk.graphicOnBlack(argb(0x000000)))
        assertEquals(argb(0x4C539A), TeamInk.graphicOnBlack(argb(0x1A237E)))
        assertEquals(argb(0xA23333), TeamInk.graphicOnBlack(argb(0x8B0000)))
    }

    @Test
    fun `una soglia piu' alta schiarisce di piu'`() {
        val c = TeamInk.graphicOnBlack(argb(0x000080), min = 4.5)
        assertTrue(TeamInk.contrast(c, TeamInk.NERO) >= 4.5)
        assertTrue(TeamInk.luminance(c) > TeamInk.luminance(TeamInk.graphicOnBlack(argb(0x000080))))
    }
}
