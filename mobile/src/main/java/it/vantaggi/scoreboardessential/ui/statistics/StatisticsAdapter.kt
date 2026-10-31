package it.vantaggi.scoreboardessential.ui.statistics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.domain.model.PlayerStatsDTO

/** [onPlayerClick] apre la scheda del giocatore: la riga si tocca e la freccia lo dice. */
class StatisticsAdapter(
    private val onPlayerClick: (Int) -> Unit = {},
) : ListAdapter<PlayerStatsDTO, StatisticsAdapter.ViewHolder>(DiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_player_stat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val item = getItem(position)
        holder.bind(item, position, leader = position == 0 && Classifica.haUnPrimo(currentList.map { it.goals }))
        holder.itemView.setOnClickListener { onPlayerClick(item.playerId) }
    }

    /**
     * Una riga e' il nome e i gol. Il primo in classifica ha i gol in lime (accento: l'enfasi che
     * significa qualcosa, e il secondo segno e' la posizione in cima alla lista), ma solo se e' davvero
     * primo ([Classifica.haUnPrimo]). Rango e presenze non sono a vista ma TalkBack le sente nella
     * descrizione della riga.
     */
    class ViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.text_player_name)
        private val goalsTextView: TextView = itemView.findViewById(R.id.text_goals)

        init {
            itemView.findViewById<View>(R.id.stat_chevron).visibility = View.VISIBLE
        }

        fun bind(
            item: PlayerStatsDTO,
            position: Int,
            leader: Boolean = position == 0,
        ) {
            val rank = position + 1
            val resources = itemView.resources
            val context = itemView.context
            nameTextView.text = item.playerName
            val gol = resources.getQuantityString(R.plurals.stats_goals, item.goals, item.goals)
            goalsTextView.text = gol
            goalsTextView.setTextColor(context.getColor(if (leader) R.color.accent_default else R.color.text_primary))
            val presenze = resources.getQuantityString(R.plurals.stats_appearances, item.appearances, item.appearances)
            itemView.contentDescription = resources.getString(R.string.stats_row_description, rank, item.playerName, gol, presenze)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PlayerStatsDTO>() {
        override fun areItemsTheSame(
            oldItem: PlayerStatsDTO,
            newItem: PlayerStatsDTO,
        ): Boolean = oldItem.playerId == newItem.playerId

        override fun areContentsTheSame(
            oldItem: PlayerStatsDTO,
            newItem: PlayerStatsDTO,
        ): Boolean = oldItem == newItem
    }
}
