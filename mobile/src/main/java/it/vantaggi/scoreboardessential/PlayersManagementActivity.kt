package it.vantaggi.scoreboardessential

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import it.vantaggi.scoreboardessential.views.PlayersManagementViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlayersManagementActivity : AppCompatActivity() {
    private lateinit var viewModel: PlayersManagementViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PlayersManagementAdapter
    private lateinit var fab: FloatingActionButton
    private lateinit var rolesFilterChipGroup: ChipGroup
    private var searchQuery: String = ""

    private val addEditPlayerResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val playerName = data?.getStringExtra(AddEditPlayerActivity.EXTRA_PLAYER_NAME)
                val selectedRoleIds = data?.getIntegerArrayListExtra(AddEditPlayerActivity.EXTRA_SELECTED_ROLES)
                val playerId = data?.getIntExtra(AddEditPlayerActivity.EXTRA_PLAYER_ID, -1)

                if (playerName != null && selectedRoleIds != null) {
                    if (playerId != null && playerId != -1) {
                        // Editing existing player
                        lifecycleScope.launch {
                            val playerToUpdate = viewModel.getPlayer(playerId.toLong()).first()
                            playerToUpdate?.let {
                                val updatedPlayer = it.player.copy(playerName = playerName)
                                viewModel.updatePlayer(updatedPlayer, selectedRoleIds)
                            }
                        }
                    } else {
                        // Adding new player
                        viewModel.createPlayer(playerName, selectedRoleIds)
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_players_management)

        // Setup ViewModel using the factory
        val playerDao = AppDatabase.getDatabase(application).playerDao()
        val playerRepository = PlayerRepository(playerDao)
        val factory = PlayersManagementViewModelFactory(application, playerRepository)
        viewModel = ViewModelProvider(this, factory).get(PlayersManagementViewModel::class.java)

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
        rolesFilterChipGroup = findViewById(R.id.roles_filter_chip_group)
        fab.setOnClickListener {
            val createPlayerDialog = CreatePlayerDialogFragment()
            createPlayerDialog.show(supportFragmentManager, "CreatePlayerDialog")
        }
    }

    private fun setupRecyclerView() {
        adapter =
            PlayersManagementAdapter(
                onPlayerClick = { playerWithRoles ->
                    val intent =
                        Intent(this, AddEditPlayerActivity::class.java).apply {
                            putExtra(AddEditPlayerActivity.EXTRA_PLAYER, playerWithRoles.player)
                            putIntegerArrayListExtra(
                                AddEditPlayerActivity.EXTRA_SELECTED_ROLES,
                                ArrayList(playerWithRoles.roles.map { it.roleId }),
                            )
                        }
                    addEditPlayerResultLauncher.launch(intent)
                },
                onStatsClick = { playerWithRoles -> showPlayerStatsDialog(playerWithRoles) },
            )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.players.collect { players ->
                val filteredPlayers =
                    if (searchQuery.isEmpty()) {
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

        lifecycleScope.launch {
            viewModel.allRoles.collect { roles ->
                setupRoleFilterChips(roles)
            }
        }
    }

    private fun setupRoleFilterChips(roles: List<it.vantaggi.scoreboardessential.database.Role>) {
        rolesFilterChipGroup.removeAllViews()

        val inflater = android.view.LayoutInflater.from(this)

        // Add "All" chip
        val allChip =
            (inflater.inflate(R.layout.item_chip_filter, rolesFilterChipGroup, false) as Chip).apply {
                text = "All"
                id = View.generateViewId()
                isCheckable = true
                isChecked = true
            }
        rolesFilterChipGroup.addView(allChip)

        // Add role chips
        roles.forEach { role ->
            val chip =
                (inflater.inflate(R.layout.item_chip_filter, rolesFilterChipGroup, false) as Chip).apply {
                    text = role.name
                    id = View.generateViewId()
                    tag = role.roleId
                    isCheckable = true
                }
            rolesFilterChipGroup.addView(chip)
        }

        rolesFilterChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            if (checkedId == null) {
                viewModel.setRoleFilter(null)
                return@setOnCheckedStateChangeListener
            }

            val chip = group.findViewById<Chip>(checkedId)
            if (chip != null) {
                val roleId = if (chip.text == "All") null else chip.tag as? Int
                viewModel.setRoleFilter(roleId)
            } else {
                viewModel.setRoleFilter(null)
            }
        }
    }

    private fun setupSwipeToDelete() {
        val swipeHandler =
            object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                override fun onMove(
                    r: RecyclerView,
                    v: RecyclerView.ViewHolder,
                    t: RecyclerView.ViewHolder,
                ): Boolean = false

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int,
                ) {
                    val playerWithRoles = adapter.currentList[viewHolder.adapterPosition]
                    viewModel.deletePlayer(playerWithRoles)
                    Snackbar
                        .make(recyclerView, "${playerWithRoles.player.playerName} deleted", Snackbar.LENGTH_LONG)
                        .setAction("UNDO") {
                            viewModel.restorePlayer(playerWithRoles.player, playerWithRoles.roles.map { it.roleId })
                        }.show()
                }
            }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    private fun showPlayerStatsDialog(playerWithRoles: PlayerWithRoles) {
        val player = playerWithRoles.player
        val rolesText = playerWithRoles.roles.joinToString(", ") { it.name }.ifEmpty { "Not specified" }
        val statsMessage =
            """
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
            }.setNegativeButton("Cancel", null)
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
            }.setNegativeButton("Cancel", null)
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
            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean = false

                    override fun onQueryTextChange(newText: String?): Boolean {
                        searchQuery = newText ?: ""
                        observeViewModel() // Re-filter the list
                        return true
                    }
                },
            )
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
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
