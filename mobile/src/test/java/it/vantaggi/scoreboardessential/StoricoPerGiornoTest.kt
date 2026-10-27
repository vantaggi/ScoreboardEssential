package it.vantaggi.scoreboardessential

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.ui.GiorniDelloStorico
import it.vantaggi.scoreboardessential.ui.MatchHistoryUiState
import it.vantaggi.scoreboardessential.ui.RigaDelloStorico
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Lo Storico per giorno (passo 5b della revisione dei menu): un titolo prima della prima partita di
 * ogni giorno, «Oggi» e «Ieri» per i giorni vicini e la data per gli altri. Rosso se due partite
 * dello stesso giorno prendono due titoli, se un giorno nuovo non ne prende uno, o se la data
 * perde il giorno della settimana o mostra l'anno in corso.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it")
class StoricoPerGiornoTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val zona = ZoneId.of("Europe/Rome")

    private fun partita(
        id: Int,
        quando: LocalDateTime,
    ) = MatchHistoryUiState(
        MatchWithTeams(
            Match(
                matchId = id,
                team1Id = 1,
                team2Id = 2,
                team1Score = 1,
                team2Score = 0,
                timestamp = quando.atZone(zona).toInstant().toEpochMilli(),
            ),
            null,
            null,
            emptyList(),
        ),
        "",
    )

    @Test
    fun `un titolo prima della prima partita di ogni giorno`() {
        val sera = partita(3, LocalDateTime.of(2026, 10, 27, 21, 30))
        val pomeriggio = partita(2, LocalDateTime.of(2026, 10, 27, 17, 0))
        // Un minuto prima di mezzanotte e' ancora il giorno prima, nel fuso del telefono.
        val notte = partita(1, LocalDateTime.of(2026, 10, 26, 23, 59))

        val righe = GiorniDelloStorico.dividi(listOf(sera, pomeriggio, notte), zona)

        assertEquals(
            listOf(
                RigaDelloStorico.Giorno(LocalDate.of(2026, 10, 27)),
                RigaDelloStorico.Partita(sera),
                RigaDelloStorico.Partita(pomeriggio),
                RigaDelloStorico.Giorno(LocalDate.of(2026, 10, 26)),
                RigaDelloStorico.Partita(notte),
            ),
            righe,
        )
    }

    @Test
    fun `senza partite nessun titolo`() {
        assertEquals(emptyList<RigaDelloStorico>(), GiorniDelloStorico.dividi(emptyList(), zona))
    }

    @Test
    fun `oggi, ieri e la data col giorno della settimana`() {
        val oggi = LocalDate.of(2026, 10, 27)

        fun etichetta(giorno: LocalDate) = GiorniDelloStorico.etichetta(app, giorno, oggi)

        assertEquals("Oggi", etichetta(oggi))
        assertEquals("Ieri", etichetta(oggi.minusDays(1)))
        assertEquals("Domenica 25 ottobre", etichetta(oggi.minusDays(2)))
        assertEquals("Mercoledì 12 novembre 2025", etichetta(LocalDate.of(2025, 11, 12)))
    }
}
