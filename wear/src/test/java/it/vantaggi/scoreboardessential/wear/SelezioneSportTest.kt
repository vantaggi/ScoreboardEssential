package it.vantaggi.scoreboardessential.wear

import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.wear.widget.WearableRecyclerView
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La selezione dello sport sul polso (passo 10): titolo a 14sp su una riga, voci a 20sp in righe da
 * almeno 52dp, la spunta e "in uso" in ciano, il focus alla lista per la corona.
 *
 * Le righe si leggono dall'adapter, non da una lista impaginata: in Robolectric la lista non ha
 * un'altezza vera, e le misure che contano sono quelle dichiarate nelle risorse.
 */
@RunWith(RobolectricTestRunner::class)
class SelezioneSportTest {
    private fun apri(): SportSelectionActivity {
        val intent =
            Intent(org.robolectric.RuntimeEnvironment.getApplication(), SportSelectionActivity::class.java).apply {
                putStringArrayListExtra(SportSelectionActivity.EXTRA_IDS, arrayListOf("football", "padel", "tennis"))
                putStringArrayListExtra(SportSelectionActivity.EXTRA_LABELS, arrayListOf("Calcio", "Padel", "Tennis"))
                putExtra(SportSelectionActivity.EXTRA_CURRENT, "Padel")
            }
        return Robolectric.buildActivity(SportSelectionActivity::class.java, intent).setup().get()
    }

    private fun sp(
        attivita: SportSelectionActivity,
        valore: Float,
    ): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, valore, attivita.resources.displayMetrics)

    private fun dp(
        attivita: SportSelectionActivity,
        valore: Float,
    ): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valore, attivita.resources.displayMetrics)

    /** La riga numero [posizione], come la disegna l'adapter della schermata. */
    private fun riga(
        attivita: SportSelectionActivity,
        posizione: Int,
    ): MaterialCardView {
        val lista = attivita.findViewById<WearableRecyclerView>(R.id.sport_list)
        val holder = lista.adapter!!.createViewHolder(FrameLayout(attivita), 0)
        lista.adapter!!.bindViewHolder(holder, posizione)
        return holder.itemView as MaterialCardView
    }

    @Test
    fun `il titolo e' a 14sp su una riga`() {
        val attivita = apri()
        val titolo = attivita.findViewById<TextView>(R.id.sport_title)

        assertEquals(sp(attivita, 14f), titolo.textSize, 0.01f)
        assertEquals(1, titolo.maxLines)
    }

    @Test
    fun `le voci sono a 20sp in righe da almeno 52dp`() {
        val attivita = apri()

        (0 until 3).forEach { posizione ->
            val card = riga(attivita, posizione)
            assertEquals(sp(attivita, 20f), card.findViewById<TextView>(R.id.sport_name).textSize, 0.01f)
            val contenuto = card.getChildAt(0) as LinearLayout
            assertTrue("riga ${contenuto.minimumHeight}px", contenuto.minimumHeight >= dp(attivita, 52f))
        }
        assertEquals("Calcio", riga(attivita, 0).findViewById<TextView>(R.id.sport_name).text.toString())
    }

    @Test
    fun `lo sport in uso porta la spunta in ciano e gli altri niente`() {
        val attivita = apri()

        val inUso = riga(attivita, 1).findViewById<TextView>(R.id.sport_current)
        assertEquals(View.VISIBLE, inUso.visibility)
        assertEquals(attivita.getString(R.string.wear_sport_current), inUso.text.toString())
        // La spunta e' un'icona (Material Symbols check, 16dp) e non un carattere nel testo.
        val spunta = inUso.compoundDrawablesRelative[0]
        assertTrue("manca la spunta come icona", spunta != null)
        assertEquals(dp(attivita, 16f), spunta.bounds.width())
        assertTrue("il testo non deve portare il carattere della spunta", !inUso.text.contains("✓"))
        assertEquals(ContextCompat.getColor(attivita, R.color.neon_cyan), inUso.currentTextColor)

        listOf(0, 2).forEach {
            assertEquals(View.GONE, riga(attivita, it).findViewById<TextView>(R.id.sport_current).visibility)
        }
    }

    @Test
    @Config(qualifiers = "it")
    fun `in italiano le parole sono in uso e la spunta e' l'icona`() {
        val attivita = apri()

        assertEquals("in uso", riga(attivita, 1).findViewById<TextView>(R.id.sport_current).text.toString())
    }

    @Test
    fun `la lista ha il focus, perche' la corona scorre cio' che ha il focus`() {
        val attivita = apri()
        val lista = attivita.findViewById<WearableRecyclerView>(R.id.sport_list)

        assertTrue("la lista non ha il focus", lista.hasFocus())
    }

    @Test
    fun `il sottotitolo del menu resta grigio e il ciano e' solo della selezione sport`() {
        val menu = Robolectric.buildActivity(MenuActivity::class.java).create().get()
        val sottotitoli = menu.findViewById<LinearLayout>(R.id.menu_voci)

        (0 until sottotitoli.childCount).forEach {
            val sottotitolo = sottotitoli.getChildAt(it).findViewById<TextView>(R.id.sport_current)
            assertTrue(
                "\"${sottotitolo.text}\" e' ciano nel menu",
                sottotitolo.currentTextColor != ContextCompat.getColor(menu, R.color.neon_cyan),
            )
        }
    }
}
