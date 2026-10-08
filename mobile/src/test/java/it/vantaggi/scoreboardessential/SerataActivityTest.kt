package it.vantaggi.scoreboardessential

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import it.vantaggi.scoreboardessential.core.Serata
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.repository.SerataPrefsStore
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

/** La schermata Serata gonfiata davvero: i gruppi che servono, il primario, la sola lettura. */
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
    }

    @After
    fun chiudi() {
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
}
