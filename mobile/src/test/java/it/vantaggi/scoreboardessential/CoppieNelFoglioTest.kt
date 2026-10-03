package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import it.vantaggi.scoreboardessential.core.SportRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * COPPIE nel foglio PARTITA (passo 14): due posti numerati per lato, scambio da 48dp, lucchetto
 * dopo il primo punto. Il foglio gonfiato da solo e `mostraLeCoppie` sulle sue viste, come per
 * i pallini del servizio: `MainActivity` sotto Robolectric non si monta. Che lo scambio chiami
 * davvero il ViewModel e che le rose restino dopo la rotazione lo provano MainViewModelTest e lo
 * strumentato.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class CoppieNelFoglioTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private fun gonfiaIlFoglio(): View {
        val contesto = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)
        val radice = LayoutInflater.from(contesto).inflate(R.layout.content_scoreboard_details, null)
        radice.layoutDirection = View.LAYOUT_DIRECTION_LTR
        return radice
    }

    private fun giocatoriPerLato(sportId: String): Int? = SportRegistry.byId(sportId).capabilities.playersPerSide

    private fun mostra(
        foglio: View,
        sportId: String,
        rose: List<List<String>>,
        partitaIniziata: Boolean = false,
    ) = mostraLeCoppie(foglio, giocatoriPerLato(sportId), rose, listOf("ROSSI", "BIANCHI"), partitaIniziata)

    private fun testo(
        foglio: View,
        id: Int,
    ): String = foglio.findViewById<TextView>(id).text.toString()

    private fun scambio(
        foglio: View,
        squadra: Int,
    ) = foglio.findViewById<MaterialButton>(if (squadra == 1) R.id.team1_swap_button else R.id.team2_swap_button)

    @Test
    fun `nel padel i posti sono numerati e mostrano i giocatori della rosa in ordine`() {
        val foglio = gonfiaIlFoglio()

        mostra(foglio, SportRegistry.PADEL, listOf(listOf("Marco", "Luca"), listOf("Anna", "Sara")))

        assertEquals(View.VISIBLE, foglio.findViewById<View>(R.id.pairs_card).visibility)
        assertEquals("Marco", testo(foglio, R.id.team1_slot1_name))
        assertEquals("Luca", testo(foglio, R.id.team1_slot2_name))
        assertEquals("Anna", testo(foglio, R.id.team2_slot1_name))
        assertEquals("Sara", testo(foglio, R.id.team2_slot2_name))
        // Due posti per lato, con il loro numero.
        val numeri =
            listOf(R.id.team1_slot1, R.id.team1_slot2, R.id.team2_slot1, R.id.team2_slot2).map { id ->
                (foglio.findViewById<android.view.ViewGroup>(id).getChildAt(0) as TextView).text.toString()
            }
        assertEquals(listOf("1", "2", "1", "2"), numeri)
    }

    @Test
    fun `un posto senza giocatore dice libero e lo scambio e spento`() {
        val foglio = gonfiaIlFoglio()

        mostra(foglio, SportRegistry.PADEL, listOf(listOf("Marco"), emptyList()))

        assertEquals("Marco", testo(foglio, R.id.team1_slot1_name))
        assertEquals(app.getString(R.string.pair_slot_empty), testo(foglio, R.id.team1_slot2_name))
        assertEquals(app.getString(R.string.pair_slot_empty), testo(foglio, R.id.team2_slot1_name))
        assertFalse("con un solo giocatore non c'e' niente da scambiare", scambio(foglio, 1).isEnabled)
        assertFalse(scambio(foglio, 2).isEnabled)
    }

    @Test
    fun `a registro vuoto lo scambio e acceso, dal primo punto e spento col lucchetto`() {
        val foglio = gonfiaIlFoglio()
        val rose = listOf(listOf("Marco", "Luca"), listOf("Anna", "Sara"))

        mostra(foglio, SportRegistry.PADEL, rose, partitaIniziata = false)
        assertTrue(scambio(foglio, 1).isEnabled)
        assertTrue(scambio(foglio, 2).isEnabled)
        val descrizioneAcceso = scambio(foglio, 1).contentDescription.toString()
        assertTrue("dice di quale squadra: $descrizioneAcceso", descrizioneAcceso.contains("ROSSI"))

        mostra(foglio, SportRegistry.PADEL, rose, partitaIniziata = true)
        assertFalse("dopo il primo punto l'ordine e' quello della partita", scambio(foglio, 1).isEnabled)
        assertFalse(scambio(foglio, 2).isEnabled)
        assertEquals(app.getString(R.string.pair_swap_locked), scambio(foglio, 1).contentDescription.toString())
        // Il comando resta al suo posto: sparire non spiegherebbe niente.
        assertEquals(View.VISIBLE, scambio(foglio, 1).visibility)
        assertEquals("i posti restano leggibili", "Marco", testo(foglio, R.id.team1_slot1_name))
    }

    /**
     * Rilievo della revisione: la descrizione stava sulla riga, che non era un nodo a se', e il
     * nome dentro restava importante: TalkBack leggeva il nome due volte. Ora la riga e' un solo
     * nodo con la descrizione completa, e il nome dentro non conta per l'accessibilita'.
     */
    @Test
    fun `ogni posto e un solo nodo di accessibilita e il nome dentro non si legge due volte`() {
        val foglio = gonfiaIlFoglio()
        mostra(foglio, SportRegistry.PADEL, listOf(listOf("Marco", "Luca"), listOf("Anna", "Sara")))
        val righe = listOf(R.id.team1_slot1, R.id.team1_slot2, R.id.team2_slot1, R.id.team2_slot2)
        val nomi = listOf(R.id.team1_slot1_name, R.id.team1_slot2_name, R.id.team2_slot1_name, R.id.team2_slot2_name)

        righe.zip(nomi).forEachIndexed { i, (rigaId, nomeId) ->
            val riga = foglio.findViewById<View>(rigaId)
            // Sulla vista e non sul nodo: Robolectric non riporta flag e descrizione sul nodo, che
            // sul dispositivo li copia dalla vista.
            assertTrue("posto $i: la riga e' un nodo letto dallo screen reader", riga.isScreenReaderFocusable)
            assertTrue("posto $i: la riga porta la descrizione", riga.contentDescription.toString().isNotEmpty())
            val nome = foglio.findViewById<TextView>(nomeId)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, nome.importantForAccessibility)
            assertFalse("posto $i: il nome non e' un secondo nodo", nome.isImportantForAccessibility)
        }
    }

    @Test
    fun `il comando di scambio e alto almeno 48dp`() {
        val foglio = gonfiaIlFoglio()
        mostra(foglio, SportRegistry.PADEL, listOf(listOf("Marco", "Luca"), listOf("Anna", "Sara")))
        val densita = foglio.resources.displayMetrics.density
        foglio.measure(
            View.MeasureSpec.makeMeasureSpec((411 * densita).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        foglio.layout(0, 0, foglio.measuredWidth, foglio.measuredHeight)

        for (squadra in 1..2) {
            val altezza = scambio(foglio, squadra).height / densita
            val larghezza = scambio(foglio, squadra).width / densita
            assertTrue("squadra $squadra: altezza $altezza", altezza >= 48f)
            assertTrue("squadra $squadra: larghezza $larghezza", larghezza >= 48f)
        }
    }

    @Test
    fun `nel calcio la card delle coppie non c'e e il resto del foglio e quello di prima`() {
        val foglio = gonfiaIlFoglio()

        mostra(foglio, SportRegistry.FOOTBALL, listOf(listOf("Marco", "Luca"), listOf("Anna", "Sara")))

        assertEquals(View.GONE, foglio.findViewById<View>(R.id.pairs_card).visibility)
        // Rose, registro e formazioni restano dov'erano, e le rose non si perdono.
        assertEquals(View.VISIBLE, foglio.findViewById<View>(R.id.rosters_card).visibility)
        assertEquals(View.VISIBLE, foglio.findViewById<View>(R.id.match_log_card).visibility)
        assertEquals(View.VISIBLE, foglio.findViewById<View>(R.id.formations_card).visibility)
    }

    @Test
    fun `nel tennis singolare la card non c'e, con le rose in doppio compare`() {
        val foglio = gonfiaIlFoglio()
        val rose = listOf(listOf("Marco", "Luca"), listOf("Anna", "Sara"))

        mostra(foglio, SportRegistry.TENNIS, rose)
        assertEquals(View.GONE, foglio.findViewById<View>(R.id.pairs_card).visibility)

        // Con quattro giocatori l'ordine di servizio c'e' e il tennis e' un doppio.
        val doppio = SportRegistry.forMatch(SportRegistry.TENNIS, listOf(1, 2, 3, 4)).capabilities.playersPerSide
        mostraLeCoppie(foglio, doppio, rose, listOf("ROSSI", "BIANCHI"), false)
        assertEquals(View.VISIBLE, foglio.findViewById<View>(R.id.pairs_card).visibility)
    }
}
