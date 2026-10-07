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
import it.vantaggi.scoreboardessential.databinding.ActivityStatisticsBinding
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
        observeViewModel()
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

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.topScorers.collect { stats ->
                if (stats.isEmpty()) {
                    binding.statsGroup.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                } else {
                    binding.statsGroup.visibility = View.VISIBLE
                    binding.emptyState.visibility = View.GONE
                    adapter.submitList(stats)
                }
            }
        }
    }
}
