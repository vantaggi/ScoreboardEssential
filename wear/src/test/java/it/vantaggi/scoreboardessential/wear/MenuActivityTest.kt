package it.vantaggi.scoreboardessential.wear

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import android.os.SystemClock
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.time.Duration
import java.util.Locale

/**
 * Il menu partita vero, con la sua conferma sul posto.
 *
 * Il tempo e' di due specie e si sposta in due posti: l'istante che misura la finestra della
 * conferma (l'orologio iniettato in [MenuActivity.orologio]) e il looper che fa rientrare la card
 * e chiude il menu da solo. [avanza] li muove insieme; niente Thread.sleep.
 */
@RunWith(RobolectricTestRunner::class)
class MenuActivityTest {
    private var adesso = 1_000_000L

    private val base =
        InputMenu(
            inCoda = 0,
            collegato = true,
            partitaIniziata = false,
            calcioConV2 = false,
            haElencoSport = true,
            sport = "Padel",
            risultato = "6–4",
        )

    @Before
    fun setup() {
        MenuActivity.orologio = { adesso }
    }

    @After
    fun ripristina() {
        MenuActivity.orologio = SystemClock::uptimeMillis
    }

    private fun apri(input: InputMenu = base): MenuActivity {
        val intent = MenuActivity.intent(RuntimeEnvironment.getApplication(), input)
        return Robolectric.buildActivity(MenuActivity::class.java, intent).setup().get()
    }

    private fun avanza(millisecondi: Long) {
        adesso += millisecondi
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millisecondi))
    }

    private fun card(
        activity: MenuActivity,
        posizione: Int,
    ) = activity.findViewById<LinearLayout>(R.id.menu_voci).getChildAt(posizione) as MaterialCardView

    private fun fine(activity: MenuActivity) = card(activity, 1)

    private fun titolo(carta: MaterialCardView) = carta.findViewById<TextView>(R.id.sport_name).text.toString()

    private fun sottotitolo(carta: MaterialCardView) = carta.findViewById<TextView>(R.id.sport_current)

    private fun colore(id: Int) = RuntimeEnvironment.getApplication().getColor(id)

    private fun azione(activity: MenuActivity) = shadowOf(activity).resultIntent?.getStringExtra(MenuActivity.EXTRA_AZIONE)

    @Test
    fun `un tocco solo non chiude niente e la card diventa rossa piena`() {
        val activity = apri()

        fine(activity).performClick()

        assertFalse(activity.isFinishing)
        assertEquals(activity.getString(R.string.wear_menu_end_confirm, "6–4"), titolo(fine(activity)))
        assertEquals(activity.getString(R.string.wear_menu_tap_again), sottotitolo(fine(activity)).text.toString())
        assertEquals(colore(R.color.error_red), fine(activity).cardBackgroundColor.defaultColor)
        // Testo nero sul rosso pieno, come dice il design (5.46:1).
        assertEquals(colore(R.color.ink_black), sottotitolo(fine(activity)).currentTextColor)
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `il secondo tocco dopo 600 ms chiude con la fine partita`() {
        val activity = apri()
        fine(activity).performClick()

        avanza(600)
        fine(activity).performClick()

        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(MenuActivity.AZIONE_FINE, azione(activity))
    }

    @Test
    fun `il secondo tocco prima di 600 ms non chiude e non rimette la card a posto`() {
        val activity = apri()
        fine(activity).performClick()

        avanza(599)
        fine(activity).performClick()

        assertFalse(activity.isFinishing)
        assertEquals(colore(R.color.error_red), fine(activity).cardBackgroundColor.defaultColor)
        // La finestra non e' ripartita: un millisecondo dopo, il secondo tocco vale.
        avanza(1)
        fine(activity).performClick()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `dopo 5 secondi la card torna com'era e il tocco successivo e' di nuovo il primo`() {
        val activity = apri()
        val prima = titolo(fine(activity))
        fine(activity).performClick()

        avanza(ConfermaSulPosto.SCADENZA_MS)

        assertEquals(prima, titolo(fine(activity)))
        assertEquals(colore(R.color.concrete_gray), fine(activity).cardBackgroundColor.defaultColor)
        assertEquals(colore(R.color.error_text), fine(activity).findViewById<TextView>(R.id.sport_name).currentTextColor)

        // Con la card com'era, il tocco non puo' valere come conferma: arma di nuovo.
        fine(activity).performClick()
        assertFalse(activity.isFinishing)
        assertEquals(colore(R.color.error_red), fine(activity).cardBackgroundColor.defaultColor)
    }

    @Test
    fun `con la coda piena FINE PARTITA e' spenta, non si arma e dice quanti punti`() {
        val activity = apri(base.copy(inCoda = 2))

        assertFalse(fine(activity).isEnabled)
        assertEquals(
            activity.resources.getQuantityString(R.plurals.wear_menu_deliver_first, 2, 2),
            sottotitolo(fine(activity)).text.toString(),
        )
        assertEquals(colore(R.color.signal_amber), sottotitolo(fine(activity)).currentTextColor)

        fine(activity).performClick()
        avanza(1_000)
        fine(activity).performClick()

        assertFalse(activity.isFinishing)
        assertNull(azione(activity))
        // Nessun Toast, nessun dialogo: la voce spenta tace.
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `SPORT attiva chiude e chiede la lista degli sport`() {
        val activity = apri()

        card(activity, 0).performClick()

        assertTrue(activity.isFinishing)
        assertEquals(MenuActivity.AZIONE_SPORT, azione(activity))
    }

    @Test
    fun `con la partita cominciata SPORT e' spenta e non apre niente`() {
        val activity = apri(base.copy(partitaIniziata = true))

        card(activity, 0).performClick()

        assertFalse(activity.isFinishing)
        assertEquals(activity.getString(R.string.wear_sport_locked), sottotitolo(card(activity, 0)).text.toString())
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `senza elenco di sport c'e' solo la fine partita`() {
        val activity = apri(base.copy(haElencoSport = false))

        assertEquals(1, activity.findViewById<LinearLayout>(R.id.menu_voci).childCount)
        assertEquals(activity.getString(R.string.wear_menu_end), titolo(card(activity, 0)))
    }

    @Test
    fun `senza nessun tocco il menu si chiude da solo dopo 10 secondi`() {
        // L'avvio dell'activity consuma qualche millisecondo del looper di Robolectric: il conto dei
        // 10 secondi parte da onCreate, dentro quella finestra. Si misura da prima e da dopo.
        val prima = SystemClock.uptimeMillis()
        val activity = apri()
        val dopo = SystemClock.uptimeMillis()

        avanza(prima + MenuActivity.CHIUSURA_AUTOMATICA_MS - 1 - dopo)
        assertFalse(activity.isFinishing)

        avanza(dopo - prima + 1)
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
    }

    @Test
    fun `un'interazione riporta a 10 secondi la chiusura automatica`() {
        val activity = apri()

        avanza(9_000)
        activity.onUserInteraction()
        avanza(9_000)
        assertFalse(activity.isFinishing)

        avanza(1_000)
        assertTrue(activity.isFinishing)
    }

    // --- Le frasi sul tondo ---

    private fun contesto(lingua: String): Context {
        val app = RuntimeEnvironment.getApplication()
        val configurazione = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(lingua)) }
        return app.createConfigurationContext(configurazione)
    }

    /**
     * Il tondo da 192dp lascia circa 100dp alla voce: un sottotitolo va a capo oltre una ventina di
     * caratteri. Il tetto qui e' una stima da confermare con il Layout Inspector, non una misura.
     */
    private fun verificaLunghezze(lingua: String) {
        val contesto = contesto(lingua)
        val sottotitoli =
            listOf(
                SottotitoloVoce.PartitaInCorso,
                SottotitoloVoce.ServeIlTelefono,
                SottotitoloVoce.ChiudiDalTelefono,
                SottotitoloVoce.SalvaRisultato("40–AV"),
            ) + listOf(1, 2, 99, 999).map { SottotitoloVoce.PrimaConsegna(it) }
        sottotitoli.forEach { sottotitolo ->
            val testo = sottotitolo.testo(contesto)
            assertTrue("[$lingua] \"$testo\" e' vuota", testo.isNotBlank())
            assertTrue("[$lingua] \"$testo\" ha ${testo.length} caratteri, il massimo e' 26", testo.length <= 26)
        }
        listOf(
            contesto.getString(R.string.wear_menu_end),
            contesto.getString(R.string.wear_menu_end_confirm, "40–AV"),
            contesto.getString(R.string.wear_menu_tap_again),
        ).forEach { testo ->
            assertTrue("[$lingua] \"$testo\" ha ${testo.length} caratteri, il massimo e' 16", testo.length <= 16)
        }
    }

    @Test
    fun `in italiano ogni frase del menu entra nel tondo`() = verificaLunghezze("it")

    @Test
    fun `in inglese ogni frase del menu entra nel tondo`() = verificaLunghezze("en")

    @Test
    fun `le due lingue sono davvero due e il design dice questo in italiano`() {
        assertEquals("Prima consegna 2 punti", SottotitoloVoce.PrimaConsegna(2).testo(contesto("it")))
        assertEquals("Prima consegna 1 punto", SottotitoloVoce.PrimaConsegna(1).testo(contesto("it")))
        assertEquals("Deliver 2 points first", SottotitoloVoce.PrimaConsegna(2).testo(contesto("en")))
        assertEquals("Serve il telefono", SottotitoloVoce.ServeIlTelefono.testo(contesto("it")))
        assertEquals("Salva 3–2 sul telefono", SottotitoloVoce.SalvaRisultato("3–2").testo(contesto("it")))
        assertEquals("CHIUDERE 3–2?", contesto("it").getString(R.string.wear_menu_end_confirm, "3–2"))
        assertEquals("FINE PARTITA", contesto("it").getString(R.string.wear_menu_end))
    }
}
