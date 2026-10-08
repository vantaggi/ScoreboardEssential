package it.vantaggi.scoreboardessential.padelelite

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.PadelEliteLink
import it.vantaggi.scoreboardessential.database.Player
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R-1, il collegamento fra i giocatori dell'app e la rosa di un gruppo di Padel Elite: unicita'
 * (per giocatore locale e per giocatore della dashboard, dentro un gruppo), proposta per nome
 * identico che non scrive niente da sola, creazione in locale, allineamento alla rosa riletta.
 * Database vero in memoria: le unicita' sono quelle di SQLite, non di un finto.
 */
@RunWith(AndroidJUnit4::class)
class RosaGruppoTest {
    private lateinit var db: AppDatabase
    private lateinit var rosa: RosaGruppo

    private val anna = RemotePlayer(100, "Anna Bianchi")
    private val marco = RemotePlayer(101, "Marco Rossi")
    private val luca = RemotePlayer(102, "Luca Verdi")

    @Before
    fun apri() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        rosa = RosaGruppo(db.padelEliteLinkDao(), db.playerDao())
    }

    @After
    fun chiudi() {
        db.close()
    }

    private fun giocatore(nome: String): Int =
        runBlocking {
            db.playerDao().insert(Player(playerName = nome, appearances = 0, goals = 0)).toInt()
        }

    private fun collegamenti(gruppo: String = "g-1") = runBlocking { rosa.linksOf(gruppo) }.sortedBy { it.remotePlayerId }

    // --- unicita' ----------------------------------------------------------------------------

    @Test
    fun `un giocatore locale ha un solo collegamento per gruppo, collegarlo altrove sostituisce`() =
        runBlocking {
            val a = giocatore("Anna")

            rosa.link("g-1", anna, a)
            rosa.link("g-1", luca, a)

            assertEquals(listOf(PadelEliteLink(a, "g-1", 102, "Luca Verdi")), collegamenti())
        }

    @Test
    fun `un giocatore della dashboard non e' di due giocatori locali, l'ultimo scelto lo prende`() =
        runBlocking {
            val uno = giocatore("Anna")
            val due = giocatore("Annina")

            rosa.link("g-1", anna, uno)
            rosa.link("g-1", anna, due)

            assertEquals(listOf(PadelEliteLink(due, "g-1", 100, "Anna Bianchi")), collegamenti())
        }

    @Test
    fun `gli stessi giocatori in due gruppi sono due collegamenti indipendenti`() =
        runBlocking {
            val a = giocatore("Anna")

            rosa.link("g-1", anna, a)
            rosa.link("g-2", RemotePlayer(7, "Anna B."), a)
            rosa.unlink("g-1", anna)

            assertTrue(collegamenti("g-1").isEmpty())
            assertEquals(listOf(PadelEliteLink(a, "g-2", 7, "Anna B.")), collegamenti("g-2"))
        }

    @Test
    fun `cancellare il giocatore locale toglie i suoi collegamenti`() =
        runBlocking {
            val a = giocatore("Anna")
            rosa.link("g-1", anna, a)

            db.playerDao().delete(Player(playerId = a, playerName = "Anna", appearances = 0, goals = 0))

            assertTrue(collegamenti().isEmpty())
        }

    // --- proposta per nome -------------------------------------------------------------------

    @Test
    fun `il nome identico senza maiuscole ne' spazi si propone, e proporre non scrive niente`() =
        runBlocking {
            val a = giocatore("  anna   BIANCHI ")
            giocatore("Marcello")

            val proposte = rosa.proposals("g-1", listOf(anna, marco, luca))

            assertEquals(listOf(anna to a), proposte.map { it.remote to it.local.playerId })
            assertTrue("la proposta non collega da sola", collegamenti().isEmpty())
        }

    @Test
    fun `con un omonimo da una parte o dall'altra non si propone niente`() =
        runBlocking {
            giocatore("Anna Bianchi")
            giocatore("anna bianchi")
            giocatore("Luca Verdi")

            // Due Anna in locale: ambigua. Due Luca nella dashboard: ambigua anche se in locale e' uno solo.
            val proposte = rosa.proposals("g-1", listOf(anna, luca, RemotePlayer(103, "LUCA VERDI")))

            assertTrue(proposte.isEmpty())
        }

    @Test
    fun `l'omonimo gia' collegato conta, due Marco in locale e uno collegato, non si propone l'altro`() =
        runBlocking {
            val primo = giocatore("Marco Rossi")
            giocatore("marco rossi")
            // Il primo Marco e' gia' collegato a un altro giocatore della dashboard.
            rosa.link("g-1", RemotePlayer(555, "Marco R."), primo)

            val proposte = rosa.proposals("g-1", listOf(marco))

            assertTrue("con due Marco in locale non si indovina", proposte.isEmpty())
        }

    @Test
    fun `l'omonimo gia' collegato conta anche nella rosa, due Marco nella dashboard e uno collegato`() =
        runBlocking {
            val locale = giocatore("Marco Rossi")
            val altro = giocatore("Altro")
            val marcoUno = RemotePlayer(101, "Marco Rossi")
            val marcoDue = RemotePlayer(105, "marco  rossi")
            rosa.link("g-1", marcoUno, altro)

            val proposte = rosa.proposals("g-1", listOf(marcoUno, marcoDue))

            assertTrue("due Marco nella rosa: nessuna proposta, nemmeno per quello non collegato", proposte.isEmpty())
            assertTrue(locale > 0)
        }

    @Test
    fun `chi e' gia' collegato, da una parte o dall'altra, non si ripropone`() =
        runBlocking {
            val a = giocatore("Anna Bianchi")
            val m = giocatore("Marco Rossi")
            giocatore("Luca Verdi")
            rosa.link("g-1", anna, a)
            // Marco locale e' collegato a un altro giocatore della dashboard: non e' libero.
            rosa.link("g-1", RemotePlayer(555, "Altro"), m)

            val proposte = rosa.proposals("g-1", listOf(anna, marco, luca))

            assertEquals(listOf("Luca Verdi"), proposte.map { it.remote.name })
        }

    @Test
    fun `il comando collega solo le proposte e dice quante`() =
        runBlocking {
            val a = giocatore("Anna Bianchi")
            val l = giocatore("luca verdi")
            giocatore("Sconosciuto")

            val quanti = rosa.linkProposals("g-1", listOf(anna, marco, luca))

            assertEquals(2, quanti)
            assertEquals(
                listOf(PadelEliteLink(a, "g-1", 100, "Anna Bianchi"), PadelEliteLink(l, "g-1", 102, "Luca Verdi")),
                collegamenti(),
            )
            assertEquals("la seconda volta non c'e' piu' niente da proporre", 0, rosa.linkProposals("g-1", listOf(anna, marco, luca)))
        }

    // --- crea in locale ----------------------------------------------------------------------

    @Test
    fun `crea in locale fa un giocatore col nome della dashboard e lo collega`() =
        runBlocking {
            val id = rosa.createLocalAndLink("g-1", marco)

            val creato = rosa.localPlayers().single()
            assertEquals(id, creato.playerId)
            assertEquals("Marco Rossi", creato.playerName)
            assertEquals(0, creato.appearances)
            assertEquals(listOf(PadelEliteLink(id, "g-1", 101, "Marco Rossi")), collegamenti())
        }

    @Test
    fun `se il collegamento fallisce il giocatore non resta creato a meta'`() =
        runBlocking {
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER blocca_collegamenti BEFORE INSERT ON padel_elite_links BEGIN SELECT RAISE(ABORT, 'interrotto'); END",
            )

            val esito = runCatching { rosa.createLocalAndLink("g-1", marco) }

            assertTrue(esito.isFailure)
            assertTrue("tutto o niente: nessun giocatore orfano", rosa.localPlayers().isEmpty())
        }

    // --- allineamento alla rosa riletta ------------------------------------------------------

    @Test
    fun `riletta la rosa il nome della dashboard si aggiorna e chi non c'e' piu' si scollega`() =
        runBlocking {
            val a = giocatore("Anna")
            val m = giocatore("Marco")
            rosa.link("g-1", anna, a)
            rosa.link("g-1", marco, m)

            rosa.sync("g-1", listOf(RemotePlayer(100, "Anna B."), luca))

            assertEquals(listOf(PadelEliteLink(a, "g-1", 100, "Anna B.")), collegamenti())
        }

    @Test
    fun `una rosa che ha toccato il tetto di PostgREST puo' essere troncata e non scollega nessuno`() =
        runBlocking {
            val a = giocatore("Anna")
            val m = giocatore("Marco")
            rosa.link("g-1", RemotePlayer(5000, "Anna vecchia"), a)
            rosa.link("g-1", RemotePlayer(101, "Marco"), m)
            val piena = (1..RosaGruppo.MAX_ROWS).map { RemotePlayer(it + 100, if (it == 1) "Marco nuovo" else "G$it") }

            rosa.sync("g-1", piena)

            // Il nome si aggiorna, ma 5000 (assente dalle prime 1000 righe) non e' per questo sparito.
            assertEquals(listOf(101 to "Marco nuovo", 5000 to "Anna vecchia"), collegamenti().map { it.remotePlayerId to it.remoteName })
        }

    @Test
    fun `sotto il tetto invece chi non c'e' piu' si scollega`() =
        runBlocking {
            val a = giocatore("Anna")
            rosa.link("g-1", RemotePlayer(5000, "Anna vecchia"), a)

            rosa.sync("g-1", (1 until RosaGruppo.MAX_ROWS).map { RemotePlayer(it + 100, "G$it") })

            assertTrue(collegamenti().isEmpty())
        }

    @Test
    fun `una rosa riletta vuota non cancella nessun collegamento`() =
        runBlocking {
            val a = giocatore("Anna")
            rosa.link("g-1", anna, a)

            rosa.sync("g-1", emptyList())

            assertEquals(1, collegamenti().size)
        }
}
