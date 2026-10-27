package it.vantaggi.scoreboardessential

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.TestLifecycleApplication
import java.lang.reflect.Method

/**
 * L'applicazione di tutti i test Robolectric di `:mobile` (`robolectric.properties`). Possiede lo scope del lavoro di
 * avvio (debito 20 di D-3) e a fine test lo cancella e aspetta che finisca: nessuna coroutine lanciata da
 * `Application.onCreate` sopravvive al test che l'ha lanciata. Se una non finisce entro il tempo, il test fallisce.
 */
class ProvaApplication :
    ScoreboardEssentialApplication(),
    TestLifecycleApplication {
    private val lavori = SupervisorJob()

    /** Il dispatcher del lavoro di avvio: un test puo' fermarlo per guardare il lavoro prima che giri. */
    var dispatcherDiAvvio: CoroutineDispatcher = Dispatchers.IO

    override val scopeDiAvvio: CoroutineScope get() = CoroutineScope(lavori + dispatcherDiAvvio)

    /** I lavori di avvio lanciati e non ancora finiti. */
    fun lavoriVivi(): Int = lavori.children.count { it.isActive }

    override fun beforeTest(method: Method) = Unit

    override fun prepareTest(test: Any) = Unit

    override fun afterTest(method: Method) {
        runBlocking { withTimeout(10_000) { lavori.cancelAndJoin() } }
    }
}
