package com.example.scoreboardessential

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RoleSelectionDialogFragment : DialogFragment() {

    private val viewModel: PlayersManagementViewModel by activityViewModels()
    private lateinit var roleAdapter: RoleSelectionAdapter
    private var initialSelection: List<Int> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialSelection = arguments?.getIntegerArrayList(ARG_SELECTED_ROLES)?.toList() ?: emptyList()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_role_selection, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.roles_recycler_view)

        roleAdapter = RoleSelectionAdapter { _, _ -> /* State is handled inside the adapter */ }
        recyclerView.adapter = roleAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        lifecycleScope.launch {
            viewModel.allRoles.collectLatest { roles ->
                roleAdapter.submitList(roles, initialSelection)
            }
        }

        return AlertDialog.Builder(requireContext())
            .setTitle("Select Roles")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                // Invia il risultato all'activity che è in ascolto
                setFragmentResult(REQUEST_KEY, bundleOf(
                    RESULT_KEY to ArrayList(roleAdapter.getSelectedRoleIds())
                ))
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    companion object {
        const val REQUEST_KEY = "role_selection_request"
        const val RESULT_KEY = "selected_roles_result"
        private const val ARG_SELECTED_ROLES = "selected_roles"

        fun newInstance(selectedRoleIds: List<Int>): RoleSelectionDialogFragment {
            return RoleSelectionDialogFragment().apply {
                arguments = bundleOf(ARG_SELECTED_ROLES to ArrayList(selectedRoleIds))
            }
        }
    }
}
