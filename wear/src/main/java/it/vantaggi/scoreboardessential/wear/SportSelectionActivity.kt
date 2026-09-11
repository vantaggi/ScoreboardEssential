package it.vantaggi.scoreboardessential.wear

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView

/**
 * Sceglie lo sport dal polso.
 *
 * Questa schermata **non sa quali sport esistono**: riceve gli id e le etichette gia' tradotte
 * dalla schermata principale, che a sua volta li ha ricevuti dal telefono nello stato v2. E' la
 * stessa ragione per cui l'orologio non calcola punteggi -- se l'elenco degli sport vivesse anche
 * qui, aggiungerne uno vorrebbe dire aggiornare due APK invece di uno.
 *
 * La scelta non viene spedita da qui: torna alla schermata principale come RISULTATO, ed e' lei
 * a chiederla al telefono. Il motivo e' il numero di sequenza -- ne esiste UNO per nodo, e vive
 * nel [WearViewModel] della schermata principale. Un ViewModel creato qui ne avrebbe uno suo,
 * seminato da un orologio di sistema diverso, e due contatori indipendenti sullo stesso nodo sono
 * esattamente il modo in cui un messaggio nuovo viene scartato come "gia' visto".
 */
class SportSelectionActivity : ComponentActivity() {
    companion object {
        const val EXTRA_IDS = "sport_ids"
        const val EXTRA_LABELS = "sport_labels"
        const val EXTRA_CURRENT = "sport_current"
        const val EXTRA_CHOSEN = "sport_chosen"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sport_selection)

        val ids = intent.getStringArrayListExtra(EXTRA_IDS).orEmpty()
        val labels = intent.getStringArrayListExtra(EXTRA_LABELS).orEmpty()
        val corrente = intent.getStringExtra(EXTRA_CURRENT).orEmpty()

        // Un elenco piu' corto dell'altro significa un telefono che parla una versione diversa:
        // si mostra cio' che e' accoppiato con certezza invece di far crashare la schermata.
        val voci = ids.zip(labels).map { (id, label) -> SportChoice(id, label, label == corrente) }

        val lista = findViewById<WearableRecyclerView>(R.id.sport_list)
        lista.layoutManager = WearableLinearLayoutManager(this)
        lista.isEdgeItemsCenteringEnabled = true
        lista.adapter =
            SportAdapter(voci) { scelta ->
                if (!scelta.current) {
                    setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_CHOSEN, scelta.id))
                }
                finish()
            }
    }
}

data class SportChoice(
    val id: String,
    val label: String,
    val current: Boolean,
)

class SportAdapter(
    private val voci: List<SportChoice>,
    private val onClick: (SportChoice) -> Unit,
) : RecyclerView.Adapter<SportAdapter.SportViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): SportViewHolder =
        SportViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_sport_wear, parent, false),
        )

    override fun onBindViewHolder(
        holder: SportViewHolder,
        position: Int,
    ) = holder.bind(voci[position], onClick)

    override fun getItemCount() = voci.size

    class SportViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val nome: TextView = itemView.findViewById(R.id.sport_name)
        private val stato: TextView = itemView.findViewById(R.id.sport_current)

        fun bind(
            scelta: SportChoice,
            onClick: (SportChoice) -> Unit,
        ) {
            nome.text = scelta.label
            // Quale sport si stia giocando si legge a parole, non dal colore di una riga.
            stato.visibility = if (scelta.current) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onClick(scelta) }
        }
    }
}
