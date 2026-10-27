package it.vantaggi.scoreboardessential.ui.statistics

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import it.vantaggi.scoreboardessential.databinding.ActivityStatisticsBinding
import it.vantaggi.scoreboardessential.sportLabel
import it.vantaggi.scoreboardessential.ui.InsetDividerDecoration
import kotlinx.coroutines.launch

class StatisticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStatisticsBinding
    private val viewModel: StatisticsViewModel by viewModels {
        StatisticsViewModel.Factory(application)
    }
    private lateinit var adapter: StatisticsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        // Lo sport in uso decide la classifica: gol nel calcio, partite vinte nel padel e nel tennis.
        lifecycleScope.launch {
            val sportId = (application as ScoreboardEssentialApplication).matchSettingsRepository.getActiveSport()
            if (StatisticsViewModel.contaIGol(sportId)) observeGoals() else observeWins(sportId)
        }
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = StatisticsAdapter()
        binding.recyclerStats.layoutManager = LinearLayoutManager(this)
        binding.recyclerStats.adapter = adapter
        binding.recyclerStats.addItemDecoration(InsetDividerDecoration(this))
        // Lo stato vuoto ha una strada: tornare alla partita, da dove si salva.
        binding.emptyState.actionButton.setOnClickListener { finish() }

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

    private suspend fun observeGoals() {
        viewModel.topScorers.collect { stats ->
            // Una classifica di soli zeri non e' una classifica: lo stato vuoto e basta.
            val vuota = Classifica.eVuota(stats.map { it.goals })
            binding.statsGroup.visibility = if (vuota) View.GONE else View.VISIBLE
            binding.emptyState.visibility = if (vuota) View.VISIBLE else View.GONE
            if (!vuota) adapter.submitList(stats)
        }
    }

    /** Padel e tennis: niente schede, la riga del titolo e le partite vinte su quelle giocate. */
    private suspend fun observeWins(sportId: String) {
        binding.tabLayout.visibility = View.GONE
        binding.statsHeading.visibility = View.VISIBLE
        binding.statsHeading.text = getString(R.string.stats_wins_heading, sportLabel(this, sportId))
        binding.emptyState.setMessage(getString(R.string.stats_empty_title), getString(R.string.stats_wins_empty_hint))
        val vittorie = RecordAdapter()
        binding.recyclerStats.adapter = vittorie
        viewModel.setSport(sportId)
        viewModel.records.collect { righe ->
            val vuota = Classifica.eVuota(righe.map { it.won })
            binding.statsGroup.visibility = if (vuota) View.GONE else View.VISIBLE
            binding.emptyState.visibility = if (vuota) View.VISIBLE else View.GONE
            if (!vuota) vittorie.submitList(righe)
        }
    }
}
