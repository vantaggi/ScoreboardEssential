package it.vantaggi.scoreboardessential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Passo G-3: le icone sono Material Symbols outlined, una per concetto, e la tabella di `DESIGN.md`
 * (sezione G-3) e' la fonte. Si legge dai file veri: i vettoriali in `drawable/`, i layout, i menu e la
 * tabella, e ogni controllo ha la sua falsificazione su un esempio sbagliato.
 *
 * Il test gira dalla cartella del modulo (`mobile/`), come gli altri che leggono le risorse.
 */
class IconeDelTelefonoTest {
    private val res = File("src/main/res")
    private val drawable = File(res, "drawable")

    private class Riga(
        val concetto: String,
        val simbolo: String,
        val telefono: String?,
        val orologio: String?,
    )

    /** Le righe della tabella concetto, simbolo, drawable, tra "#### G-3" e "#### G-4". */
    private fun tabella(): List<Riga> {
        val testo = File("../DESIGN.md").readText().replace("\r\n", "\n")
        val sezione = testo.substring(testo.indexOf("#### G-3."), testo.indexOf("#### G-4."))
        return sezione
            .lines()
            .filter { it.startsWith("| ") && !it.startsWith("| Concetto") && !it.startsWith("|---") }
            .map { riga ->
                val c = riga.trim().trim('|').split("|").map { it.trim() }
                fun nome(x: String) = x.trim('`').takeIf { it != "-" && it.isNotEmpty() }
                Riga(c[0], c[1].trim('`'), nome(c[2]), nome(c[3]))
            }
    }

    /** I vettoriali delle icone: `ic_*` salvo il launcher (G5) e il selettore del pomello. */
    private fun vettoriali(cartella: File) =
        cartella
            .listFiles { f -> f.name.startsWith("ic_") && !f.name.startsWith("ic_launcher") && f.name != "ic_switch_thumb.xml" }!!
            .sortedBy { it.name }

    // --- Vettoriali ---

    /** Che cosa non va in un vettoriale che dovrebbe essere un Material Symbol, o null se va bene. */
    private fun difettoDelVettoriale(testo: String): String? {
        if (!testo.contains("android:viewportWidth=\"960\"") || !testo.contains("android:viewportHeight=\"960\"")) {
            return "il viewport non e' 960 come nei Material Symbols"
        }
        if (!testo.contains("android:width=\"24dp\"") || !testo.contains("android:height=\"24dp\"")) return "non e' da 24dp"
        if (Regex("#[0-9A-Fa-f]{3,8}").containsMatchIn(testo)) return "ha un colore scritto a mano"
        // Il colore di base deve essere il bianco che il tint sostituisce; con il tint dichiarato.
        val colori = Regex("android:fillColor=\"([^\"]+)\"").findAll(testo).map { it.groupValues[1] }.toSet()
        if (!colori.all { it == "@android:color/white" }) return "fillColor diverso dal bianco di base: $colori"
        if (!testo.contains("android:tint=\"?attr/")) return "manca il tint da attributo del tema"
        return null
    }

    @Test
    fun `ogni ic_ del telefono e' un Material Symbol senza colori scritti a mano`() {
        val file = vettoriali(drawable)
        assertTrue(file.size >= 20)
        file.forEach { assertEquals(it.name, null, difettoDelVettoriale(it.readText())) }
    }

    @Test
    fun `il pomello dell'interruttore usa il simbolo check e non un vettore suo`() {
        val pomello = File(drawable, "ic_switch_thumb.xml").readText()

        assertTrue(pomello.contains("@drawable/ic_check"))
        assertFalse(File(drawable, "ic_switch_check.xml").exists())
        assertFalse("nessun colore nel selettore", Regex("#[0-9A-Fa-f]{3,8}").containsMatchIn(pomello.substringBefore("<shape")))
        assertTrue(File(res, "values/styles.xml").readText().contains("name=\"thumbIconTint\">@color/elite_on_lime"))
    }

    @Test
    fun `falsificazione - il controllo dei vettoriali vede i difetti`() {
        val buono = File(drawable, "ic_add.xml").readText()
        assertEquals(null, difettoDelVettoriale(buono))

        assertTrue(difettoDelVettoriale(buono.replace("@android:color/white", "#FF0000")) != null)
        assertTrue(difettoDelVettoriale(buono.replace("@android:color/white", "@color/elite_lime")) != null)
        assertTrue(difettoDelVettoriale(buono.replace("android:viewportWidth=\"960\"", "android:viewportWidth=\"24\"")) != null)
        assertTrue(difettoDelVettoriale(buono.replace("android:tint=\"?attr/colorControlNormal\"", "")) != null)
    }

    // --- Tabella ---

    @Test
    fun `la tabella ha un solo simbolo e un solo file per concetto`() {
        val righe = tabella()
        assertTrue("la tabella e' vuota", righe.size > 40)

        assertEquals("concetto ripetuto", righe.size, righe.map { it.concetto }.toSet().size)
        assertEquals("simbolo ripetuto", righe.size, righe.map { it.simbolo }.toSet().size)
        righe.forEach { r ->
            listOfNotNull(r.telefono, r.orologio).forEach {
                assertEquals("${r.concetto}: il drawable porta il nome del simbolo", "ic_${r.simbolo}", it)
            }
        }
    }

    @Test
    fun `ogni ic_ del telefono e' nella tabella e ogni riga con un drawable ha il suo file`() {
        val righe = tabella()

        val inTabella = righe.mapNotNull { it.telefono }.toSet()
        val suDisco = vettoriali(drawable).map { it.name.removeSuffix(".xml") }.toSet()
        assertEquals(inTabella, suDisco)
    }

    @Test
    fun `nessun vettoriale e' uguale a un altro, un concetto non ha due file`() {
        val tracciati = vettoriali(drawable).associate { it.name to Regex("pathData=\"([^\"]+)\"").find(it.readText())!!.groupValues[1] }

        assertEquals(tracciati.size, tracciati.values.toSet().size)
    }

    @Test
    fun `le tre misure delle icone sono 16, 20 e 24`() {
        val dimens = File(res, "values/dimens.xml").readText()

        assertTrue(dimens.contains("name=\"icon_compact\">16dp"))
        assertTrue(dimens.contains("name=\"icon_standard\">20dp"))
        assertTrue(dimens.contains("name=\"icon_prominent\">24dp"))
    }

    // --- Uso nei layout ---

    private fun leggi(testo: String): Element =
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(testo.byteInputStream())
            .documentElement

    private fun Element.attr(
        ns: String,
        nome: String,
    ): String? = getAttributeNS(ns, nome).takeIf { it.isNotEmpty() }

    private val android = "http://schemas.android.com/apk/res/android"
    private val app = "http://schemas.android.com/apk/res-auto"
    private val tools = "http://schemas.android.com/tools"

    private fun tutti(radice: Element): List<Element> {
        val lista = mutableListOf(radice)
        val figli = radice.childNodes
        for (i in 0 until figli.length) (figli.item(i) as? Element)?.let { lista += tutti(it) }
        return lista
    }

    /** I comandi di sola icona e le icone senza descrizione, o nessun difetto. */
    private fun difettiDiAccessibilita(
        nomeFile: String,
        testo: String,
    ): List<String> {
        val difetti = mutableListOf<String>()
        tutti(leggi(testo)).forEach { v ->
            val id = v.attr(android, "id") ?: v.tagName
            val descrizione = v.attr(android, "contentDescription")
            val nascosta = v.attr(android, "importantForAccessibility") == "no"
            when (v.tagName.substringAfterLast('.')) {
                "ImageButton", "FloatingActionButton" ->
                    if (descrizione == null) difetti += "$nomeFile $id: comando di sola icona senza contentDescription"
                "ImageView" -> {
                    val sorgente = v.attr(android, "src") ?: v.attr(app, "srcCompat") ?: v.attr(tools, "src")
                    if (sorgente != null && sorgente.contains("ic_") && descrizione == null && !nascosta) {
                        difetti += "$nomeFile $id: icona ne' descritta ne' nascosta"
                    }
                }
                "MaterialButton" ->
                    if (v.attr(app, "icon") != null && v.attr(android, "text") == null && descrizione == null) {
                        difetti += "$nomeFile $id: bottone di sola icona senza contentDescription"
                    }
            }
        }
        return difetti
    }

    @Test
    fun `i comandi di sola icona hanno contentDescription e le icone decorative sono nascoste`() {
        val layout = File(res, "layout").listFiles { f -> f.extension == "xml" }!!
        val difetti = layout.flatMap { difettiDiAccessibilita(it.name, it.readText()) }

        assertTrue(difetti.joinToString("\n"), difetti.isEmpty())
    }

    @Test
    fun `falsificazione - il controllo di accessibilita' vede i comandi senza nome`() {
        fun layout(corpo: String) =
            "<FrameLayout xmlns:android=\"$android\" xmlns:app=\"$app\">$corpo</FrameLayout>"

        assertEquals(
            1,
            difettiDiAccessibilita("x", layout("<ImageButton android:id=\"@+id/a\" android:src=\"@drawable/ic_edit\"/>")).size,
        )
        assertEquals(
            1,
            difettiDiAccessibilita("x", layout("<ImageView android:id=\"@+id/a\" android:src=\"@drawable/ic_edit\"/>")).size,
        )
        assertEquals(
            1,
            difettiDiAccessibilita(
                "x",
                layout("<com.google.android.material.button.MaterialButton app:icon=\"@drawable/ic_edit\"/>"),
            ).size,
        )
        assertEquals(
            0,
            difettiDiAccessibilita(
                "x",
                layout("<ImageButton android:src=\"@drawable/ic_edit\" android:contentDescription=\"Modifica\"/>"),
            ).size,
        )
    }

    @Test
    fun `layout, menu e codice citano solo drawable della tabella e nessuna icona di sistema`() {
        val ammessi = tabella().mapNotNull { it.telefono }.toSet() + "ic_switch_thumb"
        val fonti =
            File(res, "layout").listFiles()!!.toList() +
                File(res, "menu").listFiles()!!.toList() +
                File(res, "values").listFiles()!!.toList() +
                File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

        fonti.forEach { f ->
            val testo = f.readText()
            Regex("(?:@drawable/|R\\.drawable\\.)(ic_[a-z0-9_]+)").findAll(testo).forEach {
                val nome = it.groupValues[1]
                if (!nome.startsWith("ic_launcher")) assertTrue("${f.name} cita $nome, che non e' in tabella", nome in ammessi)
            }
            // Le notifiche del servizio sono l'unica eccezione dichiarata in DESIGN.md (G-3, "Non fatto").
            if (f.name != "MatchTimerService.kt") {
                assertFalse("${f.name} usa un'icona di sistema", testo.contains("@android:drawable/ic_") || testo.contains("android.R.drawable.ic_"))
            }
        }
    }

    @Test
    fun `nelle risorse non c'e' piu' nessun vettore di prima`() {
        listOf("ic_plus", "ic_play", "ic_stats", "ic_swap", "ic_color_picker", "ic_watch_connected", "ic_watch_disconnected", "ic_switch_check")
            .forEach { assertFalse("$it c'e' ancora", File(drawable, "$it.xml").exists()) }
    }
}
