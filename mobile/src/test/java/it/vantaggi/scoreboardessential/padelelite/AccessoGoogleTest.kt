package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import android.net.Uri
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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Accedi con Google": PKCE, l'indirizzo di Supabase, il deep link di ritorno e lo scambio del
 * codice, contro un server finto. Nessuna chiamata al Supabase vero ne' a Google.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AccessoGoogleTest {
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
        val store = SessionStore(preferenze(context, "sessione_google"), SoftwareSecretBox())
        return PadelEliteAccount(s.config(), PadelEliteApi(s.config()), store)
    }

    private fun sessioneEGruppi() =
        { r: RecordedRequest, _: Int ->
            if (r.path!!.startsWith("/auth/")) json(200, rispostaDiSessione("A1", "R1", Long.MAX_VALUE / 2)) else json(200, "[]")
        }

    private fun parametro(
        indirizzo: String,
        nome: String,
    ) = Uri.parse(indirizzo).getQueryParameter(nome)

    @Test
    fun `la sfida e' lo SHA-256 in base64url senza uguali`() {
        // Calcolato a parte: python3 -c "base64.urlsafe_b64encode(hashlib.sha256(v).digest()).rstrip(b'=')".
        assertEquals("Xh-yQVYKAP4oM7Sj9p-Pfwdw7_oL0xbUbRCoNiw3lMk", AccessoGoogle.sfida("dBjftJeZ4CVP-mJ92K9b2ZRDlX0-1-3qR5fT1lFWwYI"))
    }

    @Test
    fun `il verificatore e' lungo 43 caratteri base64url e cambia ogni volta`() {
        val uno = AccessoGoogle.nuovoVerificatore()
        assertTrue(uno, uno.length == 43 && uno.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertFalse(uno == AccessoGoogle.nuovoVerificatore())
    }

    @Test
    fun `l'indirizzo chiede Google a Supabase col ritorno nell'app e la sfida s256`() {
        val indirizzo = AccessoGoogle.indirizzo(PadelEliteConfig("https://esempio.supabase.co/", "k"), "SFIDA")

        assertTrue(indirizzo, indirizzo.startsWith("https://esempio.supabase.co/auth/v1/authorize?"))
        assertEquals("google", parametro(indirizzo, "provider"))
        assertEquals("scoreboardessential://padel-elite/accesso", parametro(indirizzo, "redirect_to"))
        assertEquals("SFIDA", parametro(indirizzo, "code_challenge"))
        assertEquals("s256", parametro(indirizzo, "code_challenge_method"))
    }

    @Test
    fun `il ritorno legge il codice, l'annullamento e l'errore, e ignora gli altri link`() {
        assertEquals(RitornoGoogle.Codice("c-1"), RitornoGoogle.da(Uri.parse("scoreboardessential://padel-elite/accesso?code=c-1")))
        assertEquals(
            RitornoGoogle.Annullato,
            RitornoGoogle.da(Uri.parse("scoreboardessential://padel-elite/accesso?error=access_denied&error_description=x")),
        )
        assertEquals(RitornoGoogle.Errore, RitornoGoogle.da(Uri.parse("scoreboardessential://padel-elite/accesso#error=server_error")))
        assertEquals(RitornoGoogle.Errore, RitornoGoogle.da(Uri.parse("scoreboardessential://padel-elite/accesso")))
        assertNull(RitornoGoogle.da(Uri.parse("scoreboardessential://padel-elite/altro?code=c-1")))
        assertNull(RitornoGoogle.da(Uri.parse("https://padel-elite/accesso?code=c-1")))
        assertNull(RitornoGoogle.da(null))
    }

    @Test
    fun `lo scambio manda codice e verificatore con grant pkce e salva la sessione`() =
        runBlocking {
            val account = account(sessioneEGruppi())
            val indirizzo = account.startGoogleSignIn()

            val esito = account.signInWithGoogle("c-1")

            assertTrue(esito is AuthResult.Ok)
            assertEquals("A1", account.session()?.accessToken)
            val richiesta = finto!!.richieste.single()
            assertEquals("/auth/v1/token?grant_type=pkce", richiesta.path)
            val corpo = JSONObject(richiesta.body.readUtf8())
            assertEquals("c-1", corpo.getString("auth_code"))
            // Il verificatore mandato e' proprio quello di cui l'indirizzo portava l'impronta.
            assertEquals(parametro(indirizzo, "code_challenge"), AccessoGoogle.sfida(corpo.getString("code_verifier")))
        }

    @Test
    fun `un codice senza verificatore salvato non parte e uno gia' usato non vale due volte`() =
        runBlocking {
            val account = account(sessioneEGruppi())

            assertEquals(AuthResult.Rejected(AuthFailure.OTHER), account.signInWithGoogle("c-1"))
            assertEquals(0, finto!!.richieste.size)

            account.startGoogleSignIn()
            account.signInWithGoogle("c-1")
            assertEquals(AuthResult.Rejected(AuthFailure.OTHER), account.signInWithGoogle("c-1"))
            assertEquals(1, finto!!.richieste.size)
        }

    @Test
    fun `un codice rifiutato non e' una password sbagliata`() =
        runBlocking {
            val account = account { _, _ -> json(400, """{"error_code":"invalid_grant","msg":"invalid flow state"}""") }
            account.startGoogleSignIn()

            assertEquals(AuthResult.Rejected(AuthFailure.OTHER), account.signInWithGoogle("c-1"))
            assertNull(account.session())
        }

    @Test
    fun `dalla schermata il ritorno con il codice collega l'utente`() =
        runBlocking {
            val account = account(sessioneEGruppi())
            val vm = PadelEliteViewModel(account)

            vm.startGoogle()
            vm.completeGoogle(RitornoGoogle.Codice("c-1"))

            assertEquals("mattia@example.com", withTimeout(5_000) { vm.signedInEmail.first { it != null } })
            assertEquals(LoginUi(), vm.login.value)
        }

    @Test
    fun `chiudere la pagina di Google non mostra errori, un ritorno rifiutato si`() =
        runBlocking {
            val vm = PadelEliteViewModel(account { _, _ -> json(400, """{"error_code":"invalid_grant"}""") })

            vm.startGoogle()
            vm.completeGoogle(RitornoGoogle.Annullato)
            assertEquals(LoginUi(), vm.login.value)

            vm.completeGoogle(RitornoGoogle.Errore)
            assertEquals(LoginProblem.GOOGLE_FAILED, vm.login.value.problem)

            vm.startGoogle()
            vm.completeGoogle(RitornoGoogle.Codice("c-1"))
            val stato = withTimeout(5_000) { vm.login.first { !it.sending && it.problem != null } }
            assertEquals(LoginProblem.GOOGLE_FAILED, stato.problem)
            assertNull(vm.signedInEmail.value)
        }
}
