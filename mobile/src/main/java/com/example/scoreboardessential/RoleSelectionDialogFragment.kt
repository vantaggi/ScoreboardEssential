package com.example.scoreboardessential

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scoreboardessential.database.Role
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RoleSelectionDialogFragment : DialogFragment() {

    private val viewModel: PlayersManagementViewModel by activityViewModels()
    private lateinit var roleAdapter: RoleSelectionAdapter

    private var initialSelection: List<Int> = emptyList()

    interface RoleSelectionListener {
        fun onRolesSelected(selectedRoleIds: List<Int>)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialSelection = arguments?.getIntegerArrayList("selected_roles")?.toList() ?: emptyList()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_role_selection, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.roles_recycler_view)

        roleAdapter = RoleSelectionAdapter { _, _ -> /* No-op, selection is handled in adapter state */ }
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
                (activity as? RoleSelectionListener)?.onRolesSelected(roleAdapter.getSelectedRoleIds())
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    companion object {
        fun newInstance(selectedRoleIds: List<Int>): RoleSelectionDialogFragment {
            val args = Bundle().apply {
                putIntegerArrayList("selected_roles", ArrayList(selectedRoleIds))
            }
            return RoleSelectionDialogFragment().apply {
                arguments = args
            }
        }
    }
}
