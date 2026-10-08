package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La sezione "Giocatori del gruppo", dallo stato: gli stati (nessun gruppo, vuota, errore di rete,
 * accesso scaduto, pronta), le righe con "Non collegato" e la proposta, e i comandi dell'utente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RosaGruppoViewModelTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private var finto: ServerFinto? = null
    private lateinit var account: PadelEliteAccount

    @Before
    fun avvia() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun chiudi() {
        Dispatchers.resetMain()
        db.close()
        finto?.chiudi()
    }

    private fun rosaJson(vararg giocatori: Pair<Int, String>) =
        giocatori.joinToString(prefix = "[", postfix = "]") { (id, nome) -> """{"id":$id,"name":"$nome"}""" }

    /** Un account gia' collegato, con il gruppo "g-1" scelto (o nessuno), e la rosa data dal server finto. */
    private fun viewModel(
        gruppoScelto: Boolean = true,
        onSessionLost: () -> Unit = {},
        rosa: (RecordedRequest, Int) -> MockResponse,
    ): RosaGruppoViewModel {
        val s = ServerFinto(rosa).also { finto = it }
        val store = SessionStore(preferenze(context, "sessione_rosa"), SoftwareSecretBox())
        store.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), store)
        if (gruppoScelto) account.selectGroup(PadelEliteGroup("g-1", "Padel", "member"))
        return RosaGruppoViewModel(account, RosaGruppo(db.padelEliteLinkDao(), db.playerDao()), onSessionLost)
    }

    private fun giocatore(nome: String): Int =
        runBlocking {
            db.playerDao().insert(Player(playerName = nome, appearances = 0, goals = 0)).toInt()
        }

    /** La rete e il database girano su altri thread: si aspetta il primo stato che non e' "in caricamento". */
    private fun RosaGruppoViewModel.attendi(): RosaUi = runBlocking { withTimeout(5_000) { state.first { it !is RosaUi.Loading } } }

    private fun RosaGruppoViewModel.pronta(): RosaUi.Ready = attendi() as RosaUi.Ready

    /** Esegue un comando dell'utente e aspetta lo stato nuovo (le righe cambiano, quindi lo stato e' un altro). */
    private fun RosaGruppoViewModel.comanda(azione: () -> Unit): RosaUi.Ready {
        val prima = state.value
        azione()
        return runBlocking { withTimeout(5_000) { state.first { it != prima } } } as RosaUi.Ready
    }

    @Test
    fun `senza un gruppo scelto non c'e' una rosa da mostrare e non si chiama la rete`() {
        val vm = viewModel(gruppoScelto = false) { _, _ -> json(200, "[]") }

        vm.load()

        assertEquals(RosaUi.NoGroup, vm.state.value)
        assertEquals(0, finto!!.richieste.size)
    }

    @Test
    fun `una rosa vuota e' uno stato a se`() {
        val vm = viewModel { _, _ -> json(200, "[]") }

        vm.load()

        assertEquals(RosaUi.Empty, vm.attendi())
    }

    @Test
    fun `senza rete si puo' riprovare e la lettura dopo riesce`() {
        var rete = false
        val vm = viewModel { _, _ -> if (rete) json(200, rosaJson(100 to "Anna")) else json(503, "") }

        vm.load()
        assertEquals(RosaUi.Error, vm.attendi())

        rete = true
        vm.loadIfNeeded()

        assertEquals(1, vm.pronta().rows.size)
    }

    @Test
    fun `un token rifiutato e un rinnovo rifiutato riportano all'accesso`() {
        val vm = viewModel { r, _ -> if (r.path!!.contains("/auth/")) json(400, """{"error":"invalid_grant"}""") else json(401, "{}") }

        vm.load()

        assertEquals(RosaUi.NeedLogin, vm.attendi())
    }

    @Test
    fun `un 403 e' uno stato a se, non sei piu' nel gruppo`() {
        val vm = viewModel { _, _ -> json(403, "{}") }

        vm.load()

        assertEquals(RosaUi.NotAuthorized, vm.attendi())
    }

    @Test
    fun `la sessione persa si segnala una volta sola per lettura e senza rifare richieste`() {
        var persa = 0
        val vm =
            viewModel(onSessionLost = {
                persa++
            }) { r, _ -> if (r.path!!.contains("/auth/")) json(400, """{"error":"invalid_grant"}""") else json(401, "{}") }

        vm.load()
        assertEquals(RosaUi.NeedLogin, vm.attendi())
        val richieste = finto!!.richieste.size
        // La schermata puo' riconsegnare lo stato quante volte vuole: nessun effetto.
        repeat(3) { vm.state.value }

        assertEquals(1, persa)
        assertEquals(richieste, finto!!.richieste.size)
    }

    @Test
    fun `una scrittura che fallisce non manda in crash e la lista resta com'e'`() {
        val vm = viewModel { _, _ -> json(200, rosaJson(100 to "Anna", 101 to "Marco")) }
        val esistente = giocatore("Anna")
        vm.load()
        vm.pronta()

        // Un giocatore locale che non esiste piu' (cancellato da un'altra schermata): la chiave esterna non regge.
        val comando = vm.link(100, 9_999)
        runBlocking { comando.join() }
        // Un'eccezione scappata dalla scrittura cancellerebbe il lavoro: qui deve essere finito bene.
        assertFalse("la scrittura fallita non deve far fallire il comando", comando.isCancelled)

        val dopo = vm.state.value as RosaUi.Ready
        assertEquals(listOf<String?>(null, null), dopo.rows.map { it.linkedLocalName })
        // E la schermata funziona ancora.
        assertEquals(
            "Anna",
            vm
                .comanda { vm.link(100, esistente) }
                .rows
                .first()
                .linkedLocalName,
        )
    }

    @Test
    fun `le righe dicono chi e' collegato, chi e' proposto per nome e chi non e' collegato`() {
        val vm = viewModel { _, _ -> json(200, rosaJson(100 to "Anna Bianchi", 101 to "Marco Rossi", 102 to "Luca Verdi")) }
        val annaId = giocatore("Anna Bianchi")
        giocatore("marco rossi")
        val vecchio = giocatore("Vecchio")

        vm.load()
        vm.pronta()
        val pronta = vm.comanda { vm.link(100, vecchio) }

        // Anna e' collegata a "Vecchio" (scelto a mano, il nome diverso non conta), Marco e' solo proposto, Luca non e' collegato.
        assertEquals(
            listOf(
                RosaRow(RemotePlayer(100, "Anna Bianchi"), "Vecchio", null),
                RosaRow(RemotePlayer(101, "Marco Rossi"), null, "marco rossi"),
                RosaRow(RemotePlayer(102, "Luca Verdi"), null, null),
            ),
            pronta.rows,
        )
        assertEquals(1, pronta.proposals)
        assertEquals(listOf("Anna Bianchi", "marco rossi"), pronta.freeLocals.map { it.name })
        assertTrue(annaId > 0)
    }

    @Test
    fun `i nomi uguali si collegano solo col comando e dopo non c'e' piu' niente da proporre`() {
        val vm = viewModel { _, _ -> json(200, rosaJson(100 to "Anna", 101 to "Marco")) }
        giocatore("anna")
        giocatore("Marco")
        giocatore("Altro")

        vm.load()
        assertEquals("caricare non collega", listOf<String?>(null, null), vm.pronta().rows.map { it.linkedLocalName })
        assertEquals(2, vm.pronta().proposals)

        val dopo = vm.comanda { vm.linkSameNames() }

        assertEquals(listOf<String?>("anna", "Marco"), dopo.rows.map { it.linkedLocalName })
        assertEquals(0, dopo.proposals)
        assertEquals(listOf("Altro"), dopo.freeLocals.map { it.name })
    }

    @Test
    fun `crea in locale aggiunge il giocatore e lo collega, scollega lo libera`() {
        val vm = viewModel { _, _ -> json(200, rosaJson(100 to "Anna")) }

        vm.load()
        vm.pronta()

        val creato = vm.comanda { vm.createLocal(100) }
        assertEquals("Anna", creato.rows.single().linkedLocalName)
        assertEquals(emptyList<LocalChoice>(), creato.freeLocals)

        val dopo = vm.comanda { vm.unlink(100) }
        assertEquals(null, dopo.rows.single().linkedLocalName)
        assertEquals(listOf("Anna"), dopo.freeLocals.map { it.name })
    }

    @Test
    fun `una rotazione non rifa' la richiesta ma un altro gruppo si`() {
        val vm = viewModel { _, _ -> json(200, rosaJson(100 to "Anna")) }

        vm.loadIfNeeded()
        vm.attendi()
        vm.loadIfNeeded()
        assertEquals("la seconda e' una rotazione", 1, finto!!.quante("/rest/v1/v2_players"))

        // Il gruppo scelto cambia: un'altra rosa, quella del nuovo gruppo.
        account.selectGroup(PadelEliteGroup("g-2", "Calcetto", "member"))
        vm.loadIfNeeded()
        vm.attendi()

        assertEquals(2, finto!!.quante("/rest/v1/v2_players"))
        assertTrue(
            finto!!
                .richieste
                .last()
                .path!!
                .contains("group_id=eq.g-2"),
        )
    }
}
