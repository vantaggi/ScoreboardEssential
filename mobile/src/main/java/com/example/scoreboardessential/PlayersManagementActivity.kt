package com.example.scoreboardessential

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.SearchView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AlertDialog
import android.view.View
import com.example.scoreboardessential.database.Player
import com.example.scoreboardessential.database.PlayerWithRoles
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class PlayersManagementActivity : AppCompatActivity() {

    private val viewModel: PlayersManagementViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PlayersManagementAdapter
    private lateinit var fab: FloatingActionButton
    private var searchQuery: String = ""
    private var selectedRoleIds = mutableListOf<Int>()
    private var currentDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_players_management)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Manage Players"
        }

        // Imposta il listener per i risultati del Fragment
        supportFragmentManager.setFragmentResultListener(RoleSelectionDialogFragment.REQUEST_KEY, this) { _, bundle ->
            val result = bundle.getIntegerArrayList(RoleSelectionDialogFragment.RESULT_KEY)
            if (result != null) {
                selectedRoleIds = result.toMutableList()
                // Aggiorna la UI del dialogo corrente (se esiste)
                currentDialog?.findViewById<ChipGroup>(R.id.roles_chip_group)?.let { chipGroup ->
                    updateRolesChipsInView(chipGroup)
                }
            }
        }

        initViews()
        setupRecyclerView()
        observeViewModel()
        setupSwipeToDelete()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.players_recyclerview)
        fab = findViewById(R.id.add_player_fab)
        fab.setOnClickListener { showCreatePlayerDialog() }
    }

    private fun setupRecyclerView() {
        adapter = PlayersManagementAdapter(
            onPlayerClick = { playerWithRoles -> showEditPlayerDialog(playerWithRoles) },
            onStatsClick = { playerWithRoles -> showPlayerStatsDialog(playerWithRoles) }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.players.collect { players ->
                val filteredPlayers = if (searchQuery.isEmpty()) {
                    players
                } else {
                    players.filter { playerWithRoles ->
                        playerWithRoles.player.playerName.contains(searchQuery, ignoreCase = true) ||
                                playerWithRoles.roles.any { it.name.contains(searchQuery, ignoreCase = true) }
                    }
                }
                adapter.submitList(filteredPlayers)
                findViewById<View>(R.id.empty_state).visibility = if (filteredPlayers.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val playerWithRoles = adapter.currentList[viewHolder.adapterPosition]
                viewModel.deletePlayer(playerWithRoles)
                Snackbar.make(recyclerView, "${playerWithRoles.player.playerName} deleted", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") {
                        viewModel.restorePlayer(playerWithRoles.player, playerWithRoles.roles.map { it.roleId })
                    }.show()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    private fun showCreatePlayerDialog() {
        selectedRoleIds.clear()
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_player, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.player_name_input)
        val rolesChipGroup = dialogView.findViewById<ChipGroup>(R.id.roles_chip_group)

        updateRolesChipsInView(rolesChipGroup)

    val selectRolesButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.select_roles_button)

    selectRolesButton.setOnClickListener {
        Log.d("PlayerManagement", "Select Roles button clicked.")
        try {
            val dialogFragment = RoleSelectionDialogFragment.newInstance(selectedRoleIds)
            dialogFragment.show(supportFragmentManager, "RoleSelectionDialog")
            Log.d("PlayerManagement", "RoleSelectionDialogFragment shown.")
        } catch (e: Exception) {
            Log.e("PlayerManagement", "Error showing RoleSelectionDialogFragment", e)
            Snackbar.make(fab, "Error opening roles selector.", Snackbar.LENGTH_LONG).show()
        }
        }

        currentDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Create New Player")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.createPlayer(name, selectedRoleIds)
                    Snackbar.make(fab, "Player created!", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditPlayerDialog(playerWithRoles: PlayerWithRoles) {
        selectedRoleIds = playerWithRoles.roles.map { it.roleId }.toMutableList()
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_player, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.player_name_input)
        val rolesChipGroup = dialogView.findViewById<ChipGroup>(R.id.roles_chip_group)
        nameInput.setText(playerWithRoles.player.playerName)
        updateRolesChipsInView(rolesChipGroup)

    val selectRolesButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.select_roles_button)

    selectRolesButton.setOnClickListener {
        Log.d("PlayerManagement", "Select Roles button clicked.")
        try {
            val dialogFragment = RoleSelectionDialogFragment.newInstance(selectedRoleIds)
            dialogFragment.show(supportFragmentManager, "RoleSelectionDialog")
            Log.d("PlayerManagement", "RoleSelectionDialogFragment shown.")
        } catch (e: Exception) {
            Log.e("PlayerManagement", "Error showing RoleSelectionDialogFragment", e)
            Snackbar.make(fab, "Error opening roles selector.", Snackbar.LENGTH_LONG).show()
        }
        }

        currentDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Edit Player")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    val updatedPlayer = playerWithRoles.player.copy(playerName = name)
                    viewModel.updatePlayer(updatedPlayer, selectedRoleIds)
                    Snackbar.make(fab, "Player updated!", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete") { _, _ -> showDeleteConfirmation(playerWithRoles) }
            .show()
    }

    private fun updateRolesChipsInView(chipGroup: ChipGroup) {
        chipGroup.removeAllViews()
        lifecycleScope.launch {
            viewModel.allRoles.collect { allRoles ->
                val selectedRoles = allRoles.filter { selectedRoleIds.contains(it.roleId) }
                runOnUiThread {
                    if (selectedRoles.isEmpty()) {
                        val chip = Chip(this@PlayersManagementActivity).apply {
                            text = "No roles selected"
                            isClickable = false
                        }
                        chipGroup.addView(chip)
                    } else {
                        selectedRoles.forEach { role ->
                            val chip = Chip(this@PlayersManagementActivity).apply {
                                text = role.name
                                isClickable = false
                            }
                            chipGroup.addView(chip)
                        }
                    }
                }
            }
        }
    }

    private fun showPlayerStatsDialog(playerWithRoles: PlayerWithRoles) {
        val player = playerWithRoles.player
        val rolesText = playerWithRoles.roles.joinToString(", ") { it.name }.ifEmpty { "Not specified" }
        val statsMessage = """
            Appearances: ${player.appearances}
            Goals Scored: ${player.goals}
            Goals per Match: ${if (player.appearances > 0) String.format("%.2f", player.goals.toFloat() / player.appearances) else "0.00"}
            
            Roles: $rolesText
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("${player.playerName} - Statistics")
            .setMessage(statsMessage)
            .setPositiveButton("OK", null)
            .setNeutralButton("Reset Stats") { _, _ -> showResetStatsConfirmation(playerWithRoles) }
            .show()
    }

    private fun showDeleteConfirmation(playerWithRoles: PlayerWithRoles) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Player?")
            .setMessage("Are you sure you want to delete ${playerWithRoles.player.playerName}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deletePlayer(playerWithRoles)
                Snackbar.make(fab, "Player deleted", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showResetStatsConfirmation(playerWithRoles: PlayerWithRoles) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Statistics?")
            .setMessage("Reset all statistics for ${playerWithRoles.player.playerName}?")
            .setPositiveButton("Reset") { _, _ ->
                val resetPlayer = playerWithRoles.player.copy(appearances = 0, goals = 0)
                viewModel.updatePlayer(resetPlayer, playerWithRoles.roles.map { it.roleId })
                Snackbar.make(fab, "Stats reset", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEmptyState() {
        findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.empty_state)
            ?.visibility = android.view.View.VISIBLE
    }

    private fun hideEmptyState() {
        findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.empty_state)
            ?.visibility = android.view.View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_players_management, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView

        searchView?.apply {
            queryHint = "Search players..."
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    searchQuery = newText ?: ""
                    observeViewModel() // Re-filter the list
                    return true
                }
            })
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_sort_name -> {
                viewModel.sortByName()
                true
            }
            R.id.action_sort_goals -> {
                viewModel.sortByGoals()
                true
            }
            R.id.action_sort_appearances -> {
                viewModel.sortByAppearances()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}