package it.vantaggi.scoreboardessential.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

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

    @Test
    fun `il minuto di gioco si conta come nel calcio`() {
        assertEquals("1'", TimeUtils.matchMinute(0))
        assertEquals("1'", TimeUtils.matchMinute(59_999))
        assertEquals("2'", TimeUtils.matchMinute(60_000))
        // 65:10: oltre l'ora, dove "mm" di una data ripartiva da 05.
        assertEquals("66'", TimeUtils.matchMinute(3_910_000))
        assertEquals("121'", TimeUtils.matchMinute(7_200_000))
    }

    @Test
    fun `il minuto non dipende dal fuso del telefono`() {
        val prima = TimeZone.getDefault()
        try {
            // +5:30: col tempo trattato come data ogni riga slittava di mezz'ora.
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            assertEquals("66'", TimeUtils.matchMinute(3_910_000))
            assertEquals("1'", TimeUtils.matchMinute(30_000))
        } finally {
            TimeZone.setDefault(prima)
        }
    }
}
