package it.vantaggi.scoreboardessential

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors

class MatchLogAdapter : ListAdapter<MatchEvent, MatchLogAdapter.MatchEventViewHolder>(MatchEventDiffCallback()) {

    var team1Color: Int = 0
    var team2Color: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchEventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.match_event_item, parent, false)
        return MatchEventViewHolder(view)
    }

    override fun onBindViewHolder(holder: MatchEventViewHolder, position: Int) {
        holder.bind(getItem(position), team1Color, team2Color)
    }

    class MatchEventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timestampTextView: TextView = itemView.findViewById(R.id.event_timestamp)
        private val eventTextView: TextView = itemView.findViewById(R.id.event_description)
        private val teamIndicator: View = itemView.findViewById(R.id.team_indicator)

        fun bind(event: MatchEvent, team1Color: Int, team2Color: Int) {
            timestampTextView.text = event.timestamp

            val description = if (event.event == "Goal" && event.player != null) {
                val roleInfo = if (event.playerRole?.isNotEmpty() == true) " (${event.playerRole})" else ""
                "GOAL! ${event.player}${roleInfo}"
            } else {
                event.event
            }
            eventTextView.text = description

            // Set team color indicator
            when (event.team) {
                1 -> {
                    teamIndicator.visibility = View.VISIBLE
                    teamIndicator.setBackgroundColor(team1Color)
                }
                2 -> {
                    teamIndicator.visibility = View.VISIBLE
                    teamIndicator.setBackgroundColor(team2Color)
                }
                else -> {
                    teamIndicator.visibility = View.GONE
                }
            }

            // Highlight goals
            if (event.event.contains("GOAL!")) {
                val goalColor = if (event.team == 1) team1Color else team2Color
                eventTextView.setTextColor(goalColor)
                eventTextView.textSize = 16f
            } else {
                eventTextView.setTextColor(
                    MaterialColors.getColor(itemView.context, com.google.android.material.R.attr.colorOnSurface, "Error")
                )
                eventTextView.textSize = 14f
            }
        }
    }

    class MatchEventDiffCallback : DiffUtil.ItemCallback<MatchEvent>() {
        override fun areItemsTheSame(oldItem: MatchEvent, newItem: MatchEvent): Boolean {
            return oldItem.timestamp == newItem.timestamp && oldItem.event == newItem.event
        }

        override fun areContentsTheSame(oldItem: MatchEvent, newItem: MatchEvent): Boolean {
            return oldItem == newItem
        }
    }
}