package it.vantaggi.scoreboardessential.wear

import android.graphics.Matrix
import android.graphics.Rect
import android.view.View
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Le liste del polso sul tondo (wearable.md): a tutta larghezza, e vicino al bordo alto e basso le
 * righe si stringono quanto basta per stare dentro il cerchio. La riga al centro e' intera.
 *
 * Funzione pura, misurata in pixel dello schermo: la riga sta fra [cima] e [fondo], e' larga
 * [larghezza] ed e' centrata in orizzontale su un quadrante di [diametro]. La scala e' il rapporto
 * fra la corda del cerchio all'altezza del bordo della riga piu' lontano dal centro e la larghezza
 * della riga, mai sotto [SCALA_MINIMA]: oltre, la riga e' gia' quasi fuori dallo schermo.
 */
object BordoTondo {
    const val SCALA_MINIMA = 0.7f

    fun scala(
        cima: Float,
        fondo: Float,
        larghezza: Float,
        diametro: Float,
    ): Float {
        if (larghezza <= 0f || diametro <= 0f) return 1f
        val raggio = diametro / 2f
        val distanza = maxOf(abs(cima - raggio), abs(fondo - raggio))
        if (distanza >= raggio) return SCALA_MINIMA
        val corda = 2f * sqrt(raggio * raggio - distanza * distanza)
        return (corda / larghezza).coerceIn(SCALA_MINIMA, 1f)
    }
}

/**
 * Applica [BordoTondo] alle righe di una colonna che scorre. Solo sul tondo: sul quadrato le righe
 * restano intere, come vuole wearable.md.
 *
 * La scala passa dalla matrice di disegno della riga e non da scaleX e scaleY, che sono della
 * pressione condivisa (0,97 e ritorno a 1): le due cose si sommano invece di cancellarsi. Il tocco
 * resta sulla riga intera, che e' piu' grande di quella disegnata: un bersaglio mai piu' piccolo.
 * Non e' un'animazione: la riga segue il dito e la corona, quindi niente da ridurre col movimento ridotto.
 */
class ListaTonda(
    private val scorrimento: ScrollView,
    private val righe: () -> List<View>,
) {
    private val tondo = scorrimento.resources.configuration.isScreenRound
    private val area = Rect()

    /** Da chiamare a ogni scorrimento e dopo ogni impaginazione. */
    fun aggiorna() {
        if (!tondo) return
        val diametro = scorrimento.rootView.width.toFloat()
        if (diametro <= 0f) return
        val inizio = IntArray(2).also { scorrimento.getLocationInWindow(it) }[1]
        righe().forEach { riga ->
            if (riga.visibility != View.VISIBLE || riga.width == 0) return@forEach
            area.set(0, 0, riga.width, riga.height)
            scorrimento.offsetDescendantRectToMyCoords(riga, area)
            val cima = (inizio + area.top - scorrimento.scrollY).toFloat()
            val s = BordoTondo.scala(cima, cima + riga.height, riga.width.toFloat(), diametro)
            riga.animationMatrix =
                if (s >= 1f) {
                    null
                } else {
                    Matrix().apply { setScale(s, s, riga.width / 2f, riga.height / 2f) }
                }
        }
    }
}
