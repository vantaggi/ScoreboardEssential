package it.vantaggi.scoreboardessential.utils

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View

// G-2: via playEnhancedScoreAnimation (zoom, rotazione, rimbalzo) e pulseAnimation (ciclo infinito di
// una card): la Constitution li vieta e nessuno li chiamava. Le due funzioni sotto servono alla
// schermata di gioco e restano fino a G-6.

/**
 * Il riscontro del tocco su una zona +: scala 0,96 e ritorno in 100ms.
 *
 * Solo una trasformazione, niente layout e niente colore. Prima il pulsante ondeggiava e lampeggiava
 * verso il verde: ma la zona ha il colore della squadra, e un lampo di un altro colore la fa sembrare
 * di un'altra squadra.
 *
 * DA G-6: la pressione condivisa e' 0,97 e opacita' 0,85 (animator/press_feedback.xml), e questa funzione
 * non rispetta il movimento ridotto. Va sostituita dallo StateListAnimator sulle zone.
 */
fun View.animateZoneTap() {
    ObjectAnimator
        .ofPropertyValuesHolder(
            this,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 0.96f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 0.96f, 1f),
        ).apply {
            duration = 100
            start()
        }
}

/**
 * Il numero che cambia: scala 1, 1,06, 1 in 150ms. Nessun layout e nessun colore.
 *
 * DA G-6: il punteggio e' il numero della schermata di gioco; il passo decide se diventa NumberRoll
 * (la Constitution non ammette la scala sopra 1 come rimbalzo) e con quale durata (duration_fast).
 */
fun View.animateScoreNumber() {
    ObjectAnimator
        .ofPropertyValuesHolder(
            this,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.06f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.06f, 1f),
        ).apply {
            duration = 150
            start()
        }
}
