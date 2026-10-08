package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.vantaggi.scoreboardessential.core.Serata

/**
 * Una riga della schermata Serata, come la vede l'adapter: tutto cio' che serve a disegnarla e niente
 * che serva a farla funzionare ([chiave] dice quale riga e' stata toccata, e basta).
 *
 * [intestazione] e' una riga che non si tocca (la coppia). [selezionata] ha sempre un secondo segno oltre
 * al tono (spunta e peso). [attiva] false spegne la riga (testo disattivo, icona a 0,5) senza toglierla.
 */
data class RigaSerata(
    val chiave: String,
    val titolo: String,
    val descrizione: String = titolo,
    val intestazione: Boolean = false,
    val lead: String? = null,
    val nota: String? = null,
    @DrawableRes val icona: Int? = null,
    val selezionata: Boolean = false,
    val attiva: Boolean = true,
    val mostraLaSpunta: Boolean = false,
)

/** Le righe di un gruppo della schermata Serata: posti, comandi, panchina, presenti. */
class SerataRigheAdapter(
    private val alTocco: (String) -> Unit,
) : ListAdapter<RigaSerata, RecyclerView.ViewHolder>(Confronto) {
    override fun getItemViewType(position: Int): Int = if (getItem(position).intestazione) TIPO_INTESTAZIONE else TIPO_RIGA

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TIPO_INTESTAZIONE) {
            IntestazioneHolder(inflater.inflate(R.layout.item_serata_header, parent, false) as TextView)
        } else {
            RigaHolder(inflater.inflate(R.layout.item_serata_row, parent, false), alTocco)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        val riga = getItem(position)
        when (holder) {
            is IntestazioneHolder -> holder.testo.text = riga.titolo
            is RigaHolder -> holder.lega(riga)
        }
    }

    private class IntestazioneHolder(
        val testo: TextView,
    ) : RecyclerView.ViewHolder(testo)

    private class RigaHolder(
        vista: View,
        private val alTocco: (String) -> Unit,
    ) : RecyclerView.ViewHolder(vista) {
        private val icona = vista.findViewById<ImageView>(R.id.serata_row_icon)
        private val lead = vista.findViewById<TextView>(R.id.serata_row_lead)
        private val titolo = vista.findViewById<TextView>(R.id.serata_row_title)
        private val nota = vista.findViewById<TextView>(R.id.serata_row_note)
        private val spunta = vista.findViewById<ImageView>(R.id.serata_row_check)

        fun lega(riga: RigaSerata) {
            val contesto: Context = itemView.context
            icona.visibility = if (riga.icona != null) View.VISIBLE else View.GONE
            riga.icona?.let { icona.setImageResource(it) }
            icona.alpha = if (riga.attiva) 1f else ALPHA_DISATTIVO
            lead.visibility = if (riga.lead != null) View.VISIBLE else View.GONE
            lead.text = riga.lead
            titolo.text = riga.titolo
            // Tre pesi in tutto: la selezione porta il titolo dal 500 al 600.
            titolo.setTextAppearance(
                if (riga.selezionata) R.style.TextAppearance_App_RowTitleSelected else R.style.TextAppearance_App_RowTitle,
            )
            titolo.setTextColor(
                ContextCompat.getColor(contesto, if (riga.attiva) R.color.elite_text_primary else R.color.elite_text_disabled),
            )
            nota.visibility = if (riga.nota != null) View.VISIBLE else View.GONE
            nota.text = riga.nota
            spunta.visibility = if (riga.mostraLaSpunta) View.VISIBLE else View.INVISIBLE
            spunta.alpha = if (riga.selezionata) 1f else 0f
            itemView.isSelected = riga.selezionata
            itemView.isEnabled = riga.attiva
            itemView.contentDescription = riga.descrizione
            ViewCompat.setStateDescription(itemView, if (riga.selezionata) contesto.getString(R.string.state_selected) else null)
            itemView.setOnClickListener { alTocco(riga.chiave) }
        }
    }

    private object Confronto : DiffUtil.ItemCallback<RigaSerata>() {
        override fun areItemsTheSame(
            vecchia: RigaSerata,
            nuova: RigaSerata,
        ): Boolean = vecchia.chiave == nuova.chiave

        override fun areContentsTheSame(
            vecchia: RigaSerata,
            nuova: RigaSerata,
        ): Boolean = vecchia == nuova
    }

    private companion object {
        const val TIPO_INTESTAZIONE = 0
        const val TIPO_RIGA = 1
        const val ALPHA_DISATTIVO = 0.5f
    }
}

/** Le chiavi delle righe: la schermata le legge per sapere che cosa e' stato toccato. */
object ChiaviSerata {
    private const val POSTO = "posto:"
    private const val PANCHINA = "panchina:"
    private const val PRESENTE = "presente:"
    const val RUOTA = "ruota"
    const val STESSE_COPPIE = "stesse"
    const val SCAMBIA_LATI = "lati"
    const val OSPITE = "ospite"

    fun posto(i: Int) = "$POSTO$i"

    fun panchina(id: Int) = "$PANCHINA$id"

    fun presente(id: Int) = "$PRESENTE$id"

    fun postoDa(chiave: String): Int? = chiave.takeIf { it.startsWith(POSTO) }?.removePrefix(POSTO)?.toIntOrNull()

    fun panchinaDa(chiave: String): Int? = chiave.takeIf { it.startsWith(PANCHINA) }?.removePrefix(PANCHINA)?.toIntOrNull()

    fun presenteDa(chiave: String): Int? = chiave.takeIf { it.startsWith(PRESENTE) }?.removePrefix(PRESENTE)?.toIntOrNull()
}

/**
 * Le righe di ogni gruppo, ricavate dallo stato. Funzioni pure sul [StatoDellaSerata] e sul contesto
 * (per le stringhe): la schermata le prova senza disegnare niente.
 */
object RigheDellaSerata {
    /** I due gruppi di posti: intestazione di ogni coppia e i suoi due posti. Vuoto se non c'e' una bozza. */
    fun posti(
        contesto: Context,
        stato: StatoDellaSerata,
    ): List<RigaSerata> {
        val bozza = stato.serata?.bozza ?: return emptyList()
        val selezionato = (stato.selezione as? SelezioneSerata.Posto)?.posto
        return buildList {
            for (coppia in 1..2) {
                add(
                    RigaSerata(
                        chiave = "coppia:$coppia",
                        titolo = contesto.getString(R.string.serata_pair_title, coppia),
                        intestazione = true,
                    ),
                )
                for (posto in 1..2) {
                    val indice = (coppia - 1) * 2 + (posto - 1)
                    val nome = stato.nome(bozza.posti[indice])
                    add(
                        RigaSerata(
                            chiave = ChiaviSerata.posto(indice),
                            titolo = nome,
                            descrizione = contesto.getString(R.string.serata_seat_description, coppia, posto, nome),
                            lead = posto.toString(),
                            selezionata = selezionato == indice,
                            mostraLaSpunta = true,
                            attiva = !stato.inCorso,
                        ),
                    )
                }
            }
        }
    }

    /** I tre comandi di rotazione. "Stesse coppie" si spegne se non c'e' una partita da rigiocare. */
    fun comandi(
        contesto: Context,
        stato: StatoDellaSerata,
    ): List<RigaSerata> {
        val serata = stato.serata
        if (serata?.bozza == null) return emptyList()
        val vivi = !stato.inCorso
        return listOf(
            RigaSerata(ChiaviSerata.RUOTA, contesto.getString(R.string.serata_rotate), icona = R.drawable.ic_autorenew, attiva = vivi),
            RigaSerata(
                ChiaviSerata.STESSE_COPPIE,
                contesto.getString(R.string.serata_same_pairs),
                icona = R.drawable.ic_repeat,
                attiva = vivi && serata.puoRigiocareLeStesseCoppie(),
            ),
            RigaSerata(
                ChiaviSerata.SCAMBIA_LATI,
                contesto.getString(R.string.serata_swap_sides),
                icona = R.drawable.ic_sync_alt,
                attiva = vivi,
            ),
        )
    }

    /** La panchina, nell'ordine di arrivo, con le partite giocate. */
    fun panchina(
        contesto: Context,
        stato: StatoDellaSerata,
    ): List<RigaSerata> {
        val serata = stato.serata ?: return emptyList()
        val selezionato = (stato.selezione as? SelezioneSerata.Panchina)?.id
        return serata.panchina.map { id ->
            RigaSerata(
                chiave = ChiaviSerata.panchina(id),
                titolo = stato.nome(id),
                descrizione = contesto.getString(R.string.serata_bench_description, stato.nome(id), serata.partiteDi(id)),
                nota = partite(contesto, serata, id),
                selezionata = selezionato == id,
                mostraLaSpunta = true,
                attiva = !stato.inCorso,
            )
        }
    }

    /** Tutta la rosa: chi e' presente ha la spunta; l'ultima riga e' "Aggiungi un ospite". */
    fun presenti(
        contesto: Context,
        stato: StatoDellaSerata,
    ): List<RigaSerata> {
        val serata = stato.serata
        val righe =
            stato.rosa.map { giocatore ->
                val presente = serata != null && giocatore.id in serata.presenti
                RigaSerata(
                    chiave = ChiaviSerata.presente(giocatore.id),
                    titolo = giocatore.nome,
                    descrizione =
                        contesto.getString(
                            if (presente) R.string.serata_present_description else R.string.serata_absent_description,
                            giocatore.nome,
                        ),
                    nota = if (presente && serata != null) partite(contesto, serata, giocatore.id) else null,
                    selezionata = presente,
                    mostraLaSpunta = true,
                    attiva = !stato.inCorso,
                )
            }
        return righe +
            RigaSerata(
                chiave = ChiaviSerata.OSPITE,
                titolo = contesto.getString(R.string.serata_add_guest),
                icona = R.drawable.ic_person_add,
                attiva = !stato.inCorso,
            )
    }

    /** "2 partite", o niente prima della prima. */
    private fun partite(
        contesto: Context,
        serata: Serata,
        id: Int,
    ): String? =
        serata.partiteDi(id).takeIf { it > 0 }?.let {
            contesto.resources.getQuantityString(R.plurals.serata_games_played, it, it)
        }
}
