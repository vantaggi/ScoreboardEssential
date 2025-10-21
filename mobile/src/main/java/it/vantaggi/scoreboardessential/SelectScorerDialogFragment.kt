package it.vantaggi.scoreboardessential

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import it.vantaggi.scoreboardessential.database.PlayerWithRoles

class SelectScorerDialogFragment : DialogFragment() {
    interface ScorerDialogListener {
        fun onScorerSelected(
            playerWithRoles: PlayerWithRoles,
            teamId: Int,
        )
    }

    private var listener: ScorerDialogListener? = null
    private lateinit var players: List<PlayerWithRoles>
    private var teamId: Int = 0

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Ensure the host activity implements the listener interface
        listener =
            try {
                parentFragment as? ScorerDialogListener ?: context as ScorerDialogListener
            } catch (e: ClassCastException) {
                throw ClassCastException("$context must implement ScorerDialogListener")
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val rawPlayers =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    it.getSerializable("players", ArrayList::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    it.getSerializable("players")
                }
            players = rawPlayers as? List<PlayerWithRoles> ?: emptyList()
            teamId = it.getInt("teamId")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_select_scorer, null)

        val recyclerView = view.findViewById<RecyclerView>(R.id.scorers_recyclerview)
        val adapter =
            SelectScorerAdapter { player ->
                listener?.onScorerSelected(player, teamId)
                dismiss()
            }

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter.submitList(players)

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
    }

    companion object {
        const val TAG = "SelectScorerDialog"

        fun newInstance(
            players: List<PlayerWithRoles>,
            teamId: Int,
        ): SelectScorerDialogFragment {
            val args =
                Bundle().apply {
                    // ArrayList is serializable, List is not
                    putSerializable("players", ArrayList(players))
                    putInt("teamId", teamId)
                }
            return SelectScorerDialogFragment().apply {
                arguments = args
            }
        }
    }
}
