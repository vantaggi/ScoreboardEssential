package it.vantaggi.scoreboardessential.padelelite

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A che punto e' una partita nel suo viaggio verso Padel Elite. Sempre detto con una parola e un'icona. */
enum class InvioState {
    /** Nella coda: parte da sola appena c'e' rete. */
    QUEUED,

    /** Consegnata: aspetta che un admin del gruppo la apra e scelga i giocatori. */
    SENT,

    /** Rimandata: il server ha sostituito il file della voce ancora in attesa (giocatori collegati, correzioni). */
    UPDATED,

    /**
     * Rimandata o ritentata, e la voce c'era gia' ancora in attesa: il server non ha cambiato niente (file
     * identico, o un server che non sostituisce, o l'ha inviata un altro). Non dice "aggiornata" per non mentire.
     */
    PRESENT,

    /** Un admin l'ha importata: e' nello storico del gruppo. */
    IMPORTED,

    /** Un admin l'ha scartata. Rimandarla la riapre. */
    DISCARDED,

    /** Non parte e non partira': c'e' un motivo ([InvioReason]). Rimediato, si puo' rimandare. */
    UNSENDABLE,

    /** La sessione non vale piu': rifare l'accesso, poi rimandare. */
    LOGIN_AGAIN,
}

/** Il motivo di un [InvioState.UNSENDABLE], o di una coda che aspetta. */
enum class InvioReason {
    /** In coda perche' la casella del gruppo e' piena: si riprova piu' tardi. */
    INBOX_FULL,

    /** Il server ha rifiutato il file (`details` dice il campo). */
    INVALID_PAYLOAD,

    /** Il file supera i 200 KB. */
    TOO_LARGE,

    /** Non si e' (piu') membri del gruppo scelto. */
    NOT_MEMBER,

    /** La partita non ha un file valido: senza l'identificativo, o senza quattro giocatori. */
    NO_FILE,

    /** Il server risponde in un modo che l'app non conosce, per piu' tentativi di fila. */
    SERVER,
}

data class InvioInfo(
    val state: InvioState,
    val reason: InvioReason? = null,
    /** Il pezzo del file che non tornava (`matchId`, `players`...), se il server l'ha detto. */
    val detail: String? = null,
    /** Il gruppo a cui e' partito l'ultimo file, e [links] i collegamenti che portava ([FirmaCollegamenti]); null = non si sa. */
    val group: String? = null,
    val links: String? = null,
) {
    /**
     * I collegamenti dei giocatori sono cambiati dopo l'invio, e la voce e' ancora in attesa: nella
     * casella c'e' un file con i collegamenti di prima. [current] e' la firma di adesso per quel gruppo.
     * Senza firma salvata (stati di prima, o file senza firma) non si sa e non si dice niente.
     */
    fun linksChanged(current: String?): Boolean = isPending && links != null && current != null && current != links

    /**
     * Il comando "Invia a Padel Elite" compare solo dove ha senso: mai mentre e' in coda o gia' arrivata.
     * Una voce scartata dall'admin resta scartata (il server non la riapre): niente comando.
     */
    val canSend: Boolean
        get() = state == InvioState.UNSENDABLE || state == InvioState.LOGIN_AGAIN

    /** La voce e' nella casella e aspetta un admin: ci si puo' ancora cambiare il file con "Invia di nuovo". */
    val isPending: Boolean
        get() = state == InvioState.SENT || state == InvioState.UPDATED || state == InvioState.PRESENT

    /** "Invia di nuovo": solo per una voce in attesa; importata e scartata non cambiano piu'. */
    val canResend: Boolean get() = isPending
}

/**
 * Lo stato di invio di ogni partita, per `matchUuid`, in un file di preferenze. Non e' in Room
 * per non toccare lo schema (e la sua migrazione) per un dato che si puo' anche perdere: una
 * partita senza stato e' una partita mai inviata, e il file nel database non cambia.
 *
 * [states] e' un flusso: la card dello storico si aggiorna quando il lavoro in coda cambia stato.
 */
class InvioStore(
    private val prefs: SharedPreferences,
) {
    private val flow = MutableStateFlow(carica())

    val states: StateFlow<Map<String, InvioInfo>> = flow

    fun get(matchUuid: String): InvioInfo? = flow.value[matchUuid]

    fun set(
        matchUuid: String,
        info: InvioInfo,
    ) {
        prefs.edit { putString(matchUuid, encode(info)) }
        flow.value = flow.value + (matchUuid to info)
    }

    private fun carica(): Map<String, InvioInfo> =
        prefs.all
            .mapNotNull { (uuid, raw) -> decode(raw as? String)?.let { uuid to it } }
            .toMap()

    /**
     * `STATO|MOTIVO|DETTAGLIO`, e se c'e' la firma dei collegamenti `<EXT>GRUPPO<EXT>FIRMA` in coda: un
     * valore scritto prima di questo campo si legge com'era (nessuna firma), e il dettaglio, che
     * puo' contenere `|`, resta l'ultimo dei tre.
     */
    private fun encode(info: InvioInfo): String {
        val base = listOf(info.state.name, info.reason?.name.orEmpty(), info.detail.orEmpty().replace(EXT, ' ')).joinToString(SEP)
        return if (info.group != null && info.links != null) "$base$EXT${info.group}$EXT${info.links}" else base
    }

    private fun decode(raw: String?): InvioInfo? {
        val teste = raw?.split(EXT, limit = 3) ?: return null
        val pezzi = teste[0].split(SEP, limit = 3)
        val stato = InvioState.entries.firstOrNull { it.name == pezzi[0] } ?: return null
        val motivo = InvioReason.entries.firstOrNull { it.name == pezzi.getOrNull(1) }
        val gruppo = teste.getOrNull(1)?.takeIf { teste.size == 3 && it.isNotEmpty() }
        val firma = teste.getOrNull(2).takeIf { gruppo != null }
        return InvioInfo(stato, motivo, pezzi.getOrNull(2)?.takeIf { it.isNotEmpty() }, gruppo, firma)
    }

    companion object {
        const val FILE = "padel_elite_invii"
        private const val SEP = "|"

        /** Separatore di unita' (U+001F): non sta in un gruppo, in una firma ne' in un dettaglio del server. */
        private const val EXT = '\u001F'
    }
}
