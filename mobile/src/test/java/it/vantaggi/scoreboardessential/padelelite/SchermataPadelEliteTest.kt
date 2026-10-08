package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La schermata di Padel Elite, dallo stato: accesso riuscito e fallito, i campi vuoti, i gruppi
 * (anche un solo gruppo, nessuno, la rete che manca) e uscire.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SchermataPadelEliteTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var finto: ServerFinto? = null

    @Before
    fun avvia() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun chiudi() {
        Dispatchers.resetMain()
        finto?.chiudi()
    }

    private fun account(risposte: (RecordedRequest, Int) -> MockResponse): PadelEliteAccount {
        val s = ServerFinto(risposte).also { finto = it }
        val store = SessionStore(preferenze(context, "sessione_schermata"), SoftwareSecretBox())
        return PadelEliteAccount(s.config(), PadelEliteApi(s.config()), store)
    }

    private fun viewModel(account: PadelEliteAccount) = PadelEliteViewModel(account)

    private fun gruppi(vararg righe: String) = json(200, righe.joinToString(prefix = "[", postfix = "]"))

    private fun riga(
        id: String,
        nome: String,
    ) = """{"group_id":"$id","role":"member","groups":{"name":"$nome"}}"""

    private fun accessoEGruppi(elenco: MockResponse) =
        { r: RecordedRequest, _: Int ->
            if (r.path!!.startsWith("/auth/")) json(200, rispostaDiSessione("A1", "R1", Long.MAX_VALUE / 2)) else elenco
        }

    @Test
    fun `i campi vuoti si dicono sotto il campo giusto senza chiamare la rete`() {
        val vm = viewModel(account(accessoEGruppi(gruppi())))

        vm.signIn("", "x")
        assertEquals(LoginProblem.EMAIL_EMPTY, vm.login.value.problem)
        vm.signIn("a@b.it", "")
        assertEquals(LoginProblem.PASSWORD_EMPTY, vm.login.value.problem)
        assertEquals(0, finto!!.richieste.size)
    }

    @Test
    fun `una password sbagliata torna al modulo con l'errore e senza invio in corso`() =
        runBlocking {
            val vm = viewModel(account { _, _ -> json(400, """{"error_code":"invalid_credentials"}""") })

            vm.signIn("a@b.it", "sbagliata")
            val stato = withTimeout(5_000) { vm.login.first { !it.sending && it.problem != null } }

            assertEquals(LoginProblem.INVALID_CREDENTIALS, stato.problem)
            assertNull(vm.signedInEmail.value)
        }

    @Test
    fun `senza rete l'accesso dice che manca la rete`() =
        runBlocking {
            val vm = viewModel(account { _, _ -> json(503, "") })

            vm.signIn("a@b.it", "x")

            assertEquals(LoginProblem.NETWORK, withTimeout(5_000) { vm.login.first { it.problem != null } }.problem)
        }

    @Test
    fun `un accesso riuscito collega l'utente e carica i gruppi`() =
        runBlocking {
            val vm = viewModel(account(accessoEGruppi(gruppi(riga("g-1", "Padel"), riga("g-2", "Calcetto")))))

            vm.signIn("a@b.it", "x")
            val email = withTimeout(5_000) { vm.signedInEmail.first { it != null } }
            val pronti = withTimeout(5_000) { vm.groups.first { it is GroupsUi.Ready } } as GroupsUi.Ready

            assertEquals("mattia@example.com", email)
            assertEquals(2, pronti.groups.size)
            // Con due gruppi la scelta e' dell'utente.
            assertNull(pronti.selectedId)
        }

    @Test
    fun `un solo gruppo si sceglie da solo e resta salvato`() =
        runBlocking {
            val account = account(accessoEGruppi(gruppi(riga("g-1", "Padel"))))
            val vm = viewModel(account)

            vm.signIn("a@b.it", "x")
            val pronti = withTimeout(5_000) { vm.groups.first { it is GroupsUi.Ready } } as GroupsUi.Ready

            assertEquals("g-1", pronti.selectedId)
            assertEquals("g-1" to "Padel", account.selectedGroup())
        }

    @Test
    fun `la scelta del gruppo si salva`() =
        runBlocking {
            val account = account(accessoEGruppi(gruppi(riga("g-1", "Padel"), riga("g-2", "Calcetto"))))
            val vm = viewModel(account)
            vm.signIn("a@b.it", "x")
            withTimeout(5_000) { vm.groups.first { it is GroupsUi.Ready } }

            vm.selectGroup(PadelEliteGroup("g-2", "Calcetto", "member"))

            assertEquals("g-2" to "Calcetto", account.selectedGroup())
            assertEquals("g-2", (vm.groups.value as GroupsUi.Ready).selectedId)
        }

    @Test
    fun `nessun gruppo e rete assente sono due stati diversi`() =
        runBlocking {
            val vuoto = viewModel(account(accessoEGruppi(gruppi())))
            vuoto.signIn("a@b.it", "x")
            withTimeout(5_000) { vuoto.groups.first { it == GroupsUi.Empty } }

            val senzaRete = viewModel(account(accessoEGruppi(json(503, ""))))
            senzaRete.signIn("a@b.it", "x")
            assertEquals(GroupsUi.Error, withTimeout(5_000) { senzaRete.groups.first { it == GroupsUi.Error } })
        }

    @Test
    fun `una sessione persa scoperta altrove riporta al modulo senza rifare richieste, anche se ripetuta`() =
        runBlocking {
            val vm = viewModel(account(accessoEGruppi(gruppi(riga("g-1", "Padel")))))
            vm.signIn("a@b.it", "x")
            withTimeout(5_000) { vm.groups.first { it is GroupsUi.Ready } }
            val richieste = finto!!.richieste.size

            vm.sessionLost()
            vm.sessionLost()

            assertNull(vm.signedInEmail.value)
            assertEquals(richieste, finto!!.richieste.size)
        }

    @Test
    fun `esci toglie utente e gruppo e riporta al modulo`() =
        runBlocking {
            val account = account(accessoEGruppi(gruppi(riga("g-1", "Padel"))))
            val vm = viewModel(account)
            vm.signIn("a@b.it", "x")
            withTimeout(5_000) { vm.groups.first { it is GroupsUi.Ready } }

            vm.signOut()

            assertNull(vm.signedInEmail.value)
            assertNull(account.session())
            assertNull(account.selectedGroup())
        }
}
