package it.vantaggi.scoreboardessential.ui.statistics

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import it.vantaggi.scoreboardessential.databinding.ActivityStatisticsBinding
import kotlinx.coroutines.launch

class StatisticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStatisticsBinding
    private val viewModel: StatisticsViewModel by viewModels {
        StatisticsViewModel.Factory(application)
    }
    private lateinit var adapter: StatisticsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = StatisticsAdapter()
        binding.recyclerStats.layoutManager = LinearLayoutManager(this)
        binding.recyclerStats.adapter = adapter

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Generale"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Attacco"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Difesa"))

        binding.tabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    // Filter logic can be implemented here or in ViewModel
                    // For now, we just show general stats
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
                    binding.recyclerStats.visibility = View.GONE
                    binding.emptyStateGroup.visibility = View.VISIBLE
                } else {
                    binding.recyclerStats.visibility = View.VISIBLE
                    binding.emptyStateGroup.visibility = View.GONE
                    adapter.submitList(stats)
                }
            }
        }
    }
}
