package com.example.scoreboardessential

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scoreboardessential.database.Player
import com.example.scoreboardessential.databinding.PlayerItemBinding

class PlayerRosterAdapter(private val onPlayerClicked: (Player) -> Unit) :
    ListAdapter<Player, PlayerRosterAdapter.PlayerViewHolder>(PlayerDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val binding: PlayerItemBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.player_item,
            parent,
            false
        )
        return PlayerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val player = getItem(position)
        holder.bind(player)
        holder.itemView.setOnClickListener {
            onPlayerClicked(player)
        }
    }

    class PlayerViewHolder(private val binding: PlayerItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(player: Player) {
            binding.player = player
            binding.executePendingBindings()
        }
    }
}

object PlayerDiffCallback : DiffUtil.ItemCallback<Player>() {
    override fun areItemsTheSame(oldItem: Player, newItem: Player): Boolean {
        return oldItem.playerId == newItem.playerId
    }

    override fun areContentsTheSame(oldItem: Player, newItem: Player): Boolean {
        return oldItem == newItem
    }
}
