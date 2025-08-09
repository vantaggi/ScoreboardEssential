package com.example.scoreboardessential

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class MatchLogAdapter : ListAdapter<MatchEvent, MatchLogAdapter.MatchEventViewHolder>(MatchEventDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchEventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.match_event_item, parent, false)
        return MatchEventViewHolder(view)
    }

    override fun onBindViewHolder(holder: MatchEventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MatchEventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timestampTextView: TextView = itemView.findViewById(R.id.event_timestamp)
        private val eventTextView: TextView = itemView.findViewById(R.id.event_description)
        private val teamIndicator: View = itemView.findViewById(R.id.team_indicator)

        fun bind(event: MatchEvent) {
            timestampTextView.text = event.timestamp
            eventTextView.text = event.event

            // Set team color indicator
            when (event.team) {
                1 -> {
                    teamIndicator.visibility = View.VISIBLE
                    teamIndicator.setBackgroundColor(
                        ContextCompat.getColor(itemView.context, R.color.team1_orange)
                    )
                }
                2 -> {
                    teamIndicator.visibility = View.VISIBLE
                    teamIndicator.setBackgroundColor(
                        ContextCompat.getColor(itemView.context, R.color.team2_lime)
                    )
                }
                else -> {
                    teamIndicator.visibility = View.GONE
                }
            }

            // Highlight goals
            if (event.event.contains("GOAL!")) {
                eventTextView.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.action_blue)
                )
                eventTextView.textSize = 16f
            } else {
                eventTextView.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.on_surface_light)
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