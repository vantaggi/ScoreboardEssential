package it.vantaggi.scoreboardessential.utils

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoleUtilsAndroidTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `getCategoryColor returns correct color for ATTACCO`() {
        val expected = ContextCompat.getColor(context, R.color.graffiti_pink)
        val actual = RoleUtils.getCategoryColor(context, "ATTACCO")
        assertEquals(expected, actual)
    }

    @Test
    fun `getCategoryColor returns correct color for CENTROCAMPO`() {
        val expected = ContextCompat.getColor(context, R.color.neon_cyan)
        val actual = RoleUtils.getCategoryColor(context, "CENTROCAMPO")
        assertEquals(expected, actual)
    }

    @Test
    fun `getCategoryColor returns correct color for DIFESA`() {
        val expected = ContextCompat.getColor(context, R.color.team_spray_yellow)
        val actual = RoleUtils.getCategoryColor(context, "DIFESA")
        assertEquals(expected, actual)
    }

    @Test
    fun `getCategoryColor returns correct color for PORTA`() {
        val expected = ContextCompat.getColor(context, R.color.team_electric_green)
        val actual = RoleUtils.getCategoryColor(context, "PORTA")
        assertEquals(expected, actual)
    }

    @Test
    fun `getCategoryColor returns fallback color for unknown category`() {
        val expected = ContextCompat.getColor(context, R.color.sidewalk_gray)
        val actual = RoleUtils.getCategoryColor(context, "UNKNOWN")
        assertEquals(expected, actual)
    }

    @Test
    fun `getCategoryColor is case insensitive`() {
        val expected = ContextCompat.getColor(context, R.color.graffiti_pink)
        val actual = RoleUtils.getCategoryColor(context, "attacco")
        assertEquals(expected, actual)
    }
}
