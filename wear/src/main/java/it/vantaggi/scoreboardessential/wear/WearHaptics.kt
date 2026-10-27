package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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

/**
 * La vibrazione dei punti, scelta dal menu del polso: e' dell'orologio e non del telefono, che non
 * vibra al polso di nessuno. Accesa di default, come prima che la scelta esistesse.
 */
class PreferenzeDelPolso(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var vibrazionePunti: Boolean
        get() = prefs.getBoolean(KEY_VIBRAZIONE_PUNTI, true)
        set(valore) = prefs.edit { putBoolean(KEY_VIBRAZIONE_PUNTI, valore) }

    companion object {
        private const val FILE = "preferenze_polso"
        private const val KEY_VIBRAZIONE_PUNTI = "vibrazione_punti"
    }
}

/**
 * L'aptica con la vibrazione dei punti spenta: tacciono le conferme (il tick del tocco, i colpi del
 * lato, la conferma di un cambio, l'annullamento), restano gli avvisi. Il punto in coda perde i colpi
 * del lato e tiene il colpo lungo, che e' la parte che dice "il telefono non ce l'ha"; il non
 * confermato e l'allarme del portiere suonano come sempre. Un punto perso non deve passare in silenzio.
 *
 * La scelta si legge a ogni colpo ([accesa]): cambiata dal menu, vale dal tocco dopo.
 */
class HapticsDelPolso(
    private val base: WearHaptics,
    private val accesa: () -> Boolean,
) : WearHaptics {
    override fun suona(pattern: LongArray) {
        if (accesa()) {
            base.suona(pattern)
            return
        }
        when {
            CONFERME.any { it.contentEquals(pattern) } -> Unit
            IN_CODA.any { it.contentEquals(pattern) } -> base.suona(SOLO_AVVISO_CODA)
            else -> base.suona(pattern)
        }
    }

    override fun annulla() = base.annulla()

    override fun tick() {
        if (accesa()) base.tick()
    }

    companion object {
        private val CONFERME =
            listOf(
                WearPatterns.CONFERMA_SINISTRA,
                WearPatterns.CONFERMA_DESTRA,
                WearPatterns.ANNULLAMENTO,
                HapticFeedbackManager.PATTERN_CONFIRM,
            )
        private val IN_CODA = listOf(WearPatterns.IN_CODA_SINISTRA, WearPatterns.IN_CODA_DESTRA)

        /** Il colpo lungo del punto in coda, senza i colpi del lato che lo precedono. */
        val SOLO_AVVISO_CODA = longArrayOf(0, 350)

        fun di(context: Context): HapticsDelPolso {
            val preferenze = PreferenzeDelPolso(context)
            return HapticsDelPolso(VibratoreWearHaptics.di(context)) { preferenze.vibrazionePunti }
        }
    }
}
