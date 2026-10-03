package it.vantaggi.scoreboardessential

import com.google.android.gms.wearable.DataMap
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La sequenza di un'intenzione si legge una volta sola, qualunque sia il tipo con cui e' stata
 * scritta: il vecchio `getLong(k, getInt(k, 0).toLong())` faceva girare getInt su un Long a ogni
 * messaggio, e GMS scriveva nel log una ClassCastException.
 */
@RunWith(RobolectricTestRunner::class)
class SequenzaDelMessaggioTest {
    private fun conSeq(scrivi: DataMap.() -> Unit) = DataMap().apply(scrivi)

    @Test
    fun `una sequenza Long e una Int danno lo stesso valore`() {
        val comeLong = conSeq { putLong(WearConstants.KEY_SEQ, 1_700_000_000_123L) }
        val comeInt = conSeq { putInt(WearConstants.KEY_SEQ, 42) }

        assertEquals(1_700_000_000_123L, SimplifiedDataLayerListenerService.leggiSeq(comeLong))
        assertEquals(42L, SimplifiedDataLayerListenerService.leggiSeq(comeInt))
        assertEquals(
            SimplifiedDataLayerListenerService.leggiSeq(conSeq { putLong(WearConstants.KEY_SEQ, 7L) }),
            SimplifiedDataLayerListenerService.leggiSeq(conSeq { putInt(WearConstants.KEY_SEQ, 7) }),
        )
    }

    @Test
    fun `senza sequenza o con un tipo estraneo vale zero, che la guardia scarta`() {
        assertEquals(0L, SimplifiedDataLayerListenerService.leggiSeq(DataMap()))
        assertEquals(0L, SimplifiedDataLayerListenerService.leggiSeq(conSeq { putString(WearConstants.KEY_SEQ, "x") }))
    }
}
