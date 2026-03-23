package it.vantaggi.scoreboardessential

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import it.vantaggi.scoreboardessential.views.PlayersManagementViewModelFactory
import kotlinx.coroutines.launch
import android.os.Vibrator
import android.os.VibrationEffect
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager

class AddEditPlayerActivity : AppCompatActivity() {
    private lateinit var viewModel: PlayersManagementViewModel
    private lateinit var roleAdapter: RoleSelectionAdapter
    private lateinit var playerNameInput: TextInputEditText
    private lateinit var rolesRecyclerView: RecyclerView
    private lateinit var toolbar: Toolbar

    private var playerToEdit: Player? = null

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
        rolesRecyclerView = findViewById(R.id.roles_recycler_view)

        playerToEdit = intent.getParcelableExtra(EXTRA_PLAYER)

        setupRecyclerView()
        observeRoles()

        if (playerToEdit != null) {
            supportActionBar?.title = "Edit Player"
            playerNameInput.setText(playerToEdit?.playerName)
        } else {
            supportActionBar?.title = "Add Player"
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
            else -> super.onOptionsItemSelected(item)
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

        val selectedRoleIds = roleAdapter.getSelectedRoleIds()

        val resultIntent =
            Intent().apply {
                putExtra(EXTRA_PLAYER_NAME, playerName)
                putIntegerArrayListExtra(EXTRA_SELECTED_ROLES, ArrayList(selectedRoleIds))
                playerToEdit?.let {
                    putExtra(EXTRA_PLAYER_ID, it.playerId)
                }
            }

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        const val EXTRA_PLAYER = "extra_player"
        const val EXTRA_PLAYER_ID = "extra_player_id"
        const val EXTRA_PLAYER_NAME = "extra_player_name"
        const val EXTRA_SELECTED_ROLES = "extra_selected_roles"
        private const val MAX_PLAYER_NAME_LENGTH = 30
    }
}
