package it.vantaggi.scoreboardessential

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.views.PlayersManagementViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AddEditPlayerActivity : AppCompatActivity() {
    private lateinit var viewModel: PlayersManagementViewModel
    private lateinit var roleAdapter: RoleSelectionAdapter
    private lateinit var playerNameInput: TextInputEditText
    private lateinit var padelPlayerIdInput: TextInputEditText
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
        padelPlayerIdInput = findViewById(R.id.padel_player_id_input)
        rolesRecyclerView = findViewById(R.id.roles_recycler_view)

        editingPlayerId = intent.getIntExtra(EXTRA_PLAYER_ID, -1)

        setupRecyclerView()
        observeRoles()

        if (editingPlayerId != -1) {
            supportActionBar?.title = "Edit Player"
            loadPlayer(editingPlayerId)
        } else {
            supportActionBar?.title = "Add Player"
        }
    }

    // Ricarica i dati dal DAO invece di riceverli dentro l'Intent
    private fun loadPlayer(playerId: Int) {
        lifecycleScope.launch {
            viewModel.getPlayer(playerId.toLong()).first()?.let {
                playerNameInput.setText(it.player.playerName)
                it.player.padelPlayerId?.let { padelId -> padelPlayerIdInput.setText(padelId.toString()) }
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_add_edit_player, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            R.id.action_save -> {
                val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createWaveform(HapticFeedbackManager.PATTERN_CONFIRM, -1))
                savePlayer()
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
        if (playerName.isEmpty()) {
            Toast.makeText(this, "Player name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (playerName.length > MAX_PLAYER_NAME_LENGTH) {
            Toast
                .makeText(
                    this,
                    "Player name is too long (max $MAX_PLAYER_NAME_LENGTH chars)",
                    Toast.LENGTH_SHORT,
                ).show()
            return
        }

        // Campo vuoto = nessun collegamento; zero e negativi non sono id di Padel Elite
        val padelIdText = padelPlayerIdInput.text.toString().trim()
        val padelPlayerId = padelIdText.toIntOrNull()
        if (padelIdText.isNotEmpty() && (padelPlayerId == null || padelPlayerId <= 0)) {
            Toast.makeText(this, "Padel Elite id must be a positive number", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedRoleIds = roleAdapter.getSelectedRoleIds()

        val resultIntent =
            Intent().apply {
                putExtra(EXTRA_PLAYER_NAME, playerName)
                putExtra(EXTRA_PADEL_PLAYER_ID, padelPlayerId ?: PADEL_PLAYER_ID_NONE)
                putIntegerArrayListExtra(EXTRA_SELECTED_ROLES, ArrayList(selectedRoleIds))
                if (editingPlayerId != -1) {
                    putExtra(EXTRA_PLAYER_ID, editingPlayerId)
                }
            }

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        const val EXTRA_PLAYER_ID = "extra_player_id"
        const val EXTRA_PLAYER_NAME = "extra_player_name"
        const val EXTRA_SELECTED_ROLES = "extra_selected_roles"

        /** Il collegamento e' opzionale: l'Intent non trasporta null, quindi lo dice questo valore. */
        const val EXTRA_PADEL_PLAYER_ID = "extra_padel_player_id"
        const val PADEL_PLAYER_ID_NONE = -1
        private const val MAX_PLAYER_NAME_LENGTH = 30
    }
}
