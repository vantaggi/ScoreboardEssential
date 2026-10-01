package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.shape.MaterialShapeDrawable
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.database.Team
import it.vantaggi.scoreboardessential.ui.MatchHistoryUiState
import it.vantaggi.scoreboardessential.ui.RigaDeiSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * La cronologia: i colori con cui si e' giocato, chi ha vinto, sport e durata, la riga dei set.
 */
@RunWith(AndroidJUnit4::class)
class StoricoDelContornoTest {
    private val base: Context get() = ApplicationProvider.getApplicationContext()
    private val tema: Context get() = ContextThemeWrapper(base, R.style.Theme_ScoreboardEssential)

    private val bluNotte = 0xFF1A237E.toInt()
    private val giallo = 0xFFFFD600.toInt()
    private val fine = 1_790_193_000_000L

    private fun punti(
        lato: Int,
        quanti: Int,
    ) = List(quanti) { LoggedEvent(ScoringEvent.Point(side = lato)) }

    private fun partita(
        sport: String,
        punteggio: Pair<Int, Int>,
        registro: List<LoggedEvent> = emptyList(),
        minuti: Long? = null,
    ) = Match(
        team1Id = 1,
        team2Id = 2,
        team1Score = punteggio.first,
        team2Score = punteggio.second,
        timestamp = fine,
        sportId = sport,
        eventLog = MatchLogCodec.encode(registro),
        startedAt = minuti?.let { fine - it * 60_000L },
    )

    private fun scheda(
        match: Match,
        giocatori: String = "",
    ): Pair<MatchHistoryAdapter.MatchViewHolder, View> {
        val vista = LayoutInflater.from(tema).inflate(R.layout.match_item, FrameLayout(tema), false)
        val riga = MatchHistoryAdapter.MatchViewHolder(vista, {}, {}, {})
        val squadre =
            MatchWithTeams(
                match,
                Team(id = 1, name = "Blu", color = bluNotte, logoUri = null),
                Team(id = 2, name = "Gialli", color = giallo, logoUri = null),
                emptyList(),
            )
        riga.bind(MatchHistoryUiState(squadre, giocatori, RigaDeiSet.of(match)))
        return riga to vista
    }

    private fun testo(
        vista: View,
        id: Int,
    ) = vista.findViewById<TextView>(id)

    // Prima i nomi erano sempre giallo e verde: Team.color veniva ignorato.
    @Test
    fun `le etichette portano il colore con cui si e' giocato e l'inchiostro giusto`() {
        val (_, vista) = scheda(partita(SportRegistry.PADEL, 6 to 4))

        val blu = testo(vista, R.id.team1_name_textview)
        val gialla = testo(vista, R.id.team2_name_textview)
        assertEquals(bluNotte, (blu.background as MaterialShapeDrawable).fillColor?.defaultColor)
        assertEquals(giallo, (gialla.background as MaterialShapeDrawable).fillColor?.defaultColor)
        assertEquals(TeamInk.BIANCO, blu.currentTextColor)
        assertEquals(TeamInk.NERO, gialla.currentTextColor)
        // Un blu notte sulla card grigia senza bordo sarebbe una macchia: 1,26:1.
        assertTrue((blu.background as MaterialShapeDrawable).strokeWidth > 0f)
    }

    // Chi ha vinto si legge senza il colore: #E0E0E0 il vincitore, #9E9E9E lo sconfitto.
    @Test
    fun `il vincitore e' chiaro e lo sconfitto e' grigio`() {
        val chiaro = base.getColor(R.color.stencil_white)
        val grigio = base.getColor(R.color.sidewalk_gray)

        val (_, vince1) = scheda(partita(SportRegistry.FOOTBALL, 3 to 1))
        assertEquals(chiaro, testo(vince1, R.id.team1_score_textview).currentTextColor)
        assertEquals(grigio, testo(vince1, R.id.team2_score_textview).currentTextColor)

        val (_, vince2) = scheda(partita(SportRegistry.FOOTBALL, 0 to 2))
        assertEquals(grigio, testo(vince2, R.id.team1_score_textview).currentTextColor)
        assertEquals(chiaro, testo(vince2, R.id.team2_score_textview).currentTextColor)

        val (_, pari) = scheda(partita(SportRegistry.FOOTBALL, 2 to 2))
        assertEquals(chiaro, testo(pari, R.id.team1_score_textview).currentTextColor)
        assertEquals(chiaro, testo(pari, R.id.team2_score_textview).currentTextColor)
    }

    // Un 6-4 di padel, un 2-1 di tennis e un 2-1 di calcio erano indistinguibili.
    @Test
    @Config(qualifiers = "it")
    fun `la riga meta dice sport data e durata`() {
        val (_, vista) = scheda(partita(SportRegistry.PADEL, 6 to 4, minuti = 47))
        val meta = testo(vista, R.id.timestamp_textview).text.toString()
        assertTrue(meta, Regex("""PADEL · \d\d/\d\d \d\d:\d\d · 47 MIN""").matches(meta))

        val (_, senzaDurata) = scheda(partita(SportRegistry.FOOTBALL, 2 to 1))
        val metaSenza = testo(senzaDurata, R.id.timestamp_textview).text.toString()
        assertTrue(metaSenza, Regex("""CALCIO · \d\d/\d\d \d\d:\d\d""").matches(metaSenza))
    }

    @Test
    @Config(qualifiers = "it")
    fun `l'elenco dei giocatori ha l'etichetta nella lingua dell'app`() {
        val (_, vista) = scheda(partita(SportRegistry.FOOTBALL, 2 to 1), "Mario, Luca")
        assertEquals("Giocatori: Mario, Luca", testo(vista, R.id.players_textview).text.toString())
    }

    // La riga dei set si rigioca dal registro con le regole del tennis: tre set, tutti 6-0.
    @Test
    fun `il tennis mostra i set chiusi dal motore`() {
        val registro = punti(1, 24) + punti(2, 24) + punti(1, 24)
        val match = partita(SportRegistry.TENNIS, 2 to 1, registro)

        assertEquals("6-0 · 0-6 · 6-0", RigaDeiSet.of(match))
        val (_, vista) = scheda(match)
        assertEquals("6-0 · 0-6 · 6-0", testo(vista, R.id.sets_textview).text.toString())
    }

    // Una partita interrotta non perde il set in corso.
    @Test
    fun `il set in corso conta come un set in piu`() {
        val registro = punti(1, 24) + punti(2, 4)
        assertEquals("6-0 · 0-1", RigaDeiSet.of(partita(SportRegistry.TENNIS, 1 to 0, registro)))
    }

    // Subito dopo la chiusura di un set il successivo e' 0-0 anche con qualche punto giocato: non e' un set.
    @Test
    fun `un set in corso senza game non entra nella riga`() {
        val registro = punti(1, 24) + punti(2, 2)
        assertEquals("6-0", RigaDeiSet.of(partita(SportRegistry.TENNIS, 1 to 0, registro)))
    }

    @Test
    fun `dove non c'e' niente da aggiungere la riga dei set manca`() {
        assertNull("calcio", RigaDeiSet.of(partita(SportRegistry.FOOTBALL, 2 to 1, punti(1, 2) + punti(2, 1))))
        assertNull("padel a set unico: il punteggio e' quel set", RigaDeiSet.of(partita(SportRegistry.PADEL, 6 to 0, punti(1, 24))))
        assertNull("tennis senza registro", RigaDeiSet.of(partita(SportRegistry.TENNIS, 2 to 1)))
    }

    // Nel padel la riga dice la regola, nel calcio non c'e' riga.
    @Test
    @Config(qualifiers = "it")
    fun `il padel senza set dice la regola e il calcio niente`() {
        val (_, padel) = scheda(partita(SportRegistry.PADEL, 6 to 4))
        assertEquals(View.VISIBLE, testo(padel, R.id.sets_textview).visibility)
        assertEquals("Set unico · punto secco sul 40–40 · tie-break a 7", testo(padel, R.id.sets_textview).text.toString())

        val (_, calcio) = scheda(partita(SportRegistry.FOOTBALL, 2 to 1))
        assertEquals(View.GONE, testo(calcio, R.id.sets_textview).visibility)
    }
}
