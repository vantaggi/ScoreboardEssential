package it.vantaggi.scoreboardessential

import android.content.Context
import android.os.SystemClock
import android.widget.ListView
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import it.vantaggi.scoreboardessential.core.PresenteDellaDashboard.Collegato
import it.vantaggi.scoreboardessential.core.PresenteDellaDashboard.Ospite
import it.vantaggi.scoreboardessential.core.Traduzione
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.padelelite.FonteSerataDashboard
import it.vantaggi.scoreboardessential.padelelite.SerataDashboard
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.anything
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Quanto resta davvero, su un dispositivo, il messaggio con "Annulla" dopo "Usa i presenti": 8 secondi
 * (`undo_window`), anche quando prima si risponde a una domanda sugli omonimi. Sul telefono era sembrato sparire
 * prima di 6 secondi; Robolectric controlla solo la durata impostata, non il tempo dello schermo.
 *
 * La dashboard e' una fonte finta: nessuna richiesta a Padel Elite. Il database e' in memoria.
 */
@RunWith(AndroidJUnit4::class)
class SerataAnnullaStrumentatoTest {
    private lateinit var db: AppDatabase
    private val contesto: Context get() = ApplicationProvider.getApplicationContext()
    private val strumenti get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun prepara() {
        db =
            Room
                .inMemoryDatabaseBuilder(contesto, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase::class.java
            .getDeclaredField("instance")
            .apply { isAccessible = true }
            .set(null, db)
        contesto
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun chiudi() {
        SerataActivity.fonteDiProva = null
        AppDatabase::class.java
            .getDeclaredField("instance")
            .apply { isAccessible = true }
            .set(null, null)
        db.close()
        contesto
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun senza_domande_il_messaggio_con_annulla_resta_otto_secondi() {
        val ids = giocatori("A", "B", "C", "D")
        dallaDashboard(Traduzione(ids.mapIndexed { i, id -> Collegato(100 + i, "x$i", id) }), gruppo = null)
        ActivityScenario.launch(SerataActivity::class.java).use { scena ->
            toccaUsaIPresenti(scena)
            misura(scena)
        }
    }

    @Test
    fun dopo_la_domanda_sull_omonimo_il_messaggio_con_annulla_resta_otto_secondi() {
        val ids = giocatori("A", "B", "C", "Rossi")
        val collegati = ids.take(3).mapIndexed { i, id -> Collegato(100 + i, "x$i", id) }
        dallaDashboard(Traduzione(collegati + Ospite(300, "Rossi")), gruppo = "g-1")
        ActivityScenario.launch(SerataActivity::class.java).use { scena ->
            toccaUsaIPresenti(scena)
            // "E' lui": la riga 0 e' la spiegazione, la 1 l'omonimo.
            onData(anything())
                .inRoot(isDialog())
                .inAdapterView(isAssignableFrom(ListView::class.java))
                .atPosition(1)
                .perform(click())
            misura(scena)
        }
    }

    private fun giocatori(vararg nomi: String): List<Int> =
        runBlocking { nomi.map { db.playerDao().insert(Player(playerName = it, appearances = 0, goals = 0)).toInt() } }

    private fun dallaDashboard(
        presenti: Traduzione,
        gruppo: String?,
    ) {
        val risposta = SerataDashboard.Pronta(LocalDate.now(), presenti, gruppo = gruppo)
        SerataActivity.fonteDiProva = FonteSerataDashboard { risposta }
    }

    private fun toccaUsaIPresenti(scena: ActivityScenario<SerataActivity>) {
        aspetta("la proposta della dashboard") {
            var pronta = false
            scena.onActivity { pronta = (it.findViewById<RecyclerView>(R.id.serata_dashboard_rows).adapter?.itemCount ?: 0) > 0 }
            pronta
        }
        scena.onActivity { attivita ->
            val righe = attivita.findViewById<RecyclerView>(R.id.serata_dashboard_rows)
            righe.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
        }
    }

    /** Dal momento in cui il messaggio compare a quando sparisce, e perche' sparisce. */
    private fun misura(scena: ActivityScenario<SerataActivity>) {
        var avviso: Snackbar? = null
        aspetta("il messaggio con Annulla") {
            scena.onActivity { avviso = it.avvisoConAnnulla?.takeIf { s -> s.isShown } }
            avviso != null
        }
        val comparso = SystemClock.elapsedRealtime()
        var motivo = -1
        strumenti.runOnMainSync {
            avviso!!.addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(
                        transientBottomBar: Snackbar?,
                        event: Int,
                    ) {
                        motivo = event
                    }
                },
            )
        }
        var sparito = 0L
        while (SystemClock.elapsedRealtime() - comparso < 15_000) {
            var visibile = true
            strumenti.runOnMainSync { visibile = avviso!!.isShown }
            if (!visibile) {
                sparito = SystemClock.elapsedRealtime()
                break
            }
            SystemClock.sleep(50)
        }
        val durata = if (sparito == 0L) -1L else sparito - comparso
        // onDismissed arriva alla fine della dissolvenza.
        aspetta("il motivo della chiusura") {
            var letto = -1
            strumenti.runOnMainSync { letto = motivo }
            letto != -1
        }
        android.util.Log.i(
            "SerataAnnulla",
            "durata=$durata ms, motivo=$motivo, impostata=${avviso!!.duration}",
        )
        assertTrue("il messaggio deve sparire da solo entro 15 s (durata=$durata)", durata > 0)
        assertEquals(
            "sparisce per il tempo scaduto (2 = TIMEOUT), non per altro",
            BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_TIMEOUT,
            motivo,
        )
        assertTrue("resta circa 8 s, non i 3 di sistema (durata=$durata ms)", durata in 7_500..9_500)
    }

    private fun aspetta(
        cosa: String,
        condizione: () -> Boolean,
    ) {
        val limite = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < limite) {
            strumenti.waitForIdleSync()
            if (condizione()) return
            SystemClock.sleep(20)
        }
        throw AssertionError("non arriva: $cosa")
    }
}
