package it.vantaggi.scoreboardessential

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.view.View
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class MainActivityLayoutTest {
    @Test
    fun `score section should adapt to different screen sizes`() {
        // Test on different screen sizes
        val configs =
            listOf(
                Configuration().apply {
                    screenWidthDp = 320
                    screenHeightDp = 480
                },
                Configuration().apply {
                    screenWidthDp = 480
                    screenHeightDp = 800
                },
                Configuration().apply {
                    screenWidthDp = 600
                    screenHeightDp = 1024
                },
            )

        configs.forEach { config ->
            val scenario = ActivityScenario.launch<MainActivity>(Intent())
            scenario.onActivity { activity ->
                activity.resources.configuration.updateFrom(config)

                val scoreSection = activity.findViewById<View>(R.id.score_section)

                // Verify that the section does not collapse
                // Using a minHeight of 280dp as a baseline from the original issue
                val minHeightPx = (280 * activity.resources.displayMetrics.density).toInt()
                assertTrue("Score section height should be >= $minHeightPx", scoreSection.height >= minHeightPx)
                assertTrue(scoreSection.visibility == View.VISIBLE)

                // Verify that the buttons are accessible
                val team1AddButton = activity.findViewById<View>(R.id.team1_add_button_card)
                assertTrue(team1AddButton.isClickable)
                val minButtonSizePx = (72 * activity.resources.displayMetrics.density).toInt()
                assertTrue("Button width should be >= $minButtonSizePx", team1AddButton.width >= minButtonSizePx)
                assertTrue("Button height should be >= $minButtonSizePx", team1AddButton.height >= minButtonSizePx)
            }
            scenario.close()
        }
    }

    @Test
    fun `clicking team name should show TeamNameDialogFragment`() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario.onActivity { activity ->
            // Find the clickable container for team 1's name
            val team1NameContainer = activity.findViewById<View>(R.id.team1_name_container)
            assertNotNull("Team 1 name container should not be null", team1NameContainer)

            // Simulate a click
            team1NameContainer.performClick()

            // Verify that the DialogFragment is shown
            val dialogFragment = activity.supportFragmentManager.findFragmentByTag(TeamNameDialogFragment.TAG)
            assertNotNull("TeamNameDialogFragment should be shown", dialogFragment)
            assertTrue("TeamNameDialogFragment should be a DialogFragment", dialogFragment is androidx.fragment.app.DialogFragment)
            assertTrue("TeamNameDialogFragment should be visible", (dialogFragment as androidx.fragment.app.DialogFragment).isVisible)
        }
        scenario.close()
    }
}
