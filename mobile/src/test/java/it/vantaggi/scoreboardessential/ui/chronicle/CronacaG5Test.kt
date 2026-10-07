package it.vantaggi.scoreboardessential.ui.chronicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Passo G-5, sui sorgenti: la Cronaca non ha maiuscolo forzato, colori Street, colori scritti a
 * mano o card che non siano gruppi, le sue stringhe non sono in maiuscolo in nessuna lingua, e i
 * colori Street usciti con questo passo non esistono piu'. Ogni controllo ha la sua falsificazione.
 */
class CronacaG5Test {
    private fun file(percorso: String) = File("src/main/$percorso")

    private val street =
        "concrete_gray|stencil_white|sidewalk_gray|graffiti_[a-z_]+|asphalt_[a-z]+|outline_gray|neon_cyan|" +
            "team_spray_yellow|team_electric_green|error_red|error_text"
    private val coloriStreet = Regex("@color/($street)|R\\.color\\.($street)")

    private fun difettiDelLayout(xml: String): List<String> {
        val trovati = mutableListOf<String>()
        if (xml.contains("textAllCaps=\"true\"")) trovati += "textAllCaps"
        if (xml.contains("textCapCharacters")) trovati += "textCapCharacters"
        coloriStreet.findAll(xml).forEach { trovati += it.value }
        if (xml.contains("bg_asphalt") || xml.contains("Street\"") || xml.contains(".Street")) trovati += "alias Street"
        if (xml.contains("selectableItemBackground")) trovati += "ripple di sistema"
        // Una card e' solo un gruppo: stile Widget.App.Group, senza ombra.
        Regex("<com\\.google\\.android\\.material\\.card\\.MaterialCardView[^>]*>").findAll(xml).forEach {
            if (!it.value.contains("Widget.App.Group")) trovati += "card che non e' un gruppo"
            if (Regex("(card)?[eE]levation=\"[1-9]").containsMatchIn(it.value)) trovati += "ombra"
        }
        return trovati
    }

    private fun difettiDelCodice(kt: String): List<String> {
        val trovati = mutableListOf<String>()
        coloriStreet.findAll(kt).forEach { trovati += it.value }
        if (kt.contains(".uppercase(") || kt.contains(".toUpperCase(") || kt.contains("isAllCaps") ||
            kt.contains("AllCaps")
        ) {
            trovati += "maiuscolo"
        }
        // Nessun colore, raggio o durata scritti a mano: solo token.
        if (Regex("0x[0-9A-Fa-f]{8}|Color\\.parseColor|Color\\.rgb|Color\\.argb").containsMatchIn(kt)) trovati += "colore a mano"
        return trovati
    }

    @Test
    fun `i due layout della Cronaca non hanno maiuscolo ne' colori Street ne' card che non siano gruppi`() {
        for (nome in listOf("activity_chronicle", "chronicle_section")) {
            val xml = file("res/layout/$nome.xml").readText()
            assertEquals("$nome: ${difettiDelLayout(xml)}", emptyList<String>(), difettiDelLayout(xml))
        }
    }

    @Test
    fun `il codice della Cronaca non cita colori Street, ne' maiuscolo ne' colori scritti a mano`() {
        for (nome in listOf("ChronicleActivity", "MomentumView", "ChronicleText")) {
            val kt = file("java/it/vantaggi/scoreboardessential/ui/chronicle/$nome.kt").readText()
            assertEquals("$nome: ${difettiDelCodice(kt)}", emptyList<String>(), difettiDelCodice(kt))
        }
    }

    @Test
    fun `il controllo trova ogni difetto in un esempio sbagliato`() {
        assertTrue(difettiDelLayout("""<TextView android:textAllCaps="true" />""").isNotEmpty())
        assertTrue(difettiDelLayout("""<View android:background="@color/concrete_gray" />""").isNotEmpty())
        assertTrue(difettiDelLayout("""<View android:background="@drawable/bg_asphalt_main" />""").isNotEmpty())
        assertTrue(
            difettiDelLayout("""<com.google.android.material.card.MaterialCardView style="@style/Widget.App.Card" />""").isNotEmpty(),
        )
        assertTrue(
            difettiDelLayout(
                """<com.google.android.material.card.MaterialCardView style="@style/Widget.App.Group" app:cardElevation="4dp" />""",
            ).isNotEmpty(),
        )
        assertTrue(difettiDelCodice("setTextColor(color(R.color.sidewalk_gray))").isNotEmpty())
        assertTrue(difettiDelCodice("text = nome.uppercase()").isNotEmpty())
        assertTrue(difettiDelCodice("val c = 0xFFFFD600.toInt()").isNotEmpty())
        // E un layout o un codice a posto non ne ha.
        assertEquals(
            emptyList<String>(),
            difettiDelLayout("""<com.google.android.material.card.MaterialCardView style="@style/Widget.App.Group" />"""),
        )
        assertEquals(emptyList<String>(), difettiDelCodice("setTextColor(color(R.color.elite_text_primary))"))
    }

    // --- Stringhe: niente maiuscolo, in nessuna lingua ---

    private fun stringhe(percorso: String): Map<String, String> =
        Regex("""<string name="(chronicle_[a-z_]+)">(.*?)</string>""")
            .findAll(file(percorso).readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    /** Un testo con almeno tre lettere e tutto in maiuscolo: "INTERROTTA" si', "B" e "TB 7-5" no. */
    private fun tuttoMaiuscolo(testo: String): Boolean {
        val lettere = testo.filter { it.isLetter() }
        return lettere.length >= 3 && lettere == lettere.uppercase()
    }

    @Test
    fun `le stringhe della Cronaca non sono in maiuscolo in nessuna lingua e hanno le stesse chiavi`() {
        val inglese = stringhe("res/values/strings.xml")
        val italiano = stringhe("res/values-it/strings.xml")
        assertTrue(inglese.size > 40)
        assertEquals("chiavi diverse fra le lingue", inglese.keys, italiano.keys)
        for ((lingua, mappa) in listOf("en" to inglese, "it" to italiano)) {
            val maiuscole = mappa.filterValues { tuttoMaiuscolo(it) }
            assertEquals("$lingua: ${maiuscole.keys}", emptyMap<String, String>(), maiuscole)
        }
        // Falsificazione: i testi di prima erano tutti maiuscoli e il controllo li vede.
        assertTrue(tuttoMaiuscolo("INTERROTTA"))
        assertTrue(tuttoMaiuscolo("CHRONICLE"))
        assertTrue(!tuttoMaiuscolo("B"))
        assertTrue(!tuttoMaiuscolo("TB %1\$d-%2\$d"))
    }

    // --- Colori Street: tutti usciti (G-5, G-8, G-9) ---

    private val sorgenti
        get() =
            file(
                "",
            ).walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") && it.name != "colors.xml" }.toList()

    @Test
    fun `nessun colore Street esiste ancora in colors xml e nessuno lo cita, nemmeno l'icona`() {
        val colori = file("res/values/colors.xml").readText()
        // Usciti con G-5 (la Cronaca), con G-8 (il PDF) e con G-9 (l'icona, ultimo uso).
        val usciti =
            listOf(
                "concrete_gray",
                "graffiti_dark_gray",
                "outline_gray",
                "asphalt_black",
                "stencil_white",
                "sidewalk_gray",
                "asphalt_dark",
                "team_spray_yellow",
                "team_electric_green",
            )
        for (nome in usciti) {
            assertTrue("$nome e' ancora definito", !colori.contains("name=\"$nome\""))
        }
        val citanti = sorgenti.filter { coloriStreet.containsMatchIn(it.readText()) }
        assertTrue("citano ancora un colore Street: ${citanti.map { it.name }}", citanti.isEmpty())
    }

    @Test
    fun `l'icona del telefono e' fatta di token e non di colori Street`() {
        val sfondo = file("res/drawable/ic_launcher_background_vs.xml").readText()
        val primo = file("res/drawable/ic_launcher_foreground_vs.xml").readText()
        // Il launcher non ha il tema dell'app: i colori sono quelli pieni, non i token che seguono il tema.
        assertTrue("il fondo non e' background-canvas", sfondo.contains("@color/elite_background_standard"))
        assertTrue("manca il lime", primo.contains("@color/elite_lime"))
        assertTrue("manca il testo primario", primo.contains("@color/elite_text_primary_standard"))
        assertTrue(
            "l'icona cita un token che segue il tema",
            !Regex("@color/elite_(background|text_primary)\"").containsMatchIn(sfondo + primo),
        )
        // Falsificazione: il fondo e le colonne di prima erano Street e il controllo li vede.
        assertTrue(coloriStreet.containsMatchIn("""<solid android:color="@color/asphalt_dark"/>"""))
        assertTrue(coloriStreet.containsMatchIn("""android:fillColor="@color/team_spray_yellow""""))
    }
}
