package it.vantaggi.scoreboardessential.wear

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * La scelta di chi serve a tutto schermo: le domande una dopo l'altra nella stessa schermata, e
 * l'esito restituito al quadrante. Rosso se il titolo non cambia con la domanda, se la spunta non e'
 * sulla proposta o se il risultato non porta la scelta fatta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it-w192dp-h192dp-round-notnight")
class SceltaDelServizioActivityTest {
    private val input =
        InputDelServizio(
            nomi = listOf("ROS/BIA", "VER/NER"),
            rose = listOf(listOf("Rossi", "Bianchi"), listOf("Verdi", "Neri")),
            lato = 1,
            prossimi = listOf(1, 1),
            slotAttuale = 1,
            correzione = false,
        )

    private fun apri(input: InputDelServizio) =
        Robolectric
            .buildActivity(
                SceltaDelServizioActivity::class.java,
                SceltaDelServizioActivity.intent(RuntimeEnvironment.getApplication(), input),
            ).setup()
            .get()

    private fun righe(activity: Activity): List<View> {
        val colonna = activity.findViewById<LinearLayout>(R.id.sport_voci)
        return (0 until colonna.childCount).map(colonna::getChildAt)
    }

    private fun titolo(activity: Activity) = activity.findViewById<TextView>(R.id.sport_title).text.toString()

    @Test
    fun `tre domande, poi la scelta torna al quadrante`() {
        val activity = apri(input)
        assertEquals("Chi serve?", titolo(activity))
        assertEquals(2, righe(activity).size)
        assertTrue("la proposta ha la spunta", righe(activity)[0].contentDescription.toString().endsWith("Proposta"))

        righe(activity)[1].performClick()
        assertEquals("Chi serve per primo in VER/NER?", titolo(activity))
        righe(activity)[1].performClick()
        assertEquals("Chi serve per primo in ROS/BIA?", titolo(activity))
        righe(activity)[0].performClick()

        assertTrue(activity.isFinishing)
        val esito = SceltaDelServizioActivity.esito(shadowOf(activity).resultIntent)
        assertEquals(EsitoDelServizio.Scelta(lato = 2, slot1 = 1, slot2 = 2), esito)
    }

    @Test
    fun `indietro torna alla domanda prima`() {
        val activity = apri(input)
        righe(activity)[0].performClick()
        activity.onBackPressedDispatcher.onBackPressed()
        assertEquals("Chi serve?", titolo(activity))
    }

    @Test
    fun `la correzione e' una domanda sola e torna come correzione`() {
        val activity = apri(input.copy(correzione = true))
        assertEquals("Chi sta servendo adesso?", titolo(activity))
        assertEquals(4, righe(activity).size)
        righe(activity)[2].performClick()
        assertEquals(
            EsitoDelServizio.Correzione(lato = 2, slot = 1),
            SceltaDelServizioActivity.esito(shadowOf(activity).resultIntent),
        )
    }
}
