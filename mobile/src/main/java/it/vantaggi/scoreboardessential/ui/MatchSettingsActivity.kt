package it.vantaggi.scoreboardessential.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.databinding.ActivityMatchSettingsBinding

class MatchSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMatchSettingsBinding
    private val viewModel: MatchSettingsViewModel by viewModels {
        MatchSettingsViewModelFactory(
            (application as it.vantaggi.scoreboardessential.ScoreboardEssentialApplication).matchSettingsRepository,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeViewModel()
        setupListeners()
    }

    private fun observeViewModel() {
        viewModel.team1Name.observe(this) { name ->
            if (binding.team1NameEdittext.text.toString() != name) {
                binding.team1NameEdittext.setText(name)
            }
        }

        viewModel.team2Name.observe(this) { name ->
            if (binding.team2NameEdittext.text.toString() != name) {
                binding.team2NameEdittext.setText(name)
            }
        }

        viewModel.team1Color.observe(this) { color ->
            binding.team1ColorButton.setBackgroundColor(color)
        }

        viewModel.team2Color.observe(this) { color ->
            binding.team2ColorButton.setBackgroundColor(color)
        }

        viewModel.keeperTimerDuration.observe(this) { duration ->
            if (binding.keeperTimerEdittext.text.toString() != duration.toString()) {
                binding.keeperTimerEdittext.setText(duration.toString())
            }
        }
    }

    private fun setupListeners() {
        binding.team1NameEdittext.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.saveTeam1Name(binding.team1NameEdittext.text.toString())
            }
        }

        binding.team2NameEdittext.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.saveTeam2Name(binding.team2NameEdittext.text.toString())
            }
        }

        binding.keeperTimerEdittext.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val duration =
                    binding.keeperTimerEdittext.text
                        .toString()
                        .toLongOrNull() ?: 0L
                viewModel.saveKeeperTimerDuration(duration)
            }
        }

        binding.team1ColorButton.setOnClickListener {
            showColorPickerDialog(1)
        }

        binding.team2ColorButton.setOnClickListener {
            showColorPickerDialog(2)
        }

        binding.saveSettingsButton.setOnClickListener {
            viewModel.saveTeam1Name(binding.team1NameEdittext.text.toString())
            viewModel.saveTeam2Name(binding.team2NameEdittext.text.toString())

            val keeperDuration =
                binding.keeperTimerEdittext.text
                    .toString()
                    .toLongOrNull() ?: 30L
            viewModel.saveKeeperTimerDuration(keeperDuration)

            com.google.android.material.snackbar.Snackbar
                .make(
                    binding.root,
                    "Settings saved successfully!",
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                ).show()
        }
    }

    private fun showColorPickerDialog(team: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val colorPickerView = dialogView.findViewById<ColorPickerView>(R.id.colorPickerView)
        val brightnessSlideBar = dialogView.findViewById<BrightnessSlideBar>(R.id.brightnessSlide)

        colorPickerView.attachBrightnessSlider(brightnessSlideBar)

        MaterialAlertDialogBuilder(this)
            .setTitle("Choose Team $team Color")
            .setView(dialogView)
            .setPositiveButton("Select") { _, _ ->
                if (team == 1) {
                    viewModel.saveTeam1Color(colorPickerView.color)
                } else {
                    viewModel.saveTeam2Color(colorPickerView.color)
                }
            }.setNegativeButton("Cancel", null)
            .show()
    }
}
