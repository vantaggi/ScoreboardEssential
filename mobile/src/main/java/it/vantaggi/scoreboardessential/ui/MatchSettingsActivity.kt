package it.vantaggi.scoreboardessential.ui

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.databinding.ActivityMatchSettingsBinding
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.sportLabel
import it.vantaggi.scoreboardessential.utils.LocaleHelper

class MatchSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMatchSettingsBinding
    private val viewModel: MatchSettingsViewModel by viewModels {
        MatchSettingsViewModelFactory(
            (application as it.vantaggi.scoreboardessential.ScoreboardEssentialApplication).matchSettingsRepository,
            it.vantaggi.scoreboardessential.database.AppDatabase
                .getDatabase(application)
                .matchDao(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // La stessa barra con la stessa freccia di PlayersManagementActivity e
        // AddEditPlayerActivity. parentActivityName e' gia' dichiarato nel manifest per tutte
        // e cinque le schermate: mancava solo chi lo usasse, e due di queste non avevano
        // NESSUN modo di tornare indietro che non fosse il gesto di sistema.
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupLanguageDropdown() // setup before observing
        setupSportDropdown()
        observeViewModel()
        setupListeners()
    }

    private fun setupLanguageDropdown() {
        val languages = listOf("English", "Italiano")
        val adapter =
            android.widget.ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                languages,
            )
        (binding.languageAutoComplete as? android.widget.AutoCompleteTextView)?.setAdapter(adapter)

        // Il selettore mostra la lingua che la schermata sta davvero usando, anche quando nessuno
        // l'ha scelta e viene dal sistema: non c'e' una seconda copia salvata che possa smentirla.
        val shown = LocaleHelper.currentLanguage(this)
        binding.languageAutoComplete.setText(if (shown == "it") "Italiano" else "English", false)

        binding.languageAutoComplete.setOnItemClickListener { _, _, position, _ ->
            val selectedLang = if (position == 0) "en" else "it"
            if (LocaleHelper.currentLanguage(this) != selectedLang) {
                // Niente recreate(): le Activity aperte le ricrea chi applica la lingua, il
                // sistema da API 33 e AppCompat sotto.
                LocaleHelper.apply(selectedLang)
            }
        }
    }

    private fun setupSportDropdown() {
        val sportIds = SportRegistry.selectable().map { it.id }
        val adapter =
            android.widget.ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                sportIds.map { sportLabel(this, it) },
            )
        binding.sportAutoComplete.setAdapter(adapter)

        binding.sportAutoComplete.setOnItemClickListener { _, _, position, _ ->
            val selectedSport = sportIds[position]
            if (viewModel.activeSport.value != selectedSport) {
                viewModel.saveActiveSport(selectedSport)
            }
        }
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

        // La voce mostrata segue sempre il ViewModel: se una scrittura non passasse, il selettore
        // tornerebbe da solo sulla scelta precedente invece di mentire.
        viewModel.sportChangeBlocked.observe(this) {
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, R.string.sport_change_blocked, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .show()
        }

        viewModel.activeSport.observe(this) { sportId ->
            val label = sportLabel(this, sportId)
            if (binding.sportAutoComplete.text.toString() != label) {
                binding.sportAutoComplete.setText(label, false)
            }
            // Le stesse capacita' che la schermata principale usa per spegnere il cronometro:
            // nel padel non c'e' un portiere da cambiare, quindi non c'e' niente da impostare.
            // Prima il campo restava li' a chiedere un numero che nessuno avrebbe mai usato.
            binding.keeperTimerCard.visibility =
                if (SportRegistry.byId(sportId).capabilities.hasAuxCountdown) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
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

        // Nella stessa schermata sport e lingua si applicavano SUBITO mentre nomi, colori e
        // secondi del portiere aspettavano un pulsante SALVA: due modelli mentali in un posto
        // solo, e nessun modo di sapere quale valesse per il controllo che si stava toccando.
        // Ora e' tutto immediato, che e' anche la forma giusta per una schermata che si apre a
        // bordo campo per cambiare una cosa sola.
        listOf(binding.team1NameEdittext, binding.team2NameEdittext, binding.keeperTimerEdittext)
            .forEach { campo ->
                campo.setOnFocusChangeListener { _, haIlFuoco -> if (!haIlFuoco) salvaCampi() }
            }
    }

    /**
     * Salva i tre campi di testo.
     *
     * Alla perdita del fuoco e in onPause, non a ogni battuta: scrivere a ogni carattere
     * significherebbe un giro di preferenze per lettera, e un nome a meta' spedito all'orologio.
     */
    private fun salvaCampi() {
        viewModel.saveTeam1Name(binding.team1NameEdittext.text.toString())
        viewModel.saveTeam2Name(binding.team2NameEdittext.text.toString())
        viewModel.saveKeeperTimerDuration(
            binding.keeperTimerEdittext.text
                .toString()
                .toLongOrNull() ?: 30L,
        )
    }

    override fun onPause() {
        super.onPause()
        // Chi esce con la freccia o col gesto non deve perdere quello che ha appena scritto.
        salvaCampi()
    }

    private fun showColorPickerDialog(team: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val colorPickerView = dialogView.findViewById<ColorPickerView>(R.id.colorPickerView)
        val brightnessSlideBar = dialogView.findViewById<BrightnessSlideBar>(R.id.brightnessSlide)

        colorPickerView.attachBrightnessSlider(brightnessSlideBar)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.choose_team_color, team)) // Format string
            .setView(dialogView)
            .setPositiveButton(getString(R.string.select)) { _, _ ->
                if (team == 1) {
                    viewModel.saveTeam1Color(colorPickerView.color)
                } else {
                    viewModel.saveTeam2Color(colorPickerView.color)
                }
            }.setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
