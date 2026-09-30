package it.vantaggi.scoreboardessential

import android.os.Looper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.robolectric.Shadows.shadowOf

/**
 * Svuota il looper principale e assorbe le eccezioni che il lavoro residuo lascia indietro.
 *
 * Serve ai test che devono chiamare `idle()` (per esempio perche' LocalBroadcastManager consegna
 * sul looper principale). Alcuni test precedenti lasciano in coda coroutine di un ViewModel che
 * scrivono su un database gia' chiuso: `idle()` le esegue, l'eccezione finisce nel raccoglitore
 * di kotlinx-coroutines-test e viene addebitata al PROSSIMO test che usa `runTest`, che fallisce
 * con UncaughtExceptionsBeforeTest senza averne colpa. Un `runTest` vuoto, chiamato qui, consuma
 * quel rapporto al posto dell'innocente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun assorbiLavoroResiduoDelLooper() {
    shadowOf(Looper.getMainLooper()).idle()
    try {
        runTest { }
    } catch (assorbita: Throwable) {
        // Attesa: e' il rapporto delle eccezioni lasciate da altri test.
    }
}
