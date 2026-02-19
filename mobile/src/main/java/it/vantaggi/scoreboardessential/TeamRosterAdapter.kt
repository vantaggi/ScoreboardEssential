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

class TeamRosterAdapter(
    private val onPlayerClick: (PlayerWithRoles) -> Unit,
) : ListAdapter<PlayerWithRoles, TeamRosterAdapter.PlayerViewHolder>(PlayerDiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): PlayerViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.team_player_item, parent, false)
        return PlayerViewHolder(view, onPlayerClick)
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
    ) : RecyclerView.ViewHolder(itemView) {
        private val playerNameTextView: TextView = itemView.findViewById(R.id.player_name)
        private val playerRolesGroup: ChipGroup = itemView.findViewById(R.id.player_roles)
        private val playerInitialsTextView: TextView = itemView.findViewById(R.id.player_initials)

        fun bind(playerWithRoles: PlayerWithRoles) {
            val player = playerWithRoles.player
            playerNameTextView.text = player.playerName

            // Bind Initials
            val initials =
                player.playerName
                    .split(" ")
                    .mapNotNull { it.firstOrNull()?.toString() }
                    .take(2)
                    .joinToString("")
                    .uppercase()
            playerInitialsTextView.text = if (initials.isNotEmpty()) initials else "?"

            playerRolesGroup.setRoles(playerWithRoles.roles)

            itemView.setOnClickListener {
                onPlayerClick(playerWithRoles)
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
