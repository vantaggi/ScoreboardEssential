package com.example.scoreboardessential

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.scoreboardessential.database.Player
import com.example.scoreboardessential.databinding.ActivityPlayerRosterBinding
import com.example.scoreboardessential.repository.PlayerRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlayerRosterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerRosterBinding
    private val playerViewModel: PlayerViewModel by viewModels {
        PlayerViewModelFactory((application as ScoreboardEssentialApplication).playerRepository)
    }
    private var selectionMode = false
    private var teamId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerRosterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectionMode = intent.getBooleanExtra("selectionMode", false)
        teamId = intent.getIntExtra("teamId", -1)

        // if (selectionMode) {
        //     binding.addPlayerFab.visibility = View.GONE
        // }

        // val adapter = PlayerRosterAdapter { player ->
        //     if (selectionMode) {
        //         val resultIntent = Intent()
        //         resultIntent.putExtra("selectedPlayerId", player.playerId)
        //         resultIntent.putExtra("teamId", teamId)
        //         setResult(Activity.RESULT_OK, resultIntent)
        //         finish()
        //     }
        // }
        // binding.playerRosterRecyclerview.adapter = adapter
        // binding.playerRosterRecyclerview.layoutManager = LinearLayoutManager(this)

        // playerViewModel.allPlayers.asLiveData().observe(this) { players ->
        //     players?.let { adapter.submitList(it) }
        // }

        // binding.addPlayerFab.setOnClickListener {
        //     showAddPlayerDialog()
        // }
    }

    private fun showAddPlayerDialog() {
        val nameEditText = TextInputEditText(this)
        nameEditText.hint = "Player Name"
        val rolesEditText = TextInputEditText(this)
        rolesEditText.hint = "Roles (comma-separated)"

        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.addView(nameEditText)
        layout.addView(rolesEditText)

        MaterialAlertDialogBuilder(this)
            .setTitle("Add New Player")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = nameEditText.text.toString()
                val roles = rolesEditText.text.toString()
                if (name.isNotBlank()) {
                    playerViewModel.insert(Player(playerName = name, roles = roles, appearances = 0, goals = 0))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class PlayerViewModel(private val repository: PlayerRepository) : ViewModel() {

    val allPlayers = repository.allPlayers

    fun insert(player: Player) = CoroutineScope(Dispatchers.IO).launch {
        repository.insert(player)
    }
}

class PlayerViewModelFactory(private val repository: PlayerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlayerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
