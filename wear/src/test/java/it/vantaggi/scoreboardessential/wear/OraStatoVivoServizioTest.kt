package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * L'ora dell'ultimo stato vivo ("SCOLLEGATO · 18:42") la scrive il servizio, che riceve i v2 anche
 * ad app chiusa: se la scrivesse solo il ViewModel, ad app chiusa non si aggiornerebbe mai.
 */
@RunWith(RobolectricTestRunner::class)
class OraStatoVivoServizioTest {
    private val contesto: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setup() {
        contesto
            .getSharedPreferences("wear_last_known_match", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun statoV2(): DataItem {
        val item = Mockito.mock(DataItem::class.java)
        Mockito.`when`(item.uri).thenReturn(Uri.parse("wear://nodo" + WearConstants.PATH_STATE_V2))
        Mockito.`when`(item.data).thenReturn(DataMap().toByteArray())
        // DataMapItem rilegge l'item congelato: il finto ritorna se stesso.
        Mockito.`when`(item.freeze()).thenReturn(item)
        return item
    }

    @Test
    fun `un v2 dal vivo scrive l'ora senza che esista nessun ViewModel`() {
        val prima = System.currentTimeMillis()

        WearDataLayerService.dispatchDataItem(contesto, statoV2(), dalVivo = true)

        val scritta = LastKnownMatch(contesto).ricevutoAlle
        assertTrue("l'ora $scritta non e' di adesso", scritta >= prima)
    }

    @Test
    fun `un v2 riletto al risveglio non tocca l'ora di prima`() {
        LastKnownMatch(contesto).segnaStatoVivo(1_700_000_000_000L)

        WearDataLayerService.dispatchDataItem(contesto, statoV2(), dalVivo = false)

        assertEquals(1_700_000_000_000L, LastKnownMatch(contesto).ricevutoAlle)
    }
}
