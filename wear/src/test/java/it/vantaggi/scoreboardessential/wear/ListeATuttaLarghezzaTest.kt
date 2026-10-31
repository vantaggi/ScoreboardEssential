package it.vantaggi.scoreboardessential.wear

import android.app.Activity
import android.graphics.Matrix
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il menu del polso a tutta larghezza (revisione dei menu, 27 ottobre 2026): le voci vanno da un
 * margine del 5% all'altro, non piu' nel quadrato inscritto del BoxInsetLayout; sul tondo quelle
 * vicino al bordo alto e basso si stringono ([ListaTonda]) e quelle al centro sono intere; sul
 * quadrato restano tutte intere. Il titolo sta dentro la colonna e scorre via con lei.
 *
 * La scelta del marcatore fa lo stesso con [ScalaTonda] (UI Constitution, PR 4): prima le sue righe
 * si piegavano ad arco, il comportamento di serie del WearableLinearLayoutManager.
 *
 * Rosso se il menu torna nel quadrato inscritto (righe strette), se [ListaTonda] smette di
 * stringere le righe sul bordo, o se la lista del marcatore torna all'arco di serie.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ListeATuttaLarghezzaTest {
    private val base =
        InputMenu(
            inCoda = 0,
            collegato = true,
            partitaIniziata = true,
            haElencoSport = true,
            sport = "Padel",
            risultato = "6–4",
            regola40 = "Killer point",
        )

    private fun apri(): MenuActivity {
        val intent = MenuActivity.intent(RuntimeEnvironment.getApplication(), base)
        return Robolectric.buildActivity(MenuActivity::class.java, intent).setup().get()
    }

    private fun righe(menu: MenuActivity): List<View> =
        listOf(R.id.menu_voci, R.id.menu_impostazioni).flatMap { id ->
            val gruppo = menu.findViewById<LinearLayout>(id)
            (0 until gruppo.childCount).map(gruppo::getChildAt)
        }

    private fun scala(riga: View): Float {
        val matrice = riga.animationMatrix ?: return 1f
        val valori = FloatArray(9).also(matrice::getValues)
        return valori[Matrix.MSCALE_X]
    }

    private fun dp(
        schermata: Activity,
        px: Int,
    ): Float = px / schermata.resources.displayMetrics.density

    @Test
    @Config(qualifiers = "it-w192dp-h192dp-round-notnight-xhdpi")
    fun `sul tondo da 192dp le voci vanno a tutta larghezza e si stringono solo vicino al bordo`() {
        val menu = apri()
        shadowOf(menu.mainLooper).idle()
        val scroll = menu.findViewById<ScrollView>(R.id.menu_scroll)
        val voci = righe(menu)

        // Larghe quanto lo schermo meno il 5% per lato: nel quadrato inscritto erano circa 130dp.
        voci.forEach { assertEquals(172f, dp(menu, it.width), 1f) }
        // Il titolo e' dentro la colonna che scorre, non fermo sopra.
        assertNotNull(scroll.findViewById<View>(R.id.menu_titolo))

        // L'ultima impostazione sta sotto lo schermo: alla scala minima. Nessuna e' piu' stretta.
        val ultima = voci.last()
        assertNotNull("l'ultima riga, fuori dallo schermo, non si e' stretta", ultima.animationMatrix)
        assertEquals(BordoTondo.SCALA_MINIMA, scala(ultima), 0.001f)
        voci.forEach { assertTrue(scala(it) >= BordoTondo.SCALA_MINIMA - 0.001f) }

        // Scorrendo fino in fondo l'ultima arriva verso il centro e torna intera.
        scroll.scrollTo(0, scroll.getChildAt(0).height - scroll.height)
        shadowOf(menu.mainLooper).idle()
        assertNull("in fondo alla lista l'ultima riga deve essere intera", ultima.animationMatrix)
    }

    @Test
    @Config(qualifiers = "it-w180dp-h180dp-notround-notnight-xhdpi")
    fun `sul quadrato le voci restano intere`() {
        val menu = apri()
        shadowOf(menu.mainLooper).idle()

        righe(menu).forEach { assertNull(it.animationMatrix) }
    }

    private fun posizione(riga: View): Float = IntArray(2).also { riga.getLocationInWindow(it) }[1].toFloat()

    @After
    fun ripristina() {
        PlayerSelectionActivity.creaSync = { OptimizedWearDataSync(it) }
    }

    private fun apriMarcatore(): PlayerSelectionActivity {
        val giocatori = (1..12).map { PlayerData(id = it, name = "Giocatore $it", roles = emptyList()) }
        // I client GMS veri muoiono sul looper di Robolectric: finti, e nessuno li usa qui.
        PlayerSelectionActivity.creaSync = { contesto ->
            OptimizedWearDataSync(
                contesto,
                Mockito.mock(DataClient::class.java),
                Mockito.mock(MessageClient::class.java),
                Mockito.mock(CapabilityClient::class.java),
                Mockito.mock(NodeClient::class.java),
            )
        }
        val intent = PlayerSelectionActivity.intent(RuntimeEnvironment.getApplication(), 1, giocatori, 0xFFFFD600.toInt(), "3–2")
        return Robolectric.buildActivity(PlayerSelectionActivity::class.java, intent).setup().get()
    }

    @Test
    @Config(qualifiers = "it-w192dp-h192dp-round-notnight-xhdpi")
    fun `sul tondo le righe del marcatore si stringono vicino al bordo come quelle del menu`() {
        val marcatore = apriMarcatore()
        shadowOf(marcatore.mainLooper).idle()
        val lista = marcatore.findViewById<RecyclerView>(R.id.player_list)
        val righe = (0 until lista.childCount).map(lista::getChildAt)

        righe.forEach { assertEquals(172f, dp(marcatore, it.width), 1f) }
        val diametro =
            marcatore.window.decorView.width
                .toFloat()
        // Ogni riga ha la scala di BordoTondo per dove sta: stretta sul bordo, intera al centro.
        righe.forEach { riga ->
            val cima = posizione(riga)
            val attesa = BordoTondo.scala(cima, cima + riga.height, riga.width.toFloat(), diametro)
            assertEquals("riga a ${dp(marcatore, cima.toInt())}dp dalla cima", attesa, scala(riga), 0.001f)
        }
        assertTrue("nessuna riga stretta vicino al bordo", righe.any { scala(it) <= BordoTondo.SCALA_MINIMA + 0.001f })
        // La riga che passa per il centro e' la piu' larga: e' quella che la corona mette a fuoco.
        val alCentro = righe.first { posizione(it) <= diametro / 2 && posizione(it) + it.height >= diametro / 2 }
        assertEquals(righe.maxOf { scala(it) }, scala(alCentro), 0.001f)
    }

    @Test
    @Config(qualifiers = "it-w180dp-h180dp-notround-notnight-xhdpi")
    fun `sul quadrato le righe del marcatore restano intere`() {
        val marcatore = apriMarcatore()
        shadowOf(marcatore.mainLooper).idle()
        val lista = marcatore.findViewById<RecyclerView>(R.id.player_list)

        (0 until lista.childCount).forEach { assertNull(lista.getChildAt(it).animationMatrix) }
    }
}
