package it.vantaggi.scoreboardessential

import android.content.Context
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.room.Room
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.MaterialShapeDrawable
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.ui.MatchSettingsActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/**
 * Il selettore del colore delle impostazioni parte dal colore della squadra.
 *
 * La ruota, alla prima misura, senza un colore di partenza seleziona il centro e manda il bianco
 * al listener: l'anteprima e il colore salvato con SELEZIONA ripartivano da bianco.
 */
@RunWith(RobolectricTestRunner::class)
class SelettoreDelColoreTest {
    private lateinit var database: AppDatabase
    private val bluNotte = 0xFF1A237E.toInt()

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val campo = AppDatabase::class.java.getDeclaredField("instance")
        campo.isAccessible = true
        campo.set(null, database)
    }

    @After
    fun tearDown() {
        database.close()
        val campo = AppDatabase::class.java.getDeclaredField("instance")
        campo.isAccessible = true
        campo.set(null, null)
    }

    private val preferenze
        get() = RuntimeEnvironment.getApplication().getSharedPreferences("match_settings_prefs", Context.MODE_PRIVATE)

    /** Il ViewModel legge le preferenze su Dispatchers.IO: si lascia girare finche' la condizione regge. */
    private fun attendi(
        cosa: String,
        condizione: () -> Boolean,
    ) {
        val fine = System.currentTimeMillis() + 5_000
        while (!condizione()) {
            check(System.currentTimeMillis() < fine) { "troppo tardi: $cosa" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun `aperto il selettore anteprima e colore salvato partono dal colore della squadra`() {
        preferenze.edit().putInt("team1_color", bluNotte).commit()
        val impostazioni = Robolectric.buildActivity(MatchSettingsActivity::class.java).setup().get()
        val pulsante = impostazioni.findViewById<MaterialButton>(R.id.team1ColorButton)
        attendi("il colore della squadra non e' arrivato al pulsante") { pulsante.currentTextColor == TeamInk.BIANCO }

        pulsante.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val dialogo = ShadowDialog.getLatestDialog() as AlertDialog
        val anteprima = dialogo.findViewById<TextView>(R.id.colorPreview)
        assertNotNull(anteprima)
        assertEquals(bluNotte, (anteprima!!.background as MaterialShapeDrawable).fillColor?.defaultColor)

        // SELEZIONA senza toccare la ruota non cambia il colore.
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(300)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(bluNotte, preferenze.getInt("team1_color", 0))
    }
}
