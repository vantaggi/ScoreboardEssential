package it.vantaggi.scoreboardessential.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun testFormatTime() {
        assertEquals("00:00", TimeUtils.formatTime(0))
        assertEquals("00:01", TimeUtils.formatTime(1000))
        assertEquals("00:59", TimeUtils.formatTime(59000))
        assertEquals("01:00", TimeUtils.formatTime(60000))
        assertEquals("01:01", TimeUtils.formatTime(61000))
        assertEquals("10:00", TimeUtils.formatTime(600000))
        assertEquals("99:59", TimeUtils.formatTime(5999000))
        assertEquals("100:05", TimeUtils.formatTime(6005000))
    }
}
