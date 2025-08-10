package com.example.scoreboardessential

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scoreboardessential.database.Player
import com.google.android.material.card.MaterialCardView

class PlayersManagementAdapter(
    private val onPlayerClick: (Player) -> Unit,
    private val onStatsClick: (Player) -> Unit
) : ListAdapter<Player, PlayersManagementAdapter.PlayerViewHolder>(PlayerDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_player_management, parent, false)
        return PlayerViewHolder(view, onPlayerClick, onStatsClick)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PlayerViewHolder(
        itemView: View,
        private val onPlayerClick: (Player) -> Unit,
        private val onStatsClick: (Player) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val cardView: MaterialCardView = itemView.findViewById(R.id.player_card)
        private val nameTextView: TextView = itemView.findViewById(R.id.player_name)
        private val roleTextView: TextView = itemView.findViewById(R.id.player_role)
        private val goalsTextView: TextView = itemView.findViewById(R.id.player_goals)
        private val appearancesTextView: TextView = itemView.findViewById(R.id.player_appearances)
        private val statsButton: ImageButton = itemView.findViewById(R.id.stats_button)
        private val avatarTextView: TextView = itemView.findViewById(R.id.player_avatar)

        fun bind(player: Player) {
            nameTextView.text = player.playerName
            roleTextView.text = player.roles.ifEmpty { "No role specified" }
            goalsTextView.text = "⚽ ${player.goals}"
            appearancesTextView.text = "🎮 ${player.appearances}"

            // Set avatar with first letter of name
            avatarTextView.text = player.playerName.firstOrNull()?.uppercase() ?: "?"

            // Generate a color based on the name for the avatar background
            val colors = itemView.context.resources.getIntArray(R.array.avatar_colors)
            val colorIndex = Math.abs(player.playerName.hashCode()) % colors.size
            avatarTextView.setBackgroundColor(colors[colorIndex])

            cardView.setOnClickListener {
                onPlayerClick(player)
            }

            statsButton.setOnClickListener {
                onStatsClick(player)
            }
        }
    }

    class PlayerDiffCallback : DiffUtil.ItemCallback<Player>() {
        override fun areItemsTheSame(
            oldItem: Player,
            newItem: Player
        ): Boolean {
            return oldItem.playerId == newItem.playerId
        }

        override fun areContentsTheSame(
            oldItem: Player,
            newItem: Player
        ): Boolean {
            return oldItem == newItem
        }
    }
}