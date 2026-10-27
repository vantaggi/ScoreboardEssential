package it.vantaggi.scoreboardessential.wear

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Il gruppo "In campo" del menu e la voce della prossima partita.
 *
 * Rosso se le rose non arrivano al menu (il gruppo resta spento o senza nomi), se TalkBack non legge la
 * squadra come una frase sola, o se toccare la prossima partita non torna al quadrante con la sua azione.
 */
@RunWith(RobolectricTestRunner::class)
class MenuSquadreTest {
    private val ferma =
        InputMenu(
            inCoda = 0,
            collegato = true,
            partitaIniziata = false,
            haElencoSport = true,
            sport = "Padel",
            risultato = "0–0",
            prossima = "VAN/GLO – ROS/BIA",
        )

    private fun apri(squadre: List<SquadraInCampo>) =
        Robolectric
            .buildActivity(MenuActivity::class.java, MenuActivity.intent(RuntimeEnvironment.getApplication(), ferma, squadre))
            .setup()
            .get()

    @Test
    fun `le squadre in campo stanno sotto le voci, con la sigla e chi la compone`() {
        val menu =
            apri(
                listOf(
                    SquadraInCampo("VAN/GLO", listOf("Vantaggi", "Glorio"), 0xFFC8F135.toInt()),
                    SquadraInCampo("ROS/BIA", listOf("Rossi", "Bianchi"), 0xFF00E5FF.toInt()),
                ),
            )
        val gruppo = menu.findViewById<LinearLayout>(R.id.menu_squadre)

        assertEquals(View.VISIBLE, gruppo.visibility)
        assertEquals(View.VISIBLE, menu.findViewById<View>(R.id.menu_squadre_titolo).visibility)
        assertEquals(2, gruppo.childCount)
        val prima = gruppo.getChildAt(0)
        assertEquals("VAN/GLO", prima.findViewById<TextView>(R.id.squadra_nome).text.toString())
        assertEquals("Vantaggi, Glorio", prima.findViewById<TextView>(R.id.squadra_giocatori).text.toString())
        assertEquals(menu.getString(R.string.cd_squadra, "VAN/GLO", "Vantaggi, Glorio"), prima.contentDescription)
    }

    @Test
    fun `senza rose il gruppo non c'e'`() {
        val menu = apri(listOf(SquadraInCampo("Squadra 1", emptyList(), 0), SquadraInCampo("Squadra 2", emptyList(), 0)))

        assertEquals(View.GONE, menu.findViewById<View>(R.id.menu_squadre).visibility)
        assertEquals(View.GONE, menu.findViewById<View>(R.id.menu_squadre_titolo).visibility)
    }

    @Test
    fun `la prossima partita e' la prima voce e la tocca il quadrante`() {
        val menu = apri(emptyList())
        val voce = menu.findViewById<LinearLayout>(R.id.menu_voci).getChildAt(0)

        assertEquals(menu.getString(R.string.wear_next_match), voce.findViewById<TextView>(R.id.sport_name).text.toString())
        assertEquals("VAN/GLO – ROS/BIA", voce.findViewById<TextView>(R.id.sport_current).text.toString())
        voce.performClick()

        val esito = shadowOf(menu)
        assertEquals(Activity.RESULT_OK, esito.resultCode)
        assertEquals(MenuActivity.AZIONE_PROSSIMA, esito.resultIntent.getStringExtra(MenuActivity.EXTRA_AZIONE))
    }

    /**
     * Il gruppo delle impostazioni: rosso se non compare, se la regola del 40-40 non torna al quadrante
     * con la sua azione o se la vibrazione non si cambia sul posto e non resta scelta.
     */
    @Test
    fun `le impostazioni stanno in fondo, il 40-40 torna al quadrante e la vibrazione si cambia sul posto`() {
        val contesto = RuntimeEnvironment.getApplication()
        PreferenzeDelPolso(contesto).vibrazionePunti = true
        val input = ferma.copy(regola40 = "Killer point")
        val menu =
            Robolectric
                .buildActivity(MenuActivity::class.java, MenuActivity.intent(contesto, input, emptyList()))
                .setup()
                .get()
        val gruppo = menu.findViewById<LinearLayout>(R.id.menu_impostazioni)
        fun titolo(i: Int) = gruppo.getChildAt(i).findViewById<TextView>(R.id.sport_name).text.toString()

        fun sotto(i: Int) = gruppo.getChildAt(i).findViewById<TextView>(R.id.sport_current).text.toString()

        assertEquals(2, gruppo.childCount)
        assertEquals(menu.getString(R.string.wear_setting_deuce), titolo(0))
        assertEquals("Killer point", sotto(0))
        assertEquals(menu.getString(R.string.wear_setting_vibration_on), sotto(1))

        gruppo.getChildAt(1).performClick()
        assertEquals(menu.getString(R.string.wear_setting_vibration_off), sotto(1))
        assertEquals(false, PreferenzeDelPolso(contesto).vibrazionePunti)

        gruppo.getChildAt(0).performClick()
        val esito = shadowOf(menu)
        assertEquals(Activity.RESULT_OK, esito.resultCode)
        assertEquals(MenuActivity.AZIONE_REGOLA_40, esito.resultIntent.getStringExtra(MenuActivity.EXTRA_AZIONE))
    }
}
