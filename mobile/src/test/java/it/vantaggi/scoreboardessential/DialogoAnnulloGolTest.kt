package it.vantaggi.scoreboardessential

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DialogoAnnulloGolTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    /**
     * Il dialogo dell'annullo del gol aveva due tasti con la stessa parola, "Annulla": uno annullava
     * il gol, l'altro chiudeva il dialogo. Rosso se la conferma e la rinuncia tornano a dire la
     * stessa cosa, o se la rinuncia torna ad essere il generico "Annulla".
     */
    @Test
    fun `i due tasti del dialogo dell'annullo gol dicono cose diverse`() {
        val conferma = app.getString(R.string.undo_goal_confirm)
        val rinuncia = app.getString(R.string.undo_goal_keep)
        assertNotEquals(conferma, rinuncia)
        assertNotEquals(app.getString(R.string.cancel), rinuncia)
        assertNotEquals(app.getString(R.string.cancel), conferma)
    }
}
