package it.vantaggi.scoreboardessential

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.google.android.material.textfield.TextInputEditText
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerRoleCrossRef
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.database.Role
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * I Giocatori seguono lo sport in uso (passo 6b della revisione dei menu). Nel padel la riga e' il
 * nome e le partite, senza gol ne' ruoli, e non ci sono i filtri per ruolo ne' l'ordine "Per gol";
 * nel calcio ci sono tutti. La ricerca e' un campo nella pagina e ignora maiuscole e accenti.
 * Rosso se nel padel tornano gol, ruoli o "Per gol", se il calcio li perde, se l'ordine in uso non
 * ha la spunta, o se la ricerca distingue «Nicolò» da «nicolo».
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it")
class GiocatoriPerSportTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        istanza(db)
        runBlocking {
            db.playerDao().insertRole(Role(roleId = 1, name = "Portiere", category = "PORTA"))
            db.playerDao().insert(Player(playerId = 1, playerName = "Nicolò", appearances = 1, goals = 2))
            db.playerDao().insert(Player(playerId = 2, playerName = "Bruno", appearances = 3, goals = 0))
            db.playerDao().addRoleToPlayer(PlayerRoleCrossRef(playerId = 1, roleId = 1))
        }
    }

    @After
    fun tearDown() {
        db.close()
        istanza(null)
    }

    private fun istanza(valore: AppDatabase?) {
        val campo = AppDatabase::class.java.getDeclaredField("instance")
        campo.isAccessible = true
        campo.set(null, valore)
    }

    private fun sport(id: String) {
        RuntimeEnvironment
            .getApplication()
            .getSharedPreferences("match_settings_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("active_sport", id)
            .commit()
    }

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

    private fun valori(lista: RecyclerView): List<String> {
        lista.measure(0, 0)
        lista.layout(0, 0, 1080, 2000)
        return (0 until lista.childCount).map { i ->
            val riga = lista.getChildAt(i)
            "${riga.findViewById<TextView>(R.id.player_name).text}: ${riga.findViewById<TextView>(R.id.player_value).text}"
        }
    }

    /** Il menu della barra, preparato dalla schermata come lo vedrebbe chi lo apre. */
    private fun menuDi(giocatori: PlayersManagementActivity): android.view.Menu {
        val menu = PopupMenu(giocatori, giocatori.findViewById(R.id.toolbar)).menu
        giocatori.menuInflater.inflate(R.menu.menu_players_management, menu)
        giocatori.onPrepareOptionsMenu(menu)
        return menu
    }

    @Test
    fun `nel padel la riga e' il nome e le partite, senza ruoli ne' Per gol`() {
        sport(SportRegistry.PADEL)
        val giocatori = Robolectric.buildActivity(PlayersManagementActivity::class.java).setup().get()
        val lista = giocatori.findViewById<RecyclerView>(R.id.players_recyclerview)
        attendi("le righe del padel non sono arrivate") { valori(lista) == listOf("Bruno: 3 partite", "Nicolò: 1 partita") }

        assertEquals(View.GONE, giocatori.findViewById<View>(R.id.roles_filter_scrollview).visibility)
        val menu = menuDi(giocatori)
        assertFalse("Per gol nel padel", menu.findItem(R.id.action_sort_goals).isVisible)
        assertTrue("l'ordine in uso non ha la spunta", menu.findItem(R.id.action_sort_name).isChecked)
    }

    @Test
    fun `nel calcio ci sono i gol, i ruoli e Per gol`() {
        sport(SportRegistry.FOOTBALL)
        val giocatori = Robolectric.buildActivity(PlayersManagementActivity::class.java).setup().get()
        val lista = giocatori.findViewById<RecyclerView>(R.id.players_recyclerview)
        attendi("le righe del calcio non sono arrivate") {
            valori(lista) == listOf("Bruno: 3 partite · 0 gol", "Nicolò: 1 partita · 2 gol")
        }

        attendi("i filtri per ruolo non sono comparsi") {
            giocatori.findViewById<View>(R.id.roles_filter_scrollview).visibility == View.VISIBLE
        }
        assertTrue(menuDi(giocatori).findItem(R.id.action_sort_goals).isVisible)
    }

    @Test
    fun `la ricerca e' un campo nella pagina e ignora gli accenti`() {
        sport(SportRegistry.PADEL)
        val giocatori = Robolectric.buildActivity(PlayersManagementActivity::class.java).setup().get()
        val lista = giocatori.findViewById<RecyclerView>(R.id.players_recyclerview)
        attendi("le righe non sono arrivate") { valori(lista).size == 2 }

        giocatori.findViewById<TextInputEditText>(R.id.players_search_edit).setText("NICOLO")

        attendi("la ricerca non ha filtrato") { valori(lista) == listOf("Nicolò: 1 partita") }
        // La lente nella barra che apriva un campo invisibile non c'e' piu'.
        assertEquals(0, giocatori.resources.getIdentifier("action_search", "id", giocatori.packageName))
    }

    private fun conRuolo(
        nome: String,
        ruolo: String,
    ) = PlayerWithRoles(Player(playerName = nome, appearances = 0, goals = 0), listOf(Role(1, ruolo, "PORTA")))

    @Test
    fun `la ricerca guarda i ruoli solo dove i ruoli contano`() {
        val elenco = listOf(conRuolo("Anna", "Portiere"), conRuolo("Bruno", "Ala"))

        assertEquals(listOf("Anna"), PlayersManagementViewModel.cerca(elenco, "porti", conRuoli = true).map { it.player.playerName })
        assertEquals(emptyList<String>(), PlayersManagementViewModel.cerca(elenco, "porti", conRuoli = false).map { it.player.playerName })
        assertEquals(elenco, PlayersManagementViewModel.cerca(elenco, "  ", conRuoli = false))
    }
}
