package it.vantaggi.scoreboardessential.wear

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import it.vantaggi.scoreboardessential.core.indiceDiChiServe
import it.vantaggi.scoreboardessential.shared.R as SharedR

/** Le sigle da quattro lettere (cognomi che cominciano uguale) scendono alla misura piccola. */
private const val LETTERE_DELLA_SIGLA_CORTA = 3

/**
 * Le sigle delle due coppie sopra le cifre, sempre, al posto dei pallini (bozza A di chi batte, scelta
 * da Cioti il 3 novembre 2026, dopo la prima partita dal vivo): "VAN GLO" sopra il punteggio della
 * coppia 1, "ROS BIA" sopra l'altro. Chi batte e' in lime e 600, gli altri in text-secondary e 400:
 * il lime non e' l'unico segno (anche il peso e la luminosita' cambiano). In ambient niente colore:
 * chi batte e' bianco e 600. Niente movimento: le sigle cambiano colore solo quando cambia chi batte,
 * cioe' a fine game.
 *
 * Con le sigle i pallini si spengono, ma restano INVISIBLE e non GONE: il primo pallino di ogni lato
 * e' l'ancora delle sigle (un GONE le porterebbe in cima alla colonna). [coppie] null (rose assenti,
 * calcio, telefono che non le manda) nasconde le sigle e il chiamante rimette i pallini. Funzione sulle viste e non sull'Activity: i test la chiamano sul layout gonfiato.
 *
 * @return true se le sigle sono in vista.
 */
internal fun mostraLeCoppie(
    radice: View,
    coppie: Pair<List<String>, List<String>>?,
    servingSide: Int,
    servingSlot: Int,
    ambient: Boolean,
): Boolean {
    val viste = listOf(radice.findViewById<TextView>(R.id.team1ServeNext), radice.findViewById(R.id.team2ServeNext))
    if (coppie == null) {
        viste.forEach { it?.visibility = View.GONE }
        return false
    }
    listOf(R.id.team1ServeDot, R.id.team1ServeDotSecond, R.id.team2ServeDot, R.id.team2ServeDotSecond).forEach {
        radice.findViewById<View>(it)?.visibility = View.INVISIBLE
    }
    val contesto = radice.context
    val lunghe = (coppie.first + coppie.second).any { it.length > LETTERE_DELLA_SIGLA_CORTA }
    val misura = radice.resources.getDimension(if (lunghe) R.dimen.face_coppie_text_size_lunghe else R.dimen.face_coppie_text_size)
    val coloreChiBatte = ContextCompat.getColor(contesto, if (ambient) R.color.text_on_watch else R.color.accent_default)
    val coloreAltri = ContextCompat.getColor(contesto, R.color.text_secondary)
    val forte = ResourcesCompat.getFont(contesto, SharedR.font.inter_semibold)
    val normale = ResourcesCompat.getFont(contesto, SharedR.font.inter_regular)
    listOf(coppie.first, coppie.second).forEachIndexed { i, sigle ->
        val vista = viste[i] ?: return@forEachIndexed
        val batte = indiceDiChiServe(i + 1, servingSide, servingSlot.takeIf { it != 0 }, sigle.size)
        val testo = SpannableStringBuilder()
        sigle.forEachIndexed { j, sigla ->
            if (j > 0) testo.append(" ")
            val inizio = testo.length
            testo.append(sigla)
            val suo = j == batte
            testo.setSpan(
                ForegroundColorSpan(if (suo) coloreChiBatte else coloreAltri),
                inizio,
                testo.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            (if (suo) forte else normale)?.let {
                testo.setSpan(TypefaceSpan(it), inizio, testo.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        vista.setTextSize(TypedValue.COMPLEX_UNIT_PX, misura)
        vista.text = testo
        vista.visibility = View.VISIBLE
    }
    return true
}
