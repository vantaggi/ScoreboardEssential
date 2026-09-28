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
            .setTitle(R.string.create_player_title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val playerName = playerNameInput.text.toString().trim()
                if (playerName.isNotEmpty()) {
                    viewModel.createPlayer(playerName, selectedRoleIds)
                    Toast.makeText(context, getString(R.string.player_created, playerName), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.player_name_empty, Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun updateSelectedRolesText() {
        if (selectedRoleIds.isEmpty()) {
            selectedRolesTextView.setText(R.string.no_roles_selected)
        } else {
            lifecycleScope.launch {
                val allRoles = viewModel.allRoles.first()
                val selectedRoles = allRoles.filter { it.roleId in selectedRoleIds }
                selectedRolesTextView.text = selectedRoles.joinToString(", ") { it.name }
            }
        }
    }
}
