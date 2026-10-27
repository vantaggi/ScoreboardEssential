package it.vantaggi.scoreboardessential

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.utils.setRoles

/**
 * L'elenco dei giocatori (passo 6b). [contaIGol] dice se lo sport attivo conta i gol (il calcio):
 * solo allora la riga mostra i gol e i ruoli, che negli altri sport non vogliono dire niente.
 */
class PlayersManagementAdapter(
    private val onPlayerClick: (PlayerWithRoles) -> Unit,
    contaIGol: Boolean = true,
) : ListAdapter<PlayerWithRoles, PlayersManagementAdapter.PlayerViewHolder>(PlayerDiffCallback()) {
    var contaIGol: Boolean = contaIGol
        set(valore) {
            if (field == valore) return
            field = valore
            notifyItemRangeChanged(0, itemCount)
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): PlayerViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_player_management, parent, false)
        return PlayerViewHolder(view, onPlayerClick)
    }

    override fun onBindViewHolder(
        holder: PlayerViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position), contaIGol)
    }

    class PlayerViewHolder(
        itemView: View,
        private val onPlayerClick: (PlayerWithRoles) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val rowView: View = itemView.findViewById(R.id.player_card)
        private val nameTextView: TextView = itemView.findViewById(R.id.player_name)
        private val valueTextView: TextView = itemView.findViewById(R.id.player_value)
        private val rolesGroup: ChipGroup = itemView.findViewById(R.id.player_roles_group)

        fun bind(
            playerWithRoles: PlayerWithRoles,
            contaIGol: Boolean = true,
        ) {
            val player = playerWithRoles.player
            nameTextView.text = player.playerName
            valueTextView.text = valoreDelGiocatore(itemView.resources, player, contaIGol)

            // Le viste si riciclano: i ruoli si scrivono in entrambi i casi.
            rolesGroup.setRoles(if (contaIGol) playerWithRoles.roles else emptyList())
            rolesGroup.visibility = if (contaIGol && playerWithRoles.roles.isNotEmpty()) View.VISIBLE else View.GONE

            // La riga si tocca per modificare: la freccia lo dice.
            rowView.setOnClickListener {
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

/** «3 partite», e nel calcio «3 partite · 5 gol»: il valore che conta, sotto il nome. */
internal fun valoreDelGiocatore(
    resources: Resources,
    player: Player,
    contaIGol: Boolean,
): String {
    val partite = resources.getQuantityString(R.plurals.player_value_matches, player.appearances, player.appearances)
    if (!contaIGol) return partite
    val gol = resources.getQuantityString(R.plurals.stats_goals, player.goals, player.goals)
    return resources.getString(R.string.player_value_with_goals, partite, gol)
}
