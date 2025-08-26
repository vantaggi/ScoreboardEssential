package com.example.scoreboardessential

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scoreboardessential.database.AppDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MatchHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_match_history)

        val viewModelFactory = MainViewModel.MainViewModelFactory((application as ScoreboardEssentialApplication).matchRepository, application)
        val viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        val summaryTextView = findViewById<TextView>(R.id.summary_textview)
        val recyclerView = findViewById<RecyclerView>(R.id.match_history_recyclerview)
        val adapter = MatchHistoryAdapter { matchWithTeams ->
            viewModel.deleteMatch(matchWithTeams.match)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        viewModel.allMatches.observe(this) { matches ->
            matches?.let {
                adapter.submitList(it)
                summaryTextView.text = "TOTAL MATCHES: ${it.size}"
            }
        }
    }
}
