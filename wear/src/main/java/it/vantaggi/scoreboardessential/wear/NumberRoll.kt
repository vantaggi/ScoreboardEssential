package it.vantaggi.scoreboardessential.wear

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * NumberRoll della UI Constitution (components/NumberRoll) sulle cifre del quadrante: il valore che
 * cambia di un passo mostra la direzione del cambio, una cifra alla volta. Salendo la vecchia esce in
 * alto e solo dopo la nuova arriva dal basso, scendendo il contrario. Stessa sequenza e stessi tempi
 * del telefono (bozza A del proprietario, 31 ottobre 2026; prima 80ms + 80ms con ease_standard, bozza
 * W4a); la corsa e' piu' breve, come chiede 08-wearable: un quarto della scatola.
 *
 * ```text
 * Transition name: Value step (orologio)
 * Trigger: il punteggio di un lato cambia di un passo mentre lo schermo e' acceso e guardato
 * Persistent element: la scatola della cifra (larghezza fissa dalle colonne del quadrante)
 * Animated properties: translationY e alpha, niente layout
 * Duration or spring token: duration_instant con ease_exit (uscita), poi duration_fast con ease_enter
 *   (entrata); nessuna molla: non c'e' posizione da far assestare
 * Exit behavior: la vecchia sparisce del tutto prima che compaia la nuova
 * Interruption behavior: un cambio nuovo chiude subito quello in corso e parte dal valore di adesso
 * Reduced-motion fallback: la cifra si sostituisce subito: su Android il movimento ridotto e' la scala
 *   delle animazioni a zero, e con quella anche una dissolvenza salterebbe alla fine (conflitto in DESIGN.md)
 * Purpose: far vedere che il punteggio e' cambiato e in che verso
 * ```
 *
 * Il rotolo NON parte mai da qui in ambient, al risveglio o al primo disegno: lo decide chi chiama,
 * passando direzione 0 (cambio immediato). La vista passata e' la cifra vera: tiene il testo, il
 * colore, il carattere condensato e la regione live `polite` per TalkBack. Durante il rotolo c'e' una
 * seconda vista, il fantasma della cifra vecchia, nascosta ad accessibilita': l'animazione non e'
 * mai l'unico segnale (c'e' anche la vibrazione per lato).
 */
internal class NumberRoll(
    private val cifra: TextView,
) {
    internal var corsa: AnimatorSet? = null
        private set
    private var fantasma: TextView? = null

    /** `true` mentre il rotolo e' in corso: serve ai test. */
    val inCorso: Boolean get() = corsa != null

    /** Quante volte il rotolo e' partito davvero (non i cambi immediati): serve ai test. */
    internal var avviati = 0
        private set

    /**
     * Mostra [testo] al posto della cifra di adesso. [direzione] e' [SU] se il valore sale, [GIU] se
     * scende, 0 se non si sa o se il rotolo non e' ammesso (cambio immediato). Un tocco nuovo
     * sostituisce subito il rotolo in corso.
     */
    fun mostra(
        testo: String,
        direzione: Int,
    ) {
        val vecchio = cifra.text.toString()
        if (vecchio == testo) return
        ferma()
        val genitore = cifra.parent as? ViewGroup
        // Col movimento ridotto la cifra cambia subito. Il componente chiederebbe un breve cambio di
        // opacita', ma su Android il movimento ridotto e' la scala delle animazioni a zero, e con quella
        // ogni animatore, dissolvenza compresa, salta alla fine: il cambio immediato e' l'unico onesto.
        val senzaMovimento =
            direzione == 0 ||
                genitore == null ||
                !cifra.isLaidOut ||
                vecchio.isEmpty() ||
                MovimentoRidotto.attivo(cifra.context)
        if (senzaMovimento) {
            cifra.text = testo
            return
        }
        val corsaInPixel = cifra.height * cifra.resources.getFraction(R.fraction.number_roll_travel, 1, 1)
        val uscente = copiaDellaCifra(vecchio)
        genitore!!.addView(uscente, parametriDellaCifra())
        fantasma = uscente
        cifra.text = testo

        // Prima esce la vecchia (duration_instant, ease_exit), poi entra la nuova (duration_fast,
        // ease_enter): mai due cifre a schermo nello stesso istante.
        val risorse = cifra.resources
        val uscitaMs = risorse.getInteger(R.integer.duration_instant).toLong()
        val entrataMs = risorse.getInteger(R.integer.duration_fast).toLong()
        // Salendo la cifra vecchia va verso l'alto (y negativo) e la nuova parte dal basso.
        val verso = if (direzione > 0) -1f else 1f
        cifra.translationY = -verso * corsaInPixel
        cifra.alpha = 0f
        val uscita =
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(uscente, View.TRANSLATION_Y, 0f, verso * corsaInPixel),
                    ObjectAnimator.ofFloat(uscente, View.ALPHA, 1f, 0f),
                )
                duration = uscitaMs
                interpolator = AnimationUtils.loadInterpolator(cifra.context, R.interpolator.ease_exit)
            }
        val entrata =
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(cifra, View.TRANSLATION_Y, -verso * corsaInPixel, 0f),
                    ObjectAnimator.ofFloat(cifra, View.ALPHA, 0f, 1f),
                )
                duration = entrataMs
                interpolator = AnimationUtils.loadInterpolator(cifra.context, R.interpolator.ease_enter)
            }
        val insieme =
            AnimatorSet().apply {
                playSequentially(uscita, entrata)
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) = ripulisci()
                    },
                )
            }
        corsa = insieme
        avviati++
        insieme.start()
    }

    /** Interrompe il rotolo: la cifra nuova e' gia' al suo posto, il fantasma sparisce subito. */
    fun ferma() {
        val attuale = corsa
        corsa = null
        attuale?.cancel()
        ripulisci()
    }

    private fun ripulisci() {
        corsa = null
        fantasma?.let { (it.parent as? ViewGroup)?.removeView(it) }
        fantasma = null
        cifra.translationY = 0f
        cifra.alpha = 1f
    }

    /**
     * Il fantasma sta dove sta la cifra: stessi vincoli nella colonna del quadrante (la cifra e' a
     * 0dp fra i suoi margini, quindi la scatola e' la stessa), cosi' non c'e' misura da rifare.
     */
    private fun parametriDellaCifra(): ViewGroup.LayoutParams {
        val originali = cifra.layoutParams
        return if (originali is ConstraintLayout.LayoutParams) {
            // Non il costruttore di copia: condivide con l'originale il ConstraintWidget interno, e le
            // due viste si disegnerebbero lo stesso rettangolo (la cifra resterebbe a larghezza 0).
            ConstraintLayout.LayoutParams(originali.width, originali.height).apply {
                startToStart = originali.startToStart
                startToEnd = originali.startToEnd
                endToStart = originali.endToStart
                endToEnd = originali.endToEnd
                topToTop = originali.topToTop
                topToBottom = originali.topToBottom
                bottomToTop = originali.bottomToTop
                bottomToBottom = originali.bottomToBottom
                horizontalBias = originali.horizontalBias
                verticalBias = originali.verticalBias
                marginStart = originali.marginStart
                marginEnd = originali.marginEnd
                topMargin = originali.topMargin
                bottomMargin = originali.bottomMargin
            }
        } else {
            ViewGroup.MarginLayoutParams(cifra.width, cifra.height)
        }
    }

    /** Una copia della cifra com'e' adesso: stesso corpo, colore, carattere e allineamento. */
    private fun copiaDellaCifra(testo: String): TextView =
        TextView(cifra.context).apply {
            paint.set(cifra.paint)
            text = testo
            gravity = cifra.gravity
            includeFontPadding = cifra.includeFontPadding
            setTextColor(cifra.currentTextColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            maxLines = 1
        }

    companion object {
        const val SU = 1
        const val GIU = -1

        /**
         * Il verso del cambio fra due punteggi stampati: "0", "15", "30", "40", "AV" nel tennis e nel
         * padel, i numeri interi altrove. "AV" sta fra il 40 e il game. 0 se uno dei due non e' un
         * punteggio o se sono uguali (cambio immediato). Stessa regola del telefono: un game chiuso
         * (40 che torna a 0) rotola in giu', limite noto.
         */
        fun direzione(
            prima: String?,
            dopo: String?,
        ): Int {
            val a = valore(prima) ?: return 0
            val b = valore(dopo) ?: return 0
            return when {
                b > a -> SU
                b < a -> GIU
                else -> 0
            }
        }

        private fun valore(testo: String?): Int? =
            when {
                testo == null -> null
                testo.trim().equals("AV", ignoreCase = true) -> VALORE_VANTAGGIO
                else -> testo.trim().toIntOrNull()
            }

        /** Fra 40 e il game: abbastanza da stare sopra 40, e sotto ogni punteggio a game gia' chiuso. */
        private const val VALORE_VANTAGGIO = 41
    }
}
