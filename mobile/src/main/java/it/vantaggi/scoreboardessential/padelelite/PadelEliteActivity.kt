package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import it.vantaggi.scoreboardessential.ui.EmptyStateView
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

    /** I giocatori del gruppo scelto e il loro collegamento con quelli dell'app (R-1). */
    private val rosaViewModel: RosaGruppoViewModel by lazy {
        val app = application as ScoreboardEssentialApplication
        val rosa = RosaGruppo(app.database.padelEliteLinkDao(), app.database.playerDao())
        androidx.lifecycle.ViewModelProvider(
            this,
            RosaGruppoViewModelFactory(app.padelElite.account, rosa) { viewModel.sessionLost() },
        )[RosaGruppoViewModel::class.java]
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
        findViewById<View>(R.id.logout_button).setOnClickListener {
            rosaViewModel.reset()
            viewModel.signOut()
        }
        findViewById<View>(R.id.groups_retry_button).setOnClickListener { viewModel.loadGroups() }
        findViewById<View>(R.id.rosa_refresh_button).setOnClickListener { rosaViewModel.load() }
        findViewById<View>(R.id.rosa_same_names_button).setOnClickListener { rosaViewModel.linkSameNames() }
        // Vuota e in errore rileggono la rosa; "non sei piu' membro" rilegge i gruppi (quello scelto cade e se ne sceglie un altro).
        findViewById<EmptyStateView>(R.id.rosa_empty_state).actionButton.setOnClickListener {
            if (rosaViewModel.state.value == RosaUi.NotAuthorized) viewModel.loadGroups() else rosaViewModel.load()
        }

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
                launch {
                    viewModel.groups.collect { stato ->
                        mostraGruppi(stato)
                        // La rosa si vede solo con i gruppi caricati, e si rilegge se il gruppo scelto cambia.
                        findViewById<View>(R.id.rosa_section).isVisible = stato is GroupsUi.Ready
                        if (stato is GroupsUi.Ready) rosaViewModel.loadIfNeeded()
                    }
                }
                launch { rosaViewModel.state.collect { mostraRosa(it) } }
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
            // La linea sottile fra una riga e l'altra, rientrata fino al bordo del testo (G-2, divisore con inset).
            if (elenco.isNotEmpty()) elenco.addView(rigaDivisoria())
            elenco.addView(riga)
        }
        elenco.setOnCheckedChangeListener { gruppo, id ->
            (gruppo.findViewById<View>(id)?.tag as? PadelEliteGroup)?.let(viewModel::selectGroup)
        }
    }

    /**
     * "Giocatori del gruppo". Stati: scegli un gruppo, in caricamento (una riga di testo), vuota e
     * errore di rete (EmptyStateView col suo comando), pronta (un gruppo tonale con una riga per
     * giocatore). La sessione scaduta riporta al modulo di accesso.
     */
    private fun mostraRosa(stato: RosaUi) {
        val messaggio = findViewById<TextView>(R.id.rosa_message)
        val vuoto = findViewById<EmptyStateView>(R.id.rosa_empty_state)
        val gruppo = findViewById<View>(R.id.rosa_group)
        val elenco = findViewById<LinearLayout>(R.id.rosa_list)
        val stessiNomi = findViewById<MaterialButton>(R.id.rosa_same_names_button)
        val aggiorna = findViewById<View>(R.id.rosa_refresh_button)

        messaggio.isVisible = stato == RosaUi.NoGroup || stato == RosaUi.Loading
        messaggio.setText(if (stato == RosaUi.NoGroup) R.string.padel_elite_rosa_no_group else R.string.padel_elite_rosa_loading)
        vuoto.isVisible = stato == RosaUi.Empty || stato == RosaUi.Error || stato == RosaUi.NotAuthorized
        when (stato) {
            RosaUi.Empty -> {
                vuoto.setMessage(getString(R.string.padel_elite_rosa_empty_title), getString(R.string.padel_elite_rosa_empty_reason))
                vuoto.actionButton.setText(R.string.padel_elite_rosa_refresh)
            }

            RosaUi.Error -> {
                vuoto.setMessage(getString(R.string.padel_elite_rosa_error_title), getString(R.string.padel_elite_rosa_error_reason))
                vuoto.actionButton.setText(R.string.padel_elite_retry)
            }

            RosaUi.NotAuthorized -> {
                vuoto.setMessage(
                    getString(R.string.padel_elite_rosa_forbidden_title),
                    getString(R.string.padel_elite_rosa_forbidden_reason),
                )
                vuoto.actionButton.setText(R.string.padel_elite_rosa_forbidden_action)
            }

            // La sessione scaduta la gestisce il ViewModel (una volta sola): qui non c'e' niente da disegnare.
            else -> {
                Unit
            }
        }
        gruppo.isVisible = stato is RosaUi.Ready
        aggiorna.isVisible = stato is RosaUi.Ready
        elenco.removeAllViews()
        stessiNomi.isVisible = stato is RosaUi.Ready && stato.proposals > 0
        if (stato !is RosaUi.Ready) return

        stessiNomi.text = getString(R.string.padel_elite_rosa_same_names, stato.proposals)
        stato.rows.forEach { riga ->
            if (elenco.isNotEmpty()) elenco.addView(divisoreDellaRosa())
            val vista = layoutInflater.inflate(R.layout.item_padel_elite_player, elenco, false)
            vista.findViewById<TextView>(R.id.rosa_remote_name).text = riga.remote.name
            val stati = vista.findViewById<TextView>(R.id.rosa_link_state)
            when {
                riga.linkedLocalName != null -> {
                    stati.text = getString(R.string.padel_elite_rosa_linked, riga.linkedLocalName)
                    // Collegato: la parola e la spunta (16dp, tinta del testo), mai il solo colore.
                    val spunta = AppCompatResources.getDrawable(this, R.drawable.ic_check)?.mutate()
                    val lato = resources.getDimensionPixelSize(R.dimen.icon_compact)
                    spunta?.setBounds(0, 0, lato, lato)
                    stati.setCompoundDrawablesRelative(spunta, null, null, null)
                    TextViewCompat.setCompoundDrawableTintList(stati, stati.textColors)
                }

                riga.proposedLocalName != null -> {
                    stati.text = getString(R.string.padel_elite_rosa_proposed, riga.proposedLocalName)
                    stati.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
                }

                else -> {
                    stati.setText(R.string.padel_elite_rosa_not_linked)
                    stati.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
                }
            }
            vista.setOnClickListener { scegliGiocatore(riga, stato.freeLocals) }
            elenco.addView(vista)
        }
    }

    /** Il giocatore dell'app per un giocatore della dashboard: crearlo, scollegarlo, oppure sceglierne uno non ancora collegato. */
    private fun scegliGiocatore(
        riga: RosaRow,
        locali: List<LocalChoice>,
    ) {
        val voci = mutableListOf(getString(R.string.padel_elite_rosa_create_local, riga.remote.name))
        val azioni = mutableListOf<() -> Unit>({ rosaViewModel.createLocal(riga.remote.id) })
        riga.linkedLocalName?.let {
            voci += getString(R.string.padel_elite_rosa_unlink, it)
            azioni += { rosaViewModel.unlink(riga.remote.id) }
        }
        locali.forEach { locale ->
            voci += locale.name
            azioni += { rosaViewModel.link(riga.remote.id, locale.id) }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.padel_elite_rosa_dialog_title, riga.remote.name))
            .setItems(voci.toTypedArray()) { _, posizione -> azioni[posizione]() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** La linea sottile fra due righe della rosa, rientrata come quella dei gruppi. */
    private fun divisoreDellaRosa(): View {
        val parametri =
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.border_width))
        parametri.marginStart = resources.getDimensionPixelSize(R.dimen.space_16)
        return View(this).apply {
            setBackgroundColor(getColor(R.color.elite_border_strong))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = parametri
        }
    }

    private fun rigaDivisoria(): View {
        val parametri =
            RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.border_width))
        parametri.marginStart = resources.getDimensionPixelSize(R.dimen.space_32)
        return View(this).apply {
            setBackgroundColor(getColor(R.color.elite_border_strong))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = parametri
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
