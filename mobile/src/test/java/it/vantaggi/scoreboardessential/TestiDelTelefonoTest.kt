package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Testi che facevano danni o mostravano la lingua sbagliata.
 */
@RunWith(AndroidJUnit4::class)
class TestiDelTelefonoTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * «Chiudila: poi arrivera' quella registrata al polso» era un consiglio che faceva perdere i
     * punti: seguendolo si salvava la partita senza quelli dell'orologio, e l'arretrato rifiutato
     * ne creava un'altra di un punto solo. Il testo descrive, non prescrive.
     */
    @Test
    @Config(qualifiers = "it")
    fun `in italiano l'arretrato rifiutato non invita a chiudere la partita`() {
        val testo = context.getString(R.string.watch_batch_rejected)
        assertFalse(testo, testo.contains("Chiudi", ignoreCase = true))
        assertEquals("L'orologio ha mandato punti che non sono entrati in questa partita.", testo)
    }

    @Test
    @Config(qualifiers = "en")
    fun `in inglese l'arretrato rifiutato non invita a chiudere la partita`() {
        val testo = context.getString(R.string.watch_batch_rejected)
        assertFalse(testo, testo.contains("End it", ignoreCase = true))
    }

    @Test
    @Config(qualifiers = "it")
    fun `il tasto finale del tutorial dice Inizia`() {
        assertEquals("Inizia", context.getString(R.string.onboarding_finish))
    }

    @Test
    @Config(qualifiers = "en")
    fun `in inglese il tasto finale del tutorial dice Start`() {
        assertEquals("Start", context.getString(R.string.onboarding_finish))
    }

    // Il titolo del dialogo del marcatore era 'Chi ha segnato?' cablato: in italiano anche per
    // chi usa l'app in inglese.
    @Test
    @Config(qualifiers = "en")
    fun `il titolo del marcatore segue la lingua`() {
        val tema = ContextThemeWrapper(context, R.style.Theme_ScoreboardEssential)
        val radice = LayoutInflater.from(tema).inflate(R.layout.dialog_select_scorer, null)
        assertEquals("Who scored?", radice.findViewById<TextView>(R.id.select_scorer_title).text.toString())
    }

    // 'No formation' era cablato in inglese anche con l'app in italiano.
    @Test
    @Config(qualifiers = "it")
    fun `la formazione assente si dice in italiano`() {
        assertEquals("ROSSI (nessun modulo)", context.getString(R.string.formation_none, "ROSSI"))
        assertEquals("ROSSI (4-4-2)", context.getString(R.string.formation_label, "ROSSI", "4-4-2"))
    }
}
