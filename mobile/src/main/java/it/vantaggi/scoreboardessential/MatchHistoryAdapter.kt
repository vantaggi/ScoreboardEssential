package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.vantaggi.scoreboardessential.core.RacketRules
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.ui.MatchHistoryUiState
import it.vantaggi.scoreboardessential.ui.chronicle.ChronicleText
import it.vantaggi.scoreboardessential.utils.etichettaDiSquadra
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MatchHistoryAdapter(
    private val onDeleteClicked: (MatchWithTeams) -> Unit,
    private val onExportClicked: (MatchWithTeams) -> Unit,
    private val onChronicleClicked: (MatchWithTeams) -> Unit,
) : ListAdapter<MatchHistoryUiState, MatchHistoryAdapter.MatchViewHolder>(MatchDiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): MatchViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.match_item, parent, false)
        return MatchViewHolder(view, onDeleteClicked, onExportClicked, onChronicleClicked)
    }

    override fun onBindViewHolder(
        holder: MatchViewHolder,
        position: Int,
    ) {
        val item = getItem(position)
        holder.bind(item)
    }

    class MatchViewHolder(
        itemView: View,
        private val onDeleteClicked: (MatchWithTeams) -> Unit,
        private val onExportClicked: (MatchWithTeams) -> Unit,
        private val onChronicleClicked: (MatchWithTeams) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val team1NameTextView: TextView = itemView.findViewById(R.id.team1_name_textview)
        private val team2NameTextView: TextView = itemView.findViewById(R.id.team2_name_textview)
        private val team1ScoreTextView: TextView = itemView.findViewById(R.id.team1_score_textview)
        private val team2ScoreTextView: TextView = itemView.findViewById(R.id.team2_score_textview)
        private val timestampTextView: TextView = itemView.findViewById(R.id.timestamp_textview)
        private val setsTextView: TextView = itemView.findViewById(R.id.sets_textview)
        private val playersTextView: TextView = itemView.findViewById(R.id.players_textview)
        private val deleteButton: View = itemView.findViewById(R.id.delete_match_button)
        private val exportButton: View = itemView.findViewById(R.id.export_match_button)
        private val chronicleButton: View = itemView.findViewById(R.id.chronicle_match_button)

        fun bind(item: MatchHistoryUiState) {
            val matchWithTeams = item.matchWithTeams
            val context = itemView.context
            val match = matchWithTeams.match

            // L'etichetta porta il colore con cui si e' giocato (Team.color), con l'inchiostro di
            // TeamInk: non piu' giallo e verde fissi. Se la squadra non c'e' piu' ripiega sui
            // predefiniti.
            team1NameTextView.text = matchWithTeams.team1?.name ?: "Team 1"
            team2NameTextView.text = matchWithTeams.team2?.name ?: "Team 2"
            team1NameTextView.etichettaDiSquadra(matchWithTeams.team1?.color ?: context.getColor(R.color.team_spray_yellow))
            team2NameTextView.etichettaDiSquadra(matchWithTeams.team2?.color ?: context.getColor(R.color.team_electric_green))
            team1ScoreTextView.text = match.team1Score.toString()
            team2ScoreTextView.text = match.team2Score.toString()

            // Chi ha vinto si legge senza il colore: #E0E0E0 contro #9E9E9E. Un pareggio li lascia
            // tutti e due chiari.
            val vincitore = item.winnerSide
            team1ScoreTextView.setTextColor(context.getColor(coloreDelPunteggio(vincitore, 1)))
            team2ScoreTextView.setTextColor(context.getColor(coloreDelPunteggio(vincitore, 2)))

            timestampTextView.text = metaDellaPartita(context, item)

            val righeDiSet =
                item.setLine
                    ?: if (SportRegistry.byId(match.sportId) is RacketRules) sportRulesLine(context, match.sportId) else null
            setsTextView.visibility = if (righeDiSet != null) View.VISIBLE else View.GONE
            setsTextView.text = righeDiSet

            if (item.playerNames.isNotEmpty()) {
                playersTextView.visibility = View.VISIBLE
                playersTextView.text = context.getString(R.string.history_players, item.playerNames)
            } else {
                playersTextView.visibility = View.GONE
            }

            deleteButton.setOnClickListener {
                onDeleteClicked(matchWithTeams)
            }

            // Le card si riciclano: la visibilita' va scritta in entrambi i casi.
            exportButton.visibility = if (item.canExport) View.VISIBLE else View.GONE
            exportButton.setOnClickListener {
                onExportClicked(matchWithTeams)
            }

            chronicleButton.visibility = if (item.canOpenChronicle) View.VISIBLE else View.GONE
            chronicleButton.setOnClickListener {
                onChronicleClicked(matchWithTeams)
            }
        }
    }
}

/**
 * Il colore del risultato di un lato: #E0E0E0 per chi ha vinto, #9E9E9E per chi ha perso (6,22:1
 * su #1E1E1E, leggibile). Con un pareggio, [vincitore] null, tutti e due restano chiari.
 */
internal fun coloreDelPunteggio(
    vincitore: Int?,
    lato: Int,
): Int = if (vincitore == null || vincitore == lato) R.color.stencil_white else R.color.sidewalk_gray

/** «PADEL · 12/09 18:30 · 47 MIN»: sport, data e, se c'e', durata. */
internal fun metaDellaPartita(
    context: Context,
    item: MatchHistoryUiState,
): String {
    val match = item.matchWithTeams.match
    val sport = sportLabel(context, match.sportId)
    val data = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(match.timestamp))
    val durata = item.durationMillis
    val testo =
        if (durata == null) {
            context.getString(R.string.history_meta, sport, data)
        } else {
            context.getString(R.string.history_meta_duration, sport, data, ChronicleText.duration(context.resources, durata))
        }
    return testo.uppercase()
}

class MatchDiffCallback : DiffUtil.ItemCallback<MatchHistoryUiState>() {
    override fun areItemsTheSame(
        oldItem: MatchHistoryUiState,
        newItem: MatchHistoryUiState,
    ): Boolean = oldItem.matchWithTeams.match.matchId == newItem.matchWithTeams.match.matchId

    override fun areContentsTheSame(
        oldItem: MatchHistoryUiState,
        newItem: MatchHistoryUiState,
    ): Boolean = oldItem == newItem
}
