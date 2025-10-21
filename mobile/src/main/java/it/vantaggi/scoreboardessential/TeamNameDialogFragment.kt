package it.vantaggi.scoreboardessential

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class TeamNameDialogFragment : DialogFragment() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val teamNumber = requireArguments().getInt(ARG_TEAM_NUMBER)
        val currentName = requireArguments().getString(ARG_CURRENT_NAME) ?: ""

        val dialogView = requireActivity().layoutInflater.inflate(R.layout.dialog_team_name, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.team_name_input)
        val previewText = dialogView.findViewById<TextView>(R.id.preview_text)
        val suggestionsChipGroup = dialogView.findViewById<ChipGroup>(R.id.suggestions_chips)

        editText.setText(currentName)
        previewText.text = currentName.uppercase()

        editText.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    previewText.text = s.toString().uppercase()
                }

                override fun afterTextChanged(s: Editable?) {}
            },
        )

        for (i in 0 until suggestionsChipGroup.childCount) {
            val chip = suggestionsChipGroup.getChildAt(i) as? Chip
            chip?.setOnClickListener {
                editText.setText(chip.text)
            }
        }

        return MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Edit Team $teamNumber")
            .setView(dialogView)
            .setPositiveButton("SAVE") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    if (teamNumber == 1) {
                        viewModel.setTeam1Name(newName)
                    } else {
                        viewModel.setTeam2Name(newName)
                    }
                }
            }.setNegativeButton("CANCEL", null)
            .create()
    }

    companion object {
        const val TAG = "TeamNameDialogFragment"
        private const val ARG_TEAM_NUMBER = "team_number"
        private const val ARG_CURRENT_NAME = "current_name"

        fun newInstance(
            teamNumber: Int,
            currentName: String,
        ): TeamNameDialogFragment =
            TeamNameDialogFragment().apply {
                arguments =
                    bundleOf(
                        ARG_TEAM_NUMBER to teamNumber,
                        ARG_CURRENT_NAME to currentName,
                    )
            }
    }
}
