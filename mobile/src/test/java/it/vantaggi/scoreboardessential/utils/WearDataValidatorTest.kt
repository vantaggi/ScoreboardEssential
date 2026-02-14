package it.vantaggi.scoreboardessential.utils

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
}
