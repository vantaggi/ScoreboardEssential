package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier

/**
 * Il blocco di [DirettaStore] e' per file di preferenze, non per istanza (debito 20 di D-3): chi toglie il segno di una
 * chiusura gia' scritta in WorkManager gira su un altro thread e con un altro store, e non deve perdere ne' cancellare
 * il segno appena messo da chi chiude. Con un blocco per istanza i due leggono e riscrivono l'insieme a turno, e uno
 * dei due cancella cio' che l'altro ha appena scritto.
 */
@RunWith(AndroidJUnit4::class)
class DirettaStoreConcorrenzaTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun richiesta(matchId: String) = DirettaChiusuraRichiesta(matchId, "g-1", conRisultato = true)

    @Test
    fun `due store sullo stesso file su due thread non perdono segni e non ne tolgono di altri`() {
        val prefs = preferenze(context, "diretta_concorrenza")
        val chiChiude = DirettaStore(prefs)
        val chiAccoda = DirettaStore(prefs)
        val via = CyclicBarrier(2)
        val errori = ConcurrentLinkedQueue<Throwable>()

        fun thread(lavoro: () -> Unit) =
            Thread {
                try {
                    via.await()
                    lavoro()
                } catch (t: Throwable) {
                    errori += t
                }
            }.apply { start() }

        val accoda =
            thread {
                repeat(GIRI) {
                    chiAccoda.segnaPendente(richiesta("a-$it"))
                    chiAccoda.togliPendente("a-$it")
                }
            }
        val chiude = thread { repeat(GIRI) { chiChiude.segnaPendente(richiesta("c-$it")) } }
        accoda.join(60_000)
        chiude.join(60_000)

        assertFalse("i thread hanno finito", accoda.isAlive || chiude.isAlive)
        assertTrue("nessun errore: $errori", errori.isEmpty())
        val restano = DirettaStore(prefs).pendenti().map { it.matchId }.toSet()
        assertEquals("i segni di chi chiude ci sono tutti, quelli tolti no", (0 until GIRI).map { "c-$it" }.toSet(), restano)
    }

    private companion object {
        const val GIRI = 200
    }
}
