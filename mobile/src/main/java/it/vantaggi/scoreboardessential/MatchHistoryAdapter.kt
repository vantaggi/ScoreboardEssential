package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import it.vantaggi.scoreboardessential.core.DeuceRule
import it.vantaggi.scoreboardessential.core.RacketRules
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.padelelite.invioLook
import it.vantaggi.scoreboardessential.ui.GiorniDelloStorico
import it.vantaggi.scoreboardessential.ui.MatchHistoryUiState
import it.vantaggi.scoreboardessential.ui.RigaDelloStorico
import it.vantaggi.scoreboardessential.ui.chronicle.ChronicleText
import it.vantaggi.scoreboardessential.utils.etichettaDiSquadra
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

class MatchHistoryAdapter(
    private val onDeleteClicked: (MatchWithTeams) -> Unit,
    private val onExportClicked: (MatchWithTeams) -> Unit,
    private val onChronicleClicked: (MatchWithTeams) -> Unit,
    private val onSendClicked: (MatchWithTeams) -> Unit = {},
) : ListAdapter<RigaDelloStorico, RecyclerView.ViewHolder>(MatchDiffCallback()) {
    // Le schede aperte, per partita: le viste si riciclano, lo stato aperto o chiuso non puo' stare nella vista.
    private val aperte = mutableSetOf<Int>()

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is RigaDelloStorico.Partita -> TIPO_PARTITA
            is RigaDelloStorico.Giorno -> TIPO_GIORNO
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        if (viewType == TIPO_GIORNO) return GiornoViewHolder(inflater.inflate(R.layout.item_history_day, parent, false) as TextView)
        val view = inflater.inflate(R.layout.match_item, parent, false)
        return MatchViewHolder(view, onDeleteClicked, onExportClicked, onChronicleClicked, onSendClicked, aperte)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val item = getItem(position)) {
            is RigaDelloStorico.Partita -> (holder as MatchViewHolder).bind(item.stato)
            is RigaDelloStorico.Giorno -> (holder as GiornoViewHolder).bind(item.giorno)
        }
    }

    /** Il titolo di un giorno: «Oggi», «Ieri» o la data (GiorniDelloStorico). */
    class GiornoViewHolder(
        private val titolo: TextView,
    ) : RecyclerView.ViewHolder(titolo) {
        fun bind(giorno: LocalDate) {
            titolo.text = GiorniDelloStorico.etichetta(titolo.context, giorno)
        }
    }

    companion object {
        // La partita resta il tipo 0: chi gonfia una scheda da solo (i test) passa 0 come prima.
        const val TIPO_PARTITA = 0
        const val TIPO_GIORNO = 1
    }

    class MatchViewHolder(
        itemView: View,
        private val onDeleteClicked: (MatchWithTeams) -> Unit,
        private val onExportClicked: (MatchWithTeams) -> Unit,
        private val onChronicleClicked: (MatchWithTeams) -> Unit,
        private val onSendClicked: (MatchWithTeams) -> Unit = {},
        private val aperte: MutableSet<Int> = mutableSetOf(),
    ) : RecyclerView.ViewHolder(itemView) {
        private val dettaglio: View = itemView.findViewById(R.id.match_detail)
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
        private val sendButton: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.send_match_button)
        private val sendStatusTextView: TextView = itemView.findViewById(R.id.send_status_textview)
        private val sendHintTextView: TextView = itemView.findViewById(R.id.send_hint_textview)

        /** Apre o chiude il dettaglio: la freccia cambia verso e TalkBack sente lo stato e l'azione. */
        private fun mostraDettaglio(aperto: Boolean) {
            val context = itemView.context
            dettaglio.visibility = if (aperto) View.VISIBLE else View.GONE
            timestampTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0,
                0,
                if (aperto) R.drawable.ic_expand_less else R.drawable.ic_expand_more,
                0,
            )
            ViewCompat.setStateDescription(
                itemView,
                context.getString(if (aperto) R.string.history_detail_open else R.string.history_detail_closed),
            )
            ViewCompat.replaceAccessibilityAction(
                itemView,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                context.getString(if (aperto) R.string.history_detail_hide else R.string.history_detail_show),
                null,
            )
        }

        fun bind(item: MatchHistoryUiState) {
            val matchWithTeams = item.matchWithTeams
            val context = itemView.context
            val match = matchWithTeams.match

            // L'etichetta porta il colore con cui si e' giocato (Team.color), con l'inchiostro di
            // TeamInk: non piu' giallo e verde fissi. Se la squadra non c'e' piu' ripiega sui
            // predefiniti.
            team1NameTextView.text = EtichettaSquadra.mostrato(matchWithTeams.team1?.name, context.getString(R.string.label_team_1))
            team2NameTextView.text = EtichettaSquadra.mostrato(matchWithTeams.team2?.name, context.getString(R.string.label_team_2))
            team1NameTextView.etichettaDiSquadra(matchWithTeams.team1?.color ?: context.getColor(R.color.team_side_1))
            team2NameTextView.etichettaDiSquadra(matchWithTeams.team2?.color ?: context.getColor(R.color.team_side_2))
            team1ScoreTextView.text = match.team1Score.toString()
            team2ScoreTextView.text = match.team2Score.toString()

            // Chi ha vinto si legge senza il colore: testo primario contro secondario. Un pareggio li
            // lascia tutti e due primari.
            val vincitore = item.winnerSide
            team1ScoreTextView.setTextColor(context.getColor(coloreDelPunteggio(vincitore, 1)))
            team2ScoreTextView.setTextColor(context.getColor(coloreDelPunteggio(vincitore, 2)))

            timestampTextView.text = metaDellaPartita(context, item)
            mostraDettaglio(match.matchId in aperte)
            itemView.setOnClickListener {
                val ora = match.matchId !in aperte
                if (ora) aperte.add(match.matchId) else aperte.remove(match.matchId)
                // Il resto della lista scorre al suo posto con la durata veloce; col movimento ridotto e' immediato.
                TransitionManager.beginDelayedTransition(
                    itemView.parent as? ViewGroup ?: itemView as ViewGroup,
                    AutoTransition().setDuration(context.resources.getInteger(R.integer.duration_fast).toLong()),
                )
                mostraDettaglio(ora)
            }

            val righeDiSet =
                item.setLine
                    ?: if (SportRegistry.byId(
                            match.sportId,
                        ) is RacketRules
                    ) {
                        sportRulesLine(context, match.sportId, DeuceRule.daNome(match.deuceRule))
                    } else {
                        null
                    }
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

            // Le schede si riciclano: la visibilita' va scritta in entrambi i casi.
            exportButton.visibility = if (item.canExport) View.VISIBLE else View.GONE
            exportButton.setOnClickListener {
                onExportClicked(matchWithTeams)
            }

            chronicleButton.visibility = if (item.canOpenChronicle) View.VISIBLE else View.GONE
            chronicleButton.setOnClickListener {
                onChronicleClicked(matchWithTeams)
            }

            // Padel Elite: comando e stato compaiono solo con la funzione configurata. Lo stato dice
            // sempre una parola e mostra un'icona; il colore segue ma non e' l'unico segno.
            // Lo stesso bottone, nello stesso posto: "Invia a Padel Elite" prima, "Invia di nuovo" per una
            // voce gia' nella casella e in attesa (un solo comando per volta, un solo posto).
            sendButton.visibility = if (item.canSendToPadelElite || item.canSendAgain) View.VISIBLE else View.GONE
            sendButton.setText(if (item.canSendAgain) R.string.padel_elite_resend else R.string.padel_elite_send)
            sendButton.setOnClickListener { onSendClicked(matchWithTeams) }
            sendHintTextView.visibility = if (item.showLinksHint) View.VISIBLE else View.GONE
            val invio = item.shownInvio
            sendStatusTextView.visibility = if (invio != null) View.VISIBLE else View.GONE
            if (invio != null) {
                val aspetto = invioLook(context, invio)
                sendStatusTextView.text = aspetto.text
                sendStatusTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(aspetto.icon, 0, 0, 0)
                androidx.core.widget.TextViewCompat
                    .setCompoundDrawableTintList(
                        sendStatusTextView,
                        android.content.res.ColorStateList
                            .valueOf(context.getColor(aspetto.tint)),
                    )
            }
        }
    }
}

/**
 * Il colore del risultato di un lato: testo primario per chi ha vinto, secondario per chi ha perso
 * (4,9:1 sulla scheda rialzata, leggibile). Con un pareggio, [vincitore] null, tutti e due restano
 * primari.
 */
internal fun coloreDelPunteggio(
    vincitore: Int?,
    lato: Int,
): Int = if (vincitore == null || vincitore == lato) R.color.elite_text_primary else R.color.elite_text_secondary

/** «Padel · 18:30 · 47 min»: sport, ora e, se c'e', durata. Il giorno e' il titolo sopra la scheda (passo 5b). */
internal fun metaDellaPartita(
    context: Context,
    item: MatchHistoryUiState,
): String {
    val match = item.matchWithTeams.match
    val sport = sportLabel(context, match.sportId)
    val ora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(match.timestamp))
    val durata = item.durationMillis
    val testo =
        if (durata == null) {
            context.getString(R.string.history_meta, sport, ora)
        } else {
            context.getString(R.string.history_meta_duration, sport, ora, ChronicleText.duration(context.resources, durata))
        }
    return testo
}

class MatchDiffCallback : DiffUtil.ItemCallback<RigaDelloStorico>() {
    override fun areItemsTheSame(
        oldItem: RigaDelloStorico,
        newItem: RigaDelloStorico,
    ): Boolean =
        when {
            oldItem is RigaDelloStorico.Partita && newItem is RigaDelloStorico.Partita -> {
                oldItem.stato.matchWithTeams.match.matchId == newItem.stato.matchWithTeams.match.matchId
            }

            else -> {
                oldItem == newItem
            }
        }

    override fun areContentsTheSame(
        oldItem: RigaDelloStorico,
        newItem: RigaDelloStorico,
    ): Boolean = oldItem == newItem
}
