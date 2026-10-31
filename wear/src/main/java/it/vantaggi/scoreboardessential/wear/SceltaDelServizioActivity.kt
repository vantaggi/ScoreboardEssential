package it.vantaggi.scoreboardessential.wear

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat

/**
 * Chi serve, scelto a tutto schermo: la apre la capsula SERVE? a inizio set, o la voce "Correggi chi
 * serve" del menu. Le domande sono quelle di [PassiDelServizio], con le righe delle liste del polso e
 * la spunta sulla proposta; indietro torna alla domanda prima.
 *
 * Come [SportSelectionActivity] non spedisce niente: restituisce la scelta alla schermata principale,
 * che ha il ViewModel e la sua sequenza.
 */
class SceltaDelServizioActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CORREZIONE = "servizio_correzione"
        const val EXTRA_LATO = "servizio_lato"
        const val EXTRA_SLOT1 = "servizio_slot1"
        const val EXTRA_SLOT2 = "servizio_slot2"
        const val EXTRA_SLOT = "servizio_slot"

        private const val EXTRA_NOMI = "servizio_nomi"
        private const val EXTRA_ROSA1 = "servizio_rosa1"
        private const val EXTRA_ROSA2 = "servizio_rosa2"
        private const val EXTRA_PROSSIMI = "servizio_prossimi"
        private const val EXTRA_SLOT_ATTUALE = "servizio_slot_attuale"

        fun intent(
            context: Context,
            input: InputDelServizio,
        ): Intent =
            Intent(context, SceltaDelServizioActivity::class.java)
                .putExtra(EXTRA_CORREZIONE, input.correzione)
                .putExtra(EXTRA_NOMI, input.nomi.toTypedArray())
                .putExtra(
                    EXTRA_ROSA1,
                    input.rose
                        .getOrNull(0)
                        .orEmpty()
                        .toTypedArray(),
                ).putExtra(
                    EXTRA_ROSA2,
                    input.rose
                        .getOrNull(1)
                        .orEmpty()
                        .toTypedArray(),
                ).putExtra(EXTRA_LATO, input.lato)
                .putExtra(EXTRA_PROSSIMI, input.prossimi.toIntArray())
                .putExtra(EXTRA_SLOT_ATTUALE, input.slotAttuale)

        /** L'esito che la schermata ha restituito, o null se si e' usciti senza scegliere. */
        fun esito(data: Intent?): EsitoDelServizio? {
            if (data == null) return null
            val lato = data.getIntExtra(EXTRA_LATO, 0).takeIf { it == 1 || it == 2 } ?: return null
            return if (data.getBooleanExtra(EXTRA_CORREZIONE, false)) {
                EsitoDelServizio.Correzione(lato, data.getIntExtra(EXTRA_SLOT, 1))
            } else {
                EsitoDelServizio.Scelta(lato, data.getIntExtra(EXTRA_SLOT1, 1), data.getIntExtra(EXTRA_SLOT2, 1))
            }
        }
    }

    private lateinit var passi: PassiDelServizio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MovimentoRidotto.applica(this, theme)
        setContentView(R.layout.activity_sport_selection)
        passi =
            PassiDelServizio(
                InputDelServizio(
                    nomi = intent.getStringArrayExtra(EXTRA_NOMI).orEmpty().toList(),
                    rose =
                        listOf(
                            intent.getStringArrayExtra(EXTRA_ROSA1).orEmpty().toList(),
                            intent.getStringArrayExtra(EXTRA_ROSA2).orEmpty().toList(),
                        ),
                    lato = intent.getIntExtra(EXTRA_LATO, 1),
                    prossimi = (intent.getIntArrayExtra(EXTRA_PROSSIMI) ?: IntArray(0)).toList(),
                    slotAttuale = intent.getIntExtra(EXTRA_SLOT_ATTUALE, 0),
                    correzione = intent.getBooleanExtra(EXTRA_CORREZIONE, false),
                ),
            )
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (passi.indietro()) mostra() else finish()
                }
            },
        )

        val scorrimento = findViewById<ScrollView>(R.id.sport_scroll)
        val colonna = findViewById<LinearLayout>(R.id.sport_voci)
        // Sul tondo le righe si stringono vicino al bordo: alla prima impaginazione e a ogni scorrimento.
        val bordo = ListaTonda(scorrimento) { (0 until colonna.childCount).map(colonna::getChildAt) }
        scorrimento.viewTreeObserver.addOnGlobalLayoutListener { bordo.aggiorna() }
        scorrimento.setOnScrollChangeListener { _, _, _, _, _ -> bordo.aggiorna() }
        mostra()
        // Il focus serve alla corona: senza, ruotarla non scorre niente.
        scorrimento.requestFocus()
    }

    /** La domanda di adesso: il titolo e le sue righe, con la lista riportata in cima. */
    private fun mostra() {
        val titolo = findViewById<TextView>(R.id.sport_title)
        titolo.text =
            when (val d = passi.domanda) {
                DomandaDelServizio.Coppie -> getString(R.string.wear_serve_title)
                is DomandaDelServizio.Primo -> getString(R.string.wear_serve_first, nomeDellaCoppia(d.lato))
                DomandaDelServizio.Correzione -> getString(R.string.wear_serve_correct_title)
            }
        val colonna = findViewById<LinearLayout>(R.id.sport_voci)
        colonna.removeAllViews()
        passi.righe().forEach { riga ->
            val vista = LayoutInflater.from(this).inflate(R.layout.item_sport_wear, colonna, false)
            mostraLaRiga(vista, riga)
            vista.setOnClickListener { alTocco(riga) }
            colonna.addView(vista)
        }
        findViewById<ScrollView>(R.id.sport_scroll).scrollTo(0, 0)
        // TalkBack: la domanda nuova si annuncia, come il titolo di una pagina.
        titolo.announceForAccessibility(titolo.text)
    }

    private fun nomeDellaCoppia(lato: Int): String = intent.getStringArrayExtra(EXTRA_NOMI)?.getOrNull(lato - 1).orEmpty()

    private fun alTocco(riga: RigaDelServizio) {
        when (val esito = passi.scegli(riga)) {
            null -> {
                mostra()
            }

            is EsitoDelServizio.Scelta -> {
                setResult(
                    Activity.RESULT_OK,
                    Intent()
                        .putExtra(EXTRA_LATO, esito.lato)
                        .putExtra(EXTRA_SLOT1, esito.slot1)
                        .putExtra(EXTRA_SLOT2, esito.slot2),
                )
                finish()
            }

            is EsitoDelServizio.Correzione -> {
                setResult(
                    Activity.RESULT_OK,
                    Intent()
                        .putExtra(EXTRA_CORREZIONE, true)
                        .putExtra(EXTRA_LATO, esito.lato)
                        .putExtra(EXTRA_SLOT, esito.slot),
                )
                finish()
            }
        }
    }

    /**
     * Il nome e, sotto, la coppia o i suoi giocatori; sulla proposta la spunta, come "in uso" nella
     * scelta dello sport: in text-primary e non in lime, che sul polso e' solo di chi serve in gioco.
     */
    private fun mostraLaRiga(
        vista: View,
        riga: RigaDelServizio,
    ) {
        vista.findViewById<TextView>(R.id.sport_name).text = riga.titolo
        val sotto = vista.findViewById<TextView>(R.id.sport_current)
        val testo = riga.dettaglio.ifEmpty { if (riga.spunta) getString(R.string.wear_serve_proposed) else "" }
        sotto.text = testo
        sotto.visibility = if (testo.isEmpty()) View.GONE else View.VISIBLE
        if (riga.spunta) {
            val colore = ContextCompat.getColor(this, R.color.text_primary)
            val lato = resources.getDimensionPixelSize(R.dimen.icon_compact)
            val spunta = ContextCompat.getDrawable(this, R.drawable.ic_check)!!.mutate()
            spunta.setBounds(0, 0, lato, lato)
            sotto.setTextColor(colore)
            sotto.setCompoundDrawablesRelative(spunta, null, null, null)
            sotto.compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.icon_text_gap)
            TextViewCompat.setCompoundDrawableTintList(sotto, ColorStateList.valueOf(colore))
        } else {
            sotto.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
        vista.contentDescription =
            listOfNotNull(
                riga.titolo,
                riga.dettaglio.ifEmpty { null },
                getString(R.string.wear_serve_proposed).takeIf { riga.spunta },
            ).joinToString(", ")
    }
}
