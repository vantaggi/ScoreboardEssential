package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Quello che TalkBack legge e quello che il dito riesce a toccare, sul telefono.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "it")
class AccessibilitaDelTelefonoTest {
    private val contesto: Context
        get() = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_ScoreboardEssential)

    private fun gonfia(layout: Int): View = LayoutInflater.from(contesto).inflate(layout, null)

    // Il contenitore del nome aveva la descrizione fissa "Modifica nome Squadra 1": TalkBack
    // leggeva quella e il nome ROSSI non si sentiva mai.
    @Test
    fun `sulla card TalkBack legge il nome della squadra`() {
        val radice = gonfia(R.layout.content_scoreboard_live)
        val coppie =
            listOf(
                R.id.team1_name_container to R.id.team1_name_textview,
                R.id.team2_name_container to R.id.team2_name_textview,
            )
        for ((contenitoreId, testoId) in coppie) {
            val contenitore = radice.findViewById<View>(contenitoreId)
            val testo = radice.findViewById<TextView>(testoId)
            mostraNomeSquadra(contenitore, testo, "Rossi")
            assertEquals("ROSSI", testo.text.toString())
            assertEquals("Modifica il nome di Rossi", contenitore.contentDescription?.toString())
        }
    }

    // Pulsanti da 40dp, sotto i 48 della linea guida, con descrizioni cablate in inglese.
    @Test
    fun `nella gestione giocatori i comandi sono da 48dp e parlano italiano`() {
        val riga = gonfia(R.layout.item_player_management)
        val minimo = (48 * riga.resources.displayMetrics.density).toInt()
        val attese =
            mapOf(
                R.id.stats_button to "Statistiche del giocatore",
                R.id.edit_button to "Modifica giocatore",
            )
        for ((id, descrizione) in attese) {
            val pulsante = riga.findViewById<View>(id)
            val parametri = pulsante.layoutParams
            assertTrue("larghezza ${parametri.width}px < ${minimo}px", parametri.width >= minimo)
            assertTrue("altezza ${parametri.height}px < ${minimo}px", parametri.height >= minimo)
            assertEquals(descrizione, pulsante.contentDescription?.toString())
        }
    }

    // Il FAB per aggiungere un giocatore non aveva etichetta: TalkBack diceva solo "pulsante".
    @Test
    fun `il FAB dei giocatori ha la sua etichetta`() {
        val schermo = gonfia(R.layout.activity_players_management)
        val fab = schermo.findViewById<View>(R.id.add_player_fab)
        assertEquals("Aggiungi giocatore", fab.contentDescription?.toString())
    }
}
