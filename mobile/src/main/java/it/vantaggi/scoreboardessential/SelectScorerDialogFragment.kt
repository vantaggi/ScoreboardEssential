package it.vantaggi.scoreboardessential

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SelectScorerDialogFragment : DialogFragment() {
    interface ScorerDialogListener {
        fun onScorerSelected(
            playerWithRoles: PlayerWithRoles,
            teamId: Int,
            engineIndex: Int,
        )
    }

    private var listener: ScorerDialogListener? = null
    private var adapter: SelectScorerAdapter? = null
    private var playerIds: IntArray = IntArray(0)
    private var teamId: Int = 0
    private var engineIndex: Int = -1

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
            playerIds = it.getIntArray(ARG_PLAYER_IDS) ?: IntArray(0)
            teamId = it.getInt(ARG_TEAM_ID)
            engineIndex = it.getInt(ARG_ENGINE_INDEX, -1)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_select_scorer, null)

        val recyclerView = view.findViewById<RecyclerView>(R.id.scorers_recyclerview)
        val scorerAdapter =
            SelectScorerAdapter { player ->
                listener?.onScorerSelected(player, teamId, engineIndex)
                dismiss()
            }
        adapter = scorerAdapter

        recyclerView.adapter = scorerAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)
        loadPlayers()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
    }

    // Gli argomenti trasportano solo gli id: i giocatori si ricaricano dal DAO
    private fun loadPlayers() {
        val playerDao = AppDatabase.getDatabase(requireContext().applicationContext).playerDao()
        lifecycleScope.launch {
            val playersById = playerDao.getAllPlayers().first().associateBy { it.player.playerId }
            adapter?.submitList(playerIds.asList().mapNotNull { playersById[it] })
        }
    }

    companion object {
        const val TAG = "SelectScorerDialog"

        private const val ARG_PLAYER_IDS = "player_ids"
        private const val ARG_TEAM_ID = "team_id"
        private const val ARG_ENGINE_INDEX = "engine_index"

        /**
         * @param engineIndex il punto a cui il marcatore va attribuito. Serve perche' la scelta
         *   non arriva piu' subito dopo il gol: puo' arrivare dieci minuti e sei gol dopo, e
         *   "l'ultimo punto" a quel momento sarebbe quello sbagliato.
         */
        fun newInstance(
            players: List<PlayerWithRoles>,
            teamId: Int,
            engineIndex: Int,
        ): SelectScorerDialogFragment {
            val args =
                Bundle().apply {
                    putIntArray(ARG_PLAYER_IDS, players.map { it.player.playerId }.toIntArray())
                    putInt(ARG_TEAM_ID, teamId)
                    putInt(ARG_ENGINE_INDEX, engineIndex)
                }
            return SelectScorerDialogFragment().apply {
                arguments = args
            }
        }
    }
}
