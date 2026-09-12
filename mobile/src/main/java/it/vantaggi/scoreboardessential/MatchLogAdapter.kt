package it.vantaggi.scoreboardessential

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType

/**
 * @param onAttribuisci invocato toccando un gol a cui manca il marcatore. E' la strada che ha
 *   sostituito il dialogo che si apriva da solo dopo ogni gol: la domanda aspetta, e la fa
 *   l'utente quando gli va invece che l'app mentre si guarda il campo.
 */
class MatchLogAdapter(
    private val onAttribuisci: (MatchEvent) -> Unit = {},
) : ListAdapter<MatchEvent, MatchLogAdapter.MatchEventViewHolder>(MatchEventDiffCallback()) {
    var team1Color: Int = 0
    var team2Color: Int = 0

    /**
     * Se lo sport attribuisce i punti a un giocatore. Falso in padel e tennis: li' un punto non e'
     * un "GOAL!" e non c'e' nessun marcatore da scegliere, quindi la riga non si offre al tocco.
     * Vero finche' le capacita' non arrivano, come il resto della schermata.
     */
    var attribuisceMarcatore: Boolean = true

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): MatchEventViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.match_event_item, parent, false)
        return MatchEventViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: MatchEventViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position), team1Color, team2Color, attribuisceMarcatore, onAttribuisci)
    }

    class MatchEventViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val timestampTextView: TextView = itemView.findViewById(R.id.event_timestamp)
        private val eventTextView: TextView = itemView.findViewById(R.id.event_description)
        private val teamIndicator: View = itemView.findViewById(R.id.team_indicator)

        fun bind(
            event: MatchEvent,
            team1Color: Int,
            team2Color: Int,
            attribuisceMarcatore: Boolean,
            onAttribuisci: (MatchEvent) -> Unit,
        ) {
            timestampTextView.text = event.timestamp

            // Un gol senza marcatore E' attribuibile, e la riga deve dirlo. playerId e' il segnale
            // affidabile: `player` contiene comunque il nome della squadra quando il marcatore
            // manca, quindi non e' mai nullo e non distingue i due casi.
            val daAttribuire =
                attribuisceMarcatore && event.type == MatchEventType.SCORE && event.playerId == null && event.engineIndex != null

            val description =
                when {
                    event.type == MatchEventType.SCORE && !attribuisceMarcatore -> {
                        itemView.context.getString(R.string.log_point, event.player.orEmpty())
                    }

                    event.type == MatchEventType.SCORE && event.playerId != null -> {
                        val roleInfo = if (event.playerRole?.isNotEmpty() == true) " (${event.playerRole})" else ""
                        "GOAL! ${event.player}$roleInfo"
                    }

                    daAttribuire -> {
                        itemView.context.getString(R.string.log_goal_unattributed, event.player.orEmpty())
                    }

                    else -> {
                        event.event
                    }
                }
            eventTextView.text = description

            // In quest'ordine: setOnClickListener rende la view cliccabile anche quando riceve null,
            // quindi scritto dopo annullava isClickable e ogni riga restava un bersaglio.
            itemView.setOnClickListener(if (daAttribuire) View.OnClickListener { onAttribuisci(event) } else null)
            itemView.isClickable = daAttribuire

            // Set team color indicator
            when (event.team) {
                1 -> {
                    teamIndicator.visibility = View.VISIBLE
                    teamIndicator.setBackgroundColor(team1Color)
                }

                2 -> {
                    teamIndicator.visibility = View.VISIBLE
                    teamIndicator.setBackgroundColor(team2Color)
                }

                else -> {
                    teamIndicator.visibility = View.GONE
                }
            }

            // Highlight goals. La chiave e' il tipo, non il testo: event.event di un punto vale "Goal"
            // (la riga "GOAL! ..." e' costruita sopra), quindi cercarci "GOAL!" non era mai vero.
            if (event.type == MatchEventType.SCORE) {
                val goalColor = if (event.team == 1) team1Color else team2Color
                eventTextView.setTextColor(goalColor)
                eventTextView.textSize = 16f
            } else {
                eventTextView.setTextColor(
                    MaterialColors.getColor(itemView.context, com.google.android.material.R.attr.colorOnSurface, "Error"),
                )
                eventTextView.textSize = 14f
            }
        }
    }

    class MatchEventDiffCallback : DiffUtil.ItemCallback<MatchEvent>() {
        override fun areItemsTheSame(
            oldItem: MatchEvent,
            newItem: MatchEvent,
        ): Boolean = oldItem.timestamp == newItem.timestamp && oldItem.event == newItem.event

        override fun areContentsTheSame(
            oldItem: MatchEvent,
            newItem: MatchEvent,
        ): Boolean = oldItem == newItem
    }
}
