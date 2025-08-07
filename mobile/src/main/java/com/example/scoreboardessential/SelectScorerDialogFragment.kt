package com.example.scoreboardessential

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

// A dialog fragment that allows the user to select a scorer from a list of players.
class SelectScorerDialogFragment : DialogFragment() {

    // Listener for when a scorer is selected.
    interface ScorerDialogListener {
        fun onScorerSelected(scorer: String)
    }

    private var listener: ScorerDialogListener? = null
    private var players: Array<String> = emptyArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        players = arguments?.getStringArray("players") ?: emptyArray()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setTitle("Select Scorer")
                .setItems(players) { _, which ->
                    listener?.onScorerSelected(players[which])
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    fun setScorerDialogListener(listener: ScorerDialogListener) {
        this.listener = listener
    }

    companion object {
        // Creates a new instance of the dialog fragment.
        fun newInstance(players: Array<String>): SelectScorerDialogFragment {
            val args = Bundle()
            args.putStringArray("players", players)
            val fragment = SelectScorerDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
