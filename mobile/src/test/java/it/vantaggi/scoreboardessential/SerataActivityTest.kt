package it.vantaggi.scoreboardessential

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import it.vantaggi.scoreboardessential.core.PresenteDellaDashboard.Collegato
import it.vantaggi.scoreboardessential.core.PresenteDellaDashboard.Ospite
import it.vantaggi.scoreboardessential.core.Serata
import it.vantaggi.scoreboardessential.core.Traduzione
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.padelelite.FonteSerataDashboard
import it.vantaggi.scoreboardessential.padelelite.GironeDellaDashboard
import it.vantaggi.scoreboardessential.padelelite.PadelEliteActivity
import it.vantaggi.scoreboardessential.padelelite.SerataDashboard
import it.vantaggi.scoreboardessential.repository.SerataPrefsStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * La schermata Serata gonfiata davvero: i gruppi che servono, il primario, la sola lettura, e il gruppo
 * "Dalla dashboard" nei suoi stati (con una fonte finta al posto della rete).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it")
class SerataActivityTest {
    private lateinit var db: AppDatabase
    private val contesto: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun prepara() {
        db =
            Room
                .inMemoryDatabaseBuilder(contesto, AppDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor { it.run() }
                .setTransactionExecutor { it.run() }
                .build()
        val campo = AppDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }
        campo.set(null, db)
        // Senza accesso: la schermata di sempre, qualunque sia il local.properties di chi lancia i test.
        SerataActivity.fonteDiProva = FonteSerataDashboard { SerataDashboard.Spenta }
    }

    @After
    fun chiudi() {
        SerataActivity.fonteDiProva = null
        AppDatabase::class.java
            .getDeclaredField("instance")
            .apply { isAccessible = true }
            .set(null, null)
        db.close()
    }

    private fun giocatori(vararg nomi: String): List<Int> =
        runBlocking { nomi.map { db.playerDao().insert(Player(playerName = it, appearances = 0, goals = 0)).toInt() } }

    private fun apri(inCorso: Boolean = false) =
        Robolectric
            .buildActivity(
                SerataActivity::class.java,
                Intent(contesto, SerataActivity::class.java).putExtra(SerataActivity.EXTRA_PARTITA_IN_CORSO, inCorso),
            ).setup()

    private fun Activity.righe(id: Int) = findViewById<RecyclerView>(id).adapter!!.itemCount

    @Test
    fun `senza serata c'e' solo la rosa, l'ospite, e il primario spento che dice quanti ne mancano`() {
        giocatori("A", "B")
        val attivita = apri().get()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(View.GONE, attivita.findViewById<View>(R.id.serata_next_group).visibility)
        assertEquals(View.GONE, attivita.findViewById<View>(R.id.serata_bench_group).visibility)
        assertEquals("due giocatori e Aggiungi un ospite", 3, attivita.righe(R.id.serata_present))
        assertFalse(attivita.findViewById<MaterialButton>(R.id.serata_start_button).isEnabled)
        assertTrue(attivita.findViewById<android.widget.TextView>(R.id.serata_start_hint).text.contains("Ne mancano 4"))
        assertEquals(View.GONE, attivita.findViewById<View>(R.id.serata_close_button).visibility)
    }

    @Test
    fun `con cinque presenti si vedono quattro posti con le intestazioni, tre comandi e una panchina`() {
        val ids = giocatori("A", "B", "C", "D", "E")
        SerataPrefsStore(contesto.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)).save(Serata.nuova(ids))
        val attivita = apri().get()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, attivita.findViewById<View>(R.id.serata_next_group).visibility)
        assertEquals("due intestazioni e quattro posti", 6, attivita.righe(R.id.serata_seats))
        assertEquals(3, attivita.righe(R.id.serata_commands))
        assertEquals(1, attivita.righe(R.id.serata_bench))
        assertEquals(View.GONE, attivita.findViewById<View>(R.id.serata_bench_empty).visibility)
        val avvio = attivita.findViewById<MaterialButton>(R.id.serata_start_button)
        assertTrue(avvio.isEnabled)
        assertEquals("Partita 1", attivita.findViewById<android.widget.TextView>(R.id.serata_next_title).text)
        assertEquals(View.VISIBLE, attivita.findViewById<View>(R.id.serata_close_button).visibility)

        avvio.performClick()
        assertEquals(Activity.RESULT_OK, shadowOf(attivita).resultCode)
        assertTrue(attivita.isFinishing)
    }

    @Test
    fun `con quattro presenti la panchina e' vuota e lo dice`() {
        val ids = giocatori("A", "B", "C", "D")
        SerataPrefsStore(contesto.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)).save(Serata.nuova(ids))
        val attivita = apri().get()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, attivita.findViewById<View>(R.id.serata_bench_group).visibility)
        assertEquals(View.VISIBLE, attivita.findViewById<View>(R.id.serata_bench_empty).visibility)
        assertEquals(0, attivita.righe(R.id.serata_bench))
    }

    @Test
    fun `durante una partita la schermata e' di sola lettura e lo dice in testa`() {
        val ids = giocatori("A", "B", "C", "D")
        SerataPrefsStore(contesto.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)).save(Serata.nuova(ids))
        val attivita = apri(inCorso = true).get()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, attivita.findViewById<View>(R.id.serata_readonly_notice).visibility)
        assertFalse(attivita.findViewById<MaterialButton>(R.id.serata_start_button).isEnabled)
        assertEquals(View.GONE, attivita.findViewById<View>(R.id.serata_close_button).visibility)
    }

    // ---- Dalla dashboard ----

    private val giovedi = LocalDate.of(2026, 10, 15)

    /** Una fonte finta che conta le letture. */
    private var letture = 0

    private fun dallaDashboard(risposta: SerataDashboard) {
        SerataActivity.fonteDiProva =
            FonteSerataDashboard {
                letture++
                risposta
            }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun Activity.testo(id: Int) = findViewById<TextView>(id).text.toString()

    /** La riga [posizione] di una lista, dopo averla misurata e disposta come farebbe lo schermo; null se non c'e'. */
    private fun Activity.riga(
        lista: Int,
        posizione: Int,
    ): View? {
        val vista = findViewById<RecyclerView>(lista)
        vista.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY),
        )
        vista.layout(0, 0, 1080, 2000)
        return vista.findViewHolderForAdapterPosition(posizione)?.itemView
    }

    /**
     * Fa girare il looper finche' [condizione] e' vera (al massimo 5 secondi): una lista che cambia calcola le
     * differenze su un altro thread, e il risultato arriva al thread principale dopo.
     */
    private fun aspetta(condizione: () -> Boolean) {
        val limite = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < limite) {
            idle()
            if (condizione()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `senza accesso o senza gruppo il gruppo della dashboard non c'e'`() {
        giocatori("A")
        val attivita = apri().get()
        idle()
        assertEquals(View.GONE, attivita.findViewById<View>(R.id.serata_dashboard_group).visibility)
    }

    @Test
    fun `mentre legge lo dice, senza proposte`() {
        SerataActivity.fonteDiProva = FonteSerataDashboard { awaitCancellation() }
        val attivita = apri().get()
        idle()

        assertEquals(View.VISIBLE, attivita.findViewById<View>(R.id.serata_dashboard_group).visibility)
        assertEquals("Lettura della serata dalla dashboard in corso.", attivita.testo(R.id.serata_dashboard_status))
        assertEquals(0, attivita.righe(R.id.serata_dashboard_rows))
        assertEquals(View.GONE, attivita.findViewById<View>(R.id.serata_dashboard_action).visibility)
    }

    @Test
    fun `la serata pronta dice giorno, presenti, ospiti e assenti dalla rosa, e propone presenti e gironi`() {
        val ids = giocatori("A", "B", "C", "D", "E", "F", "G", "H")
        val collegati = ids.mapIndexed { i, id -> Collegato(100 + i, "Nome$i", id) }
        dallaDashboard(
            SerataDashboard.Pronta(
                giovedi,
                Traduzione(collegati + Ospite(300, "Zeta"), nonTrovati = 1),
                gironi = listOf(GironeDellaDashboard(Traduzione(collegati.take(4))), GironeDellaDashboard(Traduzione(collegati.drop(4)))),
            ),
        )
        val attivita = apri().get()
        aspetta { attivita.righe(R.id.serata_dashboard_rows) == 3 }

        val stato = attivita.testo(R.id.serata_dashboard_status)
        assertTrue(stato, stato.startsWith("Serata di giovedì 15 ottobre, 9 presenti."))
        assertTrue(stato, stato.contains("1 non collegato entra come ospite."))
        assertTrue(stato, stato.contains("1 non è nella rosa del gruppo e resta fuori."))
        assertEquals("Usa i presenti e due gironi", 3, attivita.righe(R.id.serata_dashboard_rows))
        assertEquals(View.GONE, attivita.findViewById<View>(R.id.serata_dashboard_action).visibility)
        assertEquals(
            "Girone B: Nome4, Nome5, Nome6, Nome7. Tocca per usarlo come presenti",
            attivita.riga(R.id.serata_dashboard_rows, 2)?.contentDescription,
        )
    }

    @Test
    fun `usa i presenti riempie la serata solo al tocco, crea l'ospite e spunta la proposta`() {
        val ids = giocatori("A", "B", "C")
        dallaDashboard(
            SerataDashboard.Pronta(
                giovedi,
                Traduzione(ids.mapIndexed { i, id -> Collegato(100 + i, "x", id) } + Ospite(300, "Zeta")),
            ),
        )
        val attivita = apri().get()
        aspetta { attivita.righe(R.id.serata_dashboard_rows) == 1 }
        val memoria = SerataPrefsStore(contesto.getSharedPreferences("app_prefs", Context.MODE_PRIVATE))
        assertEquals("prima del tocco niente", null, memoria.load())

        attivita.riga(R.id.serata_dashboard_rows, 0)!!.performClick()
        aspetta { attivita.righe(R.id.serata_present) == 5 && attivita.riga(R.id.serata_dashboard_rows, 0)?.isSelected == true }

        val serata = memoria.load()!!
        assertEquals(4, serata.presenti.size)
        assertEquals(ids, serata.presenti.take(3))
        assertTrue("con quattro presenti si puo' cominciare", attivita.findViewById<MaterialButton>(R.id.serata_start_button).isEnabled)
        assertTrue(attivita.riga(R.id.serata_dashboard_rows, 0)!!.isSelected)
        assertEquals("i tre e l'ospite, piu' Aggiungi un ospite", 5, attivita.righe(R.id.serata_present))
    }

    @Test
    fun `nessuna serata configurata e nessun presente lo dicono, senza proposte`() {
        dallaDashboard(SerataDashboard.NonConfigurata)
        val senzaSerata = apri().get()
        idle()
        assertEquals("Il gruppo non ha una serata configurata nella dashboard.", senzaSerata.testo(R.id.serata_dashboard_status))
        assertEquals(0, senzaSerata.righe(R.id.serata_dashboard_rows))

        dallaDashboard(SerataDashboard.Pronta(giovedi, Traduzione(emptyList())))
        val senzaPresenti = apri().get()
        idle()
        assertEquals("Serata di giovedì 15 ottobre. Nessun presente confermato.", senzaPresenti.testo(R.id.serata_dashboard_status))
        assertEquals(0, senzaPresenti.righe(R.id.serata_dashboard_rows))
    }

    @Test
    fun `i gironi di un'altra serata si ignorano e lo dice`() {
        val (a) = giocatori("A")
        dallaDashboard(SerataDashboard.Pronta(giovedi, Traduzione(listOf(Collegato(1, "A", a))), gironiDiUnAltraSerata = true))
        val attivita = apri().get()
        aspetta { attivita.righe(R.id.serata_dashboard_rows) == 1 }

        assertTrue(attivita.testo(R.id.serata_dashboard_status).endsWith("I gironi salvati sono di una serata diversa e non si usano."))
        assertEquals("solo Usa i presenti", 1, attivita.righe(R.id.serata_dashboard_rows))
    }

    @Test
    fun `un errore di rete offre Riprova, che rilegge`() {
        dallaDashboard(SerataDashboard.Errore)
        val attivita = apri().get()
        idle()
        val prima = letture

        assertEquals("La dashboard non risponde. Controlla la connessione.", attivita.testo(R.id.serata_dashboard_status))
        val comando = attivita.findViewById<MaterialButton>(R.id.serata_dashboard_action)
        assertEquals(View.VISIBLE, comando.visibility)
        assertEquals("Riprova", comando.text.toString())

        comando.performClick()
        idle()
        assertEquals(prima + 1, letture)
    }

    @Test
    fun `la sessione scaduta offre Accedi, che apre Padel Elite`() {
        dallaDashboard(SerataDashboard.AccessoScaduto)
        val attivita = apri().get()
        idle()

        assertTrue(attivita.testo(R.id.serata_dashboard_status).startsWith("Accesso a Padel Elite scaduto."))
        val comando = attivita.findViewById<MaterialButton>(R.id.serata_dashboard_action)
        assertEquals("Accedi", comando.text.toString())
        comando.performClick()
        assertEquals(PadelEliteActivity::class.java.name, shadowOf(attivita).nextStartedActivity.component?.className)
    }

    @Test
    fun `durante una partita le proposte ci sono ma sono spente`() {
        val ids = giocatori("A", "B", "C", "D")
        SerataPrefsStore(contesto.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)).save(Serata.nuova(ids))
        dallaDashboard(SerataDashboard.Pronta(giovedi, Traduzione(ids.map { Collegato(it, "x", it) })))
        val attivita = apri(inCorso = true).get()
        aspetta { attivita.righe(R.id.serata_dashboard_rows) == 1 }

        assertFalse(attivita.riga(R.id.serata_dashboard_rows, 0)!!.isEnabled)
    }
}
