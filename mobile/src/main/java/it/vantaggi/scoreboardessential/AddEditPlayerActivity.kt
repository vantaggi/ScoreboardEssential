package it.vantaggi.scoreboardessential

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.ui.ProgressButton
import it.vantaggi.scoreboardessential.ui.statistics.StatisticsViewModel
import it.vantaggi.scoreboardessential.views.PlayersManagementViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AddEditPlayerActivity : AppCompatActivity() {
    private lateinit var viewModel: PlayersManagementViewModel
    private lateinit var roleAdapter: RoleSelectionAdapter
    private lateinit var playerNameInput: TextInputEditText
    private lateinit var playerNameLayout: TextInputLayout
    private lateinit var saveButton: ProgressButton
    private lateinit var rolesRecyclerView: RecyclerView
    private lateinit var toolbar: Toolbar

    // L'Intent trasporta solo l'id: le entita' Room non sono piu' oggetti di trasporto per la UI
    private var editingPlayerId: Int = -1

    // Il giocatore che si modifica, letto dal database: serve ad "Azzera le statistiche".
    private var giocatore: PlayerWithRoles? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_player)

        val playerDao = AppDatabase.getDatabase(application).playerDao()
        val playerRepository = PlayerRepository(playerDao)
        val factory = PlayersManagementViewModelFactory(application, playerRepository)
        viewModel = ViewModelProvider(this, factory).get(PlayersManagementViewModel::class.java)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        playerNameInput = findViewById(R.id.player_name_input)
        playerNameLayout = findViewById(R.id.player_name_layout)
        saveButton = findViewById(R.id.save_player_button)
        saveButton.loadingLabel = getString(R.string.saving_player)
        saveButton.setOnClickListener {
            val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createWaveform(HapticFeedbackManager.PATTERN_CONFIRM, -1))
            savePlayer()
        }
        // Chi corregge il nome vede sparire l'errore: il testo scritto resta e l'errore no.
        playerNameInput.doAfterTextChanged { playerNameLayout.error = null }
        rolesRecyclerView = findViewById(R.id.roles_recycler_view)

        editingPlayerId = intent.getIntExtra(EXTRA_PLAYER_ID, -1)

        setupRecyclerView()
        observeRoles()

        if (editingPlayerId != -1) {
            supportActionBar?.title = getString(R.string.title_edit_player)
            loadPlayer(editingPlayerId)
        } else {
            supportActionBar?.title = getString(R.string.label_add_player)
        }

        // I ruoli sono del calcio: negli altri sport la pagina e' il nome e basta. I ruoli gia'
        // scelti restano salvati (getSelectedRoleIds li tiene anche con la lista nascosta).
        lifecycleScope.launch {
            val sportId = (application as ScoreboardEssentialApplication).matchSettingsRepository.getActiveSport()
            val ruoli = if (StatisticsViewModel.contaIGol(sportId)) View.VISIBLE else View.GONE
            findViewById<View>(R.id.roles_title).visibility = ruoli
            rolesRecyclerView.visibility = ruoli
        }
    }

    // Ricarica i dati dal DAO invece di riceverli dentro l'Intent
    private fun loadPlayer(playerId: Int) {
        lifecycleScope.launch {
            viewModel.getPlayer(playerId.toLong()).first()?.let {
                // Solo alla prima lettura: dopo l'azzeramento il nome scritto resta quello che c'e'.
                if (giocatore == null) playerNameInput.setText(it.player.playerName)
                giocatore = it
                invalidateOptionsMenu()
            }
        }
    }

    private fun setupRecyclerView() {
        roleAdapter = RoleSelectionAdapter { _, _ -> }
        rolesRecyclerView.adapter = roleAdapter
        rolesRecyclerView.layoutManager = LinearLayoutManager(this@AddEditPlayerActivity)
    }

    private fun observeRoles() {
        lifecycleScope.launch {
            viewModel.allRoles.collect { roles ->
                val selectedRoleIds = intent.getIntegerArrayListExtra(EXTRA_SELECTED_ROLES) ?: emptyList<Int>()
                roleAdapter.submitList(roles, selectedRoleIds)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_edit_player, menu)
        return true
    }

    /** "Azzera le statistiche" c'e' solo per un giocatore che esiste e ha qualcosa da azzerare. */
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val player = giocatore?.player
        menu.findItem(R.id.action_reset_stats)?.isVisible = player != null && (player.appearances > 0 || player.goals > 0)
        return super.onPrepareOptionsMenu(menu)
    }

    private fun confermaAzzeramento(playerWithRoles: PlayerWithRoles) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_stats_title)
            .setMessage(getString(R.string.reset_stats_message, playerWithRoles.player.playerName))
            .setPositiveButton(R.string.reset_stats_confirm) { _, _ ->
                lifecycleScope.launch {
                    viewModel.resetStats(playerWithRoles)
                    Snackbar.make(saveButton, R.string.stats_reset, Snackbar.LENGTH_SHORT).show()
                    loadPlayer(editingPlayerId)
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            R.id.action_reset_stats -> {
                giocatore?.let { confermaAzzeramento(it) }
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }

    private fun savePlayer() {
        val playerName =
            playerNameInput.text
                .toString()
                .trim()
                .replace("\\s+".toRegex(), " ")
        // L'errore dice cosa e' successo e sta sotto il campo, che tiene quello che l'utente ha scritto.
        if (playerName.isEmpty()) {
            playerNameLayout.error = getString(R.string.player_name_empty)
            return
        }

        if (playerName.length > MAX_PLAYER_NAME_LENGTH) {
            playerNameLayout.error = getString(R.string.player_name_too_long, MAX_PLAYER_NAME_LENGTH)
            return
        }

        val selectedRoleIds = roleAdapter.getSelectedRoleIds()

        val resultIntent =
            Intent().apply {
                putExtra(EXTRA_PLAYER_NAME, playerName)
                putIntegerArrayListExtra(EXTRA_SELECTED_ROLES, ArrayList(selectedRoleIds))
                if (editingPlayerId != -1) {
                    putExtra(EXTRA_PLAYER_ID, editingPlayerId)
                }
            }

        // Il bottone passa a "in corso" prima di chiudere: lo stato non salta, resta lo stesso bottone.
        saveButton.setLoading(true)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        const val EXTRA_PLAYER_ID = "extra_player_id"
        const val EXTRA_PLAYER_NAME = "extra_player_name"
        const val EXTRA_SELECTED_ROLES = "extra_selected_roles"
        private const val MAX_PLAYER_NAME_LENGTH = 30
    }
}
