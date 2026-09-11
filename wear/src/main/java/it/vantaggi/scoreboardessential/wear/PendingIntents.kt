package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Un'intenzione registrata al polso e non ancora arrivata al telefono.
 *
 * Porta con se' l'orario in cui e' stato dato il tocco, non quello in cui verra' consegnata: la
 * durata della partita, le serie e i tempi esatti dei punti si calcolano da questi millisecondi,
 * e una partita giocata alle 18 che arriva al telefono alle 20 deve restare una partita delle 18.
 */
data class PendingIntent(
    val kind: String,
    val side: Int,
    val atMillis: Long,
)

/**
 * La coda dei tocchi dati mentre il telefono non c'era.
 *
 * Prima di questa classe l'orologio non scriveva NIENTE su disco: se il telefono era in borsa, o
 * spento, o semplicemente fuori portata, ogni tocco produceva una vibrazione di errore e spariva.
 * Chi voleva segnare una partita dal solo polso e mandarla dopo non poteva: non c'era un "dopo".
 *
 * La coda contiene INTENZIONI, non punteggi. E' la stessa scelta del protocollo v2 e per la stessa
 * ragione: il punteggio lo calcola il telefono, che resta l'unico a conoscere le regole. Al
 * ritorno del telefono la coda viene spedita in ordine e ripiegata nel motore, esattamente come se
 * i tocchi fossero arrivati uno per uno -- il che, per un motore che rifa' il calcolo dall'inizio
 * a ogni annullamento, e' letteralmente la stessa operazione.
 *
 * Formato: `kind,side,atMillis` separati da `;`. Una sola preferenza riscritta per intero a ogni
 * aggiunta invece di un file in append: a questi volumi (qualche centinaio di voci in una partita
 * lunga) costa niente, e toglie di mezzo la riga scritta a meta' dopo uno spegnimento.
 */
class PendingIntents(
    context: Context,
) {
    private companion object {
        const val TAG = "PendingIntents"
        const val PREFS = "wear_pending_intents"
        const val CHIAVE = "queue"
        const val SEP_VOCE = ";"
        const val SEP_CAMPO = ","

        /**
         * Oltre questo numero si smette di accodare invece di crescere all'infinito.
         *
         * Una partita di padel lunga sta abbondantemente sotto: 3 set da 13 game da ~7 punti fanno
         * meno di 300 tocchi. Il tetto non serve al caso normale, serve al caso in cui qualcosa
         * vada storto e l'orologio resti a registrare per giorni senza mai trovare il telefono.
         */
        const val MASSIMO = 2000
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<PendingIntent> =
        prefs
            .getString(CHIAVE, "")
            .orEmpty()
            .split(SEP_VOCE)
            .filter { it.isNotBlank() }
            .mapNotNull { voce ->
                val campi = voce.split(SEP_CAMPO)
                val side = campi.getOrNull(1)?.toIntOrNull()
                val at = campi.getOrNull(2)?.toLongOrNull()
                if (campi.size < 3 || campi[0].isBlank() || side == null || at == null) {
                    // Una voce illeggibile non deve impedire la consegna di tutte le altre.
                    Log.w(TAG, "Voce in coda illeggibile, saltata")
                    null
                } else {
                    PendingIntent(campi[0], side, at)
                }
            }

    val size: Int get() = all().size

    /** Ritorna `false` quando la coda e' piena: il tocco non viene registrato e va detto. */
    fun add(intento: PendingIntent): Boolean {
        val attuali = all()
        if (attuali.size >= MASSIMO) {
            Log.w(TAG, "Coda piena ($MASSIMO): tocco scartato")
            return false
        }
        scrivi(attuali + intento)
        return true
    }

    /**
     * Toglie dalla testa le voci gia' consegnate.
     *
     * Si rimuove per QUANTITA' e non svuotando tutto, perche' fra l'inizio e la fine di una
     * consegna l'utente puo' aver segnato altri punti: svuotare cancellerebbe anche quelli.
     */
    fun removeFirst(quante: Int) {
        if (quante <= 0) return
        scrivi(all().drop(quante))
    }

    private fun scrivi(voci: List<PendingIntent>) {
        prefs.edit {
            putString(
                CHIAVE,
                voci.joinToString(SEP_VOCE) { "${it.kind}$SEP_CAMPO${it.side}$SEP_CAMPO${it.atMillis}" },
            )
        }
    }
}
