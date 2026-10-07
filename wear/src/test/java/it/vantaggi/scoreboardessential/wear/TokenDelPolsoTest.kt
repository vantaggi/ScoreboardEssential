package it.vantaggi.scoreboardessential.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * G-7: i token del quadrante sono quelli della UI Constitution, gli stessi del telefono, e il tema
 * dell'orologio non dice piu' Street. Sono prove sui file del modulo (si gira da `wear/`), senza
 * Robolectric: stessi nomi e valori in `colors.xml` e `integers.xml` dei due moduli, nessun colore
 * vecchio citato da layout, disegni, temi e codice, nessuna forma `cut`, nessun maiuscolo forzato nel
 * menu e nella selezione sport, la pressione condivisa identica a quella del telefono.
 */
class TokenDelPolsoTest {
    private val res = File("src/main/res")
    private val telefono = File("../mobile/src/main/res")

    private val nomiVecchi =
        listOf(
            "asphalt_dark",
            "concrete_gray",
            "stencil_white",
            "sidewalk_gray",
            "graffiti_pink",
            "neon_cyan",
            "team_spray_yellow",
            "team_electric_green",
            "error_red",
            "error_text",
            "signal_amber",
        )

    /** nome -> valore, con gli alias `@color/x` risolti. */
    private fun colori(file: File): Map<String, String> {
        val grezzi =
            Regex("<color name=\"([a-z0-9_]+)\">([^<]+)</color>")
                .findAll(file.readText())
                .associate { it.groupValues[1] to it.groupValues[2].trim() }
        fun risolvi(valore: String): String = if (valore.startsWith("@color/")) risolvi(grezzi.getValue(valore.removePrefix("@color/"))) else valore.uppercase()
        return grezzi.mapValues { risolvi(it.value) }
    }

    private fun interi(file: File): Map<String, String> =
        Regex("<integer name=\"([a-z_]+)\">([^<]+)</integer>")
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2].trim() }

    private fun tutti(
        cartella: File,
        estensione: String,
    ) = cartella.walkTopDown().filter { it.isFile && it.extension == estensione }.toList()

    /** I file che possono citare un colore: layout, disegni, valori (tranne colors.xml), codice. */
    private fun fontiDelModulo(): List<File> =
        tutti(res, "xml").filter { it.name != "colors.xml" } + tutti(File("src/main/java"), "kt")

    @Test
    fun `i ruoli dell'orologio hanno gli stessi valori del telefono`() {
        val polso = colori(File(res, "values/colors.xml"))
        val telefonoColori = colori(File(telefono, "values/colors.xml"))
        val comuni = polso.keys.filter { it in telefonoColori }
        assertTrue("pochi token in comune: $comuni", comuni.size >= 14)
        comuni.forEach { assertEquals("il token $it diverge fra telefono e orologio", telefonoColori.getValue(it), polso.getValue(it)) }

        listOf("elite_lime", "elite_on_lime", "elite_text_primary", "elite_text_secondary", "elite_error", "elite_warning", "ink_black", "team_side_1", "team_side_2")
            .forEach { assertTrue("manca $it sull'orologio", it in polso) }
        assertEquals("#000000", polso.getValue("ink_black"))
        assertEquals("#C8F135", polso.getValue("elite_lime"))
        assertEquals("#C8F135", polso.getValue("team_side_1"))
        assertEquals("#00E5FF", polso.getValue("team_side_2"))

        val durataPolso = interi(File(res, "values/integers.xml"))
        val durataTelefono = interi(File(telefono, "values/integers.xml"))
        durataPolso.forEach { (nome, valore) -> assertEquals(nome, durataTelefono.getValue(nome), valore) }
    }

    @Test
    fun `i nove colori Street duplicati non ci sono piu' e nessuno li cita`() {
        val polso = colori(File(res, "values/colors.xml"))
        nomiVecchi.forEach { assertFalse("$it e' ancora in colors.xml", it in polso) }
        fontiDelModulo().forEach { file ->
            val testo = file.readText()
            nomiVecchi.forEach { vecchio ->
                assertFalse("${file.path} cita $vecchio", Regex("[@.]color[/.]$vecchio\\b").containsMatchIn(testo))
            }
        }
    }

    @Test
    fun `falsificazione, il controllo dei colori vecchi trova un esempio sbagliato`() {
        val sbagliato = "<TextView android:textColor=\"@color/stencil_white\" /> ContextCompat.getColor(this, R.color.graffiti_pink)"
        val trovati = nomiVecchi.filter { Regex("[@.]color[/.]$it\\b").containsMatchIn(sbagliato) }
        assertEquals(listOf("stencil_white", "graffiti_pink"), trovati)
    }

    @Test
    fun `nessuna forma tagliata ne' StreetCard nel tema e nei layout`() {
        fontiDelModulo().forEach { file ->
            val testo = file.readText()
            assertFalse("${file.path} ha una forma cut", Regex("cornerFamily\">cut|cornerFamily=\"cut").containsMatchIn(testo))
            assertFalse("${file.path} cita StreetCard", testo.contains("StreetCard"))
            assertFalse("${file.path} cita Street", Regex("TextAppearance\\.App\\.[A-Za-z]+\\.Street").containsMatchIn(testo))
        }
        val tema = File(res, "values/theme.xml").readText()
        assertTrue(tema.contains("ShapeAppearance.App.Control") && tema.contains("@dimen/radius_control"))
        assertTrue(tema.contains("ShapeAppearance.App.Surface") && tema.contains("@dimen/radius_surface"))
        val dimens = File(res, "values/dimens.xml").readText()
        assertTrue(dimens.contains("name=\"radius_control\">8dp"))
        assertTrue(dimens.contains("name=\"radius_surface\">14dp"))
    }

    @Test
    fun `menu e selezione sport sono gruppi tonali senza ombra, senza maiuscolo e a tre pesi al massimo`() {
        val menu = File(res, "layout/activity_menu.xml").readText()
        val sport = File(res, "layout/activity_sport_selection.xml").readText()
        val riga = File(res, "layout/item_sport_wear.xml").readText()
        assertTrue(menu.contains("@drawable/bg_group") && menu.contains("@drawable/bg_group_divider"))
        assertTrue(sport.contains("@drawable/bg_group"))
        assertTrue(riga.contains("Widget.App.GroupRow"))
        listOf(menu, sport, riga).forEach {
            assertFalse("textAllCaps nel layout", it.contains("textAllCaps"))
            assertFalse("ombra nel layout", Regex("android:elevation=\"[1-9]|cardElevation=\"[1-9]").containsMatchIn(it))
        }

        val tema = File(res, "values/theme.xml").readText()
        assertFalse("textAllCaps nel tema", tema.contains("textAllCaps"))
        val pesi = Regex("textFontWeight\">(\\d+)<").findAll(tema).map { it.groupValues[1] }.toSet()
        assertTrue("pesi nel tema: $pesi", pesi.size <= 3 && pesi.all { it in setOf("400", "500", "600") })
        // La riga di un gruppo non ha ombra e ha la pressione condivisa.
        val stileRiga = tema.substringAfter("name=\"Widget.App.GroupRow\"").substringBefore("</style>")
        assertTrue(stileRiga.contains("<item name=\"cardElevation\">0dp</item>"))
        assertTrue(stileRiga.contains("?attr/pressFeedback"))

        // Il menu e la selezione sport non hanno stringhe in maiuscolo.
        listOf("values/strings.xml", "values-it/strings.xml").forEach { nome ->
            val testo = File(res, nome).readText()
            listOf("wear_menu_title", "wear_menu_end", "wear_menu_end_confirm", "wear_menu_discard", "wear_menu_discard_confirm", "wear_sport", "wear_sport_title")
                .forEach { chiave ->
                    val valore = Regex("<string name=\"$chiave\">([^<]*)</string>").find(testo)!!.groupValues[1]
                    assertTrue("$nome/$chiave e' \"$valore\" in maiuscolo", valore != valore.uppercase())
                }
        }
    }

    @Test
    fun `la pressione dell'orologio e' quella del telefono, scala 0,97 e opacita' 0,85 in duration_instant`() {
        val polso = File(res, "animator/press_feedback.xml").readText()
        val telefonoPressione = File(telefono, "animator/press_feedback.xml").readText()
        assertTrue(polso.contains("android:valueTo=\"0.97\""))
        assertTrue(polso.contains("android:valueTo=\"0.85\""))
        assertTrue(polso.contains("@integer/duration_instant"))
        assertEquals(estraiMisure(telefonoPressione), estraiMisure(polso))

        val ridotta = File(res, "animator/press_feedback_reduced.xml").readText()
        assertFalse("col movimento ridotto niente scala", ridotta.contains("scaleX") || ridotta.contains("scaleY"))
        assertTrue(ridotta.contains("android:valueTo=\"0.85\""))
        val tema = File(res, "values/theme.xml").readText()
        assertTrue(tema.contains("<item name=\"pressFeedback\">@animator/press_feedback</item>"))
        assertTrue(tema.contains("<item name=\"pressFeedback\">@animator/press_feedback_reduced</item>"))
    }

    private fun estraiMisure(testo: String) = Regex("android:(propertyName|valueTo|duration)=\"([^\"]+)\"").findAll(testo).map { it.value }.toList()

    @Test
    fun `i predefiniti delle strisce sono lime e ciano e il marcatore parte dagli stessi`() {
        val quadrante = File(res, "layout/activity_main.xml").readText()
        assertTrue(quadrante.contains("android:background=\"@color/team_side_1\""))
        assertTrue(quadrante.contains("android:background=\"@color/team_side_2\""))
        val marcatore = File("src/main/java/it/vantaggi/scoreboardessential/wear/PlayerSelectionActivity.kt").readText()
        assertTrue(marcatore.contains("R.color.team_side_2 else R.color.team_side_1"))
    }
}
