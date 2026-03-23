package it.vantaggi.scoreboardessential.shared.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearDataValidatorTest {
    @Test
    fun `isValidScore returns true for positive score`() {
        assertTrue(WearDataValidator.isValidScore(1))
    }

    @Test
    fun `isValidScore returns true for zero score`() {
        assertTrue(WearDataValidator.isValidScore(0))
    }

    @Test
    fun `isValidScore returns false for negative score`() {
        assertFalse(WearDataValidator.isValidScore(-1))
    }

    @Test
    fun `isValidTimer returns true for positive timer`() {
        assertTrue(WearDataValidator.isValidTimer(1000L))
    }

    @Test
    fun `isValidTimer returns true for zero timer`() {
        assertTrue(WearDataValidator.isValidTimer(0L))
    }

    @Test
    fun `isValidTimer returns false for negative timer`() {
        assertFalse(WearDataValidator.isValidTimer(-1000L))
    }

    @Test
    fun `isValidTeamNumber returns true for team 1`() {
        assertTrue(WearDataValidator.isValidTeamNumber(1))
    }

    @Test
    fun `isValidTeamNumber returns true for team 2`() {
        assertTrue(WearDataValidator.isValidTeamNumber(2))
    }

    @Test
    fun `isValidTeamNumber returns false for invalid team 0`() {
        assertFalse(WearDataValidator.isValidTeamNumber(0))
    }

    @Test
    fun `isValidTeamNumber returns false for invalid team 3`() {
        assertFalse(WearDataValidator.isValidTeamNumber(3))
    }

    @Test
    fun `isValidTeamNumber returns false for negative team number`() {
        assertFalse(WearDataValidator.isValidTeamNumber(-1))
    }
}
