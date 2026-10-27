package it.vantaggi.scoreboardessential.padelelite

import it.vantaggi.scoreboardessential.database.PadelEliteLink
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.padelelite.SezioneDellaScelta.DASHBOARD
import it.vantaggi.scoreboardessential.padelelite.SezioneDellaScelta.DASHBOARD_NON_LETTA
import it.vantaggi.scoreboardessential.padelelite.SezioneDellaScelta.SOLO_APP
import it.vantaggi.scoreboardessential.padelelite.VoceDellaScelta.DallaDashboard
import it.vantaggi.scoreboardessential.padelelite.VoceDellaScelta.Intestazione
import it.vantaggi.scoreboardessential.padelelite.VoceDellaScelta.SoloNellApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'elenco di "Aggiungi un giocatore" con la rosa del gruppo: prima la dashboard (collegati e no), poi chi c'e'
 * solo nell'app; chi e' gia' in squadra non si offre; senza rosa letta restano i giocatori dell'app.
 */
class SceltaDelGiocatoreTest {
    private fun locale(
        id: Int,
        nome: String,
    ) = Player(playerId = id, playerName = nome, appearances = 0, goals = 0)

    private val rossi = RemotePlayer(101, "Rossi")
    private val bianchi = RemotePlayer(100, "bianchi")
    private val verdi = RemotePlayer(102, "Verdi")

    @Test
    fun `prima la rosa della dashboard in ordine di nome, poi chi c'e' solo nell'app`() {
        val voci =
            SceltaDelGiocatore.voci(
                rosa = listOf(rossi, bianchi, verdi),
                locali = listOf(locale(1, "Mattia"), locale(2, "Luca"), locale(3, "Rossi Marco")),
                collegamenti = listOf(PadelEliteLink(1, "g-1", 102, "Verdi"), PadelEliteLink(3, "g-1", 101, "Rossi")),
                esclusi = emptySet(),
            )

        assertEquals(
            listOf(
                Intestazione(DASHBOARD),
                DallaDashboard(bianchi, null, null),
                DallaDashboard(rossi, 3, "Rossi Marco"),
                DallaDashboard(verdi, 1, "Mattia"),
                // I collegati in questo gruppo sono gia' nella sezione della dashboard: qui non si ripetono.
                Intestazione(SOLO_APP),
                SoloNellApp(2, "Luca"),
            ),
            voci,
        )
    }

    @Test
    fun `chi e' gia' in questa squadra non si offre, ne' dalla dashboard ne' dall'app`() {
        val voci =
            SceltaDelGiocatore.voci(
                rosa = listOf(rossi, verdi),
                locali = listOf(locale(1, "Verdi"), locale(2, "Luca")),
                collegamenti = listOf(PadelEliteLink(1, "g-1", 102, "Verdi")),
                esclusi = setOf(1, 2),
            )

        assertEquals(listOf(Intestazione(DASHBOARD), DallaDashboard(rossi, null, null)), voci)
    }

    @Test
    fun `senza rosa letta la sezione lo dice e restano i giocatori dell'app`() {
        val voci = SceltaDelGiocatore.voci(null, listOf(locale(1, "Luca")), emptyList(), emptySet())

        assertEquals(listOf(Intestazione(DASHBOARD_NON_LETTA), SoloNellApp(1, "Luca")), voci)
    }

    @Test
    fun `niente da offrire e' un elenco vuoto, senza intestazioni sole`() {
        assertTrue(SceltaDelGiocatore.voci(null, listOf(locale(1, "Luca")), emptyList(), setOf(1)).isEmpty())
        assertTrue(SceltaDelGiocatore.voci(emptyList(), emptyList(), emptyList(), emptySet()).isEmpty())
    }
}
