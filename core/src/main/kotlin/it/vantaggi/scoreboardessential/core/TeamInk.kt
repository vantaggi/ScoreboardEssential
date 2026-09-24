package it.vantaggi.scoreboardessential.core

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * La regola del colore di squadra, una volta sola per telefono e orologio.
 *
 * Il colore lo sceglie l'utente con un selettore libero, quindi puo' essere qualsiasi cosa: un
 * giallo, un blu notte, il nero. Chi ci scrive sopra, o lo mette su un fondo nero, non puo'
 * fidarsi della tinta. Prima il telefono decideva l'inchiostro con una media NON linearizzata e
 * soglia 0,5, e su #FF4BFF scendeva a 2,07:1; l'orologio non decideva niente e metteva le cifre
 * nel colore grezzo, che col blu notte sparivano (1,59:1).
 *
 * Sta in `:core` e non in `:shared` perche' qui il plugin e' kotlin.jvm: il compilatore
 * impedisce `android.*`, e il test gira su JVM puro. `:mobile` e `:wear` dipendono gia' da
 * `:core`, quindi lo stesso Int ARGB arrivato col protocollo Wear da' lo stesso inchiostro sui
 * due lati. Due copie della regola, con due arrotondamenti della soglia, davano esiti diversi
 * proprio sui colori al confine (vedi DESIGN.md, "Coerenza fra telefono e orologio").
 *
 * Tutto su Int ARGB; l'alfa viene ignorata, i colori di squadra sono opachi.
 */
object TeamInk {
    const val NERO: Int = 0xFF000000.toInt()
    const val BIANCO: Int = 0xFFFFFFFF.toInt()

    /**
     * La luminanza in cui nero e bianco danno lo stesso contrasto: (L+0,05)/0,05 = 1,05/(L+0,05).
     *
     * Calcolata e non scritta a mano: 0,179 e 0,1791 sono tutti e due arrotondamenti, e fra i due
     * cadono migliaia di colori (#8454F6 e' uno di questi) su cui le due piste avevano scelto
     * inchiostri opposti. Col valore esatto il caso peggiore su tutto l'RGB resta 4,58:1.
     */
    val SOGLIA: Double = sqrt(1.05 * 0.05) - 0.05

    /** Luminanza relativa WCAG: canali sRGB linearizzati, pesi 0,2126 / 0,7152 / 0,0722. */
    fun luminance(argb: Int): Double =
        0.2126 * lineare((argb shr 16) and 0xFF) +
            0.7152 * lineare((argb shr 8) and 0xFF) +
            0.0722 * lineare(argb and 0xFF)

    /** Rapporto di contrasto WCAG fra due colori, da 1 a 21, indipendente dall'ordine. */
    fun contrast(
        a: Int,
        b: Int,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * L'inchiostro per un testo o un glifo sopra [argb]: nero o bianco PURI.
     *
     * Puri perche' con la coppia #E0E0E0 / #1E1E1E dello street, anche scegliendo sempre la
     * migliore, il caso peggiore e' 3,55:1 (#66758A): non basta per il testo. Con nero e bianco
     * ogni colore sRGB ha almeno 4,58:1.
     */
    fun on(argb: Int): Int = if (luminance(argb) >= SOGLIA) NERO else BIANCO

    /**
     * Il colore di squadra usato come GRAFICA (barretta, striscia) su fondo nero.
     *
     * Per la grafica basta 3:1 (WCAG 1.4.11). Se il colore li' ha gia', resta com'e': la barretta
     * deve essere il colore scelto, non una sua interpretazione. Altrimenti lo si mescola col
     * bianco al passo di 0,01 finche' il contrasto arriva a [min]: la tinta resta riconoscibile e
     * sale solo la luminanza (il blu notte #000080 diventa #4F4FA7). Il bianco puro vale 21:1,
     * quindi per ogni [min] fino a 21 il ciclo trova una risposta.
     */
    fun graphicOnBlack(
        argb: Int,
        min: Double = 3.0,
    ): Int {
        if (contrast(argb, NERO) >= min) return argb
        for (passo in 1..100) {
            val schiarito = versoIlBianco(argb, passo / 100.0)
            if (contrast(schiarito, NERO) >= min) return schiarito
        }
        return BIANCO
    }

    private fun lineare(canale: Int): Double {
        val c = canale / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun versoIlBianco(
        argb: Int,
        t: Double,
    ): Int {
        fun canale(c: Int): Int = (c + (255 - c) * t).roundToInt()
        val r = canale((argb shr 16) and 0xFF)
        val g = canale((argb shr 8) and 0xFF)
        val b = canale(argb and 0xFF)
        return NERO or (r shl 16) or (g shl 8) or b
    }
}
