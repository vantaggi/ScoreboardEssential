package it.vantaggi.scoreboardessential

import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.ui.MatchHistoryViewModel
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * La cronologia non rigioca il registro di ogni partita a ogni emissione: Room rilegge la query
 * a ogni punto della partita viva, e lo storico non e' cambiato.
 */
class CacheDelleRigheTest {
    private val chiamate = mutableListOf<Int>()

    private fun viewModel(): MatchHistoryViewModel {
        val repository = mock<MatchRepository>()
        whenever(repository.allMatches).thenReturn(emptyFlow())
        return MatchHistoryViewModel(repository) { match ->
            chiamate += match.matchId
            "riga-${match.matchId}"
        }
    }

    private fun partita(
        id: Int,
        registro: String = "",
    ) = MatchWithTeams(
        Match(
            matchId = id,
            team1Id = 1,
            team2Id = 2,
            team1Score = 1,
            team2Score = 0,
            timestamp = 0L,
            sportId = SportRegistry.TENNIS,
            eventLog = registro,
        ),
        null,
        null,
        emptyList(),
    )

    @Test
    fun `due emissioni con le stesse partite calcolano la riga una volta per partita`() {
        val vm = viewModel()
        val storico = listOf(partita(1, "a"), partita(2, "b"))

        val prima = vm.elenco(storico)
        val seconda = vm.elenco(storico)

        assertEquals(listOf(1, 2), chiamate)
        assertEquals(listOf("riga-1", "riga-2"), seconda.map { it.setLine })
        assertEquals(prima.map { it.setLine }, seconda.map { it.setLine })
    }

    @Test
    fun `una partita con registro diverso si ricalcola e le altre no`() {
        val vm = viewModel()
        vm.elenco(listOf(partita(1, "a"), partita(2, "b")))
        chiamate.clear()

        vm.elenco(listOf(partita(1, "a"), partita(2, "b-con-un-punto-in-piu")))

        assertEquals(listOf(2), chiamate)
    }
}
