package it.vantaggi.scoreboardessential.ui.statistics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.domain.model.PlayerStatsDTO

class StatisticsAdapter : ListAdapter<PlayerStatsDTO, StatisticsAdapter.ViewHolder>(DiffCallback()) {
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
        holder.bind(getItem(position), position)
    }

    class ViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val rankTextView: TextView = itemView.findViewById(R.id.text_rank)
        private val nameTextView: TextView = itemView.findViewById(R.id.text_player_name)
        private val goalsTextView: TextView = itemView.findViewById(R.id.text_goals)
        private val appearancesTextView: TextView = itemView.findViewById(R.id.text_appearances)
        private val cardView: MaterialCardView = itemView.findViewById(R.id.card_player_stat)

        fun bind(
            item: PlayerStatsDTO,
            position: Int,
        ) {
            val rank = position + 1
            rankTextView.text = "#$rank"
            nameTextView.text = item.playerName
            val context = itemView.context
            goalsTextView.text = context.resources.getQuantityString(R.plurals.stats_goals, item.goals, item.goals)
            appearancesTextView.text = context.resources.getQuantityString(R.plurals.stats_appearances, item.appearances, item.appearances)

            if (rank == 1) {
                // Il podio e' la card rosa piena con testo nero (5,02:1). Prima era gialla, il
                // colore della squadra 1, con il rank in ciano a 1,09:1.
                cardView.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.graffiti_pink),
                )
                nameTextView.setTextColor(ContextCompat.getColor(context, R.color.asphalt_black))
                rankTextView.setTextColor(ContextCompat.getColor(context, R.color.asphalt_black))
                goalsTextView.setTextColor(ContextCompat.getColor(context, R.color.asphalt_black))
                appearancesTextView.setTextColor(ContextCompat.getColor(context, R.color.asphalt_black))
            } else {
                // Default style
                cardView.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.graffiti_dark_gray),
                )
                nameTextView.setTextColor(ContextCompat.getColor(context, R.color.stencil_white))
                rankTextView.setTextColor(ContextCompat.getColor(context, R.color.graffiti_pink))
                // I gol in #E0E0E0: il verde della squadra 2 non e' un evidenziatore.
                goalsTextView.setTextColor(ContextCompat.getColor(context, R.color.stencil_white))
                appearancesTextView.setTextColor(ContextCompat.getColor(context, R.color.sidewalk_gray))
            }
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
