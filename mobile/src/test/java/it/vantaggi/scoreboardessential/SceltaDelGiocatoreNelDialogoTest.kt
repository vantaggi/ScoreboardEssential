package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.padelelite.ElencoDellaScelta
import it.vantaggi.scoreboardessential.padelelite.RemotePlayer
import it.vantaggi.scoreboardessential.padelelite.SezioneDellaScelta
import it.vantaggi.scoreboardessential.padelelite.VoceDellaScelta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Aggiungi un giocatore" con la rosa di Padel Elite (passo 7b della revisione dei menu): la frase
 * "chi non e' nell'app ci entra quando lo scegli" sta una volta sola, sotto il titolo della prima
 * sezione della dashboard, e non su ogni riga come prima (sei volte uguali). Rosso se torna su una
 * riga, se compare piu' di una volta o se la sezione dei soli giocatori dell'app la porta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it")
class SceltaDelGiocatoreNelDialogoTest {
    private val tema: Context get() = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_ScoreboardEssential)

    private val avviso = "Chi non è ancora nell'app ci entra quando lo scegli"

    private fun elenco(vararg voci: VoceDellaScelta) = ElencoDellaScelta("g1", "Cioti", voci.toList())

    private fun nuovo(nome: String) = VoceDellaScelta.DallaDashboard(RemotePlayer(nome.length, nome), localId = null, nomeLocale = null)

    @Test
    fun `l'avviso sta una volta sola, sotto il titolo della prima sezione della dashboard`() {
        val righe =
            righeDellaScelta(
                tema,
                elenco(
                    VoceDellaScelta.Intestazione(SezioneDellaScelta.ISCRITTI),
                    nuovo("Anna"),
                    nuovo("Bruno"),
                    VoceDellaScelta.Intestazione(SezioneDellaScelta.DASHBOARD),
                    nuovo("Carla"),
                    VoceDellaScelta.Intestazione(SezioneDellaScelta.SOLO_APP),
                    VoceDellaScelta.SoloNellApp(1, "Dario"),
                ),
            )

        assertEquals(listOf(avviso), righe.mapNotNull { it.didascalia })
        assertEquals(avviso, righe.first().didascalia)
        assertEquals(true, righe.first().intestazione)
        assertEquals("Iscritti alla prossima serata", righe.first().titolo)
    }

    @Test
    fun `chi e' gia' nell'app lo dice sulla sua riga`() {
        val righe =
            righeDellaScelta(
                tema,
                elenco(
                    VoceDellaScelta.Intestazione(SezioneDellaScelta.DASHBOARD),
                    VoceDellaScelta.DallaDashboard(RemotePlayer(1, "Anna"), localId = 7, nomeLocale = "Anna"),
                    VoceDellaScelta.DallaDashboard(RemotePlayer(2, "Bru"), localId = 8, nomeLocale = "Bruno"),
                ),
            )

        assertEquals(listOf(avviso, "Già nell'app", "Nell'app come Bruno"), righe.map { it.didascalia })
    }

    @Test
    fun `senza giocatori della dashboard nessun avviso`() {
        val righe =
            righeDellaScelta(
                tema,
                elenco(
                    VoceDellaScelta.Intestazione(SezioneDellaScelta.DASHBOARD_NON_LETTA),
                    VoceDellaScelta.SoloNellApp(1, "Dario"),
                ),
            )

        assertEquals(emptyList<String>(), righe.mapNotNull { it.didascalia })
    }

    @Test
    fun `l'intestazione mostra la didascalia sotto il titolo e quella senza non ha spazio`() {
        val adapter =
            RigheDelDialogo(
                tema,
                listOf(RigaDelDialogo("Padel Elite · Cioti", avviso, intestazione = true), RigaDelDialogo("Sezione", intestazione = true)),
            )
        val genitore = FrameLayout(tema)

        val conAvviso = adapter.getView(0, null, genitore)
        assertEquals(avviso, conAvviso.findViewById<TextView>(R.id.header_caption).text.toString())
        assertEquals(View.VISIBLE, conAvviso.findViewById<View>(R.id.header_caption).visibility)

        val senza = adapter.getView(1, null, genitore)
        assertEquals(View.GONE, senza.findViewById<View>(R.id.header_caption).visibility)
        assertNull(RigaDelDialogo("Sezione", intestazione = true).didascalia)
    }
}
