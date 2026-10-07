package it.vantaggi.scoreboardessential

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.ui.ProgressButton
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
    }

    // Ricarica i dati dal DAO invece di riceverli dentro l'Intent
    private fun loadPlayer(playerId: Int) {
        lifecycleScope.launch {
            viewModel.getPlayer(playerId.toLong()).first()?.let {
                playerNameInput.setText(it.player.playerName)
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
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
