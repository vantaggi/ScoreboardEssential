package it.vantaggi.scoreboardessential.views

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.domain.models.Formation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FormationViewTest {
    @Test
    fun setFormation_addsCorrectNumberOfChildViews() {
        // Use a themed context so Material components work
        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        val context = ContextThemeWrapper(appCtx, R.style.Theme_ScoreboardEssential)

        val view = FormationView(context)

        // Set size so updateViews doesn't return early
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1000, 1500)

        val p1 = PlayerWithRoles(Player(1, "GK", 0, 0), emptyList())
        val p2 = PlayerWithRoles(Player(2, "DEF", 0, 0), emptyList())

        val formation =
            Formation(
                goalkeeper = listOf(p1),
                defenders = listOf(p2),
                midfielders = emptyList(),
                forwards = emptyList(),
            )

        view.setFormation(formation)

        // Expected: 1 GK + 1 DEF = 2 children
        assertEquals(2, view.childCount)
    }
}
