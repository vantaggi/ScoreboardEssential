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
) : ListAdapter<MatchEvent, RecyclerView.ViewHolder>(MatchEventDiffCallback()) {
    var team1Color: Int = 0
    var team2Color: Int = 0

    /**
     * Se lo sport attribuisce i punti a un giocatore. Falso in padel e tennis: li' un punto non e'
     * un "Goal!" e non c'e' nessun marcatore da scegliere, quindi la riga non si offre al tocco.
     * Vero finche' le capacita' non arrivano, come il resto della schermata.
     */
    var attribuisceMarcatore: Boolean = true

    private companion object {
        const val TIPO_EVENTO = 0
        const val TIPO_GAME = 1
    }

    /** Un game chiuso ha il suo layout; ogni altra riga, punti e avvisi, quello di sempre. */
    override fun getItemViewType(position: Int): Int = if (getItem(position).type == MatchEventType.GAME) TIPO_GAME else TIPO_EVENTO

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TIPO_GAME) {
            GameViewHolder(inflater.inflate(R.layout.match_game_item, parent, false))
        } else {
            MatchEventViewHolder(inflater.inflate(R.layout.match_event_item, parent, false))
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        val event = getItem(position)
        when (holder) {
            is GameViewHolder -> holder.bind(event, team1Color, team2Color)
            is MatchEventViewHolder -> holder.bind(event, team1Color, team2Color, attribuisceMarcatore, onAttribuisci)
        }
    }

    /**
     * La riga di un game: tre testi e la barretta del vincitore, e una frase sola per TalkBack
     * ([testiDelGame]). Non si tocca: nel padel e nel tennis non c'e' niente da scegliere.
     */
    class GameViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val titolo: TextView = itemView.findViewById(R.id.game_title)
        private val dettaglio: TextView = itemView.findViewById(R.id.game_detail)
        private val chiusura: TextView = itemView.findViewById(R.id.game_closing)
        private val teamIndicator: View = itemView.findViewById(R.id.team_indicator)

        fun bind(
            event: MatchEvent,
            team1Color: Int,
            team2Color: Int,
        ) {
            val game = event.game ?: return
            val testi = testiDelGame(itemView.context, game, event.player.orEmpty())
            titolo.text = testi.titolo
            dettaglio.text = testi.dettaglio
            dettaglio.visibility = if (testi.dettaglio == null) View.GONE else View.VISIBLE
            chiusura.text = testi.chiusura
            chiusura.visibility = if (testi.chiusura == null) View.GONE else View.VISIBLE
            itemView.contentDescription = testi.descrizione
            itemView.setOnClickListener(null)
            itemView.isClickable = false

            // Come le altre righe: il testo e' colorOnSurface (il layout lo eredita dal tema), il
            // colore della squadra sta solo sulla barretta.
            val colore = if (event.team == 2) team2Color else team1Color
            teamIndicator.visibility = View.VISIBLE
            teamIndicator.setBackgroundColor(colore)
            val onSurface = MaterialColors.getColor(itemView.context, com.google.android.material.R.attr.colorOnSurface, "Error")
            titolo.setTextColor(onSurface)
            chiusura.setTextColor(onSurface)
        }
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
                        "Goal! ${event.player}$roleInfo"
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

            // Il testo e' sempre colorOnSurface, anche per i punti: il colore della squadra resta
            // solo sulla barretta qui sopra. Come testo su #1E1E1E un colore scelto dall'utente
            // sta sotto 4,5:1 in quasi la meta' dei casi (il blu notte #1A237E fa 1,26) e la riga
            // spariva. I punti si distinguono per dimensione; la chiave e' il tipo, non il testo.
            eventTextView.setTextColor(
                MaterialColors.getColor(itemView.context, com.google.android.material.R.attr.colorOnSurface, "Error"),
            )
            eventTextView.textSize = if (event.type == MatchEventType.SCORE) 16f else 14f
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
