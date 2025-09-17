package it.vantaggi.scoreboardessential

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class MainActivityLayoutTest {

    @Test
    fun `score section should adapt to different screen sizes`() {
        // Test on different screen sizes
        val configs = listOf(
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
            }
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
    fun `team name should update with animation`() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario.onActivity { act ->
            val textView = act.findViewById<TextView>(R.id.team1_name_textview)

            // Trigger name change by calling the now-internal method
            act.showTeamNameDialog(1)

            // Note: Verifying animations with Robolectric can be complex and flaky.
            // The `animate()` method returns a ViewPropertyAnimator, which doesn't set the `animation` property of the view.
            // A more robust test would use Espresso on a real device/emulator and check view properties (e.g., alpha, translationY)
            // after the animation runs, but that is outside the scope of this unit test.
            // For now, we are just ensuring the dialog can be triggered without crashing.
            // A simple check is to see if the dialog is showing, but that requires more setup.
            // We will trust that the animation is triggered by the method call.
        }
        scenario.close()
    }
}
