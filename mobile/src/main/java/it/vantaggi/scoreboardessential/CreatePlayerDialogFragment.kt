package it.vantaggi.scoreboardessential

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CreatePlayerDialogFragment : DialogFragment() {
    private val viewModel: PlayersManagementViewModel by activityViewModels()
    private var selectedRoleIds = mutableListOf<Int>()
    private lateinit var selectedRolesTextView: TextView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_create_player, null)

        val playerNameInput = view.findViewById<TextInputEditText>(R.id.player_name_input)
        val selectRolesButton = view.findViewById<Button>(R.id.select_roles_button)
        selectedRolesTextView = view.findViewById(R.id.selected_roles_textview)

        selectRolesButton.setOnClickListener {
            val roleSelectionDialog = RoleSelectionDialogFragment.newInstance(selectedRoleIds)
            roleSelectionDialog.setOnRolesSelectedListener { ids ->
                selectedRoleIds.clear()
                selectedRoleIds.addAll(ids)
                updateSelectedRolesText()
            }
            roleSelectionDialog.show(parentFragmentManager, "RoleSelectionDialog")
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create New Player")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val playerName = playerNameInput.text.toString().trim()
                if (playerName.isNotEmpty()) {
                    viewModel.createPlayer(playerName, selectedRoleIds)
                    Toast.makeText(context, "$playerName created", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Player name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Cancel", null)
            .create()
    }

    private fun updateSelectedRolesText() {
        if (selectedRoleIds.isEmpty()) {
            selectedRolesTextView.text = "No roles selected"
        } else {
            lifecycleScope.launch {
                val allRoles = viewModel.allRoles.first()
                val selectedRoles = allRoles.filter { it.roleId in selectedRoleIds }
                selectedRolesTextView.text = selectedRoles.joinToString(", ") { it.name }
            }
        }
    }
}
