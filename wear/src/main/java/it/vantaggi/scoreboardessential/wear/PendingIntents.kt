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
        const val CHIAVE_BATCH_ID = "batch_id"
        const val CHIAVE_BATCH_QUANTE = "batch_n"
        const val CHIAVE_RIFIUTATA = "rifiutata"
        const val CHIAVE_BASE = "base"
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

    /**
     * Ritorna `false` quando la coda e' piena: il tocco non viene registrato e va detto.
     *
     * [base] e' l'impronta del registro del telefono che il polso vedeva (L5): si salva solo se la
     * coda era vuota, perche' e' la base su cui la coda NASCE e non cambia finche' non si svuota.
     */
    fun add(
        intento: PendingIntent,
        base: String? = null,
    ): Boolean {
        val attuali = all()
        if (attuali.size >= MASSIMO) {
            Log.w(TAG, "Coda piena ($MASSIMO): tocco scartato")
            return false
        }
        if (attuali.isEmpty() && base != null) this.base = base
        scrivi(attuali + intento)
        return true
    }

    /**
     * Il registro del telefono su cui la coda e' nata, come impronta (null se non e' ancora noto:
     * una coda scritta prima di L5, o rimasta in coda dopo un arretrato, la cui base e' lo stato che
     * il telefono mandera'). Va nel batch ([it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_BATCH_BASE]):
     * il telefono accoda solo se il suo registro e' ancora quello.
     */
    var base: String?
        get() = prefs.getString(CHIAVE_BASE, null)
        set(valore) = prefs.edit { if (valore == null) remove(CHIAVE_BASE) else putString(CHIAVE_BASE, valore) }

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

    /**
     * L'arretrato spedito e non ancora confermato: la sua identita' e quante voci della testa comprende.
     *
     * Sta su disco CON la coda (L5): l'id deve restare lo stesso a ogni rinvio, anche dopo la morte
     * del processo, perche' e' l'id a dire al telefono "questo l'hai gia' applicato" quando l'ack si
     * e' perso. Un rinvio manda le prime [quante] voci, non quelle segnate dopo.
     */
    data class BatchInVolo(
        val id: Long,
        val quante: Int,
    )

    fun batchInVolo(): BatchInVolo? {
        val id = prefs.getLong(CHIAVE_BATCH_ID, 0L)
        val quante = prefs.getInt(CHIAVE_BATCH_QUANTE, 0)
        return if (id > 0L && quante > 0) BatchInVolo(id, quante) else null
    }

    fun segnaBatchInVolo(batch: BatchInVolo) {
        prefs.edit {
            putLong(CHIAVE_BATCH_ID, batch.id)
            putInt(CHIAVE_BATCH_QUANTE, batch.quante)
        }
    }

    /**
     * Il telefono ha applicato l'arretrato [id]: le sue voci escono dalla testa e l'identita' si
     * azzera. Ritorna quante ne ha tolte, o null se [id] non e' quello in volo (gia' confermato da
     * un altro percorso: l'ack, lo stato v2 col suo id, il servizio ad app chiusa). Idempotente.
     */
    fun confermaBatch(id: Long): Int? {
        val inVolo = batchInVolo()?.takeIf { it.id == id } ?: return null
        removeFirst(inVolo.quante)
        // Le voci rimaste sono state segnate mentre l'arretrato era in volo: la loro base e' il
        // registro dopo di lui, che il polso conoscera' dallo stato del telefono.
        prefs.edit {
            remove(CHIAVE_BATCH_ID)
            remove(CHIAVE_BATCH_QUANTE)
            remove(CHIAVE_RIFIUTATA)
            remove(CHIAVE_BASE)
        }
        return inVolo.quante
    }

    /**
     * Il telefono ha detto di no: la sua partita non e' quella su cui la coda e' stata calcolata. Da
     * qui la coda non si rimanda piu' da sola, nemmeno alla partita dopo: la scarta l'utente.
     */
    var rifiutata: Boolean
        get() = prefs.getBoolean(CHIAVE_RIFIUTATA, false)
        set(valore) = prefs.edit { putBoolean(CHIAVE_RIFIUTATA, valore) }

    /** Butta la coda, l'identita' dell'arretrato e il rifiuto: l'utente ha scelto di scartare. */
    fun scarta() {
        prefs.edit {
            remove(CHIAVE)
            remove(CHIAVE_BATCH_ID)
            remove(CHIAVE_BATCH_QUANTE)
            remove(CHIAVE_RIFIUTATA)
            remove(CHIAVE_BASE)
        }
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
