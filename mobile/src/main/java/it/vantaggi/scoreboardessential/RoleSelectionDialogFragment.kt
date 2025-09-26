package it.vantaggi.scoreboardessential

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class RoleSelectionDialogFragment : DialogFragment() {

    private val viewModel: PlayersManagementViewModel by activityViewModels()
    private lateinit var rolesRecyclerView: RecyclerView
    private lateinit var roleAdapter: RoleSelectionAdapter

    private var listener: ((List<Int>) -> Unit)? = null
    private var initialSelectedRoleIds: List<Int> = emptyList()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_role_selection, null)

        rolesRecyclerView = view.findViewById(R.id.roles_recycler_view)
        setupRecyclerView()
        observeRoles()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Roles")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                listener?.invoke(roleAdapter.getSelectedRoleIds())
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun setupRecyclerView() {
        roleAdapter = RoleSelectionAdapter { _, _ -> /* No immediate action needed on selection */ }
        rolesRecyclerView.adapter = roleAdapter
        rolesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun observeRoles() {
        lifecycleScope.launch {
            viewModel.allRoles.collect { roles ->
                roleAdapter.submitList(roles, initialSelectedRoleIds)
            }
        }
    }

    fun setOnRolesSelectedListener(listener: (List<Int>) -> Unit) {
        this.listener = listener
    }

    companion object {
        fun newInstance(selectedIds: List<Int> = emptyList()): RoleSelectionDialogFragment {
            return RoleSelectionDialogFragment().apply {
                initialSelectedRoleIds = selectedIds
            }
        }
    }
}