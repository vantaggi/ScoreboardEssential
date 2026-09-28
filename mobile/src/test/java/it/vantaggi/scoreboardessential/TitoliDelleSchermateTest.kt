package it.vantaggi.scoreboardessential

import android.content.ComponentName
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.ui.MatchSettingsActivity
import it.vantaggi.scoreboardessential.ui.statistics.StatisticsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Le schermate secondarie avevano titoli inglesi cablati nel manifest (anche con l'app in
 * italiano) e la statistica testi italiani cablati (anche con l'app in inglese).
 */
@RunWith(AndroidJUnit4::class)
class TitoliDelleSchermateTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun titolo(schermata: Class<*>): String {
        val pm = context.packageManager
        return pm.getActivityInfo(ComponentName(context, schermata), 0).loadLabel(pm).toString()
    }

    private fun testiDi(vista: View): List<String> =
        when (vista) {
            is TextView -> listOf(vista.text.toString())
            is ViewGroup -> (0 until vista.childCount).flatMap { testiDi(vista.getChildAt(it)) }
            else -> emptyList()
        }

    @Test
    @Config(qualifiers = "it")
    fun `in italiano i titoli delle schermate secondarie sono italiani`() {
        assertEquals("Storico Partite", titolo(MatchHistoryActivity::class.java))
        assertEquals("Giocatore", titolo(AddEditPlayerActivity::class.java))
        assertEquals("Impostazioni Partita", titolo(MatchSettingsActivity::class.java))
        assertEquals("Gestisci Giocatori", titolo(PlayersManagementActivity::class.java))
        assertEquals("Statistiche", titolo(StatisticsActivity::class.java))
    }

    @Test
    @Config(qualifiers = "en")
    fun `in inglese i titoli delle schermate secondarie sono inglesi`() {
        assertEquals("Match History", titolo(MatchHistoryActivity::class.java))
        assertEquals("Player", titolo(AddEditPlayerActivity::class.java))
        assertEquals("Match Settings", titolo(MatchSettingsActivity::class.java))
        assertEquals("Manage Players", titolo(PlayersManagementActivity::class.java))
        assertEquals("Statistics", titolo(StatisticsActivity::class.java))
    }

    @Test
    @Config(qualifiers = "en")
    fun `in inglese la statistica vuota non parla italiano`() {
        val tema = ContextThemeWrapper(context, R.style.Theme_ScoreboardEssential)
        val testi = testiDi(LayoutInflater.from(tema).inflate(R.layout.activity_statistics, null))
        assertTrue(testi.toString(), "No matches played yet" in testi)
        assertTrue(testi.toString(), testi.none { it.contains("Nessuna") || it.contains("Gioca") })
    }

    @Test
    @Config(qualifiers = "en")
    fun `in inglese gol e presenze della statistica sono inglesi`() {
        assertEquals("3 goals", context.resources.getQuantityString(R.plurals.stats_goals, 3, 3))
        assertEquals("1 appearance", context.resources.getQuantityString(R.plurals.stats_appearances, 1, 1))
    }

    @Test
    @Config(qualifiers = "it")
    fun `in italiano i dialoghi dei giocatori sono italiani`() {
        assertEquals("Eliminare il giocatore?", context.getString(R.string.delete_player_title))
        assertEquals("Annulla", context.getString(R.string.cancel))
    }
}
