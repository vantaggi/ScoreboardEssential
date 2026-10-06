package it.vantaggi.scoreboardessential.padelelite

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A che punto e' una partita nel suo viaggio verso Padel Elite. Sempre detto con una parola e un'icona. */
enum class InvioState {
    /** Nella coda: parte da sola appena c'e' rete. */
    QUEUED,

    /** Consegnata: aspetta che un admin del gruppo la apra e scelga i giocatori. */
    SENT,

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
) {
    /** Il comando "Invia a Padel Elite" compare solo dove ha senso: mai mentre e' in coda o gia' arrivata. */
    val canSend: Boolean
        get() = state == InvioState.UNSENDABLE || state == InvioState.LOGIN_AGAIN || state == InvioState.DISCARDED
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
        prefs.edit().putString(matchUuid, encode(info)).apply()
        flow.value = flow.value + (matchUuid to info)
    }

    private fun carica(): Map<String, InvioInfo> =
        prefs.all
            .mapNotNull { (uuid, raw) -> decode(raw as? String)?.let { uuid to it } }
            .toMap()

    private fun encode(info: InvioInfo) = listOf(info.state.name, info.reason?.name.orEmpty(), info.detail.orEmpty()).joinToString(SEP)

    private fun decode(raw: String?): InvioInfo? {
        val pezzi = raw?.split(SEP, limit = 3) ?: return null
        val stato = InvioState.entries.firstOrNull { it.name == pezzi[0] } ?: return null
        val motivo = InvioReason.entries.firstOrNull { it.name == pezzi.getOrNull(1) }
        return InvioInfo(stato, motivo, pezzi.getOrNull(2)?.takeIf { it.isNotEmpty() })
    }

    companion object {
        const val FILE = "padel_elite_invii"
        private const val SEP = "|"
    }
}
