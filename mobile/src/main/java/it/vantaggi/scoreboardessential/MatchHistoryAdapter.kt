package it.vantaggi.scoreboardessential

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.ui.MatchHistoryUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MatchHistoryAdapter(
    private val onDeleteClicked: (MatchWithTeams) -> Unit,
) : ListAdapter<MatchHistoryUiState, MatchHistoryAdapter.MatchViewHolder>(MatchDiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): MatchViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.match_item, parent, false)
        return MatchViewHolder(view, onDeleteClicked)
    }

    override fun onBindViewHolder(
        holder: MatchViewHolder,
        position: Int,
    ) {
        val item = getItem(position)
        holder.bind(item)
    }

    class MatchViewHolder(
        itemView: View,
        private val onDeleteClicked: (MatchWithTeams) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val team1NameTextView: TextView = itemView.findViewById(R.id.team1_name_textview)
        private val team2NameTextView: TextView = itemView.findViewById(R.id.team2_name_textview)
        private val team1ScoreTextView: TextView = itemView.findViewById(R.id.team1_score_textview)
        private val team2ScoreTextView: TextView = itemView.findViewById(R.id.team2_score_textview)
        private val timestampTextView: TextView = itemView.findViewById(R.id.timestamp_textview)
        private val playersTextView: TextView = itemView.findViewById(R.id.players_textview)
        private val deleteButton: View = itemView.findViewById(R.id.delete_match_button)

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        fun bind(item: MatchHistoryUiState) {
            val matchWithTeams = item.matchWithTeams

            team1NameTextView.text = matchWithTeams.team1?.name ?: "Team 1"
            team2NameTextView.text = matchWithTeams.team2?.name ?: "Team 2"
            team1ScoreTextView.text = matchWithTeams.match.team1Score.toString()
            team2ScoreTextView.text = matchWithTeams.match.team2Score.toString()

            timestampTextView.text = dateFormat.format(Date(matchWithTeams.match.timestamp))

            if (item.formattedPlayers.isNotEmpty()) {
                playersTextView.visibility = View.VISIBLE
                playersTextView.text = item.formattedPlayers
            } else {
                playersTextView.visibility = View.GONE
            }

            deleteButton.setOnClickListener {
                onDeleteClicked(matchWithTeams)
            }
        }
    }
}

class MatchDiffCallback : DiffUtil.ItemCallback<MatchHistoryUiState>() {
    override fun areItemsTheSame(
        oldItem: MatchHistoryUiState,
        newItem: MatchHistoryUiState,
    ): Boolean = oldItem.matchWithTeams.match.matchId == newItem.matchWithTeams.match.matchId

    override fun areContentsTheSame(
        oldItem: MatchHistoryUiState,
        newItem: MatchHistoryUiState,
    ): Boolean = oldItem == newItem
}
