package it.vantaggi.scoreboardessential

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.views.RoleBadgeGroup

class SelectScorerAdapter(
    private val onScorerClicked: (PlayerWithRoles) -> Unit,
) : ListAdapter<PlayerWithRoles, SelectScorerAdapter.ScorerViewHolder>(PlayerWithRolesDiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ScorerViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.scorer_item, parent, false)
        return ScorerViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ScorerViewHolder,
        position: Int,
    ) {
        val player = getItem(position)
        holder.bind(player)
        holder.itemView.setOnClickListener {
            onScorerClicked(player)
        }
    }

    class ScorerViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.player_name_textview)
        private val roleBadgeGroup: RoleBadgeGroup = itemView.findViewById(R.id.role_badge_group)

        fun bind(playerWithRoles: PlayerWithRoles) {
            nameTextView.text = playerWithRoles.player.playerName
            roleBadgeGroup.setRoles(playerWithRoles.roles)
        }
    }

    class PlayerWithRolesDiffCallback : DiffUtil.ItemCallback<PlayerWithRoles>() {
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
