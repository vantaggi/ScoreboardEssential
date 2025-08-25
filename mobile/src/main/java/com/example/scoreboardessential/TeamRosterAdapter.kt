package com.example.scoreboardessential

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scoreboardessential.database.PlayerWithRoles
import com.google.android.material.card.MaterialCardView

class TeamRosterAdapter(
    private val onPlayerClick: (PlayerWithRoles) -> Unit
) : ListAdapter<PlayerWithRoles, TeamRosterAdapter.PlayerViewHolder>(PlayerDiffCallback()) {

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
        private val onPlayerClick: (PlayerWithRoles) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val playerCard: MaterialCardView = itemView.findViewById(R.id.player_card)
        private val playerNameTextView: TextView = itemView.findViewById(R.id.player_name)
        private val playerRoleTextView: TextView = itemView.findViewById(R.id.player_role)
        private val playerStatsTextView: TextView = itemView.findViewById(R.id.player_stats)

        fun bind(playerWithRoles: PlayerWithRoles) {
            val player = playerWithRoles.player
            playerNameTextView.text = player.playerName
            playerRoleTextView.text = playerWithRoles.roles.joinToString(", ").ifEmpty { "Player" }
            playerStatsTextView.text = "Goals: ${player.goals}"

            playerCard.setOnLongClickListener {
                onPlayerClick(playerWithRoles)
                true
            }
        }
    }

    class PlayerDiffCallback : DiffUtil.ItemCallback<PlayerWithRoles>() {
        override fun areItemsTheSame(oldItem: PlayerWithRoles, newItem: PlayerWithRoles): Boolean {
            return oldItem.player.playerId == newItem.player.playerId
        }

        override fun areContentsTheSame(oldItem: PlayerWithRoles, newItem: PlayerWithRoles): Boolean {
            return oldItem == newItem
        }
    }
}