package it.vantaggi.scoreboardessential.ui.statistics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.sportLabel
import it.vantaggi.scoreboardessential.utils.enableEdgeToEdgeConAspetto
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * La scheda di un giocatore (bozza B delle Statistiche per sport): un blocco per ogni sport che ha
 * giocato, con partite, vinte e, dove si contano, i gol. Si apre toccando una riga delle Statistiche.
 * Il titolo e' il nome del giocatore; senza partite c'e' lo stato vuoto che dice come averle.
 */
class SchedaGiocatoreActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeConAspetto()
        setContentView(R.layout.activity_scheda_giocatore)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.sheet_root)) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            windowInsets
        }

        val playerId = intent.getIntExtra(EXTRA_PLAYER_ID, -1)
        val database = AppDatabase.getDatabase(application)
        lifecycleScope.launch {
            val scheda =
                combine(
                    database.playerDao().getPlayerWithRoles(playerId),
                    database.matchDao().getPlayerSportCounts(),
                ) { giocatore, conteggi ->
                    giocatore?.player to giocatore?.let { SchedaDelGiocatore.blocchi(it.player, conteggi) }.orEmpty()
                }
            scheda.collect { (player, blocchi) ->
                if (player == null) {
                    // Il giocatore e' stato cancellato mentre la scheda era aperta: non c'e' piu' niente da mostrare.
                    finish()
                    return@collect
                }
                supportActionBar?.title = player.playerName
                mostra(blocchi)
            }
        }
    }

    private fun mostra(blocchi: List<BloccoDelloSport>) {
        val contenuto = findViewById<LinearLayout>(R.id.sheet_content)
        contenuto.removeAllViews()
        findViewById<View>(R.id.sheet_scroll).visibility = if (blocchi.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.empty_state).visibility = if (blocchi.isEmpty()) View.VISIBLE else View.GONE
        blocchi.forEach { contenuto.addView(vistaDelBlocco(it, contenuto)) }
    }

    private fun vistaDelBlocco(
        blocco: BloccoDelloSport,
        genitore: ViewGroup,
    ): View {
        val inflater = LayoutInflater.from(this)
        val vista = inflater.inflate(R.layout.item_sport_block, genitore, false)
        vista.findViewById<TextView>(R.id.block_title).text = sportLabel(this, blocco.sportId)
        val righe = vista.findViewById<LinearLayout>(R.id.block_rows)
        val valori =
            listOfNotNull(
                getString(R.string.sheet_matches) to blocco.partite,
                getString(R.string.sheet_won) to blocco.vinte,
                blocco.gol?.let { getString(R.string.sheet_goals) to it },
            )
        valori.forEachIndexed { i, (nome, valore) ->
            val riga = inflater.inflate(R.layout.item_player_stat, righe, false)
            riga.findViewById<TextView>(R.id.text_player_name).text = nome
            riga.findViewById<TextView>(R.id.text_goals).text = valore.toString()
            riga.contentDescription = getString(R.string.sheet_row_description, nome, valore)
            righe.addView(riga)
            if (i < valori.lastIndex) righe.addView(linea())
        }
        return vista
    }

    /** La linea sottile fra le righe, rientrata fino al testo come quella delle liste ([InsetDividerDecoration]). */
    private fun linea(): View {
        val parametri =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.border_width),
            )
        parametri.marginStart = resources.getDimensionPixelSize(R.dimen.space_16)
        return View(this).apply {
            setBackgroundColor(getColor(R.color.elite_border_strong))
            layoutParams = parametri
        }
    }

    companion object {
        const val EXTRA_PLAYER_ID = "player_id"
    }
}
