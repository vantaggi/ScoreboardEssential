package it.vantaggi.scoreboardessential

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.ui.chronicle.ChronicleActivity
import it.vantaggi.scoreboardessential.utils.MatchExportUtils
import kotlinx.coroutines.launch

class MatchHistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_match_history)
        // La stessa barra con la stessa freccia di PlayersManagementActivity e
        // AddEditPlayerActivity. parentActivityName e' gia' dichiarato nel manifest per tutte
        // e cinque le schermate: mancava solo chi lo usasse, e due di queste non avevano
        // NESSUN modo di tornare indietro che non fosse il gesto di sistema.
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Edge-to-edge: gli insets di sistema diventano padding del contenitore radice.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.match_history_root)) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            windowInsets
        }

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
            MatchHistoryAdapter(
                onDeleteClicked = { matchWithTeams ->
                    androidx.appcompat.app.AlertDialog
                        .Builder(this)
                        .setTitle("Delete Match")
                        .setMessage("Are you sure you want to delete this match log?")
                        .setPositiveButton("Delete") { _, _ ->
                            viewModel.deleteMatch(matchWithTeams.match)
                        }.setNegativeButton("Cancel", null)
                        .show()
                },
                onExportClicked = { matchWithTeams -> exportSavedMatch(viewModel, matchWithTeams) },
                onChronicleClicked = { matchWithTeams ->
                    startActivity(ChronicleActivity.intent(this, matchWithTeams.match.matchId))
                },
            )
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

    /**
     * Esporta una partita gia' chiusa, con la stessa condivisione della schermata di gioco. Il
     * nome del file porta la data della partita, non quella di oggi.
     */
    private fun exportSavedMatch(
        viewModel: MainViewModel,
        matchWithTeams: MatchWithTeams,
    ) {
        val match = matchWithTeams.match
        val etichetta = "${match.sportId}-${matchWithTeams.team1?.name ?: "Team 1"}-vs-${matchWithTeams.team2?.name ?: "Team 2"}"
        lifecycleScope.launch {
            val esito = viewModel.buildSavedExport(match.matchId) ?: return@launch
            MatchExportUtils.shareExport(this@MatchHistoryActivity, esito, etichetta, match.startedAt ?: match.timestamp)
        }
    }
}
