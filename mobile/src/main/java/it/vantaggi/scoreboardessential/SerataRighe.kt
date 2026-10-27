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
import it.vantaggi.scoreboardessential.core.OrdineDiBattuta
import it.vantaggi.scoreboardessential.core.Serata
import it.vantaggi.scoreboardessential.padelelite.SerataDashboard
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    const val DASHBOARD_PRESENTI = "dashboard:presenti"
    private const val DASHBOARD_GIRONE = "dashboard:girone:"

    fun posto(i: Int) = "$POSTO$i"

    fun girone(indice: Int) = "$DASHBOARD_GIRONE$indice"

    fun gironeDa(chiave: String): Int? = chiave.takeIf { it.startsWith(DASHBOARD_GIRONE) }?.removePrefix(DASHBOARD_GIRONE)?.toIntOrNull()

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
                    // Il numero e' il turno di battuta: A1, B1, A2, B2 (1 e 3 la coppia 1, 2 e 4 la coppia 2).
                    val turno = OrdineDiBattuta.di(bozza).indexOf(bozza.posti[indice]) + 1
                    add(
                        RigaSerata(
                            chiave = ChiaviSerata.posto(indice),
                            titolo = nome,
                            descrizione = contesto.getString(R.string.serata_seat_description, coppia, turno, nome),
                            lead = turno.toString(),
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

    /**
     * Le proposte della dashboard: "Usa i presenti" (se qualcuno ha confermato) e un girone per riga. Ogni riga
     * e' un comando che cambia i presenti; la spunta dice quale e' in uso. Vuoto finche' la serata non e' letta.
     */
    fun dashboard(
        contesto: Context,
        stato: StatoDellaSerata,
    ): List<RigaSerata> {
        val pronta = stato.dashboard as? SerataDashboard.Pronta ?: return emptyList()
        val vivi = !stato.inCorso
        val presenti = pronta.presenti.voci.size
        val righe = mutableListOf<RigaSerata>()
        if (presenti > 0) {
            righe +=
                RigaSerata(
                    chiave = ChiaviSerata.DASHBOARD_PRESENTI,
                    titolo = contesto.getString(R.string.serata_dashboard_use_present),
                    descrizione = contesto.getString(R.string.serata_dashboard_use_present_description, presenti),
                    nota = presenti.toString(),
                    icona = R.drawable.ic_event_available,
                    selezionata = stato.inUso(PropostaDashboard.Presenti),
                    mostraLaSpunta = true,
                    attiva = vivi,
                )
        }
        pronta.gironi.forEachIndexed { indice, girone ->
            val lettera = letteraDelGirone(indice)
            val nomi = girone.traduzione.voci.joinToString(", ") { it.nome }
            righe +=
                RigaSerata(
                    chiave = ChiaviSerata.girone(indice),
                    titolo = contesto.getString(R.string.serata_dashboard_pool, lettera, nomi),
                    descrizione = contesto.getString(R.string.serata_dashboard_pool_description, lettera, nomi),
                    icona = R.drawable.ic_group,
                    selezionata = stato.inUso(PropostaDashboard.Girone(indice)),
                    mostraLaSpunta = true,
                    attiva = vivi,
                )
        }
        return righe
    }

    /**
     * La riga di stato del gruppo della dashboard, in frase: la serata e quanti presenti, poi cosa succede a chi
     * non e' collegato o non e' nella rosa, e i gironi di un'altra serata. Null quando il gruppo non c'e'.
     */
    fun statoDashboard(
        contesto: Context,
        stato: StatoDellaSerata,
    ): String? {
        val risorse = contesto.resources
        return when (val dashboard = stato.dashboard) {
            SerataDashboard.Spenta -> {
                null
            }

            SerataDashboard.InLettura -> {
                contesto.getString(R.string.serata_dashboard_loading)
            }

            SerataDashboard.NonConfigurata -> {
                contesto.getString(R.string.serata_dashboard_not_configured)
            }

            SerataDashboard.Errore -> {
                contesto.getString(R.string.serata_dashboard_error)
            }

            SerataDashboard.AccessoScaduto -> {
                contesto.getString(R.string.serata_dashboard_login)
            }

            SerataDashboard.FuoriDalGruppo -> {
                contesto.getString(R.string.serata_dashboard_not_member)
            }

            is SerataDashboard.Pronta -> {
                val giorno = giornoDellaSerata(contesto, dashboard.giorno)
                val traduzione = dashboard.presenti
                val n = traduzione.voci.size
                buildList {
                    add(
                        if (n == 0) {
                            contesto.getString(R.string.serata_dashboard_nobody, giorno)
                        } else {
                            risorse.getQuantityString(R.plurals.serata_dashboard_ready, n, giorno, n)
                        },
                    )
                    if (traduzione.ospiti > 0) {
                        add(risorse.getQuantityString(R.plurals.serata_dashboard_guests, traduzione.ospiti, traduzione.ospiti))
                    }
                    if (traduzione.nonTrovati > 0) {
                        add(risorse.getQuantityString(R.plurals.serata_dashboard_missing, traduzione.nonTrovati, traduzione.nonTrovati))
                    }
                    if (dashboard.gironiDiUnAltraSerata) add(contesto.getString(R.string.serata_dashboard_other_evening))
                }.joinToString(" ")
            }
        }
    }

    /** "martedi' 20 ottobre", nella lingua dell'app. */
    fun giornoDellaSerata(
        contesto: Context,
        giorno: LocalDate,
    ): String {
        val lingua = contesto.resources.configuration.locales[0]
        return DateTimeFormatter.ofPattern("EEEE d MMMM", lingua).format(giorno)
    }

    /** A, B, C... come i gironi della dashboard; dopo la Z il numero. */
    private fun letteraDelGirone(indice: Int): String = if (indice < LETTERE) ('A' + indice).toString() else (indice + 1).toString()

    private const val LETTERE = 26

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
