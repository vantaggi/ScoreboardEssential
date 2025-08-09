package com.example.scoreboardessential

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scoreboardessential.database.Player
import com.google.android.material.card.MaterialCardView

class TeamRosterAdapter(
    private val onPlayerClick: (Player) -> Unit
) : ListAdapter<Player, TeamRosterAdapter.PlayerViewHolder>(PlayerDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.team_player_item, parent, false)
        return PlayerViewHolder(view, onPlayerClick)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PlayerViewHolder(
        itemView: View,
        private val onPlayerClick: (Player) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val playerCard: MaterialCardView = itemView.findViewById(R.id.player_card)
        private val playerNameTextView: TextView = itemView.findViewById(R.id.player_name)
        private val playerRoleTextView: TextView = itemView.findViewById(R.id.player_role)
        private val playerStatsTextView: TextView = itemView.findViewById(R.id.player_stats)

        fun bind(player: Player) {
            playerNameTextView.text = player.playerName
            playerRoleTextView.text = player.roles.ifEmpty { "Player" }
            playerStatsTextView.text = "Goals: ${player.goals}"

            playerCard.setOnLongClickListener {
                onPlayerClick(player)
                true
            }
        }
    }

    class PlayerDiffCallback : DiffUtil.ItemCallback<Player>() {
        override fun areItemsTheSame(oldItem: Player, newItem: Player): Boolean {
            return oldItem.playerId == newItem.playerId
        }

        override fun areContentsTheSame(oldItem: Player, newItem: Player): Boolean {
            return oldItem == newItem
        }
    }
}