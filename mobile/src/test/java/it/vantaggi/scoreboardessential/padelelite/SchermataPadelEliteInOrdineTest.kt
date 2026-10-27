package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La schermata Padel Elite dopo la revisione dei menu (passo 4): Aggiorna nella barra, i giocatori del
 * gruppo in due parti (collegati e da collegare con la ricerca), Esci in fondo con l'account sotto.
 * Rosso se Aggiorna non e' piu' nel menu della barra, se l'account torna in cima o se sparisce la ricerca.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class SchermataPadelEliteInOrdineTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val contesto get() = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)

    private fun gonfiaLaSchermata(): View = LayoutInflater.from(contesto).inflate(R.layout.activity_padel_elite, null)

    /** La posizione di [id] fra i figli diretti del pannello dell'account, o di chi lo contiene li'. */
    private fun posizioneNelPannello(
        pannello: ViewGroup,
        id: Int,
    ): Int {
        var vista: View = pannello.findViewById(id)
        while (vista.parent !== pannello) vista = vista.parent as View
        return pannello.indexOfChild(vista)
    }

    @Test
    fun `Esci e l'account stanno in fondo, sotto i giocatori del gruppo`() {
        val pannello = gonfiaLaSchermata().findViewById<ViewGroup>(R.id.account_panel)
        val rosa = posizioneNelPannello(pannello, R.id.rosa_section)
        val esci = posizioneNelPannello(pannello, R.id.logout_button)
        val account = posizioneNelPannello(pannello, R.id.account_email)

        assertTrue("Esci sta sopra la rosa", esci > rosa)
        assertEquals("l'account non sta subito sotto Esci", esci + 1, account)
        assertEquals("l'account non e' l'ultima riga", pannello.childCount - 1, account)
    }

    @Test
    fun `i da collegare hanno la ricerca, i collegati la loro lista`() {
        val schermata = gonfiaLaSchermata()
        val sezione = schermata.findViewById<ViewGroup>(R.id.rosa_to_link_section)

        assertNotNull(sezione.findViewById<View>(R.id.rosa_search_edit))
        assertNotNull(sezione.findViewById<View>(R.id.rosa_to_link_list))
        assertNotNull(schermata.findViewById<View>(R.id.rosa_list))
    }

    @Test
    fun `Aggiorna sta nella barra con l'icona e la parola`() {
        val menu = PopupMenu(contesto, View(contesto)).apply { inflate(R.menu.menu_padel_elite) }.menu
        val aggiorna = menu.findItem(R.id.action_refresh)

        assertNotNull(aggiorna.icon)
        assertEquals("Aggiorna", aggiorna.title.toString())
    }
}
