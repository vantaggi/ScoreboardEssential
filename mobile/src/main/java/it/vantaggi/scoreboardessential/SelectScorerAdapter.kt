package it.vantaggi.scoreboardessential

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.utils.setRoles

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
        val holder = ScorerViewHolder(view)
        view.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onScorerClicked(getItem(position))
            }
        }
        return holder
    }

    override fun onBindViewHolder(
        holder: ScorerViewHolder,
        position: Int,
    ) {
        val player = getItem(position)
        holder.bind(player)
    }

    class ScorerViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.player_name_textview)
        private val roleBadgeGroup: ChipGroup = itemView.findViewById(R.id.role_badge_group)

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
