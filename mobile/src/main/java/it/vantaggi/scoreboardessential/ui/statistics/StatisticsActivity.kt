package it.vantaggi.scoreboardessential.ui.statistics

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.databinding.ActivityStatisticsBinding
import it.vantaggi.scoreboardessential.sportLabel
import it.vantaggi.scoreboardessential.ui.InsetDividerDecoration
import it.vantaggi.scoreboardessential.utils.enableEdgeToEdgeConAspetto
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class StatisticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStatisticsBinding
    private val viewModel: StatisticsViewModel by viewModels {
        StatisticsViewModel.Factory(application)
    }
    private lateinit var goalsAdapter: StatisticsAdapter
    private lateinit var winsAdapter: RecordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeConAspetto()
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // La stessa barra con la stessa freccia di PlayersManagementActivity e
        // AddEditPlayerActivity. parentActivityName e' gia' dichiarato nel manifest per tutte
        // e cinque le schermate: mancava solo chi lo usasse, e due di queste non avevano
        // NESSUN modo di tornare indietro che non fosse il gesto di sistema.
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Edge-to-edge: gli insets di sistema diventano padding del contenitore radice,
        // cosi' la toolbar scende sotto la status bar e la lista si ferma sopra la barra di navigazione.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            windowInsets
        }

        setupUI()
        // Si parte dallo sport in uso (gol nel calcio, partite vinte nel padel e nel tennis); poi lo
        // sport lo sceglie la schermata. Dopo una rotazione il ViewModel ricorda quello guardato.
        lifecycleScope.launch {
            if (viewModel.sport.value == null) {
                viewModel.setSport((application as ScoreboardEssentialApplication).matchSettingsRepository.getActiveSport())
            }
            viewModel.sport.filterNotNull().collectLatest { sportId ->
                segnaLoSport(sportId)
                if (StatisticsViewModel.contaIGol(sportId)) observeGoals() else observeWins()
            }
        }
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        goalsAdapter = StatisticsAdapter(::apriLaScheda)
        winsAdapter = RecordAdapter(::apriLaScheda)
        binding.recyclerStats.layoutManager = LinearLayoutManager(this)
        binding.recyclerStats.adapter = goalsAdapter
        binding.recyclerStats.addItemDecoration(InsetDividerDecoration(this))
        // Lo stato vuoto ha una strada: tornare alla partita, da dove si salva.
        binding.emptyState.actionButton.setOnClickListener { finish() }

        setupSportChips()

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.stats_tab_general))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.stats_tab_attack))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.stats_tab_defense))

        binding.tabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    when (tab?.position) {
                        0 -> viewModel.setFilter(StatisticsViewModel.FilterType.ALL)
                        1 -> viewModel.setFilter(StatisticsViewModel.FilterType.ATTACK)
                        2 -> viewModel.setFilter(StatisticsViewModel.FilterType.DEFENSE)
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {}
            },
        )
    }

    /** Toccando una riga si apre la scheda del giocatore, con un blocco per ogni sport che ha giocato. */
    private fun apriLaScheda(playerId: Int) {
        startActivity(
            Intent(this, SchedaGiocatoreActivity::class.java).putExtra(SchedaGiocatoreActivity.EXTRA_PLAYER_ID, playerId),
        )
    }

    /** Un chip per sport, gli stessi dei filtri per ruolo dei Giocatori: se ne sceglie uno solo. */
    private fun setupSportChips() {
        SportRegistry.selectable().forEach { regole ->
            val chip =
                (layoutInflater.inflate(R.layout.item_chip_filter, binding.sportChipGroup, false) as Chip).apply {
                    text = sportLabel(this@StatisticsActivity, regole.id)
                    id = View.generateViewId()
                    tag = regole.id
                    isCheckable = true
                }
            binding.sportChipGroup.addView(chip)
        }
        binding.sportChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val scelto = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it)?.tag as? String }
            if (scelto != null) viewModel.setSport(scelto)
        }
    }

    /** Il chip dello sport guardato, anche quando lo sport arriva dalle Impostazioni e non da un tocco. */
    private fun segnaLoSport(sportId: String) {
        val chip =
            binding.sportChipGroup.children
                .filterIsInstance<Chip>()
                .firstOrNull { it.tag == sportId }
        if (chip != null && !chip.isChecked) binding.sportChipGroup.check(chip.id)
    }

    /** Il calcio: le tre schede, i gol di chi ha giocato a calcio. */
    private suspend fun observeGoals() {
        binding.tabLayout.visibility = View.VISIBLE
        binding.statsHeading.visibility = View.GONE
        binding.emptyState.setMessage(getString(R.string.stats_empty_title), getString(R.string.stats_empty_hint))
        binding.recyclerStats.adapter = goalsAdapter
        viewModel.topScorers.collect { stats ->
            // Una classifica di soli zeri non e' una classifica: lo stato vuoto e basta.
            val vuota = Classifica.eVuota(stats.map { it.goals })
            binding.statsGroup.visibility = if (vuota) View.GONE else View.VISIBLE
            binding.emptyState.visibility = if (vuota) View.VISIBLE else View.GONE
            if (!vuota) goalsAdapter.submitList(stats)
        }
    }

    /**
     * Padel e tennis: niente schede, la riga del titolo e le partite vinte su quelle giocate. Lo sport
     * lo dice il chip scelto, e il titolo non lo ripete.
     */
    private suspend fun observeWins() {
        binding.tabLayout.visibility = View.GONE
        binding.statsHeading.visibility = View.VISIBLE
        binding.statsHeading.text = getString(R.string.stats_wins_heading)
        binding.emptyState.setMessage(getString(R.string.stats_empty_title), getString(R.string.stats_wins_empty_hint))
        binding.recyclerStats.adapter = winsAdapter
        viewModel.records.collect { righe ->
            val vuota = Classifica.eVuota(righe.map { it.won })
            binding.statsGroup.visibility = if (vuota) View.GONE else View.VISIBLE
            binding.emptyState.visibility = if (vuota) View.VISIBLE else View.GONE
            if (!vuota) winsAdapter.submitList(righe)
        }
    }
}
