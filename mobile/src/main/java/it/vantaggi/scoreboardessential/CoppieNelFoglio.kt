package it.vantaggi.scoreboardessential

import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * La card COPPIE del foglio PARTITA: due posti numerati per lato (padel e tennis in doppio), nell'ordine
 * delle rose. Il primo posto serve per primo. Lavora sulle viste e non sull'Activity, come
 * [mostraIlServizio], cosi' si prova col solo layout.
 *
 * Il comando di scambio e' sempre li': dopo il primo punto non sparisce, si spegne e prende il
 * lucchetto, perche' la sua assenza non spiegherebbe niente. [giocatoriPerLato] viene da
 * [it.vantaggi.scoreboardessential.core.SportCapabilities.playersPerSide]: la card c'e' solo con 2.
 */
fun mostraLeCoppie(
    radice: View,
    giocatoriPerLato: Int?,
    rose: List<List<String>>,
    nomiSquadre: List<String>,
    partitaIniziata: Boolean,
) {
    val card = radice.findViewById<View>(R.id.pairs_card)
    card.visibility = if (giocatoriPerLato == 2) View.VISIBLE else View.GONE
    if (giocatoriPerLato != 2) return
    val contesto = radice.context
    val vuoto = contesto.getString(R.string.pair_slot_empty)
    for (squadra in 1..2) {
        val rosa = rose.getOrElse(squadra - 1) { emptyList() }
        val nomeSquadra = nomiSquadre.getOrElse(squadra - 1) { "" }
        for (posto in 1..2) {
            val nome = rosa.getOrNull(posto - 1)
            val riga = radice.findViewById<View>(slotId(squadra, posto))
            val testo = radice.findViewById<TextView>(slotNomeId(squadra, posto))
            testo.text = nome ?: vuoto
            testo.alpha = if (nome == null) ALPHA_POSTO_LIBERO else 1f
            riga.contentDescription = contesto.getString(R.string.pair_slot_description, nomeSquadra, posto, nome ?: vuoto)
        }
        // Scambiare ha senso solo con due giocatori, e solo a registro vuoto.
        val acceso = !partitaIniziata && rosa.size >= 2
        val scambio = radice.findViewById<MaterialButton>(if (squadra == 1) R.id.team1_swap_button else R.id.team2_swap_button)
        scambio.isEnabled = acceso
        scambio.setIconResource(if (partitaIniziata) R.drawable.ic_lock else R.drawable.ic_swap_horiz)
        scambio.contentDescription =
            contesto.getString(if (partitaIniziata) R.string.pair_swap_locked else R.string.pair_swap_description, nomeSquadra)
    }
}

private const val ALPHA_POSTO_LIBERO = 0.6f

private fun slotId(
    squadra: Int,
    posto: Int,
): Int =
    when {
        squadra == 1 && posto == 1 -> R.id.team1_slot1
        squadra == 1 -> R.id.team1_slot2
        posto == 1 -> R.id.team2_slot1
        else -> R.id.team2_slot2
    }

private fun slotNomeId(
    squadra: Int,
    posto: Int,
): Int =
    when {
        squadra == 1 && posto == 1 -> R.id.team1_slot1_name
        squadra == 1 -> R.id.team1_slot2_name
        posto == 1 -> R.id.team2_slot1_name
        else -> R.id.team2_slot2_name
    }
