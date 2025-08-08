package com.example.scoreboardessential

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.scoreboardessential.database.Player

class SelectScorerDialogFragment : DialogFragment() {

    interface ScorerDialogListener {
        fun onScorerSelected(player: Player)
    }

    private var listener: ScorerDialogListener? = null
    private var players: Array<Player> = emptyArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        players = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelableArray("players", Player::class.java)
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelableArray("players")
        } as? Array<Player> ?: emptyArray()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setTitle("Select Scorer")
                .setItems(players.map { it.playerName }.toTypedArray()) { _, which ->
                    listener?.onScorerSelected(players[which])
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    fun setScorerDialogListener(listener: ScorerDialogListener) {
        this.listener = listener
    }

    companion object {
        fun newInstance(players: Array<Player>): SelectScorerDialogFragment {
            val args = Bundle()
            args.putParcelableArray("players", players)
            val fragment = SelectScorerDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
