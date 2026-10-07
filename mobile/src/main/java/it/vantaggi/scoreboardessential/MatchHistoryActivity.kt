package it.vantaggi.scoreboardessential

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.ui.EmptyStateView
import it.vantaggi.scoreboardessential.ui.MatchHistoryViewModel
import it.vantaggi.scoreboardessential.ui.MatchHistoryViewModelFactory
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

        // Un ViewModel suo, sul solo repository: un MainViewModel qui ne faceva due vivi insieme,
        // e ogni evento dell'orologio veniva applicato da entrambi.
        val application = application as ScoreboardEssentialApplication
        val padelElite = application.padelElite
        val viewModelFactory = MatchHistoryViewModelFactory(application.matchRepository, padelElite)
        val viewModel = ViewModelProvider(this, viewModelFactory)[MatchHistoryViewModel::class.java]

        val recyclerView = findViewById<RecyclerView>(R.id.match_history_recyclerview)
        val emptyState = findViewById<EmptyStateView>(R.id.empty_state)
        // Lo stato vuoto ha una strada: tornare alla partita, da dove si salva.
        emptyState.actionButton.setOnClickListener { finish() }
        val adapter =
            MatchHistoryAdapter(
                onDeleteClicked = { matchWithTeams ->
                    androidx.appcompat.app.AlertDialog
                        .Builder(this)
                        .setTitle(R.string.delete_match)
                        .setMessage(R.string.delete_match_message)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            viewModel.deleteMatch(matchWithTeams.match)
                        }.setNegativeButton(R.string.cancel, null)
                        .show()
                },
                onExportClicked = { matchWithTeams -> exportSavedMatch(viewModel, matchWithTeams) },
                onChronicleClicked = { matchWithTeams ->
                    startActivity(ChronicleActivity.intent(this, matchWithTeams.match.matchId))
                },
                onSendClicked = { matchWithTeams ->
                    matchWithTeams.match.matchUuid?.let { padelElite.sendOrOpenLogin(this, it) }
                },
            )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Le partite inviate e in attesa possono essere state importate o scartate nel frattempo.
        if (padelElite.isEnabled) {
            lifecycleScope.launch {
                if (padelElite.account.session() != null) padelElite.runner.refreshStatuses()
            }
        }

        viewModel.matchHistory.observe(this) { matches ->
            matches?.let {
                adapter.submitList(it)
                recyclerView.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
                emptyState.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    /**
     * Si torna alla partita da cui si e' arrivati, senza ricrearla. La navigazione "su" di
     * AppCompat seguiva parentActivityName e ricreava MainActivity, e con lei il ViewModel della
     * partita: rose perse, service slegato, 0-0 mandato all'orologio.
     */
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /**
     * Esporta una partita gia' chiusa, con la stessa condivisione della schermata di gioco. Il
     * nome del file porta la data della partita, non quella di oggi.
     */
    private fun exportSavedMatch(
        viewModel: MatchHistoryViewModel,
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
