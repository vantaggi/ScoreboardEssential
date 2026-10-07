package it.vantaggi.scoreboardessential

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Passo G-9, il maiuscolo dei messaggi composti (conflitto 11): la striscia, la barra, il dialogo
 * di fine partita e il registro dei game si scrivono in frase, con i nomi delle squadre come
 * l'utente li ha scritti. L'unica parola in maiuscolo che resta e' CAMBIO del portiere scaduto, che
 * e' uno stato d'allarme dichiarato. Il conto del portiere che corre e' testo primario, non lime:
 * il lime marca solo chi serve (la stessa scelta dell'orologio). Ogni controllo ha la sua falsificazione.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "it")
class ChiusuraG9Test {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val padel = SportRegistry.byId(SportRegistry.PADEL)

    /** Il testo e' tutto in maiuscolo: ha almeno una lettera e nessuna e' minuscola, segnaposto esclusi. */
    private fun tuttoMaiuscolo(testo: String): Boolean {
        val lettere = testo.replace(Regex("%\\d*\\$?[a-z]"), "").filter { it.isLetter() }
        return lettere.length >= 3 && lettere.none { it.isLowerCase() }
    }

    private fun valori(percorso: String): Map<String, String> {
        val xml = File("src/main/res/$percorso").readText()
        return Regex("<string name=\"([a-z0-9_]+)\"[^>]*>([^<]*)</string>")
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2] } +
            Regex("<plurals name=\"([a-z0-9_]+)\">(.*?)</plurals>", RegexOption.DOT_MATCHES_ALL)
                .findAll(xml)
                .flatMap { p ->
                    Regex("<item quantity=\"([a-z]+)\">([^<]*)</item>")
                        .findAll(p.groupValues[2])
                        .map { "${p.groupValues[1]}.${it.groupValues[1]}" to it.groupValues[2] }
                }.toMap()
    }

    private val prefissiDeiMessaggi = listOf("strip_", "bar_", "end_match_over", "log_game", "log_goal", "log_point")

    private fun maiuscoliNei(percorso: String): List<String> =
        valori(percorso)
            .filter { (nome, _) -> prefissiDeiMessaggi.any { nome.startsWith(it) } }
            .filter { (_, testo) -> tuttoMaiuscolo(testo) }
            .keys
            .toList()

    @Test
    fun `i messaggi composti non sono in maiuscolo ne' in italiano ne' in inglese`() {
        assertEquals(emptyList<String>(), maiuscoliNei("values/strings.xml"))
        assertEquals(emptyList<String>(), maiuscoliNei("values-it/strings.xml"))
        // I messaggi li abbiamo letti davvero: senza questo un prefisso sbagliato farebbe passare tutto.
        assertTrue(valori("values-it/strings.xml").keys.containsAll(listOf("strip_point", "bar_winner", "log_game", "strip_none_goal")))
        assertEquals("Punto %1\$s · %2\$s", valori("values-it/strings.xml").getValue("strip_point"))
        assertEquals("Vince %1\$s · %2\$s", valori("values-it/strings.xml").getValue("bar_winner"))
    }

    @Test
    fun `falsificazione, il controllo vede i messaggi di prima e lascia stare quelli in frase`() {
        assertTrue(tuttoMaiuscolo("PUNTO %1\$s · %2\$s"))
        assertTrue(tuttoMaiuscolo("PARTITA FINITA · TERMINA ›"))
        assertTrue(tuttoMaiuscolo("%1\$d PUNTI DALL'OROLOGIO"))
        assertFalse(tuttoMaiuscolo("Punto %1\$s · %2\$s"))
        assertFalse(tuttoMaiuscolo("Partita finita · termina ›"))
        assertFalse(tuttoMaiuscolo("%1\$d punti dall'orologio"))
    }

    @Test
    fun `l'unica parola in maiuscolo del telefono fra i comandi e' CAMBIO del portiere scaduto`() {
        for ((file, cambio) in listOf("values/strings.xml" to "CHANGE", "values-it/strings.xml" to "CAMBIO")) {
            val v = valori(file)
            assertEquals(cambio, v.getValue("label_keeper_change"))
            val tutti = v.filterValues { tuttoMaiuscolo(it) }.keys
            assertEquals("$file: maiuscoli oltre all'allarme", setOf("label_keeper_change"), tutti.filter { it.startsWith("label_") }.toSet())
        }
    }

    @Test
    fun `il codice dei messaggi non maiuscolizza piu' i nomi delle squadre`() {
        val sorgenti =
            listOf("StatoStriscia.kt", "PartitaARacchetta.kt", "TestiDelGame.kt", "MainActivity.kt")
                .map { it to File("src/main/java/it/vantaggi/scoreboardessential/$it").readText() }
        for ((nome, testo) in sorgenti) {
            assertFalse("$nome ha ancora maiuscolo()", Regex("""\bmaiuscolo\b""").containsMatchIn(codice(testo)))
            assertFalse("$nome maiuscolizza un testo", Regex("""\.(uppercase|toUpperCase)\(""").containsMatchIn(codice(testo)))
        }
        // Falsificazione: il codice di prima.
        assertTrue(Regex("""\.(uppercase|toUpperCase)\(""").containsMatchIn("val nome = nomeSquadra1.uppercase(Locale.getDefault())"))
        assertTrue(Regex("""\bmaiuscolo\b""").containsMatchIn("val squadra = nomeDellaSquadra.maiuscolo()"))
    }

    /** Il testo senza i commenti, che possono nominare il vecchio comportamento. */
    private fun codice(sorgente: String): String =
        sorgente.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").lines().joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `striscia, barra e dialogo scrivono i nomi come l'utente li ha scritti`() {
        val evento =
            MatchEvent(
                timestamp = "12'",
                event = "Point",
                team = 1,
                player = "Real Madrid",
                type = MatchEventType.SCORE,
                engineIndex = 0,
            )
        val striscia =
            statoDellaStriscia(
                context,
                listOf(evento),
                padel.capabilities,
                ScoreDisplay(side1Primary = "40", side2Primary = "30"),
                "Real Madrid",
                "aC mIlAn",
            )
        assertEquals("Punto Real Madrid · 40-30", striscia.testo)

        val finita = ScoreDisplay(side1Primary = "3", side1Secondary = "6-3", side2Primary = "6", matchOver = true)
        assertEquals("Vince aC mIlAn · 6-3", testoDellaBarra(context, padel.id, finita, "Real Madrid", "aC mIlAn"))
        val dialogo = testoDelDialogoDiFine(context, padel.id, finita, "Real Madrid", "aC mIlAn")
        assertEquals("Partita finita", dialogo.titolo)
        assertEquals("Vince aC mIlAn · 6-3", dialogo.messaggio)

        val inCorso = ScoreDisplay(side1Primary = "30", side1Secondary = "5-3", side2Primary = "15")
        assertEquals(
            "Real Madrid – aC mIlAn · game 5-3 · punto 30-15",
            testoDelDialogoDiFine(context, padel.id, inCorso, "Real Madrid", "aC mIlAn").messaggio,
        )
    }

    @Test
    fun `il conto del portiere che corre e' testo primario e non lime`() {
        val sorgente = File("src/main/java/it/vantaggi/scoreboardessential/MainActivity.kt").readText()
        val funzione = sorgente.substringAfter("private fun updateKeeperTimerTextView").substringBefore("private fun dimensioneDelCambio")
        val corsa = funzione.lines().single { it.contains("inCorso ->") }
        assertTrue("il conto che corre non e' testo primario: $corsa", corsa.contains("R.color.elite_text_primary"))
        assertFalse("il lime non e' di chi corre: $corsa", funzione.contains("elite_lime"))
        // Il fermo resta secondario e la scadenza resta l'allarme: tre stati, tre segni.
        assertTrue(funzione.contains("else -> ContextCompat.getColor(this, R.color.elite_text_secondary)"))
        assertTrue(funzione.contains("scaduto -> nero"))
        // Falsificazione: la riga di prima.
        assertFalse("inCorso -> ContextCompat.getColor(this, R.color.elite_lime)".contains("R.color.elite_text_primary"))
    }
}
