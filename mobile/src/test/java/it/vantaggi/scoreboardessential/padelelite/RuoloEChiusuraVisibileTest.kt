package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.testing.WorkManagerTestInitHelper
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.repository.ColorRepository
import it.vantaggi.scoreboardessential.repository.MatchRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Il ruolo riletto all'avvio (debito 3) e la chiusura finale visibile (debito 4) di D-3. Server finto, mai quello vero.
 */
@RunWith(RobolectricTestRunner::class)
class RuoloEChiusuraVisibileTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private var finto: ServerFinto? = null

    @After
    fun chiudi() {
        finto?.chiudi()
    }

    private fun gruppi(ruolo: String) = json(200, """[{"group_id":"g-1","role":"$ruolo","groups":{"name":"Padel"}}]""")

    /** I servizi con sessione e gruppo scelto; [ruoloSalvato] null = una scelta fatta prima di D-2, senza ruolo. */
    private fun servizi(
        ruoloSalvato: String?,
        conSessione: Boolean = true,
        conGruppo: Boolean = true,
        risposta: (RecordedRequest, Int) -> MockResponse,
    ): PadelEliteServices {
        val s = ServerFinto(risposta).also { finto = it }
        val sessione = SessionStore(preferenze(app, "sessione_ruolo"), SoftwareSecretBox())
        if (conSessione) sessione.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), sessione)
        if (conGruppo) account.selectGroup(PadelEliteGroup("g-1", "Padel", ruoloSalvato.orEmpty()))
        return PadelEliteServices(
            app,
            s.config(),
            MatchRepository(mock(MatchDao::class.java), app, ColorRepository(app)),
            accountFactory = { account },
            invioStoreFactory = { InvioStore(preferenze(app, "invii_ruolo")) },
            direttaStoreFactory = { DirettaStore(preferenze(app, "diretta_ruolo")) },
        )
    }

    private fun letture() = finto!!.quante("/rest/v1/group_members")

    // --- Ruolo ---

    @Test
    fun `chi aveva scelto il gruppo prima di D-2 vede l'interruttore dopo la rilettura`() =
        runBlocking {
            val s = servizi(ruoloSalvato = null) { _, _ -> gruppi("admin") }
            assertFalse("prima: nessun ruolo, nessun comando", s.puoTrasmettere("padel"))

            assertTrue(s.aggiornaIlRuolo())

            assertEquals("admin", s.account.selectedGroupRole())
            assertTrue("dopo: l'interruttore c'e'", s.puoTrasmettere("padel"))
            assertEquals(1, letture())
        }

    @Test
    fun `un admin declassato non vede piu' l'interruttore`() =
        runBlocking {
            val s = servizi(ruoloSalvato = "admin") { _, _ -> gruppi("member") }
            assertTrue(s.puoTrasmettere("padel"))

            assertTrue(s.aggiornaIlRuolo())

            assertEquals("member", s.account.selectedGroupRole())
            assertFalse(s.puoTrasmettere("padel"))
        }

    @Test
    fun `un ruolo che non e' cambiato non ridisegna niente`() =
        runBlocking {
            val s = servizi(ruoloSalvato = "owner") { _, _ -> gruppi("owner") }

            assertFalse(s.aggiornaIlRuolo())

            assertEquals("owner", s.account.selectedGroupRole())
            assertEquals(1, letture())
        }

    @Test
    fun `se la lettura fallisce non succede niente, nessun errore e il ruolo resta l'ultimo letto`() =
        runBlocking {
            val s = servizi(ruoloSalvato = "admin") { _, _ -> json(500, "{}") }

            assertFalse(s.aggiornaIlRuolo())

            assertEquals("admin", s.account.selectedGroupRole())
            assertTrue(s.puoTrasmettere("padel"))
        }

    @Test
    fun `un'eccezione nella lettura (il Keystore nel rinnovo del token) e' inghiottita`() =
        runBlocking {
            var rotta = false
            val scatola =
                object : SecretBox {
                    private val vera = SoftwareSecretBox()

                    override fun seal(plain: ByteArray): ByteArray {
                        check(!rotta) { "Keystore non disponibile" }
                        return vera.seal(plain)
                    }

                    override fun open(sealed: ByteArray): ByteArray? = vera.open(sealed)
                }
            val s =
                ServerFinto { richiesta, _ ->
                    if (richiesta.path!!.contains("refresh_token")) {
                        json(200, rispostaDiSessione("A2", "R2", Long.MAX_VALUE / 2))
                    } else {
                        gruppi("member")
                    }
                }.also { finto = it }
            val sessione = SessionStore(preferenze(app, "sessione_ruolo_rotta"), scatola)
            // Scaduta: il rinnovo si fa e, rotto il Keystore, salvare la sessione nuova lancia.
            sessione.save(PadelEliteSession("A1", "R1", 0L, "u-1", "a@b.it"))
            rotta = true
            val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), sessione)
            account.selectGroup(PadelEliteGroup("g-1", "Padel", "admin"))
            val servizi =
                PadelEliteServices(
                    app,
                    s.config(),
                    MatchRepository(mock(MatchDao::class.java), app, ColorRepository(app)),
                    accountFactory = { account },
                    invioStoreFactory = { InvioStore(preferenze(app, "invii_ruolo2")) },
                )

            assertFalse(servizi.aggiornaIlRuolo())
            assertEquals("admin", account.selectedGroupRole())
        }

    @Test
    fun `se nel frattempo si e' scelto un altro gruppo il risultato vecchio non si scrive`() =
        runBlocking {
            lateinit var s: PadelEliteServices
            s =
                servizi(ruoloSalvato = "member") { _, _ ->
                    // La risposta sta per arrivare: intanto l'utente sceglie un altro gruppo.
                    s.account.selectGroup(PadelEliteGroup("g-2", "Altro", "owner"))
                    gruppi("admin")
                }

            assertFalse(s.aggiornaIlRuolo())

            assertEquals("g-2", s.account.selectedGroup()?.first)
            assertEquals("owner", s.account.selectedGroupRole())
        }

    @Test
    fun `se nel frattempo si e' usciti dall'account non si riscrive il gruppo`() =
        runBlocking {
            lateinit var s: PadelEliteServices
            s =
                servizi(ruoloSalvato = "member") { _, _ ->
                    s.account.signOut()
                    gruppi("admin")
                }

            assertFalse(s.aggiornaIlRuolo())

            assertNull(s.account.session())
            assertNull(s.account.selectedGroup())
            assertNull(s.account.selectedGroupRole())
        }

    @Test
    fun `una sola lettura per avvio, anche se la si chiede piu' volte`() =
        runBlocking {
            val s = servizi(ruoloSalvato = "member") { _, _ -> gruppi("admin") }

            assertTrue(s.aggiornaIlRuolo())
            assertFalse(s.aggiornaIlRuolo())
            assertFalse(s.aggiornaIlRuolo())

            assertEquals(1, letture())
        }

    @Test
    fun `una sola lettura anche se la prima e' fallita`() =
        runBlocking {
            val s = servizi(ruoloSalvato = "member") { _, _ -> json(500, "{}") }

            s.aggiornaIlRuolo()
            s.aggiornaIlRuolo()

            assertEquals(1, letture())
        }

    @Test
    fun `senza accesso o senza gruppo scelto non legge niente e non consuma la lettura`() =
        runBlocking {
            val senzaAccesso = servizi(ruoloSalvato = "admin", conSessione = false) { _, _ -> gruppi("member") }
            assertFalse(senzaAccesso.aggiornaIlRuolo())
            assertEquals(0, letture())
            finto!!.chiudi()

            val senzaGruppo = servizi(ruoloSalvato = null, conGruppo = false) { _, _ -> gruppi("member") }
            assertFalse(senzaGruppo.aggiornaIlRuolo())
            assertEquals(0, letture())
        }

    @Test
    fun `senza configurazione non legge niente`() =
        runBlocking {
            val s = ServerFinto { _, _ -> gruppi("admin") }.also { finto = it }
            val spento =
                PadelEliteServices(
                    app,
                    PadelEliteConfig.OFF,
                    MatchRepository(mock(MatchDao::class.java), app, ColorRepository(app)),
                    accountFactory = { error("con la funzione spenta l'account non si tocca") },
                )

            assertFalse(spento.aggiornaIlRuolo())
            assertEquals(0, s.quante("/rest/v1/group_members"))
        }

    // --- Chiusura finale visibile ---

    private fun lavoro(stato: WorkInfo.State) = WorkInfo(UUID.randomUUID(), stato, emptySet())

    @Test
    fun `la fase della chiusura segue lo stato del lavoro in coda`() {
        assertEquals(ChiusuraFase.NESSUNA, chiusuraFase(emptyList()))
        assertEquals(ChiusuraFase.IN_CORSO, chiusuraFase(listOf(lavoro(WorkInfo.State.ENQUEUED))))
        assertEquals(ChiusuraFase.IN_CORSO, chiusuraFase(listOf(lavoro(WorkInfo.State.RUNNING))))
        assertEquals(ChiusuraFase.IN_CORSO, chiusuraFase(listOf(lavoro(WorkInfo.State.BLOCKED))))
        assertEquals(ChiusuraFase.FALLITA, chiusuraFase(listOf(lavoro(WorkInfo.State.FAILED))))
        assertEquals(ChiusuraFase.NESSUNA, chiusuraFase(listOf(lavoro(WorkInfo.State.SUCCEEDED))))
        assertEquals(ChiusuraFase.NESSUNA, chiusuraFase(listOf(lavoro(WorkInfo.State.CANCELLED))))
    }

    @Test
    fun `un lavoro vero in coda e' in corso finche' aspetta la rete`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        DirettaChiusuraWorker.enqueue(app, DirettaChiusuraRichiesta(UUID_PARTITA, "g-1", conRisultato = true))

        val fase = runBlocking { DirettaChiusuraWorker.fase(app, UUID_PARTITA).first() }

        // I vincoli di rete non sono soddisfatti nel test: il lavoro e' ENQUEUED, come con il telefono offline.
        assertEquals(ChiusuraFase.IN_CORSO, fase)
        assertEquals(ChiusuraFase.NESSUNA, runBlocking { DirettaChiusuraWorker.fase(app, "un-altra-partita").first() })
    }

    @Test
    fun `il foglio dice una riga sola, chiusura in corso, non chiusa o semplicemente chiusa`() {
        val chiusa = DirettaStato(DirettaFase.CHIUSA, gruppo = "Padel", matchId = UUID_PARTITA)

        val normale = direttaLook(app, chiusa)!!
        val inCorso = direttaLook(app, chiusa, ChiusuraFase.IN_CORSO)!!
        val fallita = direttaLook(app, chiusa, ChiusuraFase.FALLITA)!!

        assertEquals(app.getString(R.string.diretta_closed), normale.text)
        assertEquals(app.getString(R.string.diretta_closing), inCorso.text)
        assertEquals(app.getString(R.string.diretta_close_failed), fallita.text)
        assertEquals(3, setOf(normale.text, inCorso.text, fallita.text).size)
        // La chiusura non cambia le altre fasi: una diretta in corso non parla di chiusura.
        val viva = DirettaStato(DirettaFase.IN_CORSO, gruppo = "Padel")
        assertEquals(app.getString(R.string.diretta_live, "Padel"), direttaLook(app, viva, ChiusuraFase.FALLITA)!!.text)
        assertNull(direttaLook(app, DirettaStato(), ChiusuraFase.FALLITA))
        assertNotNull(R.string.diretta_unavailable_old_match)
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "it")
    fun `in italiano la riga dice le parole del proprietario`() {
        val chiusa = DirettaStato(DirettaFase.CHIUSA, gruppo = "Padel", matchId = UUID_PARTITA)

        assertEquals("Chiusura della diretta in corso", direttaLook(app, chiusa, ChiusuraFase.IN_CORSO)!!.text)
        assertEquals(
            "La diretta non si è chiusa: la chiude la dashboard o si chiude da sola entro 6 ore",
            direttaLook(app, chiusa, ChiusuraFase.FALLITA)!!.text,
        )
    }
}
