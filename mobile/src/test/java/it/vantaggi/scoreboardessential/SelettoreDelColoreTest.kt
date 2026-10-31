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
import it.vantaggi.scoreboardessential.ui.TavolozzaDiSquadra
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/**
 * Il selettore del colore delle impostazioni parte dal colore della squadra (passo 7: otto colori e,
 * dietro "Altro colore", la ruota).
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

    private fun apriIlColoreDellaSquadra1(colore: Int?): AlertDialog {
        if (colore != null) preferenze.edit().putInt("team1_color", colore).commit()
        val impostazioni = Robolectric.buildActivity(MatchSettingsActivity::class.java).setup().get()
        val pulsante = impostazioni.findViewById<MaterialButton>(R.id.team1ColorButton)
        colore?.let { atteso ->
            attendi("il colore della squadra non e' arrivato al pulsante") { pulsante.currentTextColor == TeamInk.on(atteso) }
        }

        pulsante.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private fun cerchi(dialogo: AlertDialog) = TavolozzaDiSquadra.cerchi.map { dialogo.findViewById<MaterialButton>(it)!! }

    private fun attendiIlSalvataggio() {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(300)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Passo 7: otto colori gia' provati al posto della ruota, con la spunta su quello in uso. */
    @Test
    fun `il dialogo propone otto colori e segna quello della squadra`() {
        val dialogo = apriIlColoreDellaSquadra1(0xFFFF9F1C.toInt())

        val tutti = cerchi(dialogo)
        assertEquals(8, tutti.size)
        assertEquals(listOf(false, false, false, true, false, false, false, false), tutti.map { it.isSelected })
        tutti.forEachIndexed { i, cerchio ->
            assertNotNull("il cerchio $i non ha il nome per TalkBack", cerchio.contentDescription)
            assertEquals(i == 3, cerchio.icon != null)
        }
        // Il primario e' uno solo e dice cosa fa; la ruota sta dietro "Altro colore".
        assertEquals("Use this color", dialogo.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
        assertEquals("Other color…", dialogo.findViewById<MaterialButton>(R.id.team_color_other_button)!!.text.toString())
    }

    @Test
    fun `scegliere un colore e usarlo lo salva`() {
        val dialogo = apriIlColoreDellaSquadra1(null)

        cerchi(dialogo)[5].performClick()
        assertEquals(listOf(5), cerchi(dialogo).withIndex().filter { it.value.isSelected }.map { it.index })
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        attendiIlSalvataggio()

        assertEquals(RuntimeEnvironment.getApplication().getColor(R.color.user_6), preferenze.getInt("team1_color", 0))
    }

    /** Un colore preso dalla ruota non e' nella tavolozza: nessun cerchio segnato, e "Usa" lo conferma com'e'. */
    @Test
    fun `un colore della ruota resta com'e' e nessun cerchio e' segnato`() {
        val dialogo = apriIlColoreDellaSquadra1(bluNotte)

        assertEquals(emptyList<Boolean>(), cerchi(dialogo).map { it.isSelected }.filter { it })
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        attendiIlSalvataggio()
        assertEquals(bluNotte, preferenze.getInt("team1_color", 0))
    }

    @Test
    fun `i colori della tavolozza si staccano dal nero e dalle superfici`() {
        val app = RuntimeEnvironment.getApplication()
        val fondi =
            listOf(
                R.color.background_watch_standard,
                R.color.background_surface,
                R.color.background_elevated,
            ).map { app.getColor(it) }
        TavolozzaDiSquadra.colori.forEach { id ->
            fondi.forEach { fondo ->
                val contrasto =
                    androidx.core.graphics.ColorUtils
                        .calculateContrast(app.getColor(id), fondo)
                assertTrue("${app.resources.getResourceEntryName(id)}: $contrasto:1 sotto 3:1", contrasto >= 3.0)
            }
        }
    }

    @Test
    fun `aperta la ruota anteprima e colore salvato partono dal colore della squadra`() {
        val dialogo = apriIlColoreDellaSquadra1(bluNotte)
        dialogo.findViewById<MaterialButton>(R.id.team_color_other_button)!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val ruota = ShadowDialog.getLatestDialog() as AlertDialog
        val anteprima = ruota.findViewById<TextView>(R.id.colorPreview)
        assertNotNull(anteprima)
        assertEquals(bluNotte, (anteprima!!.background as MaterialShapeDrawable).fillColor?.defaultColor)

        // "Usa questo colore" senza toccare la ruota non cambia il colore.
        ruota.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        attendiIlSalvataggio()
        assertEquals(bluNotte, preferenze.getInt("team1_color", 0))
    }
}
