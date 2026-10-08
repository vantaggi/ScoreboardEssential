package it.vantaggi.scoreboardessential

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.core.Composizione
import it.vantaggi.scoreboardessential.core.Serata
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.repository.SerataPrefsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La schermata Serata senza disegnarla: il compositore (presenti, ospite, rotazione, scambi, sola
 * lettura), la memoria fra due aperture e le righe che ne escono. Database vero in memoria.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it")
class SerataDelTelefonoTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private val creati = mutableListOf<SerataViewModel>()
    private val contesto: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = contesto.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val store get() = SerataPrefsStore(prefs)

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
    }

    @After
    fun chiudi() {
        creati.forEach { it.viewModelScope.cancel() }
        db.close()
        Dispatchers.resetMain()
    }

    private fun nuovoViewModel(inCorso: Boolean = false): SerataViewModel =
        SerataViewModel(db.playerDao(), store, inCorso).also { creati += it }

    private suspend fun rosa(vararg nomi: String): List<Int> =
        nomi.map { db.playerDao().insert(Player(playerName = it, appearances = 0, goals = 0)).toInt() }

    @Test
    fun `senza serata la rosa e' tutta assente e non c'e' una bozza`() =
        runTest {
            rosa("Vantaggi", "Trinari")
            val vm = nuovoViewModel()
            advanceUntilIdle()
            val stato = vm.stato.value
            assertEquals(listOf("Trinari", "Vantaggi"), stato.rosa.map { it.nome })
            assertNull(stato.serata)
            assertFalse(stato.puoIniziare)
            assertEquals(4, stato.presentiMancanti)
        }

    @Test
    fun `quattro presenti dalla rosa compongono la prima partita e si ricordano dopo il riavvio`() =
        runTest {
            val ids = rosa("Vantaggi", "Trinari", "Nudi", "Porcacchia")
            val vm = nuovoViewModel()
            advanceUntilIdle()
            ids.forEachIndexed { i, id ->
                vm.scambiaPresente(id)
                assertEquals(i == 3, vm.stato.value.puoIniziare)
            }
            val bozza =
                vm.stato.value.serata!!
                    .bozza!!
            assertEquals(ids.toSet(), bozza.posti.toSet())

            // Riavvio: un ViewModel nuovo ritrova presenti e bozza dalle preferenze.
            val dopo = nuovoViewModel()
            advanceUntilIdle()
            assertEquals(vm.stato.value.serata, dopo.stato.value.serata)
            assertEquals(
                bozza,
                dopo.stato.value.serata!!
                    .bozza,
            )
        }

    @Test
    fun `un ospite e' un giocatore locale creato al volo, e lo stesso nome non fa un doppione`() =
        runTest {
            rosa("Vantaggi", "Trinari", "Nudi", "Porcacchia")
            val vm = nuovoViewModel()
            advanceUntilIdle()
            vm.stato.value.rosa
                .forEach { vm.scambiaPresente(it.id) }

            assertFalse("un nome vuoto non fa un ospite", vm.aggiungiOspite("   "))
            assertTrue(vm.aggiungiOspite("  Marco  "))
            advanceUntilIdle()

            val giocatori = db.playerDao().getAllPlayers().first()
            val marco = giocatori.single { it.player.playerName == "Marco" }.player
            assertEquals("e' un Player come gli altri, con le statistiche a zero", 0, marco.appearances)
            assertTrue(
                marco.playerId in
                    vm.stato.value.serata!!
                        .presenti,
            )
            assertEquals(
                listOf(marco.playerId),
                vm.stato.value.serata!!
                    .panchina,
            )

            // Lo stesso nome (anche con altre maiuscole) e' la stessa persona.
            assertTrue(vm.aggiungiOspite("marco"))
            advanceUntilIdle()
            assertEquals(
                5,
                db
                    .playerDao()
                    .getAllPlayers()
                    .first()
                    .size,
            )
            assertEquals(
                5,
                vm.stato.value.serata!!
                    .presenti.size,
            )
        }

    @Test
    fun `toccare due posti li scambia, toccare un posto e la panchina li sostituisce`() =
        runTest {
            val ids = rosa("A", "B", "C", "D", "E")
            val vm = nuovoViewModel()
            advanceUntilIdle()
            ids.forEach { vm.scambiaPresente(it) }
            val prima =
                vm.stato.value.serata!!
                    .bozza!!

            vm.toccaIlPosto(0)
            assertEquals(SelezioneSerata.Posto(0), vm.stato.value.selezione)
            vm.toccaIlPosto(0)
            assertNull("un secondo tocco sullo stesso posto toglie la selezione", vm.stato.value.selezione)

            vm.toccaIlPosto(0)
            vm.toccaIlPosto(3)
            assertEquals(
                prima.scambia(0, 3),
                vm.stato.value.serata!!
                    .bozza,
            )
            assertNull(vm.stato.value.selezione)

            val fuori =
                vm.stato.value.serata!!
                    .panchina
                    .single()
            vm.toccaLaPanchina(fuori)
            vm.toccaIlPosto(1)
            assertEquals(
                fuori,
                vm.stato.value.serata!!
                    .bozza!!
                    .posti[1],
            )
            assertEquals(
                1,
                vm.stato.value.serata!!
                    .panchina.size,
            )

            // Prima la panchina e poi il posto: lo stesso scambio.
            val nuovoFuori =
                vm.stato.value.serata!!
                    .panchina
                    .single()
            vm.toccaIlPosto(2)
            vm.toccaLaPanchina(nuovoFuori)
            assertEquals(
                nuovoFuori,
                vm.stato.value.serata!!
                    .bozza!!
                    .posti[2],
            )
        }

    @Test
    fun `ruota, stesse coppie e scambia i lati cambiano la bozza e si ricordano`() =
        runTest {
            val ids = rosa("A", "B", "C", "D")
            val vm = nuovoViewModel()
            advanceUntilIdle()
            ids.forEach { vm.scambiaPresente(it) }
            val prima =
                vm.stato.value.serata!!
                    .bozza!!

            vm.ruota()
            val ruotata =
                vm.stato.value.serata!!
                    .bozza!!
            assertTrue(ruotata.coppie != prima.coppie)
            vm.scambiaILati()
            assertEquals(ruotata.scambiaLati(), store.load()!!.bozza)
            // Senza partite giocate "Stesse coppie" non c'e': non cambia niente.
            val prima2 = vm.stato.value.serata
            vm.stesseCoppie()
            assertEquals(prima2, vm.stato.value.serata)
        }

    /**
     * La Serata e' aperta con la copia letta all'apertura, e intanto MainViewModel chiude la partita (anche
     * dall'orologio). Un comando fatto dopo parte dalla memoria, non dalla copia vecchia: la partita
     * chiusa non sparisce.
     */
    @Test
    fun `un comando dopo che MainViewModel ha chiuso la partita non la perde`() =
        runTest {
            val ids = rosa("A", "B", "C", "D", "E")
            store.save(Serata.nuova(ids).consegna())
            val vm = nuovoViewModel()
            advanceUntilIdle()
            assertNull(vm.stato.value.serata!!.giocate.firstOrNull())

            // Scrive "l'altro processo": la partita e' chiusa, e la bozza e' un'altra.
            store.save(store.load()!!.chiudiPartita())
            vm.ruota()

            val dopo = store.load()!!
            assertEquals("la partita chiusa resta", 1, dopo.giocate.size)
            assertNull("e non torna in gioco", dopo.inGioco)
            assertEquals(dopo, vm.stato.value.serata)

            // Anche scambiare un presente parte dalla memoria.
            store.save(dopo.chiudiPartita(dopo.bozza))
            vm.scambiaPresente(ids[4])
            assertEquals(2, store.load()!!.giocate.size)
            assertFalse(ids[4] in store.load()!!.presenti)
        }

    @Test
    fun `ricarica legge la serata cambiata nel frattempo e butta la selezione`() =
        runTest {
            val ids = rosa("A", "B", "C", "D")
            store.save(Serata.nuova(ids))
            val vm = nuovoViewModel()
            advanceUntilIdle()
            vm.toccaIlPosto(0)
            store.save(store.load()!!.consegna().chiudiPartita())

            vm.ricarica()

            assertEquals(1, vm.stato.value.serata!!.giocate.size)
            assertNull(vm.stato.value.selezione)
        }

    @Test
    fun `in sola lettura nessun comando cambia la serata`() =
        runTest {
            val ids = rosa("A", "B", "C", "D", "E")
            store.save(Serata.nuova(ids.take(4)))
            val vm = nuovoViewModel(inCorso = true)
            advanceUntilIdle()
            val iniziale = vm.stato.value.serata

            vm.scambiaPresente(ids[4])
            vm.ruota()
            vm.scambiaILati()
            vm.toccaIlPosto(0)
            vm.toccaIlPosto(1)
            assertFalse(vm.aggiungiOspite("Marco"))
            vm.chiudiLaSerata()

            assertEquals(iniziale, vm.stato.value.serata)
            assertEquals(iniziale, store.load())
            assertFalse("e non si inizia", vm.stato.value.puoIniziare)
            // Le righe sono spente.
            assertTrue(RigheDellaSerata.posti(contesto, vm.stato.value).filter { !it.intestazione }.none { it.attiva })
            assertTrue(RigheDellaSerata.comandi(contesto, vm.stato.value).none { it.attiva })
        }

    @Test
    fun `aggiungere e togliere un presente fra una partita e l'altra funziona in qualunque momento`() =
        runTest {
            val ids = rosa("A", "B", "C", "D", "E")
            val vm = nuovoViewModel()
            advanceUntilIdle()
            ids.take(4).forEach { vm.scambiaPresente(it) }
            store.save(store.load()!!.consegna().chiudiPartita())
            val vm2 = nuovoViewModel()
            advanceUntilIdle()

            vm2.scambiaPresente(ids[4])
            assertEquals(
                5,
                vm2.stato.value.serata!!
                    .presenti.size,
            )
            vm2.scambiaPresente(ids[0])
            assertEquals(
                4,
                vm2.stato.value.serata!!
                    .presenti.size,
            )
            assertEquals(
                1,
                vm2.stato.value.serata!!
                    .giocate.size,
            )
            assertNotNull(
                vm2.stato.value.serata!!
                    .bozza,
            )
        }

    @Test
    fun `un giocatore eliminato dalla rosa esce dalla serata`() =
        runTest {
            val ids = rosa("A", "B", "C", "D", "E")
            store.save(Serata.nuova(ids))
            val vm = nuovoViewModel()
            advanceUntilIdle()
            db.playerDao().delete(Player(ids[4], "E", 0, 0))
            advanceUntilIdle()
            assertEquals(
                ids.take(4),
                vm.stato.value.serata!!
                    .presenti,
            )
        }

    @Test
    fun `chiudere la serata non cancella nessuna partita`() =
        runTest {
            val ids = rosa("A", "B", "C", "D")
            val vm = nuovoViewModel()
            advanceUntilIdle()
            ids.forEach { vm.scambiaPresente(it) }
            db.matchDao().insert(Match(team1Id = 1, team2Id = 2, team1Score = 6, team2Score = 3, timestamp = 1L, sportId = "padel"))

            vm.chiudiLaSerata()

            assertNull(store.load())
            assertNull(vm.stato.value.serata)
            val conta =
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM matches").use {
                    it.moveToFirst()
                    it.getInt(0)
                }
            assertEquals(1, conta)
        }

    @Test
    fun `le righe della schermata, i quattro posti, i tre comandi, la panchina, la rosa e l'ospite`() =
        runTest {
            val ids = rosa("A", "B", "C", "D", "E")
            val vm = nuovoViewModel()
            advanceUntilIdle()
            ids.forEach { vm.scambiaPresente(it) }
            vm.toccaIlPosto(2)
            val stato = vm.stato.value

            val posti = RigheDellaSerata.posti(contesto, stato)
            assertEquals(listOf("Coppia 1", "Coppia 2"), posti.filter { it.intestazione }.map { it.titolo })
            val seggi = posti.filter { !it.intestazione }
            assertEquals(4, seggi.size)
            assertEquals(listOf("1", "2", "1", "2"), seggi.map { it.lead })
            assertEquals(listOf(false, false, true, false), seggi.map { it.selezionata })
            assertTrue(seggi[2].descrizione.startsWith("Coppia 2, posto 1: "))

            val comandi = RigheDellaSerata.comandi(contesto, stato)
            assertEquals(listOf("Ruota le coppie", "Stesse coppie", "Scambia i lati"), comandi.map { it.titolo })
            assertEquals("senza partite giocate non si rigioca", listOf(true, false, true), comandi.map { it.attiva })

            assertEquals(1, RigheDellaSerata.panchina(contesto, stato).size)
            val presenti = RigheDellaSerata.presenti(contesto, stato)
            assertEquals(6, presenti.size)
            assertEquals("Aggiungi un ospite", presenti.last().titolo)
            assertTrue(presenti.dropLast(1).all { it.selezionata })
        }

    @Test
    fun `una serata ricordata prima della prima apertura si legge, e un testo rotto no`() {
        store.save(Serata.nuova(listOf(1, 2, 3, 4)).consegna())
        assertEquals(Composizione(listOf(1, 2), listOf(3, 4)), store.load()!!.inGioco)
        prefs.edit().putString(SerataPrefsStore.CHIAVE, "rotto").commit()
        assertNull(store.load())
    }
}
