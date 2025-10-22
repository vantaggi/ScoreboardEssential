package it.vantaggi.scoreboardessential.domain.models

import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.database.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormationTest {

    @Test
    fun `fromPlayers groups players by role category correctly`() {
        // Given
        val goalkeeper = PlayerWithRoles(
            Player(1, "Buffon", 0, 0),
            listOf(Role(1, "Portiere", "PORTA"))
        )
        val defender = PlayerWithRoles(
            Player(2, "Maldini", 0, 0),
            listOf(Role(2, "Difensore Centrale", "DIFESA"))
        )
        val midfielder = PlayerWithRoles(
            Player(3, "Pirlo", 0, 0),
            listOf(Role(3, "Centrocampista", "CENTROCAMPO"))
        )
        val forward = PlayerWithRoles(
            Player(4, "Del Piero", 0, 0),
            listOf(Role(4, "Attaccante", "ATTACCO"))
        )

        val players = listOf(goalkeeper, defender, midfielder, forward)

        // When
        val formation = Formation.fromPlayers(players)

        // Then
        assertEquals(1, formation.goalkeeper.size)
        assertEquals(1, formation.defenders.size)
        assertEquals(1, formation.midfielders.size)
        assertEquals(1, formation.forwards.size)

        assertEquals("Buffon", formation.goalkeeper.first().player.playerName)
        assertEquals("Maldini", formation.defenders.first().player.playerName)
    }

    @Test
    fun `getFormationString returns correct format`() {
        // Given
        val formation = Formation(
            goalkeeper = listOf(mockPlayerWithRoles()),
            defenders = listOf(mockPlayerWithRoles(), mockPlayerWithRoles(), mockPlayerWithRoles(), mockPlayerWithRoles()),
            midfielders = listOf(mockPlayerWithRoles(), mockPlayerWithRoles(), mockPlayerWithRoles()),
            forwards = listOf(mockPlayerWithRoles(), mockPlayerWithRoles())
        )

        // When
        val formationString = formation.getFormationString()

        // Then
        assertEquals("4-3-2", formationString)
    }

    @Test
    fun `isValid returns false when no goalkeeper`() {
        // Given
        val formation = Formation(
            goalkeeper = emptyList(),
            defenders = listOf(mockPlayerWithRoles()),
            midfielders = emptyList(),
            forwards = emptyList()
        )

        // When/Then
        assertFalse(formation.isValid())
    }

    @Test
    fun `isValid returns true when has goalkeeper`() {
        // Given
        val formation = Formation(
            goalkeeper = listOf(mockPlayerWithRoles()),
            defenders = emptyList(),
            midfielders = emptyList(),
            forwards = emptyList()
        )

        // When/Then
        assertTrue(formation.isValid())
    }

    private fun mockPlayerWithRoles() = PlayerWithRoles(
        Player(0, "Mock", 0, 0),
        emptyList()
    )
}