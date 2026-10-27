package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchPlayerCrossRef
import it.vantaggi.scoreboardessential.database.PadelEliteLink
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.padelelite.SezioneDellaScelta.DASHBOARD
import it.vantaggi.scoreboardessential.padelelite.SezioneDellaScelta.DASHBOARD_NON_LETTA
import it.vantaggi.scoreboardessential.padelelite.SezioneDellaScelta.SOLO_APP
import it.vantaggi.scoreboardessential.padelelite.VoceDellaScelta.DallaDashboard
import it.vantaggi.scoreboardessential.padelelite.VoceDellaScelta.Intestazione
import it.vantaggi.scoreboardessential.padelelite.VoceDellaScelta.SoloNellApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * I giocatori della dashboard scelti per una partita, senza collegarli prima: chi e' collegato e' il suo
 * giocatore dell'app; chi non ha omonimi nasce nell'app gia' collegato; chi ha un omonimo non si unisce da solo
 * (nella dashboard spesso c'e' solo il cognome) e torna con gli indizi per riconoscerlo. E l'elenco del dialogo,
 * con la rosa letta dal server finto, la dashboard che non risponde e quella che risponde tardi.
 * Database vero in memoria.
 */
@RunWith(AndroidJUnit4::class)
class ScegliPerLaPartitaTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var rosa: RosaGruppo
    private var finto: ServerFinto? = null

    private val rossi = RemotePlayer(101, "Rossi")
    private val bianchi = RemotePlayer(100, "Bianchi")

    @Before
    fun apri() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        rosa = RosaGruppo(db.padelEliteLinkDao(), db.playerDao(), db.matchDao())
    }

    @After
    fun chiudi() {
        db.close()
        finto?.chiudi()
    }

    private fun giocatore(nome: String): Int =
        runBlocking { db.playerDao().insert(Player(playerName = nome, appearances = 0, goals = 0)).toInt() }

    private fun collegamenti() = runBlocking { rosa.linksOf("g-1") }.sortedBy { it.remotePlayerId }

    private fun nomi() =
        runBlocking {
            db
                .playerDao()
                .getAllPlayers()
                .first()
                .map { it.player.playerName }
        }

    /** Una partita chiusa: [lato1] contro [lato2], al [quando]. */
    private fun partita(
        quando: Long,
        lato1: List<Int>,
        lato2: List<Int>,
    ) = runBlocking {
        val id =
            db
                .matchDao()
                .insert(
                    Match(team1Id = 1, team2Id = 2, team1Score = 2, team2Score = 1, timestamp = quando, sportId = "padel"),
                ).toInt()
        db.matchDao().insertMatchPlayerCrossRefs(
            lato1.map { MatchPlayerCrossRef(id, it, 1) } + lato2.map { MatchPlayerCrossRef(id, it, 2) },
        )
    }

    // --- la scelta di un giocatore ------------------------------------------------------------

    @Test
    fun `un giocatore della dashboard gia' collegato e' il suo giocatore dell'app, niente di nuovo`() =
        runBlocking {
            val mattia = giocatore("Mattia")
            rosa.link("g-1", rossi, mattia)

            assertEquals(SceltaDallaDashboard.Pronto(mattia), rosa.scegliPerLaPartita("g-1", rossi))
            assertEquals(listOf("Mattia"), nomi())
        }

    @Test
    fun `senza omonimi nasce nell'app col nome della dashboard, gia' collegato`() =
        runBlocking {
            giocatore("Luca")

            val esito = rosa.scegliPerLaPartita("g-1", rossi) as SceltaDallaDashboard.Pronto

            assertEquals(listOf(PadelEliteLink(esito.localId, "g-1", 101, "Rossi")), collegamenti())
            assertEquals(listOf("Luca", "Rossi"), nomi())
            // La seconda volta e' lo stesso giocatore: non si ricrea.
            assertEquals(esito, rosa.scegliPerLaPartita("g-1", rossi))
            assertEquals(2, nomi().size)
        }

    @Test
    fun `con un omonimo non si unisce da solo, e torna con le partite giocate e l'ultima`() =
        runBlocking {
            val omonimo = giocatore("  rossi ")
            val compagno = giocatore("Luca")
            val a = giocatore("Anna")
            val b = giocatore("Bea")
            partita(quando = 1_000L, lato1 = listOf(a, b), lato2 = listOf(omonimo, compagno))
            partita(quando = 5_000L, lato1 = listOf(omonimo, compagno), lato2 = listOf(a, b))

            val esito = rosa.scegliPerLaPartita("g-1", rossi)

            assertEquals(
                SceltaDallaDashboard.Omonimi(
                    rossi,
                    listOf(IndizioOmonimo(omonimo, "  rossi ", 2, UltimaPartita(5_000L, listOf("Luca"), listOf("Anna", "Bea")))),
                ),
                esito,
            )
            assertTrue("nessun collegamento senza la risposta dell'utente", collegamenti().isEmpty())
            assertEquals("e nessun giocatore nuovo", 4, nomi().size)
        }

    @Test
    fun `l'omonimo senza partite lo dice, e un omonimo collegato a un altro della dashboard non conta`() =
        runBlocking {
            val libero = giocatore("Rossi")
            val preso = giocatore("ROSSI")
            rosa.link("g-1", RemotePlayer(555, "Rossi M."), preso)

            val esito = rosa.scegliPerLaPartita("g-1", rossi)

            assertEquals(SceltaDallaDashboard.Omonimi(rossi, listOf(IndizioOmonimo(libero, "Rossi", 0, null))), esito)
        }

    @Test
    fun `e' lui, l'omonimo si collega e tiene le sue statistiche`() =
        runBlocking {
            val omonimo = giocatore("Rossi")

            assertEquals(omonimo, rosa.unisci("g-1", rossi, omonimo))

            assertEquals(listOf(PadelEliteLink(omonimo, "g-1", 101, "Rossi")), collegamenti())
            assertEquals(SceltaDallaDashboard.Pronto(omonimo), rosa.scegliPerLaPartita("g-1", rossi))
        }

    @Test
    fun `e' un'altra persona, nasce un giocatore nuovo collegato e l'omonimo resta com'era`() =
        runBlocking {
            val omonimo = giocatore("Rossi")

            val nuovo = rosa.creaNuovo("g-1", rossi)

            assertTrue(nuovo != omonimo)
            assertEquals(listOf(PadelEliteLink(nuovo, "g-1", 101, "Rossi")), collegamenti())
            assertEquals(listOf("Rossi", "Rossi"), nomi())
        }

    // --- l'elenco del dialogo -----------------------------------------------------------------

    private fun rosaJson(vararg giocatori: RemotePlayer) =
        giocatori.joinToString(prefix = "[", postfix = "]") { """{"id":${it.id},"name":"${it.name}"}""" }

    private fun account(
        gruppoScelto: Boolean = true,
        risposte: (RecordedRequest, Int) -> MockResponse,
    ): PadelEliteAccount {
        val s = ServerFinto(risposte).also { finto = it }
        val store = SessionStore(preferenze(context, "sessione_scelta"), SoftwareSecretBox())
        store.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), store)
        if (gruppoScelto) account.selectGroup(PadelEliteGroup("g-1", "Padel del giovedi", "member"))
        return account
    }

    private fun CoroutineScope.giocatori(
        account: PadelEliteAccount,
        attesaMs: Long = 4_000L,
    ) = GiocatoriDalGruppo(account, rosa, this, attesaMs) { LocalDate.of(2026, 10, 15).atTime(10, 0) }

    /** La dashboard con la sola rosa: la serata (configurazione, iscritti, gironi) non c'e'. */
    private fun soloLaRosa(rosa: MockResponse): (RecordedRequest, Int) -> MockResponse =
        { r, _ -> if (r.path.orEmpty().startsWith("/rest/v1/v2_players")) rosa else json(404, "{}") }

    /** La dashboard con la serata di giovedi' 15 ottobre: [iscritti] confermati e un girone. */
    private fun conLaSerata(
        rosa: String,
        iscritti: List<Int>,
        girone: List<Int>,
    ): (RecordedRequest, Int) -> MockResponse =
        { r, _ ->
            val percorso = r.path.orEmpty()
            when {
                percorso.contains("key=eq.venue_config") -> {
                    json(200, """[{"value":{"day_of_week":4,"bookings":[{"time":"19:00","duration":"90m"}]}}]""")
                }

                percorso.contains("key=eq.gironi_serata") -> {
                    json(200, """[{"value":{"v":1,"serata":"2026-10-15","gironi":[${girone.joinToString(",", "[", "]")}]}}]""")
                }

                percorso.startsWith("/rest/v1/v2_rsvp") -> {
                    json(200, iscritti.joinToString(",", "[", "]") { """{"player_id":$it}""" })
                }

                percorso.startsWith("/rest/v1/v2_players") -> {
                    json(200, rosa)
                }

                else -> {
                    json(404, "{}")
                }
            }
        }

    @Test
    fun `con la rosa letta l'elenco ha la dashboard e poi chi c'e' solo nell'app`() =
        runBlocking {
            val mattia = giocatore("Mattia")
            val luca = giocatore("Luca")
            rosa.link("g-1", bianchi, mattia)
            val conto = account(risposte = soloLaRosa(json(200, rosaJson(rossi, bianchi))))

            val elenco = giocatori(conto).elenco(inCampo = emptySet())!!

            assertEquals("g-1", elenco.gruppo)
            assertEquals("Padel del giovedi", elenco.nomeGruppo)
            assertEquals(
                listOf(
                    Intestazione(DASHBOARD),
                    DallaDashboard(bianchi, mattia, "Mattia"),
                    DallaDashboard(rossi, null, null),
                    Intestazione(SOLO_APP),
                    SoloNellApp(luca, "Luca"),
                ),
                elenco.voci,
            )
        }

    @Test
    fun `prima i compagni di girone di chi e' in campo, poi gli iscritti, poi il resto, ognuno in ordine di nome`() =
        runBlocking {
            val anna = RemotePlayer(1, "Anna")
            val bea = RemotePlayer(2, "Bea")
            val carlo = RemotePlayer(3, "Carlo")
            val dario = RemotePlayer(4, "Dario")
            val elio = RemotePlayer(5, "Elio")
            val zoe = RemotePlayer(6, "Zoe")
            val mattia = giocatore("Mattia")
            rosa.link("g-1", dario, mattia)
            val conto =
                account(
                    risposte =
                        conLaSerata(
                            rosaJson(anna, bea, carlo, dario, elio, zoe),
                            iscritti = listOf(6, 5, 4, 3, 2, 1),
                            girone = listOf(4, 2, 6, 1),
                        ),
                )

            val voci = giocatori(conto).elenco(inCampo = setOf(mattia))!!.voci

            assertEquals(
                listOf(
                    Intestazione(SezioneDellaScelta.GIRONE),
                    DallaDashboard(anna, null, null),
                    DallaDashboard(bea, null, null),
                    DallaDashboard(zoe, null, null),
                    Intestazione(SezioneDellaScelta.ISCRITTI),
                    DallaDashboard(carlo, null, null),
                    DallaDashboard(elio, null, null),
                ),
                voci,
            )
        }

    @Test
    fun `senza nessuno in campo gli iscritti vengono prima del resto della rosa`() =
        runBlocking {
            val conto = account(risposte = conLaSerata(rosaJson(rossi, bianchi), iscritti = listOf(101), girone = listOf(101, 100)))

            val voci = giocatori(conto).elenco(inCampo = emptySet())!!.voci

            assertEquals(
                listOf(
                    Intestazione(SezioneDellaScelta.ISCRITTI),
                    DallaDashboard(rossi, null, null),
                    Intestazione(DASHBOARD),
                    DallaDashboard(bianchi, null, null),
                ),
                voci,
            )
        }

    @Test
    fun `senza un gruppo scelto non c'e' elenco e non si chiama la rete`() =
        runBlocking {
            val conto = account(gruppoScelto = false) { _, _ -> json(200, "[]") }

            assertNull(giocatori(conto).elenco(emptySet()))
            assertEquals(0, finto!!.richieste.size)
        }

    @Test
    fun `la dashboard che non risponde lascia i giocatori dell'app, e lo dice`() =
        runBlocking {
            val luca = giocatore("Luca")
            val conto = account { _, _ -> json(503, "") }

            val elenco = giocatori(conto).elenco(emptySet())!!

            assertEquals(listOf(Intestazione(DASHBOARD_NON_LETTA), SoloNellApp(luca, "Luca")), elenco.voci)
        }

    @Test
    fun `una rosa che arriva tardi non fa aspettare il dialogo, e la volta dopo e' pronta`() =
        runBlocking {
            giocatore("Luca")
            val conto = account(risposte = soloLaRosa(json(200, rosaJson(rossi)).setBodyDelay(600, TimeUnit.MILLISECONDS)))
            val giocatori = giocatori(conto, attesaMs = 100L)

            val prima = giocatori.elenco(emptySet())!!
            assertEquals(Intestazione(DASHBOARD_NON_LETTA), prima.voci.first())

            // La lettura continua in sottofondo e riempie la memoria.
            delay(2_000)
            val dopo = giocatori.elenco(emptySet())!!
            assertEquals(listOf(Intestazione(DASHBOARD), DallaDashboard(rossi, null, null)), dopo.voci.take(2))
        }
}
