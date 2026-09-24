package it.vantaggi.scoreboardessential.wear

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Dal 31 agosto 2026 Google Play rifiuta gli aggiornamenti Wear OS che puntano sotto API 35.
 *
 * Il piano dava l'orologio per esente, ma l'esenzione Wear vale solo per il salto ad API 36
 * chiesto al telefono: con targetSdk 34 l'upload del bundle wear veniva rifiutato. Si legge il
 * targetSdk dal manifest unito che Robolectric carica, cioe' quello che finisce nell'APK.
 */
@RunWith(RobolectricTestRunner::class)
class TargetSdkTest {
    @Test
    fun `l'orologio punta almeno ad API 35, il minimo che Play accetta per Wear OS`() {
        val target = RuntimeEnvironment.getApplication().applicationInfo.targetSdkVersion
        assertTrue("targetSdk $target: Play rifiuta gli aggiornamenti Wear OS sotto API 35", target >= 35)
    }
}
