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

    // --- Colori Street usciti con questo passo ---

    @Test
    fun `i colori Street che nessuno citava piu' sono stati tolti da colors xml e quello rimasto lo cita solo l'icona`() {
        val colori = file("res/values/colors.xml").readText()
        // Usciti con G-5 (la Cronaca) e con G-8 (il PDF).
        for (nome in listOf("concrete_gray", "graffiti_dark_gray", "outline_gray", "asphalt_black", "stencil_white", "sidewalk_gray")) {
            assertTrue("$nome e' ancora definito", !colori.contains("name=\"$nome\""))
        }
        // Quello rimasto lo cita l'icona (G-9); se nessuno lo cita piu' va tolto, e la Cronaca non lo deve citare.
        val sorgenti =
            file(
                "",
            ).walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") && it.name != "colors.xml" }.toList()
        for (nome in listOf("asphalt_dark")) {
            val citanti = sorgenti.filter { it.readText().contains("color/$nome") || it.readText().contains("color.$nome") }
            assertTrue("$nome non lo cita nessuno: toglierlo", citanti.isNotEmpty())
            assertTrue("$nome e' citato dalla Cronaca: ${citanti.map { it.name }}", citanti.none { "chronic" in it.name.lowercase() })
            assertEquals("$nome deve restare solo all'icona (G-9)", listOf("ic_launcher_background_vs.xml"), citanti.map { it.name })
        }
    }
}
