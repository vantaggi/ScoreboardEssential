package it.vantaggi.scoreboardessential

import android.content.ComponentName
import android.content.Context
import android.graphics.Matrix
import android.view.ContextThemeWrapper
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.Transformation
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.appcompat.R as AppCompat

/**
 * Le transizioni del telefono (04-transitions, PR 8), lette dalle risorse vere attraverso il tema:
 * pagine figlie con l'asse condiviso (N1, N2), la pagina di un compito che sale dal basso (N7), i
 * dialoghi da scale-popover al centro (M5) e i menu dall'angolo del loro bottone (M1). I fotogrammi
 * sono calcolati dall'animazione stessa a 0, 1/4, 1/2, 3/4 e alla fine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it-w400dp-h800dp-mdpi")
class TransizioniTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val tema = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)
    private val larghezza = 400
    private val altezza = 800

    private fun risorsa(
        stile: Int,
        attributo: Int,
    ): Int {
        val a = app.obtainStyledAttributes(stile, intArrayOf(attributo))
        try {
            return a.getResourceId(0, 0)
        } finally {
            a.recycle()
        }
    }

    private fun stileDelTema(
        contesto: Context,
        attributo: Int,
    ): Int {
        val a = contesto.obtainStyledAttributes(intArrayOf(attributo))
        try {
            return a.getResourceId(0, 0)
        } finally {
            a.recycle()
        }
    }

    private data class Fotogramma(
        val x: Float,
        val y: Float,
        val scala: Float,
        val opacita: Float,
    )

    /** Il fotogramma di [anim] alla frazione [t] del suo tempo, su una finestra di 400x800. */
    private fun fotogramma(
        anim: Int,
        t: Float,
    ): Fotogramma {
        val animazione: Animation = AnimationUtils.loadAnimation(tema, anim)
        animazione.initialize(larghezza, altezza, larghezza, altezza)
        animazione.startTime = 0
        val trasformazione = Transformation()
        animazione.getTransformation((animazione.computeDurationHint() * t).toLong(), trasformazione)
        val valori = FloatArray(9).also { trasformazione.matrix.getValues(it) }
        return Fotogramma(valori[Matrix.MTRANS_X], valori[Matrix.MTRANS_Y], valori[Matrix.MSCALE_X], trasformazione.alpha)
    }

    private fun assertVicino(
        atteso: Fotogramma,
        reale: Fotogramma,
    ) {
        val vicino =
            listOf(atteso.x to reale.x, atteso.y to reale.y, atteso.scala to reale.scala, atteso.opacita to reale.opacita)
                .all { (a, b) -> kotlin.math.abs(a - b) < 0.01f }
        assertEquals("$atteso contro $reale", true, vicino)
    }

    private fun durata(anim: Int): Long =
        AnimationUtils
            .loadAnimation(tema, anim)
            .apply { initialize(larghezza, altezza, larghezza, altezza) }
            .computeDurationHint()

    private val corsa = 32f

    /** Rosso se le pagine non scorrono di shift-axis nel verso giusto, o se le durate non sono quelle dei token. */
    @Test
    fun `una pagina figlia entra dal lato finale e indietro torna al contrario`() {
        val stile = stileDelTema(tema, android.R.attr.windowAnimationStyle)
        assertEquals(R.style.Animazioni_Pagina, stile)
        assertEquals(R.anim.pagina_entra, risorsa(stile, android.R.attr.activityOpenEnterAnimation))
        assertEquals(R.anim.pagina_esce, risorsa(stile, android.R.attr.activityOpenExitAnimation))
        assertEquals(R.anim.pagina_torna, risorsa(stile, android.R.attr.activityCloseEnterAnimation))
        assertEquals(R.anim.pagina_chiude, risorsa(stile, android.R.attr.activityCloseExitAnimation))

        val standard = app.resources.getInteger(R.integer.duration_standard).toLong()
        val veloce = app.resources.getInteger(R.integer.duration_fast).toLong()
        assertEquals(standard, durata(R.anim.pagina_entra))
        assertEquals(veloce, durata(R.anim.pagina_esce))
        assertEquals(standard, durata(R.anim.pagina_torna))
        assertEquals(veloce, durata(R.anim.pagina_chiude))

        // Avanti: la nuova da +32 a 0 mentre compare, la vecchia da 0 a -32 mentre sparisce.
        assertVicino(Fotogramma(corsa, 0f, 1f, 0f), fotogramma(R.anim.pagina_entra, 0f))
        assertVicino(Fotogramma(0f, 0f, 1f, 1f), fotogramma(R.anim.pagina_entra, 1f))
        assertVicino(Fotogramma(-corsa, 0f, 1f, 0f), fotogramma(R.anim.pagina_esce, 1f))
        // Indietro: lo stesso al contrario.
        assertVicino(Fotogramma(-corsa, 0f, 1f, 0f), fotogramma(R.anim.pagina_torna, 0f))
        assertVicino(Fotogramma(corsa, 0f, 1f, 0f), fotogramma(R.anim.pagina_chiude, 1f))
    }

    /** Rosso se a meta' strada una pagina salta, torna indietro o esce dalla sua corsa. */
    @Test
    fun `i fotogrammi intermedi vanno sempre nello stesso verso`() {
        listOf(R.anim.pagina_entra, R.anim.pagina_esce, R.anim.pagina_torna, R.anim.pagina_chiude).forEach { anim ->
            val fotogrammi = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { fotogramma(anim, it) }
            val xs = fotogrammi.map { it.x }
            val monotona = xs.zipWithNext().all { (a, b) -> a <= b } || xs.zipWithNext().all { (a, b) -> a >= b }
            assertEquals("verso unico in ${app.resources.getResourceEntryName(anim)}: $xs", true, monotona)
            fotogrammi.forEach { f ->
                assertEquals("dentro la corsa: $f", true, f.x in -corsa - 0.01f..corsa + 0.01f)
                assertEquals("opacita' fra 0 e 1", true, f.opacita in 0f..1f)
            }
        }
    }

    /** Rosso se Nuovo giocatore non sale dal basso, se la pagina dietro si muove o se in alto non c'e' Chiudi. */
    @Test
    fun `la pagina di un compito sale dal basso e la pagina dietro resta ferma`() {
        val info = app.packageManager.getActivityInfo(ComponentName(app, AddEditPlayerActivity::class.java), 0)
        assertEquals(R.style.Theme_ScoreboardEssential_Compito, info.theme)
        val stile = stileDelTema(ContextThemeWrapper(app, info.theme), android.R.attr.windowAnimationStyle)
        assertEquals(R.style.Animazioni_Compito, stile)
        assertEquals(R.anim.compito_sale, risorsa(stile, android.R.attr.activityOpenEnterAnimation))
        assertEquals(R.anim.compito_scende, risorsa(stile, android.R.attr.activityCloseExitAnimation))

        assertEquals(Fotogramma(0f, altezza.toFloat(), 1f, 1f), fotogramma(R.anim.compito_sale, 0f))
        assertVicino(Fotogramma(0f, 0f, 1f, 1f), fotogramma(R.anim.compito_sale, 1f))
        assertEquals(Fotogramma(0f, altezza.toFloat(), 1f, 1f), fotogramma(R.anim.compito_scende, 1f))
        listOf(0f, 0.5f, 1f).forEach { t ->
            assertVicino(Fotogramma(0f, 0f, 1f, 1f), fotogramma(R.anim.compito_sotto, t))
        }
        // Le altre pagine restano pagine figlie.
        val impostazioni = app.packageManager.getActivityInfo(ComponentName(app, PlayersManagementActivity::class.java), 0)
        val temaImpostazioni = if (impostazioni.theme != 0) impostazioni.theme else app.applicationInfo.theme
        assertEquals(
            R.style.Animazioni_Pagina,
            stileDelTema(ContextThemeWrapper(app, temaImpostazioni), android.R.attr.windowAnimationStyle),
        )
    }

    /** Rosso se un dialogo non nasce da scale-popover al centro o non usa le durate dei token. */
    @Test
    fun `un dialogo nasce al centro da scale-popover`() {
        val dialogo = MaterialAlertDialogBuilder(tema).setTitle("x").create()
        dialogo.create()
        assertEquals(R.style.Animazioni_Sovrapposto, dialogo.window!!.attributes.windowAnimations)
        val entra = risorsa(R.style.Animazioni_Sovrapposto, android.R.attr.windowEnterAnimation)
        assertEquals(R.anim.sovrapposto_entra, entra)
        val iniziale = fotogramma(entra, 0f)
        assertEquals(0.96f, iniziale.scala, 0.001f)
        assertEquals(0f, iniziale.opacita)
        // Al centro: il punto in mezzo alla finestra non si sposta.
        assertEquals(larghezza / 2f * (1 - 0.96f), iniziale.x, 0.01f)
        assertEquals(app.resources.getInteger(R.integer.duration_fast).toLong(), durata(entra))
        assertEquals(app.resources.getInteger(R.integer.duration_instant).toLong(), durata(R.anim.sovrapposto_esce))
    }

    /** Rosso se il menu dei bottoni in barra non nasce dal suo angolo in alto a destra, o se Material lo sostituisce. */
    @Test
    fun `il menu nasce dall'angolo del suo bottone`() {
        listOf(AppCompat.attr.popupMenuStyle, AppCompat.attr.actionOverflowMenuStyle).forEach { attributo ->
            val stile = stileDelTema(tema, attributo)
            assertEquals(R.style.Animazioni_Menu, risorsa(stile, android.R.attr.popupAnimationStyle))
            val a = app.obtainStyledAttributes(stile, intArrayOf(android.R.attr.popupEnterTransition))
            val transizione = a.peekValue(0)
            a.recycle()
            assertNull("nessuna transizione di Material al posto dell'animazione", transizione?.takeIf { it.resourceId != 0 })
        }
        val entra = risorsa(R.style.Animazioni_Menu, android.R.attr.windowEnterAnimation)
        val iniziale = fotogramma(entra, 0f)
        assertEquals(0.96f, iniziale.scala, 0.001f)
        // L'angolo in alto a destra resta fermo: x si sposta di tutta la riduzione, y di niente.
        assertEquals(larghezza * (1 - 0.96f), iniziale.x, 0.01f)
        assertEquals(0f, iniziale.y, 0.01f)
    }
}
