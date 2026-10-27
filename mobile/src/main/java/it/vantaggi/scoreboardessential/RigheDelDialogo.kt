package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import it.vantaggi.scoreboardessential.padelelite.IndizioOmonimo
import java.text.DateFormat
import java.util.Date

/** Una riga di un dialogo di scelta: un'intestazione (non si tocca) o una voce con una didascalia facoltativa. */
data class RigaDelDialogo(
    val titolo: String,
    val didascalia: String? = null,
    val intestazione: Boolean = false,
)

/**
 * Le righe della domanda "e' gia' nell'app?" (scelta dei giocatori della partita e Serata): la spiegazione come
 * intestazione (riga 0), un omonimo per riga con il suo indizio, e in fondo "E' un'altra persona".
 */
fun righeDellaDomanda(
    context: Context,
    nome: String,
    candidati: List<IndizioOmonimo>,
): List<RigaDelDialogo> =
    listOf(RigaDelDialogo(context.getString(R.string.scelta_same_person_hint), intestazione = true)) +
        candidati.map { RigaDelDialogo(it.nome, testoDellIndizio(context, it)) } +
        RigaDelDialogo(
            context.getString(R.string.scelta_other_person),
            context.getString(R.string.scelta_other_person_detail, nome),
        )

/** "3 partite · ultima il 12 ott 2026 · con Rossi · contro Bianchi, Verdi", o "Nessuna partita giocata". */
fun testoDellIndizio(
    context: Context,
    indizio: IndizioOmonimo,
): String {
    val ultima = indizio.ultima ?: return context.getString(R.string.scelta_same_person_no_matches)
    val parti =
        buildList {
            add(context.resources.getQuantityString(R.plurals.scelta_same_person_matches, indizio.partite, indizio.partite))
            add(
                context.getString(
                    R.string.scelta_same_person_last,
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ultima.timestamp)),
                ),
            )
            if (ultima.compagni.isNotEmpty()) add(context.getString(R.string.scelta_same_person_with, ultima.compagni.joinToString(", ")))
            if (ultima.avversari.isNotEmpty()) {
                add(
                    context.getString(R.string.scelta_same_person_against, ultima.avversari.joinToString(", ")),
                )
            }
        }
    return parti.joinToString(" · ")
}

/**
 * Le righe di un dialogo di scelta con le sezioni ("Aggiungi un giocatore" con la rosa di Padel Elite, la
 * domanda sugli omonimi). Riusa le righe che ci sono: l'intestazione dei ruoli e la riga della rosa (nome e,
 * sotto, una parola sullo stato). Le intestazioni non sono abilitate: un tocco non fa niente e TalkBack le
 * legge come titoli. La pressione la da' la lista (la riga non e' cliccabile da se').
 */
class RigheDelDialogo(
    context: Context,
    private val righe: List<RigaDelDialogo>,
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)

    override fun getCount(): Int = righe.size

    override fun getItem(position: Int): RigaDelDialogo = righe[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getViewTypeCount(): Int = 2

    override fun getItemViewType(position: Int): Int = if (righe[position].intestazione) TIPO_INTESTAZIONE else TIPO_VOCE

    override fun areAllItemsEnabled(): Boolean = false

    override fun isEnabled(position: Int): Boolean = !righe[position].intestazione

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val riga = righe[position]
        if (riga.intestazione) {
            val vista = convertView ?: inflater.inflate(R.layout.item_role_header, parent, false)
            vista.findViewById<TextView>(R.id.header_title).text = riga.titolo
            return vista
        }
        val vista =
            convertView ?: inflater.inflate(R.layout.item_padel_elite_player, parent, false).apply {
                isClickable = false
                isFocusable = false
            }
        vista.findViewById<TextView>(R.id.rosa_remote_name).text = riga.titolo
        vista.findViewById<TextView>(R.id.rosa_link_state).apply {
            text = riga.didascalia.orEmpty()
            visibility = if (riga.didascalia == null) View.GONE else View.VISIBLE
        }
        return vista
    }

    private companion object {
        const val TIPO_INTESTAZIONE = 0
        const val TIPO_VOCE = 1
    }
}
