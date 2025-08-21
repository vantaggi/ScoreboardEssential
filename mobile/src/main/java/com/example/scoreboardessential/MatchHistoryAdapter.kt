package com.example.scoreboardessential

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scoreboardessential.database.MatchWithPlayers
import java.util.Date

class MatchHistoryAdapter(
    private val onDeleteClicked: (MatchWithPlayers) -> Unit
) : ListAdapter<MatchWithPlayers, MatchHistoryAdapter.MatchViewHolder>(MatchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.match_item, parent, false)
        return MatchViewHolder(view, onDeleteClicked)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        val match = getItem(position)
        holder.bind(match)
    }

    class MatchViewHolder(
        itemView: View,
        private val onDeleteClicked: (MatchWithPlayers) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
        private val team1NameTextView: TextView = itemView.findViewById(R.id.team1_name_textview)
        private val team2NameTextView: TextView = itemView.findViewById(R.id.team2_name_textview)
        private val team1ScoreTextView: TextView = itemView.findViewById(R.id.team1_score_textview)
        private val team2ScoreTextView: TextView = itemView.findViewById(R.id.team2_score_textview)
        private val timestampTextView: TextView = itemView.findViewById(R.id.timestamp_textview)
        private val playersTextView: TextView = itemView.findViewById(R.id.players_textview)
        private val deleteButton: View = itemView.findViewById(R.id.delete_match_button)


        fun bind(matchWithPlayers: MatchWithPlayers) {
            team1NameTextView.text = matchWithPlayers.teams.find { it.teamId == matchWithPlayers.match.team1Id }?.teamName
            team2NameTextView.text = matchWithPlayers.teams.find { it.teamId == matchWithPlayers.match.team2Id }?.teamName
            team1ScoreTextView.text = matchWithPlayers.match.team1Score.toString()
            team2ScoreTextView.text = matchWithPlayers.match.team2Score.toString()
            timestampTextView.text = Date(matchWithPlayers.match.timestamp).toString()
            playersTextView.text = matchWithPlayers.players.joinToString(", ") { it.playerName }
            deleteButton.setOnClickListener {
                onDeleteClicked(matchWithPlayers)
            }
        }
    }
}

class MatchDiffCallback : DiffUtil.ItemCallback<MatchWithPlayers>() {
    override fun areItemsTheSame(oldItem: MatchWithPlayers, newItem: MatchWithPlayers): Boolean {
        return oldItem.match.matchId == newItem.match.matchId
    }

    override fun areContentsTheSame(oldItem: MatchWithPlayers, newItem: MatchWithPlayers): Boolean {
        return oldItem == newItem
    }
}
