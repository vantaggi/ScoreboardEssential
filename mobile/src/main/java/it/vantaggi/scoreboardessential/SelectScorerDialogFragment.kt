package it.vantaggi.scoreboardessential

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import it.vantaggi.scoreboardessential.database.PlayerWithRoles

class SelectScorerDialogFragment : DialogFragment() {

    interface ScorerDialogListener {
        fun onScorerSelected(playerWithRoles: PlayerWithRoles)
    }

    private var listener: ScorerDialogListener? = null
    private var players: Array<PlayerWithRoles> = emptyArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        players = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelableArray("players", PlayerWithRoles::class.java)
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelableArray("players")
        } as? Array<PlayerWithRoles> ?: emptyArray()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setTitle("Select Scorer")
                .setItems(players.map { it.player.playerName }.toTypedArray()) { _, which ->
                    listener?.onScorerSelected(players[which])
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    fun setScorerDialogListener(listener: ScorerDialogListener) {
        this.listener = listener
    }

    companion object {
        fun newInstance(players: Array<PlayerWithRoles>): SelectScorerDialogFragment {
            val args = Bundle()
            args.putParcelableArray("players", players)
            val fragment = SelectScorerDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
