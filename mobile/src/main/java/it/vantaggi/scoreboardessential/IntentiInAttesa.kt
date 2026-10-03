package it.vantaggi.scoreboardessential

import android.content.Context
import androidx.core.content.edit

/**
 * I tocchi dell'orologio arrivati quando il ViewModel del telefono non c'era (L5).
 *
 * Il servizio riceve i messaggi anche ad app chiusa, ma non conosce le regole: puo' solo inoltrare
 * a un ViewModel, e senza ricevitori il broadcast andava nel vuoto. L'orologio non vibra la
 * conferma finche' non torna uno stato col registro cresciuto, e dopo la scadenza dice NON
 * CONFERMATO senza rimettere il tocco in coda (potrebbe essere gia' contato): il punto restava
 * perso. Qui il servizio lo mette da parte, e il ViewModel lo applica quando nasce, nell'ordine di
 * arrivo. Un solo percorso, quindi nessun doppio conteggio: il tocco non torna dall'orologio.
 *
 * Solo punti, annullamenti e correzioni. Una chiusura o un cambio sport arrivati ore dopo
 * agirebbero su un'altra partita. Le voci piu' vecchie di [VALIDITA_MS] si buttano: una partita non
 * dura tanto, e un tocco di ieri non deve cadere nella partita di oggi.
 *
 * Formato: `kind,side,atMillis` separati da `;`, come l'arretrato.
 */
class IntentiInAttesa(
    context: Context,
) {
    data class Voce(
        val kind: String,
        val side: Int,
        val atMillis: Long,
    )

    companion object {
        private const val PREFS = "intenti_dal_polso_in_attesa"
        private const val CHIAVE = "voci"
        private const val SEP_VOCE = ";"
        private const val SEP_CAMPO = ","

        /** Oltre, si smette di accumulare: l'app non e' stata aperta per giorni. */
        const val MASSIMO = 200

        /** Dodici ore: oltre, il tocco non appartiene piu' alla partita che sta per ripartire. */
        const val VALIDITA_MS = 12L * 60L * 60L * 1000L
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun aggiungi(
        kind: String,
        side: Int,
        atMillis: Long,
    ) {
        val voci = leggi()
        if (voci.size >= MASSIMO) return
        scrivi(voci + Voce(kind, side, atMillis))
    }

    /** Toglie e restituisce le voci ancora valide, nell'ordine di arrivo. Una chiamata sola le consuma. */
    @Synchronized
    fun prendiTutte(adesso: Long = System.currentTimeMillis()): List<Voce> {
        val voci = leggi()
        if (voci.isEmpty()) return emptyList()
        prefs.edit { remove(CHIAVE) }
        return voci.filter { adesso - it.atMillis <= VALIDITA_MS }
    }

    private fun leggi(): List<Voce> =
        prefs
            .getString(CHIAVE, "")
            .orEmpty()
            .split(SEP_VOCE)
            .filter { it.isNotBlank() }
            .mapNotNull { voce ->
                val campi = voce.split(SEP_CAMPO)
                val side = campi.getOrNull(1)?.toIntOrNull()
                val at = campi.getOrNull(2)?.toLongOrNull()
                if (campi.size < 3 || campi[0].isBlank() || side == null || at == null) null else Voce(campi[0], side, at)
            }

    private fun scrivi(voci: List<Voce>) {
        prefs.edit {
            putString(CHIAVE, voci.joinToString(SEP_VOCE) { "${it.kind}$SEP_CAMPO${it.side}$SEP_CAMPO${it.atMillis}" })
        }
    }
}
