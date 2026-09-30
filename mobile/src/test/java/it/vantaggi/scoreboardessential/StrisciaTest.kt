package it.vantaggi.scoreboardessential

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Il testo base della striscia dell'ultima azione, dato il registro. Il registro e' in ordine
 * cronologico inverso: il primo SCORE e' l'ultimo punto segnato. Il tocco sulla striscia e il
 * dialogo che apre si provano su emulatore (MainActivityLayoutTest).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "it")
class StrisciaTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val calcio = SportRegistry.byId(SportRegistry.FOOTBALL).capabilities
    private val padel = SportRegistry.byId(SportRegistry.PADEL).capabilities

    private fun gol(
        squadra: Int,
        indice: Int,
        marcatore: String? = null,
        idGiocatore: Int? = null,
    ) = MatchEvent(
        timestamp = "12'",
        event = "Goal",
        team = squadra,
        // Senza marcatore `player` porta il nome della squadra: e' il contratto di MatchEvent.
        player = marcatore ?: if (squadra == 1) "Rossi" else "Blu",
        type = MatchEventType.SCORE,
        engineIndex = indice,
        playerId = idGiocatore,
    )

    private val avvio = MatchEvent("0'", "New match ready")
    private val correzione = MatchEvent("13'", "Score correction for Rossi", team = 1, engineIndex = 1)

    private fun striscia(
        eventi: List<MatchEvent>?,
        capacita: it.vantaggi.scoreboardessential.core.SportCapabilities? = calcio,
        display: ScoreDisplay? = null,
    ) = statoDellaStriscia(context, eventi, capacita, display, "Rossi", "Blu")

    @Test
    fun `nel calcio senza marcatore la striscia chiede chi ha segnato e si puo toccare`() {
        val stato = striscia(listOf(gol(1, 0), avvio))

        assertEquals("GOL ROSSI · CHI HA SEGNATO? ›", stato.testo)
        assertNotNull("il gol senza marcatore e' la scorciatoia", stato.daAttribuire)
        assertEquals("e porta l'indice del motore di quel gol", 0, stato.daAttribuire?.engineIndex)
    }

    @Test
    fun `la striscia segue l'ultimo gol e la squadra che l'ha segnato`() {
        val stato = striscia(listOf(gol(2, 1), gol(1, 0), avvio))

        assertEquals("GOL BLU · CHI HA SEGNATO? ›", stato.testo)
        assertEquals(1, stato.daAttribuire?.engineIndex)
        assertEquals(2, stato.daAttribuire?.team)
    }

    @Test
    fun `nel calcio con il marcatore la striscia lo nomina e non offre niente da toccare`() {
        val stato = striscia(listOf(gol(1, 0, marcatore = "Marco B.", idGiocatore = 7), avvio))

        assertEquals("GOL ROSSI · MARCO B.", stato.testo)
        assertNull(stato.daAttribuire)
    }

    @Test
    fun `una correzione o un avvio in testa non nascondono l'ultimo gol`() {
        val stato = striscia(listOf(correzione, gol(1, 0), avvio))

        assertEquals("GOL ROSSI · CHI HA SEGNATO? ›", stato.testo)
    }

    @Test
    fun `senza punti la striscia dice che non c'e niente`() {
        assertEquals("NESSUN GOL", striscia(null).testo)
        assertEquals("NESSUN GOL", striscia(emptyList()).testo)
        assertEquals("NESSUN GOL", striscia(listOf(avvio)).testo)
        assertEquals("NESSUN PUNTO", striscia(listOf(avvio), capacita = padel).testo)
    }

    @Test
    fun `dopo ANNULLA la striscia torna al gol precedente e poi a nessun gol`() {
        // Annullare toglie la riga del gol dal registro: la striscia non ha memoria propria.
        val dopoIlPrimo = striscia(listOf(gol(2, 1, marcatore = "Anna", idGiocatore = 3), gol(1, 0), avvio))
        assertEquals("GOL BLU · ANNA", dopoIlPrimo.testo)

        val dopoAnnulla = striscia(listOf(gol(1, 0), avvio))
        assertEquals("GOL ROSSI · CHI HA SEGNATO? ›", dopoAnnulla.testo)

        val dopoAncora = striscia(listOf(avvio))
        assertEquals("NESSUN GOL", dopoAncora.testo)
        assertNull(dopoAncora.daAttribuire)
    }

    @Test
    fun `nel padel la striscia mostra il punto e il punteggio del display e non si tocca`() {
        val display = ScoreDisplay(side1Primary = "40", side2Primary = "30")
        val stato = striscia(listOf(gol(1, 0), avvio), capacita = padel, display = display)

        assertEquals("PUNTO ROSSI · 40-30", stato.testo)
        assertNull("negli sport senza marcatore non c'e' scorciatoia", stato.daAttribuire)
    }

    @Test
    fun `nel padel dopo ANNULLA senza altri punti torna nessun punto`() {
        val display = ScoreDisplay(side1Primary = "0", side2Primary = "0")

        assertEquals("NESSUN PUNTO", striscia(listOf(avvio), capacita = padel, display = display).testo)
    }

    @Test
    fun `finche le capacita non arrivano vale il calcio`() {
        assertTrue(striscia(listOf(gol(1, 0)), capacita = null).testo.contains("CHI HA SEGNATO"))
    }

    @Test
    fun `senza indice del motore il gol non si offre da attribuire`() {
        val vecchio = gol(1, 0).copy(engineIndex = null)

        assertNull(striscia(listOf(vecchio)).daAttribuire)
    }

    @Test
    @Config(qualifiers = "en")
    fun `in inglese il testo segue la lingua`() {
        assertEquals("GOAL ROSSI · WHO SCORED? ›", striscia(listOf(gol(1, 0))).testo)
        assertEquals("NO GOALS YET", striscia(emptyList()).testo)
    }
}
