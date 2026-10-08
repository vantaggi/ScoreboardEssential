package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.database.PadelEliteLink
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.repository.ColorRepository
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.ui.MatchHistoryViewModel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Il suggerimento discreto sulla card: una voce ancora in attesa i cui giocatori collegati sono
 * cambiati DOPO l'invio dice "invia di nuovo per aggiornare la casella". Non rimanda mai da solo.
 * Si confronta la firma dei collegamenti del file partito con quella dei collegamenti di adesso.
 */
@RunWith(AndroidJUnit4::class)
class SuggerimentoCollegamentiTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repository: MatchRepository
    private var finto: ServerFinto? = null
    private var ids = IntArray(5)

    @Before
    fun apri() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = MatchRepository(db.matchDao(), context, ColorRepository(context), db.padelEliteLinkDao())
        runBlocking {
            // Marco, Luca, Anna, Sara giocano; Elena e' nella rosa ma non in questa partita.
            ids =
                listOf("Marco", "Luca", "Anna", "Sara", "Elena")
                    .map { db.playerDao().insert(Player(playerName = it, appearances = 0, goals = 0)).toInt() }
                    .toIntArray()
            val registro = MatchLogCodec.encode(List(8) { LoggedEvent(ScoringEvent.Point(side = 1), it * 30_000L) })
            val matchId =
                db
                    .matchDao()
                    .insert(
                        Match(
                            team1Id = 1,
                            team2Id = 2,
                            team1Score = 0,
                            team2Score = 0,
                            timestamp = 1_790_193_000_000L,
                            sportId = SportRegistry.PADEL,
                            eventLog = registro,
                            serveOrder = "${ids[0]},${ids[2]},${ids[1]},${ids[3]}",
                            startedAt = 1_790_190_240_000L,
                            matchUuid = UUID_PARTITA,
                        ),
                    ).toInt()
            db.matchDao().replaceLineup(matchId, listOf(ids[0], ids[1]), listOf(ids[2], ids[3]))
        }
    }

    @After
    fun chiudi() {
        db.close()
        finto?.chiudi()
    }

    private fun collega(
        gruppo: String,
        locale: Int,
        remoto: Int,
    ) = runBlocking { db.padelEliteLinkDao().link(PadelEliteLink(locale, gruppo, remoto, "n$remoto")) }

    private fun servizi(): PadelEliteServices {
        val s = ServerFinto { _, _ -> rispostaDiInvio(giaInviata = true, aggiornata = true) }.also { finto = it }
        val store = SessionStore(preferenze(context, "sessione_suggerimento"), SoftwareSecretBox())
        store.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), store)
        return PadelEliteServices(
            context,
            s.config(),
            repository,
            accountFactory = { account },
            invioStoreFactory = { InvioStore(preferenze(context, "invii_suggerimento")) },
        )
    }

    /** Le card dello storico come le compone il ViewModel, con i collegamenti di adesso. */
    private fun card(
        invio: InvioInfo?,
        legami: List<PadelEliteLink>,
        entrato: Boolean = true,
    ): it.vantaggi.scoreboardessential.ui.MatchHistoryUiState {
        val partita = runBlocking { db.matchDao().getMatchByUuid(UUID_PARTITA)!! }
        val giocatori = ids.take(4).map { Player(playerId = it, playerName = "g$it", appearances = 0, goals = 0) }
        val repo = mock<MatchRepository>()
        whenever(repo.allMatches).thenReturn(emptyFlow())
        val vm = MatchHistoryViewModel(repo, padelEliteEnabled = true) { "riga" }
        val stati = invio?.let { mapOf(UUID_PARTITA to it) }.orEmpty()
        return vm
            .elenco(listOf(MatchWithTeams(partita, null, null, giocatori)), stati, entrato, legami)
            .single()
    }

    private fun tutti() = runBlocking { ids.flatMap { db.padelEliteLinkDao().linksOfPlayer(it) } }

    @Test
    fun `la firma ricorda i collegamenti del file partito e si legge dallo stato salvato`() =
        runBlocking {
            collega("g-1", ids[0], 11)
            val servizi = servizi()

            servizi.runner.run(UUID_PARTITA, "g-1", 0)

            val info = servizi.invii.get(UUID_PARTITA)!!
            assertEquals(InvioState.UPDATED, info.state)
            assertEquals("g-1", info.group)
            assertEquals("${ids[0]}:11", info.links)
        }

    @Test
    fun `un collegamento cambiato dopo l'invio suggerisce di rimandare, solo per i giocatori della partita e del gruppo`() =
        runBlocking {
            val servizi = servizi()
            servizi.runner.run(UUID_PARTITA, "g-1", 0)
            val inviata = servizi.invii.get(UUID_PARTITA)!!
            // Nessun collegamento prima, nessuno dopo: niente da dire.
            assertFalse(card(inviata, tutti()).showLinksHint)

            // Un giocatore che non gioca questa partita, e un altro gruppo: niente da dire.
            collega("g-1", ids[4], 55)
            collega("g-2", ids[0], 99)
            assertFalse(card(inviata, tutti()).showLinksHint)

            // Marco, che gioca, e' collegato in g-1 dopo l'invio: la voce nella casella e' vecchia.
            collega("g-1", ids[0], 11)
            assertTrue(card(inviata, tutti()).showLinksHint)

            // Rimandata (il file ora porta il collegamento), la firma si rinnova e il suggerimento sparisce.
            servizi.runner.run(UUID_PARTITA, "g-1", 0)
            assertFalse(card(servizi.invii.get(UUID_PARTITA), tutti()).showLinksHint)

            // Scollegarlo di nuovo e' un altro cambiamento.
            runBlocking { db.padelEliteLinkDao().unlinkLocal(ids[0], "g-1") }
            assertTrue(card(servizi.invii.get(UUID_PARTITA), tutti()).showLinksHint)
        }

    @Test
    fun `il suggerimento c'e' solo per una voce in attesa e con Invia di nuovo disponibile`() {
        val firmaDiPrima = ""
        val legami = listOf(PadelEliteLink(ids[0], "g-1", 11, "Marco"))

        fun conStato(s: InvioState) = card(InvioInfo(s, group = "g-1", links = firmaDiPrima), legami)

        for (s in listOf(InvioState.SENT, InvioState.UPDATED, InvioState.PRESENT)) {
            assertTrue("$s", conStato(s).showLinksHint)
        }
        // Importata, scartata, in coda: la casella non si puo' piu' aggiornare o sta gia' partendo.
        for (s in listOf(InvioState.IMPORTED, InvioState.DISCARDED, InvioState.QUEUED, InvioState.UNSENDABLE)) {
            assertFalse("$s", conStato(s).showLinksHint)
        }
        // Senza accesso il comando non c'e', quindi il suggerimento non ha dove portare.
        assertFalse(card(InvioInfo(InvioState.SENT, group = "g-1", links = firmaDiPrima), legami, entrato = false).showLinksHint)
        // Uno stato di prima, senza firma: non si sa, non si dice.
        assertFalse(card(InvioInfo(InvioState.SENT), legami).showLinksHint)
        // Nessuno stato: la partita non e' mai partita.
        assertFalse(card(null, legami).showLinksHint)
    }

    @Test
    fun `lo stato con la firma sopravvive alle preferenze, e quello di prima si legge com'era`() {
        val prefs = preferenze(context, "invii_codec")
        InvioStore(prefs).set("a", InvioInfo(InvioState.UPDATED, group = "g-1", links = "3:11,4:12"))
        InvioStore(prefs).set("b", InvioInfo(InvioState.SENT, group = "g-1", links = ""))
        InvioStore(prefs).set("c", InvioInfo(InvioState.UNSENDABLE, InvioReason.INVALID_PAYLOAD, "players|0"))
        InvioStore(prefs).set("d", InvioInfo(InvioState.QUEUED, group = "g-1", links = "3:11", previous = InvioState.UPDATED))
        InvioStore(prefs).set("e", InvioInfo(InvioState.SENT, group = "g-1"))
        // Il formato scritto prima della firma: tre campi e basta.
        prefs.edit().putString("vecchio", "SENT||").commit()

        val riletto = InvioStore(prefs)

        assertEquals(InvioInfo(InvioState.UPDATED, group = "g-1", links = "3:11,4:12"), riletto.get("a"))
        assertEquals(InvioInfo(InvioState.SENT, group = "g-1", links = ""), riletto.get("b"))
        assertEquals(InvioInfo(InvioState.UNSENDABLE, InvioReason.INVALID_PAYLOAD, "players|0"), riletto.get("c"))
        assertEquals(InvioInfo(InvioState.QUEUED, group = "g-1", links = "3:11", previous = InvioState.UPDATED), riletto.get("d"))
        // Gruppo noto, firma non nota: resta "non si sa", non diventa "nessun collegamento".
        assertEquals(InvioInfo(InvioState.SENT, group = "g-1"), riletto.get("e"))
        assertNull(riletto.get("e")?.links)
        assertEquals(InvioInfo(InvioState.SENT), riletto.get("vecchio"))
        assertNull(riletto.get("vecchio")?.links)
    }
}
