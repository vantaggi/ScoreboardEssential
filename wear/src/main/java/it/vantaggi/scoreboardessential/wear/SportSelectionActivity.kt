package it.vantaggi.scoreboardessential.wear

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat

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

        /** Il titolo della lista: la stessa schermata sceglie anche la regola del 40-40 e il portiere. */
        const val EXTRA_TITLE = "sport_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MovimentoRidotto.applica(this, theme)
        setContentView(R.layout.activity_sport_selection)
        intent.getStringExtra(EXTRA_TITLE)?.let { findViewById<TextView>(R.id.sport_title).text = it }

        val ids = intent.getStringArrayListExtra(EXTRA_IDS).orEmpty()
        val labels = intent.getStringArrayListExtra(EXTRA_LABELS).orEmpty()
        val corrente = intent.getStringExtra(EXTRA_CURRENT).orEmpty()

        // Un elenco piu' corto dell'altro significa un telefono che parla una versione diversa:
        // si mostra cio' che e' accoppiato con certezza invece di far crashare la schermata.
        val voci = ids.zip(labels).map { (id, label) -> SportChoice(id, label, label == corrente) }

        val colonna = findViewById<LinearLayout>(R.id.sport_voci)
        voci.forEach { scelta ->
            val riga = LayoutInflater.from(this).inflate(R.layout.item_sport_wear, colonna, false)
            mostraLaScelta(riga, scelta) {
                if (!scelta.current) {
                    setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_CHOSEN, scelta.id))
                }
                finish()
            }
            colonna.addView(riga)
        }

        val scorrimento = findViewById<ScrollView>(R.id.sport_scroll)
        // Sul tondo le righe si stringono vicino al bordo: alla prima impaginazione e a ogni scorrimento.
        val bordo = ListaTonda(scorrimento) { (0 until colonna.childCount).map(colonna::getChildAt) }
        scorrimento.viewTreeObserver.addOnGlobalLayoutListener { bordo.aggiorna() }
        scorrimento.setOnScrollChangeListener { _, _, _, _, _ -> bordo.aggiorna() }
        // Il focus serve alla corona: senza, ruotarla non scorre niente.
        scorrimento.requestFocus()
    }

    /**
     * Una scelta nella riga del menu: il nome e, per quella in uso, la spunta con "in uso". Quale sia
     * in uso si legge a parole e dalla spunta, non dal colore di una riga: l'accento e' solo un
     * rinforzo. Colore e spunta stanno qui e non nel layout, che il menu riusa per sottotitoli grigi.
     * La spunta e' il simbolo check di Material Symbols a icon-compact (16dp), non un carattere.
     * Il colore e' text-primary e non il lime: sul quadrante il lime e' solo di chi serve.
     */
    private fun mostraLaScelta(
        riga: View,
        scelta: SportChoice,
        alTocco: () -> Unit,
    ) {
        riga.findViewById<TextView>(R.id.sport_name).text = scelta.label
        val stato = riga.findViewById<TextView>(R.id.sport_current)
        val colore = ContextCompat.getColor(this, R.color.text_primary)
        val lato = resources.getDimensionPixelSize(R.dimen.icon_compact)
        val spunta = ContextCompat.getDrawable(this, R.drawable.ic_check)!!.mutate()
        spunta.setBounds(0, 0, lato, lato)
        stato.setTextColor(colore)
        stato.setCompoundDrawablesRelative(spunta, null, null, null)
        stato.compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.icon_text_gap)
        TextViewCompat.setCompoundDrawableTintList(stato, ColorStateList.valueOf(colore))
        stato.visibility = if (scelta.current) View.VISIBLE else View.GONE
        riga.setOnClickListener { alTocco() }
    }
}

data class SportChoice(
    val id: String,
    val label: String,
    val current: Boolean,
)
