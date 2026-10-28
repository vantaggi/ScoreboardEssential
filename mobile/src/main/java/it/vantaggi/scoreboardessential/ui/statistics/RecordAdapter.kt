package it.vantaggi.scoreboardessential.ui.statistics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.database.PlayerRecord

/**
 * La classifica del padel e del tennis (passo 6): la stessa riga di quella dei gol (`item_player_stat`),
 * con le partite vinte su quelle giocate al posto dei gol. Il lime solo per un primo vero.
 */
class RecordAdapter(
    private val onPlayerClick: (Int) -> Unit = {},
) : ListAdapter<PlayerRecord, RecordAdapter.ViewHolder>(DiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_player_stat, parent, false))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val item = getItem(position)
        holder.bind(item, position, leader = position == 0 && Classifica.haUnPrimo(currentList.map { it.won }))
        holder.itemView.setOnClickListener { onPlayerClick(item.playerId) }
    }

    class ViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.text_player_name)
        private val value: TextView = itemView.findViewById(R.id.text_goals)

        init {
            itemView.findViewById<View>(R.id.stat_chevron).visibility = View.VISIBLE
        }

        fun bind(
            item: PlayerRecord,
            position: Int,
            leader: Boolean,
        ) {
            val resources = itemView.resources
            name.text = item.playerName
            val vinte = resources.getQuantityString(R.plurals.stats_wins, item.won, item.won, item.played)
            value.text = vinte
            value.setTextColor(itemView.context.getColor(if (leader) R.color.elite_accent else R.color.elite_text_primary))
            itemView.contentDescription = resources.getString(R.string.stats_record_description, position + 1, item.playerName, vinte)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PlayerRecord>() {
        override fun areItemsTheSame(
            oldItem: PlayerRecord,
            newItem: PlayerRecord,
        ): Boolean = oldItem.playerId == newItem.playerId

        override fun areContentsTheSame(
            oldItem: PlayerRecord,
            newItem: PlayerRecord,
        ): Boolean = oldItem == newItem
    }
}
