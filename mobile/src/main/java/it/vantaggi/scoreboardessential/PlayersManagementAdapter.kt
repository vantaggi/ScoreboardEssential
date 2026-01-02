package it.vantaggi.scoreboardessential

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.views.RoleBadgeGroup

class PlayersManagementAdapter(
    private val onPlayerClick: (PlayerWithRoles) -> Unit,
    private val onStatsClick: (PlayerWithRoles) -> Unit,
) : ListAdapter<PlayerWithRoles, PlayersManagementAdapter.PlayerViewHolder>(PlayerDiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): PlayerViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_player_management, parent, false)
        return PlayerViewHolder(view, onPlayerClick, onStatsClick)
    }

    override fun onBindViewHolder(
        holder: PlayerViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class PlayerViewHolder(
        itemView: View,
        private val onPlayerClick: (PlayerWithRoles) -> Unit,
        private val onStatsClick: (PlayerWithRoles) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.player_card)
        private val nameTextView: TextView = itemView.findViewById(R.id.player_name)
        private val rolesGroup: RoleBadgeGroup = itemView.findViewById(R.id.player_roles_group)
        private val goalsTextView: TextView = itemView.findViewById(R.id.player_goals)
        private val appearancesTextView: TextView = itemView.findViewById(R.id.player_appearances)
        private val statsButton: ImageButton = itemView.findViewById(R.id.stats_button)
        private val avatarTextView: TextView = itemView.findViewById(R.id.player_avatar)
        private val avatarCardView: MaterialCardView = itemView.findViewById(R.id.player_avatar_card)

        fun bind(playerWithRoles: PlayerWithRoles) {
            val player = playerWithRoles.player
            nameTextView.text = player.playerName

            rolesGroup.setRoles(playerWithRoles.roles)

            goalsTextView.text = "⚽ ${player.goals}"
            appearancesTextView.text = "🎮 ${player.appearances}"

            avatarTextView.text = player.playerName.firstOrNull()?.uppercase() ?: "?"

            val colors = itemView.context.resources.getIntArray(R.array.avatar_colors)
            val colorIndex = Math.abs(player.playerName.hashCode()) % colors.size
            avatarCardView.setCardBackgroundColor(colors[colorIndex])

            cardView.setOnClickListener {
                onPlayerClick(playerWithRoles)
            }

            statsButton.setOnClickListener {
                onStatsClick(playerWithRoles)
            }
        }
    }

    class PlayerDiffCallback : DiffUtil.ItemCallback<PlayerWithRoles>() {
        override fun areItemsTheSame(
            oldItem: PlayerWithRoles,
            newItem: PlayerWithRoles,
        ): Boolean = oldItem.player.playerId == newItem.player.playerId

        override fun areContentsTheSame(
            oldItem: PlayerWithRoles,
            newItem: PlayerWithRoles,
        ): Boolean = oldItem == newItem
    }
}
