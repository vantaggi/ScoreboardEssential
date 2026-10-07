package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Il tentativo di invio contro il contratto: cosa si ritenta (rete, 5xx, casella piena), cosa
 * no (file non valido, troppo grande), cosa chiede l'accesso, e che il ritentativo manda lo stesso file.
 */
@RunWith(AndroidJUnit4::class)
class InvioRunnerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var finto: ServerFinto? = null
    private lateinit var invii: InvioStore
    private var payload: PayloadOutcome = PayloadOutcome.Json(FILE_V2)

    @After
    fun chiudi() {
        finto?.chiudi()
    }

    /** Un runner con una sessione valida gia' salvata: i test dell'invio non ripassano dall'accesso. */
    private fun runner(invio: (RecordedRequest, Int) -> MockResponse): InvioRunner {
        val s =
            ServerFinto { richiesta, n ->
                if (richiesta.path!!.contains("refresh_token")) json(400, """{"error":"invalid_grant"}""") else invio(richiesta, n)
            }.also { finto = it }
        val store = SessionStore(preferenze(context, "sessione_runner"), SoftwareSecretBox())
        store.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), store)
        invii = InvioStore(preferenze(context, "invii_runner"))
        return InvioRunner(account, invii) { _, _ -> payload }
    }

    private fun stato() = invii.get(UUID_PARTITA)

    private val inviiAlServer get() = finto!!.quante("/rest/v1/rpc/submit_scoreboard_match")

    @Test
    fun `una consegna riuscita segna la partita come inviata e finisce`() =
        runBlocking {
            val runner = runner { _, _ -> rispostaDiInvio() }

            assertEquals(RunOutcome.DONE, runner.run(UUID_PARTITA, "g-1", 0))

            assertEquals(InvioInfo(InvioState.SENT), stato())
        }

    @Test
    fun `una voce gia' importata dall'admin si legge come importata`() =
        runBlocking {
            val runner = runner { _, _ -> rispostaDiInvio("imported", giaInviata = true) }

            assertEquals(RunOutcome.DONE, runner.run(UUID_PARTITA, "g-1", 0))

            assertEquals(InvioState.IMPORTED, stato()?.state)
        }

    @Test
    fun `already_submitted su una voce in attesa non e' un errore e non si riprova`() =
        runBlocking {
            val runner = runner { _, _ -> rispostaDiInvio("pending", giaInviata = true) }

            assertEquals(RunOutcome.DONE, runner.run(UUID_PARTITA, "g-1", 0))

            assertEquals(InvioState.SENT, stato()?.state)
            assertEquals(1, inviiAlServer)
        }

    @Test
    fun `un 5xx si ritenta e resta in coda`() =
        runBlocking {
            val runner = runner { _, _ -> json(503, "") }

            assertEquals(RunOutcome.RETRY, runner.run(UUID_PARTITA, "g-1", 0))

            assertEquals(InvioInfo(InvioState.QUEUED), stato())
        }

    @Test
    fun `senza rete si ritenta e resta in coda`() =
        runBlocking {
            val runner = runner { _, _ -> json(200, "") }
            finto!!.chiudi()

            assertEquals(RunOutcome.RETRY, runner.run(UUID_PARTITA, "g-1", 0))

            assertEquals(InvioState.QUEUED, stato()?.state)
        }

    @Test
    fun `la casella piena si ritenta e lo dice`() =
        runBlocking {
            val runner = runner { _, _ -> erroreRpc(500, "54000", "inbox_full", "user_pending") }

            assertEquals(RunOutcome.RETRY, runner.run(UUID_PARTITA, "g-1", 0))

            assertEquals(InvioInfo(InvioState.QUEUED, InvioReason.INBOX_FULL), stato())
        }

    @Test
    fun `un file non valido non si ritenta e dice dov'era il difetto`() =
        runBlocking {
            val runner = runner { _, _ -> erroreRpc(400, "22023", "invalid_payload", "matchId") }

            assertEquals(RunOutcome.DONE, runner.run(UUID_PARTITA, "g-1", 0))

            assertEquals(InvioInfo(InvioState.UNSENDABLE, InvioReason.INVALID_PAYLOAD, "matchId"), stato())
            assertEquals(1, inviiAlServer)
        }

    @Test
    fun `un file troppo grande per il server non si ritenta`() =
        runBlocking {
            val runner = runner { _, _ -> erroreRpc(500, "54000", "payload_too_large") }

            assertEquals(RunOutcome.DONE, runner.run(UUID_PARTITA, "g-1", 0))

            assertEquals(InvioInfo(InvioState.UNSENDABLE, InvioReason.TOO_LARGE), stato())
            assertEquals(1, inviiAlServer)
        }

    @Test
    fun `oltre 200 KB non si prova nemmeno la rete`() =
        runBlocking {
            payload = PayloadOutcome.Json("x".repeat(InvioRunner.MAX_PAYLOAD_BYTES + 1))
            val runner = runner { _, _ -> rispostaDiInvio() }

            assertEquals(RunOutcome.DONE, runner.run(UUID_PARTITA, "g-1", 0))

            assertEquals(InvioReason.TOO_LARGE, stato()?.reason)
            assertEquals(0, finto!!.richieste.size)
        }

    @Test
    fun `non essere piu' membro del gruppo e' non inviabile e non si ritenta`() =
        runBlocking {
            val runner = runner { _, _ -> erroreRpc(403, "42501", "not_authorized") }

            assertEquals(RunOutcome.DONE, runner.run(UUID_PARTITA, "g-1", 0))

            assertEquals(InvioInfo(InvioState.UNSENDABLE, InvioReason.NOT_MEMBER), stato())
        }

    @Test
    fun `not_authenticated chiede di accedere di nuovo e non si ritenta`() =
        runBlocking {
            val runner = runner { _, _ -> erroreRpc(403, "28000", "not_authenticated") }

            assertEquals(RunOutcome.DONE, runner.run(UUID_PARTITA, "g-1", 0))

            assertEquals(InvioInfo(InvioState.LOGIN_AGAIN), stato())
        }

    @Test
    fun `senza sessione chiede di accedere e non chiama la rete`() =
        runBlocking {
            val runner = runner { _, _ -> rispostaDiInvio() }
            SessionStore(context.getSharedPreferences("sessione_runner", Context.MODE_PRIVATE), SoftwareSecretBox()).clear()

            assertEquals(RunOutcome.DONE, runner.run(UUID_PARTITA, "g-1", 0))

            assertEquals(InvioState.LOGIN_AGAIN, stato()?.state)
            assertEquals(0, finto!!.richieste.size)
        }

    @Test
    fun `il ritentativo manda lo stesso identico file`() =
        runBlocking {
            val runner = runner { _, n -> if (n == 0) json(503, "") else rispostaDiInvio() }

            assertEquals(RunOutcome.RETRY, runner.run(UUID_PARTITA, "g-1", 0))
            assertEquals(RunOutcome.DONE, runner.run(UUID_PARTITA, "g-1", 1))

            val corpi = finto!!.richieste.filter { it.path!!.contains("submit") }.map { it.body.readUtf8() }
            assertEquals(2, corpi.size)
            assertEquals(corpi[0], corpi[1])
            assertEquals(InvioState.SENT, stato()?.state)
        }

    @Test
    fun `la riga della partita non ancora scritta si aspetta poi diventa non inviabile`() =
        runBlocking {
            payload = PayloadOutcome.Missing
            val runner = runner { _, _ -> rispostaDiInvio() }

            for (tentativo in 0 until InvioRunner.MAX_MISSING_ATTEMPTS) {
                assertEquals(RunOutcome.RETRY, runner.run(UUID_PARTITA, "g-1", tentativo))
                assertEquals(InvioState.QUEUED, stato()?.state)
            }
            assertEquals(RunOutcome.DONE, runner.run(UUID_PARTITA, "g-1", InvioRunner.MAX_MISSING_ATTEMPTS))

            assertEquals(InvioInfo(InvioState.UNSENDABLE, InvioReason.NO_FILE), stato())
            assertEquals(0, finto!!.richieste.size)
        }

    @Test
    fun `una partita senza file valido non parte`() =
        runBlocking {
            payload = PayloadOutcome.Incomplete
            val runner = runner { _, _ -> rispostaDiInvio() }

            assertEquals(RunOutcome.DONE, runner.run(UUID_PARTITA, "g-1", 0))

            assertEquals(InvioReason.NO_FILE, stato()?.reason)
        }

    @Test
    fun `una RPC che non c'e' ancora si ritenta poche volte e poi si ferma`() =
        runBlocking {
            val runner = runner { _, _ -> json(404, """{"code":"PGRST202","message":"Could not find the function"}""") }

            for (tentativo in 0 until InvioRunner.MAX_UNEXPECTED_ATTEMPTS) {
                assertEquals(RunOutcome.RETRY, runner.run(UUID_PARTITA, "g-1", tentativo))
            }
            assertEquals(RunOutcome.DONE, runner.run(UUID_PARTITA, "g-1", InvioRunner.MAX_UNEXPECTED_ATTEMPTS))

            assertEquals(InvioReason.SERVER, stato()?.reason)
        }

    @Test
    fun `lo stato delle partite in attesa si aggiorna dalla casella`() =
        runBlocking {
            val runner =
                runner { richiesta, _ ->
                    if (richiesta.path!!.startsWith(
                            "/rest/v1/v2_scoreboard_inbox",
                        )
                    ) {
                        json(200, """[{"status":"imported","match_id":"m-1"}]""")
                    } else {
                        rispostaDiInvio()
                    }
                }
            invii.set(UUID_PARTITA, InvioInfo(InvioState.SENT))
            invii.set("altra", InvioInfo(InvioState.UNSENDABLE, InvioReason.TOO_LARGE))

            runner.refreshStatuses()

            assertEquals(InvioState.IMPORTED, stato()?.state)
            // Chi non era in attesa non si tocca e non si interroga.
            assertEquals(InvioState.UNSENDABLE, invii.get("altra")?.state)
            assertEquals(1, finto!!.quante("/rest/v1/v2_scoreboard_inbox"))
        }

    @Test
    fun `una partita mai inviata non ha stato`() {
        runner { _, _ -> rispostaDiInvio() }
        assertNull(stato())
    }
}
