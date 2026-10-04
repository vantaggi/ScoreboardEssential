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
        const val CHIAVE_RIFIUTATE = "rifiutate"
        const val CHIAVE_BASE = "base"
        const val CHIAVE_PARTITA = "partita"
        const val CHIAVE_BASE_DOPO = "base_dopo"
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

    fun all(): List<PendingIntent> = leggi(CHIAVE)

    private fun leggi(chiave: String): List<PendingIntent> =
        prefs
            .getString(chiave, "")
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
        partita: String? = null,
    ): Boolean {
        val attuali = all()
        if (attuali.size >= MASSIMO) {
            Log.w(TAG, "Coda piena ($MASSIMO): tocco scartato")
            return false
        }
        if (attuali.isEmpty() && base != null) {
            this.base = base
            this.partita = partita
        }
        scrivi(attuali + intento)
        return true
    }

    /**
     * Il prefisso del registro del telefono su cui la coda e' nata, come impronta (null se non e'
     * ancora noto: una coda scritta prima di L5). Va nel batch
     * ([it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_BATCH_BASE]): il telefono
     * accoda se il suo registro INIZIA con questi eventi (dopo ce ne possono essere altri, i tocchi dal
     * vivo arrivati prima dell'arretrato) e la partita e' la stessa ([partita]).
     */
    var base: String?
        get() = prefs.getString(CHIAVE_BASE, null)
        set(valore) = prefs.edit { if (valore == null) remove(CHIAVE_BASE) else putString(CHIAVE_BASE, valore) }

    /**
     * L'identita' della partita del telefono su cui la coda e' nata ([base] ne e' il registro): il
     * `matchUuid` che il telefono manda nello stato, vuoto se non ne aveva ancora uno.
     */
    var partita: String?
        get() = prefs.getString(CHIAVE_PARTITA, null)
        set(valore) = prefs.edit { if (valore == null) remove(CHIAVE_PARTITA) else putString(CHIAVE_PARTITA, valore) }

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
     *
     * Le voci rimaste sono state segnate mentre l'arretrato era in volo: la loro base e' il registro
     * DOPO di lui ([baseDopo] e [partitaDopo], dallo stato del telefono che porta [id] come ultimo
     * arretrato applicato). Se il polso non lo sa ancora (l'ack e' arrivato prima dello stato, o
     * senza Activity) la base resta da risolvere ([baseDopoDi]) e la coda non parte: prendere il
     * registro "del momento" applicherebbe le voci a un'altra partita, in silenzio (D4).
     */
    fun confermaBatch(
        id: Long,
        baseDopo: String? = null,
        partitaDopo: String? = null,
    ): Int? {
        val inVolo = batchInVolo()?.takeIf { it.id == id } ?: return null
        removeFirst(inVolo.quante)
        val rimaste = size > 0
        prefs.edit {
            remove(CHIAVE_BATCH_ID)
            remove(CHIAVE_BATCH_QUANTE)
            remove(CHIAVE_BASE)
            remove(CHIAVE_PARTITA)
            remove(CHIAVE_BASE_DOPO)
            when {
                !rimaste -> Unit
                baseDopo != null -> {
                    putString(CHIAVE_BASE, baseDopo)
                    putString(CHIAVE_PARTITA, partitaDopo.orEmpty())
                }
                else -> putLong(CHIAVE_BASE_DOPO, id)
            }
        }
        return inVolo.quante
    }

    /** L'id del blocco dopo il quale e' nata la base delle voci rimaste, se e' ancora da risolvere; 0 altrimenti. */
    fun baseDopoDi(): Long = prefs.getLong(CHIAVE_BASE_DOPO, 0L)

    /** Lo stato del telefono dopo il blocco e' arrivato: la base delle voci rimaste e' questa. */
    fun risolviBaseDopo(
        base: String,
        partita: String,
    ) {
        prefs.edit {
            putString(CHIAVE_BASE, base)
            putString(CHIAVE_PARTITA, partita)
            remove(CHIAVE_BASE_DOPO)
        }
    }

    /**
     * I tocchi che il telefono ha rifiutato: la sua partita non e' quella su cui la coda era stata
     * calcolata (L5). Stanno da parte, in una chiave sola loro: non si rimandano mai, nemmeno alla
     * partita dopo, e non si mischiano ai tocchi nuovi, che aprono una coda nuova con la base del
     * registro che il polso vede adesso. Li butta l'utente ([scartaRifiutate]).
     */
    fun rifiutate(): List<PendingIntent> = leggi(CHIAVE_RIFIUTATE)

    val rifiutateSize: Int get() = rifiutate().size

    /**
     * Il telefono ha detto di no: l'intera coda (le voci del blocco e quelle segnate mentre era in
     * volo, tutte sulla stessa base) passa fra le rifiutate, e la coda riparte vuota, senza id, base
     * ne' partita. Si aggiunge a quelle gia' messe da parte: un secondo rifiuto non cancella il primo.
     */
    fun rifiutaCoda() {
        val daParte = rifiutate() + all()
        prefs.edit {
            putString(CHIAVE_RIFIUTATE, serializza(daParte))
            remove(CHIAVE)
            remove(CHIAVE_BATCH_ID)
            remove(CHIAVE_BATCH_QUANTE)
            remove(CHIAVE_BASE)
            remove(CHIAVE_PARTITA)
            remove(CHIAVE_BASE_DOPO)
        }
    }

    /** Butta le sole voci rifiutate: l'utente ha scelto di scartarle. La coda nuova non si tocca. */
    fun scartaRifiutate() {
        prefs.edit { remove(CHIAVE_RIFIUTATE) }
    }

    private fun serializza(voci: List<PendingIntent>): String =
        voci.joinToString(SEP_VOCE) { "${it.kind}$SEP_CAMPO${it.side}$SEP_CAMPO${it.atMillis}" }

    private fun scrivi(voci: List<PendingIntent>) {
        prefs.edit { putString(CHIAVE, serializza(voci)) }
    }
}
