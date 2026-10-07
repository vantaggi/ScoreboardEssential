package it.vantaggi.scoreboardessential

import android.app.Application
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.repository.ColorRepository
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import it.vantaggi.scoreboardessential.utils.NumberRoll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Passo G-6: la schermata di gioco e il foglio PARTITA sui ruoli della UI Constitution. I colori
 * predefiniti delle squadre (lime e ciano e la loro migrazione), il pallino del servizio lime, il punteggio
 * che rotola (NumberRoll) e il divieto di maiuscolo forzato e di colori Street nei layout di gioco e foglio.
 * Ogni controllo che potrebbe passare per caso ha la sua falsificazione.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class GiocoG6Test {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun contesto(): Context = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)

    // --- Colori predefiniti delle squadre ---

    @Test
    fun `i predefiniti delle squadre sono lime e ciano`() {
        val colori = ColorRepository(app)
        assertEquals(0xFFC8F135.toInt(), colori.getTeam1DefaultColor())
        assertEquals(0xFF00E5FF.toInt(), colori.getTeam2DefaultColor())
        // Falsificazione: non sono piu' il giallo e il verde di prima.
        assertNotEquals(0xFFFFD600.toInt(), colori.getTeam1DefaultColor())
        assertNotEquals(0xFF76FF03.toInt(), colori.getTeam2DefaultColor())
    }

    @Test
    fun `chi non ha mai scelto un colore passa ai nuovi, chi l'ha scelto tiene la sua scelta anche se era il vecchio predefinito`() {
        app
            .getSharedPreferences("match_settings_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val deposito = MatchSettingsRepository(app, ColorRepository(app))
        runBlocking {
            assertEquals("senza scelta: lime", 0xFFC8F135.toInt(), deposito.getTeam1Color())
            assertEquals("senza scelta: ciano", 0xFF00E5FF.toInt(), deposito.getTeam2Color())
            // Il vecchio giallo scelto di proposito resta giallo: il predefinito non viene mai scritto, la scelta si'.
            deposito.setTeam1Color(0xFFFFD600.toInt())
            assertEquals(0xFFFFD600.toInt(), deposito.getTeam1Color())
            assertEquals("il lato 2 non scelto segue il predefinito", 0xFF00E5FF.toInt(), deposito.getTeam2Color())
        }
        app
            .getSharedPreferences("match_settings_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `la zona del lato 1 parte lime con l'inchiostro nero e quella del lato 2 ciano`() {
        val radice = LayoutInflater.from(contesto()).inflate(R.layout.content_scoreboard_live, null)
        val zona1 = radice.findViewById<com.google.android.material.card.MaterialCardView>(R.id.team1_add_button_card)
        val zona2 = radice.findViewById<com.google.android.material.card.MaterialCardView>(R.id.team2_add_button_card)
        assertEquals(0xFFC8F135.toInt(), zona1.cardBackgroundColor.defaultColor)
        assertEquals(0xFF00E5FF.toInt(), zona2.cardBackgroundColor.defaultColor)
        assertEquals(TeamInk.NERO, TeamInk.on(zona1.cardBackgroundColor.defaultColor))
        assertTrue(TeamInk.contrast(TeamInk.NERO, zona1.cardBackgroundColor.defaultColor) >= 4.5)
    }

    // --- Il pallino del servizio ---

    @Test
    fun `il pallino del servizio e' lime e si legge sul nero`() {
        val pallino = contesto().getDrawable(R.drawable.bg_serve_dot) as GradientDrawable
        val colore = pallino.color!!.defaultColor
        assertEquals(contesto().getColor(R.color.elite_lime), colore)
        assertTrue("pallino su nero %.2f".format(TeamInk.contrast(colore, TeamInk.NERO)), TeamInk.contrast(colore, TeamInk.NERO) >= 4.5)
        // Falsificazione: il bianco di prima era un altro colore.
        assertNotEquals(contesto().getColor(R.color.ink_white), colore)
        // Il lime non e' l'unico segno: il pallino sta nello slot accanto al nome, che e' una forma e una posizione.
        val radice = LayoutInflater.from(contesto()).inflate(R.layout.content_scoreboard_live, null)
        assertEquals(
            12f,
            radice.findViewById<View>(R.id.team1_serve_dot).layoutParams.width / contesto().resources.displayMetrics.density,
            0.5f,
        )
    }

    // --- NumberRoll ---

    private class Scena(
        val genitore: FrameLayout,
        val cifra: TextView,
        val rotolo: NumberRoll,
    )

    private fun montaLaCifra(testo: String = "0"): Scena {
        val ctx = contesto()
        val genitore = FrameLayout(ctx)
        val cifra =
            TextView(ctx).apply {
                text = testo
                textSize = 60f
                gravity = Gravity.CENTER
                fontFeatureSettings = "tnum"
            }
        genitore.addView(
            cifra,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )
        genitore.measure(
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
        )
        genitore.layout(0, 0, 600, 600)
        return Scena(genitore, cifra, NumberRoll(cifra))
    }

    private fun passano(millisecondi: Long) = ShadowLooper.idleMainLooper(millisecondi, TimeUnit.MILLISECONDS)

    @Test
    fun `quando il punteggio sale la cifra vecchia esce in alto e la nuova entra dal basso`() {
        val s = montaLaCifra("0")
        s.rotolo.mostra("1", NumberRoll.SU)
        assertEquals("il nuovo valore e' subito nel testo", "1", s.cifra.text.toString())
        assertEquals("la cifra uscente nasce con il cambio (laid=${s.cifra.isLaidOut})", 2, s.genitore.childCount)
        // Sotto Robolectric il looper porta l'animazione a fine corsa in un colpo: si guarda a meta' corsa
        // spostando l'animatore, come farebbe il tempo.
        s.rotolo.corsa!!.setCurrentPlayTime(120)
        assertEquals("una cifra uscente e' ancora in vista", 2, s.genitore.childCount)
        val uscente = s.genitore.getChildAt(1)
        assertEquals("la vecchia", "0", (uscente as TextView).text.toString())
        assertTrue("la vecchia sale (y negativo): ${uscente.translationY}", uscente.translationY < 0f)
        assertTrue("la nuova arriva dal basso (y positivo): ${s.cifra.translationY}", s.cifra.translationY > 0f)
        passano(400)
        assertEquals("a fine corsa resta una sola cifra", 1, s.genitore.childCount)
        assertEquals(0f, s.cifra.translationY, 0f)
        assertEquals(1f, s.cifra.alpha, 0f)
    }

    @Test
    fun `quando il punteggio scende il verso e' l'opposto`() {
        val s = montaLaCifra("5")
        s.rotolo.mostra("4", NumberRoll.GIU)
        s.rotolo.corsa!!.setCurrentPlayTime(120)
        val uscente = s.genitore.getChildAt(1)
        assertTrue("la vecchia scende (y positivo)", uscente.translationY > 0f)
        assertTrue("la nuova arriva dall'alto (y negativo)", s.cifra.translationY < 0f)
        passano(400)
    }

    @Test
    fun `il verso del cambio si legge dai due punteggi, con il vantaggio fra 40 e il game`() {
        assertEquals(NumberRoll.SU, NumberRoll.direzione("0", "1"))
        assertEquals(NumberRoll.GIU, NumberRoll.direzione("1", "0"))
        assertEquals(NumberRoll.SU, NumberRoll.direzione("30", "40"))
        assertEquals(NumberRoll.SU, NumberRoll.direzione("40", "AV"))
        assertEquals(NumberRoll.GIU, NumberRoll.direzione("AV", "40"))
        assertEquals(0, NumberRoll.direzione("3", "3"))
        assertEquals(0, NumberRoll.direzione("-", "3"))
        assertEquals(0, NumberRoll.direzione(null, "3"))
        // Falsificazione: invertire il segno scambia su e giu'.
        assertNotEquals(NumberRoll.direzione("0", "1"), NumberRoll.direzione("1", "0"))
    }

    @Test
    fun `un cambio nuovo sostituisce subito quello in corso`() {
        val s = montaLaCifra("0")
        s.rotolo.mostra("1", NumberRoll.SU)
        s.rotolo.corsa!!.setCurrentPlayTime(60)
        s.rotolo.mostra("2", NumberRoll.SU)
        assertEquals("2", s.cifra.text.toString())
        assertEquals("una sola cifra uscente, quella del cambio nuovo", 2, s.genitore.childCount)
        assertEquals("1", (s.genitore.getChildAt(1) as TextView).text.toString())
        passano(400)
        assertEquals(1, s.genitore.childCount)
        assertEquals(0f, s.cifra.translationY, 0f)
    }

    @Test
    fun `con le animazioni di sistema ridotte la cifra cambia subito, senza traslazione ne' cifra uscente`() {
        val prima = Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            val s = montaLaCifra("0")
            s.rotolo.mostra("1", NumberRoll.SU)
            assertEquals("1", s.cifra.text.toString())
            assertEquals(1, s.genitore.childCount)
            assertFalse(s.rotolo.inCorso)
            assertEquals(0f, s.cifra.translationY, 0f)
        } finally {
            Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, prima)
        }
    }

    @Test
    fun `la scatola della cifra non cambia di larghezza fra 1 e 15`() {
        val s = montaLaCifra("1")
        NumberRoll.fissaLaScatola(s.cifra, "88")

        fun larghezza(testo: String): Int {
            s.cifra.text = testo
            s.cifra.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED)
            return s.cifra.measuredWidth
        }
        val uno = larghezza("1")
        assertEquals(uno, larghezza("15"))
        assertEquals(uno, larghezza("88"))
        // Falsificazione: senza la scatola fissa "1" e "15" misurano diverso.
        val libera = montaLaCifra("1").cifra

        fun libera(testo: String): Int {
            libera.text = testo
            libera.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED)
            return libera.measuredWidth
        }
        assertNotEquals(libera("1"), libera("15"))
    }

    @Test
    fun `i numeri del gioco sono una regione live polite`() {
        val radice = LayoutInflater.from(contesto()).inflate(R.layout.content_scoreboard_live, null)
        for (id in listOf(R.id.team1_score_textview, R.id.team2_score_textview)) {
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, radice.findViewById<View>(id).accessibilityLiveRegion)
        }
    }

    // --- Niente maiuscolo forzato, niente colori Street, niente alias Street ---

    private val layoutDiGioco =
        listOf(
            "content_scoreboard_live",
            "content_scoreboard_details",
            "activity_main",
            "team_player_item",
            "match_event_item",
            "match_game_item",
        )

    private fun sorgente(nome: String) = File("src/main/res/layout/$nome.xml").readText()

    private val colorStreet =
        Regex(
            "@color/(concrete_gray|stencil_white|sidewalk_gray|graffiti_[a-z_]+|asphalt_[a-z]+|outline_gray|neon_cyan|team_spray_yellow|team_electric_green|error_red|error_text)",
        )

    private fun difetti(xml: String): List<String> {
        val trovati = mutableListOf<String>()
        if (xml.contains("textAllCaps=\"true\"")) trovati += "textAllCaps"
        if (xml.contains("textCapCharacters")) trovati += "textCapCharacters"
        colorStreet.findAll(xml).forEach { trovati += it.value }
        if (xml.contains("Street\"") || xml.contains(".Street")) trovati += "alias Street"
        if (xml.contains("selectableItemBackground")) trovati += "ripple di sistema"
        return trovati
    }

    @Test
    fun `i layout di gioco e del foglio non hanno maiuscolo forzato ne' colori Street ne' alias Street`() {
        for (nome in layoutDiGioco) {
            assertEquals("$nome: ${difetti(sorgente(nome))}", emptyList<String>(), difetti(sorgente(nome)))
        }
    }

    @Test
    fun `il controllo trova ogni difetto in un esempio sbagliato`() {
        assertTrue(difetti("""<TextView android:textAllCaps="true" />""").isNotEmpty())
        assertTrue(difetti("""<View android:background="@color/stencil_white" />""").isNotEmpty())
        assertTrue(difetti("""<View android:background="@color/error_red" />""").isNotEmpty())
        assertTrue(difetti("""<Button style="@style/Widget.App.Button.Street" />""").isNotEmpty())
        assertTrue(difetti("""<View android:foreground="?attr/selectableItemBackground" />""").isNotEmpty())
    }

    @Test
    fun `gli stili di testo del gioco non sono in maiuscolo e usano Inter nei tre pesi`() {
        val temi = File("src/main/res/values/themes.xml").readText()
        val blocco = temi.substring(temi.indexOf("TextAppearance.App.Game.Score"), temi.indexOf("TextAppearance.App.Button"))
        assertFalse("un testo di gioco e' in maiuscolo", blocco.contains("textAllCaps\">true"))
        assertFalse("il condensato e' uscito dal gioco", blocco.contains("sans-serif-condensed"))
        assertFalse("il grassetto di sistema e' uscito dal gioco", blocco.contains("textStyle"))
        val pesi = Regex("textFontWeight\">(\\d+)").findAll(blocco).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("400", "500", "600"), pesi)
    }

    @Test
    fun `gli alias Street dei bottoni e delle forme non esistono piu'`() {
        for (nome in listOf("themes.xml", "styles.xml")) {
            val testo = File("src/main/res/values/$nome").readText()
            assertFalse(
                "$nome ha ancora Widget.App.Button...Street",
                Regex("""Widget\.App\.Button\.[A-Za-z.]*Street""").containsMatchIn(testo),
            )
            assertFalse(
                "$nome ha ancora ShapeAppearance.App.StreetButton o StreetBadge",
                Regex("""ShapeAppearance\.App\.Street(Button|Badge)""").containsMatchIn(testo),
            )
        }
    }

    @Test
    fun `le etichette del foglio e i nomi delle squadre non sono in maiuscolo`() {
        val c = contesto()
        listOf(
            R.string.label_match_history,
            R.string.label_end_match,
            R.string.label_team_rosters,
            R.string.label_match_log,
            R.string.label_formations,
            R.string.label_match_sheet,
            R.string.label_close_sheet,
            R.string.label_statistics,
            R.string.label_players,
            R.string.label_settings,
            R.string.label_reset_match_time,
        ).forEach { id ->
            val testo = c.getString(id)
            assertNotEquals("${c.resources.getResourceEntryName(id)} e' tutto maiuscolo: $testo", testo, testo.uppercase())
        }
        val radice = LayoutInflater.from(c).inflate(R.layout.content_scoreboard_live, null)
        val nome = radice.findViewById<TextView>(R.id.team1_name_textview)
        mostraNomeSquadra(radice.findViewById(R.id.team1_name_container), nome, "Lupi Rossi")
        assertEquals("Lupi Rossi", nome.text.toString())
        assertFalse(nome.transformationMethod != null && nome.isAllCaps)
    }

    @Test
    fun `la pressione delle zone e' lo StateListAnimator condiviso e non una scala a mano`() {
        val radice = LayoutInflater.from(contesto()).inflate(R.layout.content_scoreboard_live, null)
        for (id in listOf(R.id.team1_add_button_card, R.id.team2_add_button_card)) {
            assertTrue("la zona non ha la pressione condivisa", radice.findViewById<View>(id).stateListAnimator != null)
        }
        val sorgenteKotlin = File("src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt").readText()
        assertFalse(sorgenteKotlin.contains("animateZoneTap"))
        assertFalse(sorgenteKotlin.contains("animateScoreNumber"))
    }
}
