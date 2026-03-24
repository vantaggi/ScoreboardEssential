package it.vantaggi.scoreboardessential

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MatchHistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_match_history)

        val application = application as ScoreboardEssentialApplication
        val viewModelFactory =
            MainViewModel.MainViewModelFactory(
                application.matchRepository,
                application.userPreferencesRepository,
                application.matchSettingsRepository,
                application,
            )
        val viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        val summaryTextView = findViewById<TextView>(R.id.summary_textview)
        val recyclerView = findViewById<RecyclerView>(R.id.match_history_recyclerview)
        val emptyStateTextView = findViewById<TextView>(R.id.empty_state_textview)
        val adapter =
            MatchHistoryAdapter { matchWithTeams ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Delete Match")
                    .setMessage("Are you sure you want to delete this match log?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteMatch(matchWithTeams.match)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        viewModel.matchHistory.observe(this) { matches ->
            matches?.let {
                adapter.submitList(it)
                summaryTextView.text = "TOTAL MATCHES: ${it.size}"
                if (it.isEmpty()) {
                    recyclerView.visibility = android.view.View.GONE
                    emptyStateTextView.visibility = android.view.View.VISIBLE
                } else {
                    recyclerView.visibility = android.view.View.VISIBLE
                    emptyStateTextView.visibility = android.view.View.GONE
                }
            }
        }
    }
}
