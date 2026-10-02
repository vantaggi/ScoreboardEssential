package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager

/**
 * Il vocabolario aptico del polso: cosa vibra, non come.
 *
 * Il numero di impulsi dice PER CHI (uno a sinistra, due a destra) e l'impulso lungo in coda dice
 * che il telefono non ce l'ha ancora: e' l'unico canale che non chiede di togliere gli occhi dal
 * campo, e deve dire la verita'. Per questo la conferma non suona alla consegna ma quando il
 * telefono rimanda lo stato (vedi WearViewModel).
 *
 * I pattern dei lati stanno qui e non in [HapticFeedbackManager] perche' il telefono non li usa: il
 * suo tabellone si muove sotto il dito e non ha una "ricevuta" da distinguere per lato. Quelli che
 * il telefono parla davvero (annullamento, tocco inerte) restano in :shared, per non avere due
 * significati dello stesso schema.
 */
object WearPatterns {
    /** Un impulso: il punto del lato sinistro e' arrivato al telefono ed e' stato applicato. */
    val CONFERMA_SINISTRA = longArrayOf(0, 70)

    /** Due impulsi: lato destro. */
    val CONFERMA_DESTRA = longArrayOf(0, 70, 90, 70)

    /** Come la conferma, piu' un colpo lungo: il punto e' al sicuro in coda, ma il telefono non l'ha. */
    val IN_CODA_SINISTRA = longArrayOf(0, 70, 120, 350)

    val IN_CODA_DESTRA = longArrayOf(0, 70, 90, 70, 120, 350)

    /**
     * Un colpo solo di 400ms per "non confermato" e per l'errore: il vecchio doppio colpo
     * (60/120/60) coinciderebbe con "destra". E' il tocco inerte condiviso col telefono.
     */
    val NON_CONFERMATO = HapticFeedbackManager.PATTERN_INERT_TAP

    /** Annullamento e correzione, uguali per i due lati: lo schema e' quello condiviso. */
    val ANNULLAMENTO = HapticFeedbackManager.PATTERN_UNDO

    /** Il lato e' 1 o 2: chiunque altro suona come il sinistro, mai in silenzio. */
    fun conferma(lato: Int): LongArray = if (lato == 2) CONFERMA_DESTRA else CONFERMA_SINISTRA

    fun inCoda(lato: Int): LongArray = if (lato == 2) IN_CODA_DESTRA else IN_CODA_SINISTRA
}

/**
 * Chi fa vibrare il polso, dietro un'interfaccia: nei test e' un finto che registra i pattern, e
 * "il pattern della conferma destra" diventa una cosa che si verifica invece di una che si spera.
 */
interface WearHaptics {
    /** Suona il pattern una volta sola; cancella quello in corso, come fa il vibratore vero. */
    fun suona(pattern: LongArray)

    fun annulla()

    /**
     * Il tocco e' stato sentito: il colpo piu' leggero e distinto che il vibratore sa dare. Non e'
     * uno dei pattern: il vocabolario conta gli impulsi, e un tick non deve potersi contare.
     */
    fun tick()
}

/** L'aptica vera: il vibratore di sistema, se c'e'. Senza, tace. */
class VibratoreWearHaptics(
    private val vibrator: Vibrator?,
) : WearHaptics {
    override fun suona(pattern: LongArray) {
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    override fun annulla() {
        vibrator?.cancel()
    }

    // Suonato dal vibratore e non da performHapticFeedback: cosi' non dipende dall'impostazione di
    // sistema "vibrazione al tocco", e vale lo stesso effetto in ogni schermata.
    override fun tick() {
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    companion object {
        fun di(context: Context): VibratoreWearHaptics = VibratoreWearHaptics(ContextCompat.getSystemService(context, Vibrator::class.java))
    }
}
