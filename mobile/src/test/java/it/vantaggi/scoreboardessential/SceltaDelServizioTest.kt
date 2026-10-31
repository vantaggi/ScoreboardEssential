package it.vantaggi.scoreboardessential

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Le voci dei fogli di chi serve (bozza A): la correzione a partita in corso e le domande «chi serve
 * per primo» a inizio set. Rosso se le voci non dicono al motore il lato e il giocatore giusti, o se
 * la spunta non sta sulla proposta.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "it")
class SceltaDelServizioTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val nomi = listOf("ROS/BIA", "VER/NER")
    private val rose = listOf(listOf("Rossi", "Bianchi"), listOf("Verdi", "Neri"))

    private fun display(
        lato: Int,
        giocatore: Int?,
    ) = ScoreDisplay(side1Primary = "15", side2Primary = "0", servingSide = lato, servingPlayerSlot = giocatore)

    @Test
    fun `la correzione elenca i quattro giocatori con la coppia e spunta chi serve`() {
        val foglio = foglioDellaCorrezione(context, display(lato = 2, giocatore = 2), nomi, rose) { 1 }

        assertEquals(
            listOf("Rossi · ROS/BIA", "Bianchi · ROS/BIA", "Verdi · VER/NER", "Neri · VER/NER"),
            foglio.voci.map { it.testo },
        )
        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1, 2 to 2), foglio.voci.map { it.lato to it.slot })
        assertEquals("spunta su Neri", 3, foglio.spunta)
    }

    @Test
    fun `senza i nomi la correzione sceglie la coppia e il giocatore lo da' la rotazione`() {
        val foglio = foglioDellaCorrezione(context, display(lato = 1, giocatore = 2), nomi, emptyList()) { lato -> if (lato == 1) 2 else 1 }

        assertEquals(listOf("ROS/BIA", "VER/NER"), foglio.voci.map { it.testo })
        assertEquals(listOf(1 to 2, 2 to 1), foglio.voci.map { it.lato to it.slot })
        assertEquals(0, foglio.spunta)
    }

    @Test
    fun `a inizio set si chiede chi serve per primo, a cominciare dalla coppia che apre`() {
        val domande = domandeDelPrimo(context, apre = 2, nomi, rose) { lato -> if (lato == 2) 2 else 1 }

        assertEquals(listOf("Chi serve per primo in VER/NER?", "Chi serve per primo in ROS/BIA?"), domande.map { it.first })
        assertEquals(listOf("Verdi", "Neri"), domande[0].second.voci.map { it.testo })
        assertEquals("la spunta sulla proposta della rotazione", 1, domande[0].second.spunta)
        assertEquals(0, domande[1].second.spunta)
    }

    @Test
    fun `nel singolare o senza rose si sceglie solo la coppia`() {
        assertTrue(domandeDelPrimo(context, apre = 1, nomi, listOf(listOf("Rossi"), listOf("Verdi"))) { 1 }.isEmpty())
        assertTrue(domandeDelPrimo(context, apre = 1, nomi, emptyList()) { 1 }.isEmpty())
    }
}
