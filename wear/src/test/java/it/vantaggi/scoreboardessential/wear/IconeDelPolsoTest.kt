package it.vantaggi.scoreboardessential.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Passo G-3 sull'orologio: le icone sono gli stessi Material Symbols outlined del telefono, scelti dalla
 * tabella di `DESIGN.md` (sezione G-3, colonna dell'orologio), senza colori scritti a mano; il glifo del
 * menu partita e' decorativo (il bersaglio sotto ha la descrizione) e la spunta dello sport in uso e' un
 * `check` a 16dp, non un carattere nel testo.
 *
 * Il test gira dalla cartella del modulo (`wear/`).
 */
class IconeDelPolsoTest {
    private val res = File("src/main/res")
    private val drawable = File(res, "drawable")

    private fun inTabella(): Set<String> {
        val testo = File("../DESIGN.md").readText().replace("\r\n", "\n")
        val sezione = testo.substring(testo.indexOf("#### G-3."), testo.indexOf("#### G-4."))
        return sezione
            .lines()
            .filter { it.startsWith("| ") && !it.startsWith("| Concetto") && !it.startsWith("|---") }
            .mapNotNull { riga ->
                riga
                    .trim()
                    .trim('|')
                    .split("|")
                    .map { it.trim() }[3]
                    .trim('`')
                    .takeIf { it != "-" && it.isNotEmpty() }
            }.toSet()
    }

    private fun vettoriali() =
        drawable.listFiles { f -> f.name.startsWith("ic_") && !f.name.startsWith("ic_launcher") }!!.sortedBy { it.name }

    @Test
    fun `le icone dell'orologio sono quelle della colonna dell'orologio e Material Symbols`() {
        assertEquals(inTabella(), vettoriali().map { it.name.removeSuffix(".xml") }.toSet())

        vettoriali().forEach {
            val testo = it.readText()
            assertTrue(it.name, testo.contains("android:viewportWidth=\"960\"") && testo.contains("android:viewportHeight=\"960\""))
            assertFalse("${it.name} ha un colore scritto a mano", Regex("#[0-9A-Fa-f]{3,8}").containsMatchIn(testo))
            assertTrue(it.name, testo.contains("android:fillColor=\"@android:color/white\"") && testo.contains("android:tint=\"?attr/"))
        }
    }

    @Test
    fun `il glifo del menu e' il simbolo more_horiz e resta nascosto a TalkBack`() {
        val layout = File(res, "layout/activity_main.xml").readText()
        val glifo = layout.substringAfter("android:id=\"@+id/menuGlyph\"").substringBefore("/>")

        assertTrue(glifo.contains("@drawable/ic_more_horiz"))
        assertTrue(glifo.contains("android:importantForAccessibility=\"no\""))
        assertFalse(File(drawable, "ic_menu_dots.xml").exists())
    }

    @Test
    fun `la spunta dello sport in uso non e' piu' un carattere nelle stringhe`() {
        File(res, "values/strings.xml").readText().let { assertFalse(it.contains("✓")) }
        File(res, "values-it/strings.xml").readText().let { assertFalse(it.contains("✓")) }
    }

    @Test
    fun `le tre misure delle icone sono 16, 20 e 24`() {
        val dimens = File(res, "values/dimens.xml").readText()

        assertTrue(dimens.contains("name=\"icon_compact\">16dp"))
        assertTrue(dimens.contains("name=\"icon_standard\">20dp"))
        assertTrue(dimens.contains("name=\"icon_prominent\">24dp"))
    }
}
