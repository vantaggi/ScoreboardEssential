package com.example.scoreboardessential

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.SearchView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scoreboardessential.database.Player
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_players_management)

        // Setup Toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Manage Players"
        }

        initViews()
        setupRecyclerView()
        observeViewModel()
        setupSwipeToDelete()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.players_recyclerview)
        fab = findViewById(R.id.add_player_fab)

        fab.setOnClickListener {
            showCreatePlayerDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = PlayersManagementAdapter(
            onPlayerClick = { player ->
                showEditPlayerDialog(player)
            },
            onStatsClick = { player ->
                showPlayerStatsDialog(player)
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlayersManagementActivity)
            adapter = this@PlayersManagementActivity.adapter
            setHasFixedSize(true)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.players.collect { players ->
                val filteredPlayers = if (searchQuery.isEmpty()) {
                    players
                } else {
                    players.filter {
                        it.playerName.contains(searchQuery, ignoreCase = true) ||
                                it.roles.contains(searchQuery, ignoreCase = true)
                    }
                }
                adapter.submitList(filteredPlayers)

                // Show empty state if no players
                if (filteredPlayers.isEmpty()) {
                    showEmptyState()
                } else {
                    hideEmptyState()
                }
            }
        }
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val player = adapter.currentList[position]

                // Delete the player
                viewModel.deletePlayer(player)

                // Show undo snackbar
                Snackbar.make(
                    recyclerView,
                    "${player.playerName} deleted",
                    Snackbar.LENGTH_LONG
                ).setAction("UNDO") {
                    viewModel.restorePlayer(player)
                }.show()
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    private fun showCreatePlayerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_player, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.player_name_input)
        val rolesInput = dialogView.findViewById<TextInputEditText>(R.id.player_roles_input)

        MaterialAlertDialogBuilder(this)
            .setTitle("Create New Player")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString().trim()
                val roles = rolesInput.text.toString().trim()

                if (name.isNotEmpty()) {
                    viewModel.createPlayer(name, roles)
                    Snackbar.make(fab, "Player created!", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(fab, "Please enter a name", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditPlayerDialog(player: Player) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_player, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.player_name_input)
        val rolesInput = dialogView.findViewById<TextInputEditText>(R.id.player_roles_input)

        // Pre-fill with current values
        nameInput.setText(player.playerName)
        rolesInput.setText(player.roles)

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Player")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val roles = rolesInput.text.toString().trim()

                if (name.isNotEmpty()) {
                    val updatedPlayer = player.copy(
                        playerName = name,
                        roles = roles
                    )
                    viewModel.updatePlayer(updatedPlayer)
                    Snackbar.make(fab, "Player updated!", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete") { _, _ ->
                showDeleteConfirmation(player)
            }
            .show()
    }

    private fun showPlayerStatsDialog(player: Player) {
        val statsMessage = """
            Appearances: ${player.appearances}
            Goals Scored: ${player.goals}
            Goals per Match: ${if (player.appearances > 0)
            String.format("%.2f", player.goals.toFloat() / player.appearances)
        else "0.00"}
            
            Roles: ${player.roles.ifEmpty { "Not specified" }}
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("${player.playerName} - Statistics")
            .setMessage(statsMessage)
            .setPositiveButton("OK", null)
            .setNeutralButton("Reset Stats") { _, _ ->
                showResetStatsConfirmation(player)
            }
            .show()
    }

    private fun showDeleteConfirmation(player: Player) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Player?")
            .setMessage("Are you sure you want to delete ${player.playerName}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deletePlayer(player)
                Snackbar.make(fab, "Player deleted", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showResetStatsConfirmation(player: Player) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Statistics?")
            .setMessage("Reset all statistics for ${player.playerName}?")
            .setPositiveButton("Reset") { _, _ ->
                val resetPlayer = player.copy(
                    appearances = 0,
                    goals = 0
                )
                viewModel.updatePlayer(resetPlayer)
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