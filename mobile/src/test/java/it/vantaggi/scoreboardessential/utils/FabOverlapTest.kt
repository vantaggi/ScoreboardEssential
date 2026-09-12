package it.vantaggi.scoreboardessential.utils

import it.vantaggi.scoreboardessential.utils.FabOverlap.Box
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometria di un telefono in verticale largo 1080px: FAB da 147px con 42px di margine agli
 * angoli in basso, e la riga delle azioni che attraversa tutta la larghezza.
 */
class FabOverlapTest {
    private val statsFab = Box(42, 2000, 189, 2147)
    private val playersFab = Box(891, 2000, 1038, 2147)
    private val fabs = listOf(statsFab, playersFab)

    private fun riga(top: Int): List<Box> {
        val bottom = top + 126
        return listOf(
            Box(42, top, 400, bottom), // HISTORY
            Box(421, top, 779, bottom), // END MATCH
            Box(800, top, 1038, bottom), // SHARE
        )
    }

    @Test
    fun rigaSottoIFab_iFabSiNascondono() {
        // Il caso degli screenshot: in posizione di riposo la riga cade nella fascia dei FAB.
        assertTrue(FabOverlap.fabsMustHide(riga(top = 1990), fabs))
    }

    @Test
    fun rigaSfioraSoloIlBordoAltoDeiFab_contaComeCoperta() {
        assertTrue(FabOverlap.fabsMustHide(riga(top = 2000 - 125), fabs))
    }

    @Test
    fun rigaSopraIFab_iFabRestano() {
        // Fine scorrimento: il margine in fondo la porta sopra i FAB.
        assertFalse(FabOverlap.fabsMustHide(riga(top = 1700), fabs))
    }

    @Test
    fun rigaFuoriSchermo_iFabRestanoRaggiungibili() {
        // Riga ancora sotto il bordo inferiore, o gia' passata sotto l'intestazione fissa.
        assertFalse(FabOverlap.fabsMustHide(riga(top = 2400), fabs))
        assertFalse(FabOverlap.fabsMustHide(riga(top = -500), fabs))
    }

    @Test
    fun toccarsiSulBordo_nonECoprire() {
        assertFalse(FabOverlap.fabsMustHide(riga(top = 2000 - 126), fabs))
    }

    @Test
    fun soloIlComandoCentraleNellaFascia_nonTraAIFab() {
        // Un comando stretto al centro non tocca i FAB agli angoli: nasconderli sarebbe inutile.
        val centrale = listOf(Box(421, 2010, 779, 2100))
        assertFalse(FabOverlap.fabsMustHide(centrale, fabs))
    }

    @Test
    fun rettangoliVuotiPrimaDelLayout_nonNascondono() {
        assertFalse(FabOverlap.fabsMustHide(listOf(Box(0, 0, 0, 0)), listOf(Box(0, 0, 0, 0))))
    }
}
