package it.vantaggi.scoreboardessential

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.core.PresenteDellaDashboard.Collegato
import it.vantaggi.scoreboardessential.core.PresenteDellaDashboard.Ospite
import it.vantaggi.scoreboardessential.core.Serata
import it.vantaggi.scoreboardessential.core.Traduzione
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.padelelite.FonteSerataDashboard
import it.vantaggi.scoreboardessential.padelelite.GironeDellaDashboard
import it.vantaggi.scoreboardessential.padelelite.SerataDashboard
import it.vantaggi.scoreboardessential.repository.SerataPrefsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * La Serata con la dashboard, senza disegnarla: la lettura all'apertura, "Usa i presenti" e il girone
 * (solo al tocco, con gli ospiti creati come giocatori locali), l'annullamento, la sola lettura e il
 * "Riprova". La dashboard e' una fonte finta: la rete e' provata in `LettoreSerataDashboardTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it")
class SerataConLaDashboardTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private val creati = mutableListOf<SerataViewModel>()
    private val contesto: Context get() = ApplicationProvider.getApplicationContext()
    private val store get() = SerataPrefsStore(contesto.getSharedPreferences("app_prefs", Context.MODE_PRIVATE))

    private val giovedi = LocalDate.of(2026, 10, 15)

    /**
     * Una fonte finta che risponde [risposta] e conta le letture. [attivaQui] falso e' "senza accesso o
     * senza gruppo"; con un [cancello] la lettura aspetta che si apra.
     */
    private class Finta(
        var risposta: SerataDashboard,
        val attivaQui: Boolean = true,
    ) : FonteSerataDashboard {
        var letture = 0
        var cancello: CompletableDeferred<Unit>? = null

        override suspend fun attiva() = attivaQui

        override suspend fun leggi(): SerataDashboard {
            letture++
            cancello?.await()
            return risposta
        }
    }

    @Before
    fun prepara() {
        Dispatchers.setMain(dispatcher)
        db =
            Room
                .inMemoryDatabaseBuilder(contesto, AppDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor { it.run() }
                .setTransactionExecutor { it.run() }
                .build()
        store.save(null)
    }

    @After
    fun chiudi() {
        creati.forEach { it.viewModelScope.cancel() }
        db.close()
        Dispatchers.resetMain()
    }

    private fun nuovoViewModel(
        fonte: FonteSerataDashboard?,
        inCorso: Boolean = false,
    ): SerataViewModel = SerataViewModel(db.playerDao(), store, inCorso, fonte).also { creati += it }

    private suspend fun rosa(vararg nomi: String): List<Int> =
        nomi.map { db.playerDao().insert(Player(playerName = it, appearances = 0, goals = 0)).toInt() }

    private suspend fun nomiInRosa(): List<String> =
        db
            .playerDao()
            .getAllPlayers()
            .first()
            .map { it.player.playerName }

    @Test
    fun `senza fonte la dashboard e' spenta e niente si legge`() =
        runTest {
            val vm = nuovoViewModel(fonte = null)
            vm.ricarica()
            advanceUntilIdle()
            assertEquals(SerataDashboard.Spenta, vm.stato.value.dashboard)
        }

    @Test
    fun `senza accesso o senza gruppo la riga non compare nemmeno per un attimo e non si legge`() =
        runTest {
            val fonte = Finta(SerataDashboard.Errore, attivaQui = false)
            val vm = nuovoViewModel(fonte)
            val visti = mutableListOf<SerataDashboard>()
            val raccolta = launch { vm.stato.collect { visti += it.dashboard } }

            vm.ricarica()
            advanceUntilIdle()
            raccolta.cancel()

            assertEquals(listOf(SerataDashboard.Spenta), visti.distinct())
            assertEquals(0, fonte.letture)
        }

    @Test
    fun `all'apertura legge la dashboard, ma non cambia niente da sola`() =
        runTest {
            val (a, b, c) = rosa("Vantaggi", "Trinari", "Nudi")
            val pronta = SerataDashboard.Pronta(giovedi, Traduzione(listOf(Collegato(10, "Vantaggi", a), Collegato(11, "Trinari", b))))
            val fonte = Finta(pronta)
            val vm = nuovoViewModel(fonte)
            assertEquals("prima di leggere niente", SerataDashboard.Spenta, vm.stato.value.dashboard)

            vm.ricarica()
            advanceUntilIdle()

            assertEquals(pronta, vm.stato.value.dashboard)
            assertNull("nessuna scrittura senza un tocco", store.load())
            assertEquals(listOf("Nudi", "Trinari", "Vantaggi"), nomiInRosa())
            assertEquals(
                c,
                vm.stato.value.rosa
                    .first { it.nome == "Nudi" }
                    .id,
            )
        }

    @Test
    fun `usa i presenti mette dentro i collegati e crea gli ospiti, poi lo dice`() =
        runTest {
            val (a, b, c) = rosa("Vantaggi", "Trinari", "Nudi")
            val presenti =
                Traduzione(
                    listOf(Collegato(10, "Vantaggi", a), Ospite(13, "Porcacchia"), Collegato(11, "Trinari", b), Collegato(12, "Nudi", c)),
                )
            val vm = nuovoViewModel(Finta(SerataDashboard.Pronta(giovedi, presenti)))
            vm.ricarica()
            advanceUntilIdle()

            vm.usaIPresenti()
            advanceUntilIdle()

            val serata = vm.stato.value.serata!!
            val porcacchia =
                vm.stato.value.rosa
                    .first { it.nome == "Porcacchia" }
                    .id
            assertEquals("l'ordine e' quello della dashboard", listOf(a, porcacchia, b, c), serata.presenti)
            assertTrue("quattro presenti compongono la bozza", vm.stato.value.puoIniziare)
            assertEquals(serata, store.load())
            assertTrue(vm.stato.value.inUso(PropostaDashboard.Presenti))
            assertEquals(RapportoDashboard(presenti = 4, ospiti = 1), vm.rapporti.first())
        }

    @Test
    fun `l'ospite che la traduzione accoppia a un omonimo non crea un doppione`() =
        runTest {
            val (porcacchia) = rosa("Porcacchia")
            val vm =
                nuovoViewModel(Finta(SerataDashboard.Pronta(giovedi, Traduzione(listOf(Ospite(13, "Porcacchia", omonimo = porcacchia))))))
            vm.ricarica()
            advanceUntilIdle()

            vm.usaIPresenti()
            advanceUntilIdle()

            assertEquals(listOf("Porcacchia"), nomiInRosa())
            assertEquals(
                listOf(porcacchia),
                vm.stato.value.serata!!
                    .presenti,
            )
        }

    @Test
    fun `annulla torna alla serata di prima, con le partite giocate`() =
        runTest {
            val ids = rosa("A", "B", "C", "D", "E")
            val prima = Serata.nuova(ids.take(4)).consegna().chiudiPartita()
            store.save(prima)
            val vm = nuovoViewModel(Finta(SerataDashboard.Pronta(giovedi, Traduzione(ids.drop(1).map { Collegato(it, "x", it) }))))
            vm.ricarica()
            advanceUntilIdle()

            vm.usaIPresenti()
            advanceUntilIdle()
            assertEquals(
                ids.drop(1),
                vm.stato.value.serata!!
                    .presenti,
            )
            assertEquals(
                "le partite giocate restano",
                prima.giocate,
                vm.stato.value.serata!!
                    .giocate,
            )

            vm.annullaLaDashboard()

            assertEquals(prima, vm.stato.value.serata)
            assertEquals(prima, store.load())
            assertFalse(vm.stato.value.inUso(PropostaDashboard.Presenti))
        }

    @Test
    fun `un girone diventa i presenti, e cambiare a mano toglie la spunta`() =
        runTest {
            val ids = rosa("A", "B", "C", "D", "E", "F", "G", "H")

            fun girone(da: Int) = GironeDellaDashboard(Traduzione(ids.subList(da, da + 4).map { Collegato(it, "x", it) }))
            val pronta = SerataDashboard.Pronta(giovedi, Traduzione(ids.map { Collegato(it, "x", it) }), listOf(girone(0), girone(4)))
            val vm = nuovoViewModel(Finta(pronta))
            vm.ricarica()
            advanceUntilIdle()

            vm.usaIlGirone(1)
            advanceUntilIdle()

            assertEquals(
                ids.subList(4, 8),
                vm.stato.value.serata!!
                    .presenti,
            )
            assertTrue(vm.stato.value.inUso(PropostaDashboard.Girone(1)))
            assertFalse(vm.stato.value.inUso(PropostaDashboard.Girone(0)))

            vm.scambiaPresente(ids[0])
            assertFalse("i presenti non sono piu' quelli del girone", vm.stato.value.inUso(PropostaDashboard.Girone(1)))
        }

    @Test
    fun `durante una partita le proposte non si applicano`() =
        runTest {
            val (a) = rosa("A")
            val vm = nuovoViewModel(Finta(SerataDashboard.Pronta(giovedi, Traduzione(listOf(Collegato(10, "A", a))))), inCorso = true)
            vm.ricarica()
            advanceUntilIdle()

            vm.usaIPresenti()
            advanceUntilIdle()

            assertNull(store.load())
        }

    @Test
    fun `nessun presente confermato non cambia niente`() =
        runTest {
            rosa("A")
            val vm = nuovoViewModel(Finta(SerataDashboard.Pronta(giovedi, Traduzione(emptyList()))))
            vm.ricarica()
            advanceUntilIdle()

            vm.usaIPresenti()
            advanceUntilIdle()

            assertNull(store.load())
            assertEquals(listOf("A"), nomiInRosa())
        }

    @Test
    fun `riprova rilegge dopo un errore, e tornando sulla schermata una serata letta resta in vista`() =
        runTest {
            val fonte = Finta(SerataDashboard.Errore)
            val vm = nuovoViewModel(fonte)
            vm.ricarica()
            advanceUntilIdle()
            assertEquals(SerataDashboard.Errore, vm.stato.value.dashboard)

            val pronta = SerataDashboard.Pronta(giovedi, Traduzione(emptyList()))
            fonte.risposta = pronta
            fonte.cancello = CompletableDeferred()
            vm.riprovaLaDashboard()
            runCurrent()
            assertEquals("Riprova mostra che sta leggendo", SerataDashboard.InLettura, vm.stato.value.dashboard)
            fonte.cancello!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(pronta, vm.stato.value.dashboard)

            fonte.cancello = CompletableDeferred()
            vm.ricarica()
            runCurrent()
            assertEquals("niente lampeggio tornando sulla schermata", pronta, vm.stato.value.dashboard)
            fonte.cancello!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(3, fonte.letture)
        }
}
