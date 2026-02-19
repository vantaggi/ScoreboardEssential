package it.vantaggi.scoreboardessential.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDataTest {

    @Test
    fun `constructor sets properties correctly`() {
        val id = 1
        val name = "Test Player"
        val roles = listOf("Forward", "Midfielder")
        val goals = 5
        val appearances = 10

        val playerData = PlayerData(id, name, roles, goals, appearances)

        assertEquals(id, playerData.id)
        assertEquals(name, playerData.name)
        assertEquals(roles, playerData.roles)
        assertEquals(goals, playerData.goals)
        assertEquals(appearances, playerData.appearances)
    }

    @Test
    fun `constructor uses default values for goals and appearances`() {
        val id = 2
        val name = "Default Player"
        val roles = listOf("Defender")

        val playerData = PlayerData(id, name, roles)

        assertEquals(id, playerData.id)
        assertEquals(name, playerData.name)
        assertEquals(roles, playerData.roles)
        assertEquals(0, playerData.goals)
        assertEquals(0, playerData.appearances)
    }

    @Test
    fun `equals works correctly`() {
        val roles = listOf("Forward")
        val player1 = PlayerData(1, "Player", roles, 5, 10)
        val player2 = PlayerData(1, "Player", roles, 5, 10)
        val player3 = PlayerData(2, "Player", roles, 5, 10)

        assertEquals(player1, player2)
        assertNotEquals(player1, player3)
    }

    @Test
    fun `hashCode works correctly`() {
        val roles = listOf("Forward")
        val player1 = PlayerData(1, "Player", roles, 5, 10)
        val player2 = PlayerData(1, "Player", roles, 5, 10)

        assertEquals(player1.hashCode(), player2.hashCode())
    }

    @Test
    fun `toString contains all fields`() {
        val id = 1
        val name = "Test Player"
        val roles = listOf("Forward")
        val goals = 5
        val appearances = 10

        val playerData = PlayerData(id, name, roles, goals, appearances)
        val toString = playerData.toString()

        assertTrue(toString.contains("id=$id"))
        assertTrue(toString.contains("name=$name"))
        assertTrue(toString.contains("roles=$roles"))
        assertTrue(toString.contains("goals=$goals"))
        assertTrue(toString.contains("appearances=$appearances"))
    }

    @Test
    fun `copy works correctly`() {
        val roles = listOf("Forward")
        val original = PlayerData(1, "Original", roles, 5, 10)
        val copied = original.copy(name = "Copied", goals = 10)

        assertEquals(1, copied.id)
        assertEquals("Copied", copied.name)
        assertEquals(roles, copied.roles)
        assertEquals(10, copied.goals)
        assertEquals(10, copied.appearances)
        assertEquals(original.id, copied.id)
        assertNotEquals(original.name, copied.name)
    }
}
