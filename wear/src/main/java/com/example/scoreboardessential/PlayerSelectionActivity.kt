package com.example.scoreboardessential

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable

data class WearPlayer(
    val name: String,
    val role: String
)

class PlayerSelectionActivity : ComponentActivity() {

    private lateinit var playerList: WearableRecyclerView
    private lateinit var adapter: PlayerAdapter
    private val viewModel: WearViewModel by viewModels()
    private var teamNumber: Int = 1
    private lateinit var messageClient: MessageClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_selection)

        messageClient = Wearable.getMessageClient(this)
        teamNumber = intent.getIntExtra("team_number", 1)

        setupRecyclerView()
        loadPlayers()
    }

    private fun setupRecyclerView() {
        playerList = findViewById(R.id.player_list)
        adapter = PlayerAdapter { player ->
            selectPlayer(player)
        }
        
        playerList.layoutManager = WearableLinearLayoutManager(this)
        playerList.adapter = adapter
        
        // Enable edge items scaling for better Wear OS experience
        playerList.isEdgeItemsCenteringEnabled = true
    }

    private fun loadPlayers() {
        // Mock players for now - in a real implementation, this would come from sync with mobile app
        val players = listOf(
            WearPlayer("John Doe", "Forward"),
            WearPlayer("Jane Smith", "Midfielder"),
            WearPlayer("Mike Johnson", "Defender"),
            WearPlayer("Sarah Wilson", "Goalkeeper"),
            WearPlayer("Tom Brown", "Forward"),
            WearPlayer("Lisa Davis", "Midfielder")
        )
        adapter.submitList(players)
    }

    private fun selectPlayer(player: WearPlayer) {
        // Send scorer information to mobile app
        val message = "${player.name}|${player.role}|$teamNumber"
        sendMessageToMobile("/scorer_selected", message)
        finish()
    }

    private fun sendMessageToMobile(path: String, message: String) {
        val data = message.toByteArray()
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, path, data)
            }
        }
    }
}

class PlayerAdapter(private val onPlayerClick: (WearPlayer) -> Unit) : 
    RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {
    
    private var players: List<WearPlayer> = emptyList()
    
    fun submitList(newPlayers: List<WearPlayer>) {
        players = newPlayers
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_player_wear, parent, false)
        return PlayerViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(players[position], onPlayerClick)
    }
    
    override fun getItemCount() = players.size
    
    class PlayerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val playerName: TextView = itemView.findViewById(R.id.player_name)
        private val playerRole: TextView = itemView.findViewById(R.id.player_role)
        
        fun bind(player: WearPlayer, onPlayerClick: (WearPlayer) -> Unit) {
            playerName.text = player.name
            playerRole.text = player.role
            
            itemView.setOnClickListener {
                onPlayerClick(player)
            }
        }
    }
}