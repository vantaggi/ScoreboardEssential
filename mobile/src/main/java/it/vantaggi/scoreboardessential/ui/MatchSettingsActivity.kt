package it.vantaggi.scoreboardessential.ui

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar
import it.vantaggi.scoreboardessential.DurataPortiere
import it.vantaggi.scoreboardessential.EtichettaSquadra
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import it.vantaggi.scoreboardessential.core.DeuceRule
import it.vantaggi.scoreboardessential.core.RacketRules
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.databinding.ActivityMatchSettingsBinding
import it.vantaggi.scoreboardessential.deuceLabel
import it.vantaggi.scoreboardessential.padelelite.PadelEliteActivity
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.sportLabel
import it.vantaggi.scoreboardessential.sportRulesLine
import it.vantaggi.scoreboardessential.ui.TavolozzaDiSquadra
import it.vantaggi.scoreboardessential.utils.AltoContrasto
import it.vantaggi.scoreboardessential.utils.Aspetto
import it.vantaggi.scoreboardessential.utils.LocaleHelper
import it.vantaggi.scoreboardessential.utils.ScelteAspetto
import it.vantaggi.scoreboardessential.utils.anteprimaDiSquadra
import it.vantaggi.scoreboardessential.utils.dipingiDiSquadra

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
        setupDeuceDropdown()
        observeViewModel()
        setupListeners()
        setupPadelElite()
        setupAltoContrasto()
        setupAspetto()
    }

    override fun onResume() {
        super.onResume()
        // Si torna dalla schermata di accesso: la card dice chi e' collegato e il gruppo.
        showPadelEliteStatus()
    }

    private val padelElite get() = (application as ScoreboardEssentialApplication).padelElite

    /**
     * Il gruppo di Padel Elite c'e' solo se la build conosce il Supabase: senza, l'app e' identica a prima.
     * E' una riga che porta alla schermata di Padel Elite, dove si accede, si sceglie il gruppo e si esce.
     */
    private fun setupPadelElite() {
        if (!padelElite.isEnabled) return
        binding.padelEliteCard.visibility = View.VISIBLE
        binding.padelEliteRow.setOnClickListener { startActivity(PadelEliteActivity.intent(this)) }
    }

    /**
     * L'interruttore dell'alto contrasto (G-10) mostra lo stato in vigore, sia scelto sia dal sistema.
     * Toccarlo salva la scelta (anche "spento") e ricrea la schermata col tema nuovo. Non salva il suo
     * stato: se il contrasto di sistema cambia mentre l'activity e' ferma, il valore ripristinato
     * verrebbe scambiato per una scelta dell'utente.
     */
    private fun setupAltoContrasto() {
        binding.highContrastSwitch.isSaveEnabled = false
        binding.highContrastSwitch.isChecked = AltoContrasto.attivo(this)
        binding.highContrastSwitch.setOnCheckedChangeListener { _, attivo ->
            AltoContrasto.scegli(this, attivo)
            recreate()
        }
    }

    /**
     * L'aspetto: scuro, chiaro o come il telefono. Il valore e' la scelta; con l'alto contrasto acceso (solo scuro) il
     * chiaro resta sospeso e la riga lo dice. Cambiare ricrea la schermata col tema nuovo; le altre activity le ricrea
     * chi applica il tema ([Aspetto]) quando tornano in primo piano.
     */
    private fun setupAspetto() {
        val scelte = ScelteAspetto.entries

        fun nome(scelta: ScelteAspetto) = getString(Aspetto.nomeDi(scelta))

        fun valore(): String {
            val scelta = Aspetto.scelta(this)
            val sospeso = AltoContrasto.attivo(this) && scelta != ScelteAspetto.SCURO
            return if (sospeso) getString(R.string.appearance_value_suspended, nome(scelta)) else nome(scelta)
        }
        binding.appearanceValue.text = valore()
        binding.appearanceRow.setOnClickListener {
            scegliDaElenco(getString(R.string.appearance_title), scelte.map(::nome), scelte.indexOf(Aspetto.scelta(this))) { posizione ->
                if (scelte[posizione] != Aspetto.scelta(this)) {
                    Aspetto.scegli(this, scelte[posizione])
                    recreate()
                }
            }
        }
    }

    private fun showPadelEliteStatus() {
        if (!padelElite.isEnabled) return
        val sessione = padelElite.account.session()
        binding.padelEliteRowTitle.setText(if (sessione == null) R.string.padel_elite_login_title else R.string.padel_elite_groups_title)
        binding.padelEliteStatus.text =
            if (sessione == null) {
                getString(R.string.padel_elite_settings_signed_out)
            } else {
                val gruppo =
                    padelElite.account
                        .selectedGroup()
                        ?.second
                        ?.ifEmpty { null } ?: getString(R.string.padel_elite_settings_no_group)
                getString(R.string.padel_elite_settings_signed_in, sessione.email, gruppo)
            }
    }

    /**
     * Si torna alla partita da cui si e' arrivati, senza ricrearla. La navigazione "su" di
     * AppCompat seguiva parentActivityName e ricreava MainActivity, e con lei il ViewModel della
     * partita.
     */
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupLanguageDropdown() {
        val lingue = listOf("en" to "English", "it" to "Italiano")
        // La riga mostra la lingua che la schermata sta davvero usando, anche quando nessuno
        // l'ha scelta e viene dal sistema: non c'e' una seconda copia salvata che possa smentirla.
        binding.languageValue.text = lingue.first { it.first == LocaleHelper.currentLanguage(this) }.second

        binding.languageRow.setOnClickListener {
            val corrente = lingue.indexOfFirst { it.first == LocaleHelper.currentLanguage(this) }
            scegliDaElenco(getString(R.string.language), lingue.map { it.second }, corrente) { posizione ->
                val scelta = lingue[posizione].first
                if (LocaleHelper.currentLanguage(this) != scelta) {
                    // Niente recreate(): le Activity aperte le ricrea chi applica la lingua, il
                    // sistema da API 33 e AppCompat sotto.
                    LocaleHelper.apply(scelta)
                }
            }
        }
    }

    private fun setupSportDropdown() {
        val sportIds = SportRegistry.selectable().map { it.id }
        binding.sportRow.setOnClickListener {
            val corrente = sportIds.indexOf(viewModel.activeSport.value)
            scegliDaElenco(getString(R.string.settings_sport_title), sportIds.map { sportLabel(this, it) }, corrente) { posizione ->
                val selectedSport = sportIds[posizione]
                if (viewModel.activeSport.value != selectedSport) {
                    viewModel.saveActiveSport(selectedSport)
                }
            }
        }
    }

    /** Le tre regole del 40-40, nell'ordine di [DeuceRule]; il nome si legge da [etichettaDel40]. */
    private fun setupDeuceDropdown() {
        val regole = DeuceRule.entries
        binding.deuceRuleLayout.setOnClickListener {
            val corrente = regole.indexOf(viewModel.deuceRule.value)
            scegliDaElenco(getString(R.string.settings_deuce_title), regole.map { etichettaDel40(it) }, corrente) { posizione ->
                val scelta = regole[posizione]
                if (viewModel.deuceRule.value != scelta) viewModel.saveDeuceRule(scelta)
            }
        }
    }

    private fun etichettaDel40(regola: DeuceRule): String = deuceLabel(this, regola)

    private fun observeViewModel() {
        // Il nome predefinito ("Team 1" nei dati) non si scrive nel campo: il campo resta vuoto e
        // dice "Nome squadra 1", che e' la verita' (nessun nome scelto) nella lingua in uso.
        viewModel.team1Name.observe(this) { name ->
            val testo = if (EtichettaSquadra.predefinito(name)) "" else name
            if (binding.team1NameEdittext.text.toString() != testo) {
                binding.team1NameEdittext.setText(testo)
            }
        }

        viewModel.team2Name.observe(this) { name ->
            val testo = if (EtichettaSquadra.predefinito(name)) "" else name
            if (binding.team2NameEdittext.text.toString() != testo) {
                binding.team2NameEdittext.setText(testo)
            }
        }

        // Sfondo, scritta e icona insieme: lo sfondo da solo lasciava il testo quasi bianco sul
        // giallo e sul verde predefiniti.
        viewModel.team1Color.observe(this) { color ->
            binding.team1ColorButton.dipingiDiSquadra(color)
        }

        viewModel.team2Color.observe(this) { color ->
            binding.team2ColorButton.dipingiDiSquadra(color)
        }

        viewModel.keeperTimerDuration.observe(this) { duration ->
            // Il ViewModel e' la verita': l'interruttore e il campo si allineano senza riscrivere.
            aggiornandoIlPortiere = true
            binding.keeperSwitch.isChecked = duration > 0
            binding.keeperTimerLayout.visibility = if (duration > 0) android.view.View.VISIBLE else android.view.View.GONE
            if (duration > 0) {
                val testo = DurataPortiere.minutiDi(duration)
                if (binding.keeperTimerEdittext.text.toString() != testo) binding.keeperTimerEdittext.setText(testo)
            }
            aggiornandoIlPortiere = false
        }

        viewModel.keeperUltimaDurata.observe(this) { if (it > 0) ultimaDurataPortiere = it }

        // La voce mostrata segue sempre il ViewModel: se una scrittura non passasse, il selettore
        // tornerebbe da solo sulla scelta precedente invece di mentire.
        viewModel.sportChangeBlocked.observe(this) {
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, R.string.sport_change_blocked, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .show()
        }

        viewModel.deuceChangeBlocked.observe(this) {
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, R.string.deuce_change_blocked, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .show()
        }

        // Come lo sport: la voce mostrata e la riga delle regole seguono il ViewModel.
        viewModel.deuceRule.observe(this) { regola ->
            binding.deuceValue.text = etichettaDel40(regola)
            viewModel.activeSport.value?.let { binding.sportRulesText.text = sportRulesLine(this, it, regola) }
        }

        viewModel.activeSport.observe(this) { sportId ->
            binding.sportValue.text = sportLabel(this, sportId)
            binding.sportRulesText.text = sportRulesLine(this, sportId, viewModel.deuceRule.value)
            // Il 40-40 esiste solo negli sport con racchetta.
            binding.deuceRuleLayout.visibility =
                if (SportRegistry.byId(sportId) is RacketRules) android.view.View.VISIBLE else android.view.View.GONE
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

    /** Vero mentre l'interruttore e il campo si allineano al ViewModel: il cambio non e' un tocco. */
    private var aggiornandoIlPortiere = false

    /** L'ultima durata acceso, per quando l'interruttore si riaccende. */
    private var ultimaDurataPortiere = DurataPortiere.PREDEFINITA_SECONDI

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

        // Acceso ripropone l'ultima durata scelta (cinque minuti la prima volta); spento scrive 0.
        binding.keeperSwitch.isSaveEnabled = false
        binding.keeperSwitch.setOnCheckedChangeListener { _, acceso ->
            if (!aggiornandoIlPortiere) viewModel.saveKeeperTimerDuration(if (acceso) ultimaDurataPortiere else 0L)
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

        // Il tasto fatto della tastiera salva: prima il valore restava nel campo fino a quando non si usciva.
        binding.keeperTimerEdittext.setOnEditorActionListener { campo, azione, _ ->
            if (azione == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                salvaCampi()
                campo.clearFocus()
            }
            false
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
        // Spento non c'e' niente da leggere: il campo e' nascosto e scriverlo riaccenderebbe il portiere.
        if (binding.keeperSwitch.isChecked) {
            val scritta = DurataPortiere.secondiDi(binding.keeperTimerEdittext.text.toString())
            viewModel.saveKeeperTimerDuration(scritta ?: ultimaDurataPortiere)
        }
    }

    override fun onPause() {
        super.onPause()
        // Chi esce con la freccia o col gesto non deve perdere quello che ha appena scritto.
        salvaCampi()
    }

    /**
     * Il colore della squadra (passo 7): prima otto colori gia' provati, con la spunta su quello in uso;
     * "Usa questo colore" e' l'unico primario. La ruota resta dietro "Altro colore".
     */
    private fun showColorPickerDialog(team: Int) {
        val vista = layoutInflater.inflate(R.layout.dialog_team_palette, null)
        val attuale = (if (team == 1) viewModel.team1Color.value else viewModel.team2Color.value) ?: getColor(R.color.team_side_1)
        // Un colore della ruota fuori dalla tavolozza non ha cerchio segnato: "Usa questo colore" lo conferma com'e'.
        var scelto = attuale
        val cerchi = TavolozzaDiSquadra.cerchi.map { vista.findViewById<MaterialButton>(it) }
        val segnaIlScelto = {
            cerchi.forEachIndexed { i, cerchio -> TavolozzaDiSquadra.segna(cerchio, getColor(TavolozzaDiSquadra.colori[i]) == scelto) }
        }
        cerchi.forEachIndexed { i, cerchio ->
            val colore = getColor(TavolozzaDiSquadra.colori[i])
            cerchio.dipingiDiSquadra(colore)
            cerchio.setOnClickListener {
                scelto = colore
                segnaIlScelto()
            }
        }
        segnaIlScelto()

        val dialogo =
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.choose_team_color, team))
                .setView(vista)
                .setPositiveButton(getString(R.string.team_color_use)) { _, _ -> salvaColore(team, scelto) }
                .setNegativeButton(getString(R.string.cancel), null)
                .create()
        vista.findViewById<View>(R.id.team_color_other_button).setOnClickListener {
            dialogo.dismiss()
            showColorWheelDialog(team)
        }
        dialogo.show()
    }

    private fun salvaColore(
        team: Int,
        colore: Int,
    ) {
        if (team == 1) viewModel.saveTeam1Color(colore) else viewModel.saveTeam2Color(colore)
    }

    /** La ruota di tutti i colori, dietro "Altro colore". */
    private fun showColorWheelDialog(team: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val colorPickerView = dialogView.findViewById<ColorPickerView>(R.id.colorPickerView)
        val brightnessSlideBar = dialogView.findViewById<BrightnessSlideBar>(R.id.brightnessSlide)

        colorPickerView.attachBrightnessSlider(brightnessSlideBar)

        // L'anteprima parte dal colore attuale e segue la ruota a ogni movimento.
        val anteprima = dialogView.findViewById<TextView>(R.id.colorPreview)
        val nome =
            EtichettaSquadra.mostrato(
                if (team == 1) viewModel.team1Name.value else viewModel.team2Name.value,
                getString(if (team == 1) R.string.label_team_1 else R.string.label_team_2),
            )
        val attuale = (if (team == 1) viewModel.team1Color.value else viewModel.team2Color.value) ?: getColor(R.color.team_side_1)
        anteprima.anteprimaDiSquadra(attuale, nome)
        colorPickerView.setColorListener(
            ColorEnvelopeListener { envelope, _ -> anteprima.anteprimaDiSquadra(envelope.color, nome) },
        )
        // Senza un colore di partenza la ruota, alla prima misura, sceglie il centro e manda il
        // bianco al listener: anteprima e colore salvato partirebbero da bianco, non dalla squadra.
        colorPickerView.setInitialColor(attuale)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.choose_team_color, team)) // Format string
            .setView(dialogView)
            .setPositiveButton(getString(R.string.team_color_use)) { _, _ -> salvaColore(team, colorPickerView.color) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
