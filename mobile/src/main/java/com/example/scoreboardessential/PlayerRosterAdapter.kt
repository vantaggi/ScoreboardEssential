package com.example.scoreboardessential

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scoreboardessential.database.PlayerWithRoles
import com.example.scoreboardessential.databinding.PlayerItemBinding

class PlayerRosterAdapter(private val onPlayerClicked: (PlayerWithRoles) -> Unit) :
    ListAdapter<PlayerWithRoles, PlayerRosterAdapter.PlayerViewHolder>(PlayerDiffCallback) {

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
        fun bind(player: PlayerWithRoles) {
            binding.player = player
            binding.executePendingBindings()
        }
    }
}

object PlayerDiffCallback : DiffUtil.ItemCallback<PlayerWithRoles>() {
    override fun areItemsTheSame(oldItem: PlayerWithRoles, newItem: PlayerWithRoles): Boolean {
        return oldItem.player.playerId == newItem.player.playerId
    }

    override fun areContentsTheSame(oldItem: PlayerWithRoles, newItem: PlayerWithRoles): Boolean {
        return oldItem == newItem
    }
}
