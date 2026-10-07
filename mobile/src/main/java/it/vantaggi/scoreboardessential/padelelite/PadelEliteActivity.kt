package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import kotlinx.coroutines.launch

/**
 * "Padel Elite": accesso con email e password, scelta del gruppo, esci. Si raggiunge dalle
 * impostazioni e dal comando "Invia a Padel Elite" quando manca l'accesso o il gruppo.
 *
 * Stati del modulo: vuoto, invio in corso (il bottone resta largo uguale e cambia solo la parola),
 * errore sotto il campo o in una riga con icona, riuscito (si passa al pannello dell'account).
 * La funzione spenta non arriva fin qui: chi apre la schermata ha gia' controllato la configurazione.
 */
class PadelEliteActivity : AppCompatActivity() {
    private val viewModel: PadelEliteViewModel by lazy {
        val account = (application as ScoreboardEssentialApplication).padelElite.account
        androidx.lifecycle.ViewModelProvider(this, PadelEliteViewModelFactory(account))[PadelEliteViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_padel_elite)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val emailLayout = findViewById<TextInputLayout>(R.id.email_layout)
        val passwordLayout = findViewById<TextInputLayout>(R.id.password_layout)
        val emailEdit = findViewById<TextInputEditText>(R.id.email_edit)
        val passwordEdit = findViewById<TextInputEditText>(R.id.password_edit)
        val loginButton = findViewById<MaterialButton>(R.id.login_button)
        val loginMessage = findViewById<TextView>(R.id.login_message)

        fun accedi() = viewModel.signIn(emailEdit.text?.toString().orEmpty(), passwordEdit.text?.toString().orEmpty())
        loginButton.setOnClickListener { accedi() }
        passwordEdit.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                accedi()
                true
            } else {
                false
            }
        }
        findViewById<View>(R.id.logout_button).setOnClickListener { viewModel.signOut() }
        findViewById<View>(R.id.groups_retry_button).setOnClickListener { viewModel.loadGroups() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.login.collect { stato ->
                        val inCorso = stato.sending
                        loginButton.isEnabled = !inCorso
                        emailEdit.isEnabled = !inCorso
                        passwordEdit.isEnabled = !inCorso
                        loginButton.setText(if (inCorso) R.string.padel_elite_signing_in else R.string.padel_elite_sign_in)
                        val testo = stato.problem?.let { getString(testoDelProblema(it)) }
                        // Un errore di campo sta sotto il campo, gli altri in una riga con icona.
                        emailLayout.error = testo.takeIf { stato.problem in EMAIL_PROBLEMS }
                        passwordLayout.error = testo.takeIf { stato.problem in PASSWORD_PROBLEMS }
                        loginMessage.isVisible = stato.problem != null && stato.problem !in EMAIL_PROBLEMS + PASSWORD_PROBLEMS
                        loginMessage.text = if (loginMessage.isVisible) testo else null
                    }
                }
                launch {
                    viewModel.signedInEmail.collect { email ->
                        findViewById<View>(R.id.login_panel).isVisible = email == null
                        findViewById<View>(R.id.account_panel).isVisible = email != null
                        findViewById<TextView>(R.id.account_email).text = email?.let { getString(R.string.padel_elite_signed_in_as, it) }
                    }
                }
                launch { viewModel.groups.collect { mostraGruppi(it) } }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun mostraGruppi(stato: GroupsUi) {
        val elenco = findViewById<RadioGroup>(R.id.group_list)
        val messaggio = findViewById<TextView>(R.id.groups_message)
        val riprova = findViewById<View>(R.id.groups_retry_button)
        elenco.setOnCheckedChangeListener(null)
        elenco.removeAllViews()
        riprova.isVisible = stato == GroupsUi.Error
        messaggio.isVisible = stato !is GroupsUi.Ready
        messaggio.setText(
            when (stato) {
                GroupsUi.Loading -> R.string.padel_elite_groups_loading
                GroupsUi.Empty -> R.string.padel_elite_groups_empty
                GroupsUi.Error -> R.string.padel_elite_groups_error
                is GroupsUi.Ready -> R.string.padel_elite_groups_loading
            },
        )
        if (stato !is GroupsUi.Ready) return
        stato.groups.forEach { gruppo ->
            val riga =
                (layoutInflater.inflate(R.layout.item_padel_elite_group, elenco, false) as RadioButton).apply {
                    id = View.generateViewId()
                    tag = gruppo
                    text = getString(R.string.padel_elite_group_row, gruppo.name, nomeDelRuolo(gruppo.role))
                    isChecked = gruppo.id == stato.selectedId
                }
            elenco.addView(riga)
        }
        elenco.setOnCheckedChangeListener { gruppo, id ->
            (gruppo.findViewById<View>(id)?.tag as? PadelEliteGroup)?.let(viewModel::selectGroup)
        }
    }

    private fun nomeDelRuolo(ruolo: String): String =
        when (ruolo) {
            "owner" -> getString(R.string.padel_elite_role_owner)
            "admin" -> getString(R.string.padel_elite_role_admin)
            else -> getString(R.string.padel_elite_role_member)
        }

    private fun testoDelProblema(problema: LoginProblem): Int =
        when (problema) {
            LoginProblem.EMAIL_EMPTY -> R.string.padel_elite_error_email_empty
            LoginProblem.PASSWORD_EMPTY -> R.string.padel_elite_error_password_empty
            LoginProblem.INVALID_CREDENTIALS -> R.string.padel_elite_error_invalid_credentials
            LoginProblem.EMAIL_NOT_CONFIRMED -> R.string.padel_elite_error_email_not_confirmed
            LoginProblem.RATE_LIMITED -> R.string.padel_elite_error_rate_limited
            LoginProblem.NETWORK -> R.string.padel_elite_error_network
            LoginProblem.OTHER -> R.string.padel_elite_error_other
        }

    companion object {
        private val EMAIL_PROBLEMS = setOf(LoginProblem.EMAIL_EMPTY, LoginProblem.EMAIL_NOT_CONFIRMED)
        private val PASSWORD_PROBLEMS = setOf(LoginProblem.PASSWORD_EMPTY, LoginProblem.INVALID_CREDENTIALS)

        fun intent(context: Context) = Intent(context, PadelEliteActivity::class.java)
    }
}
