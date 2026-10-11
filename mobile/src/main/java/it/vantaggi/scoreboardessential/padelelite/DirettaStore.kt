package it.vantaggi.scoreboardessential.padelelite

import android.content.SharedPreferences
import androidx.core.content.edit

/** La diretta accesa, nel minimo che serve a riprenderla: quale partita, verso quale gruppo (D-2). */
data class DirettaSalvata(
    val matchId: String,
    val groupId: String,
    val groupName: String,
)

/**
 * Lo stato persistito della diretta: `matchId`, gruppo, e "accesa" (la presenza del valore). Il punteggio
 * vive nel registro della partita, la diretta si ricostruisce da quello. A parte, le chiusure segnate e non
 * ancora accodate ([pendenti], D-3).
 *
 * Una sola diretta alla volta, come una sola partita in corso: [clear] la spegne.
 */
class DirettaStore(
    private val prefs: SharedPreferences,
) {
    fun load(): DirettaSalvata? {
        val matchId = prefs.getString(KEY_MATCH, null)?.takeIf { it.isNotEmpty() } ?: return null
        val groupId = prefs.getString(KEY_GROUP, null)?.takeIf { it.isNotEmpty() } ?: return null
        return DirettaSalvata(matchId, groupId, prefs.getString(KEY_GROUP_NAME, "").orEmpty())
    }

    fun save(diretta: DirettaSalvata) {
        prefs.edit {
            putString(KEY_MATCH, diretta.matchId)
            putString(KEY_GROUP, diretta.groupId)
            putString(KEY_GROUP_NAME, diretta.groupName)
        }
    }

    fun clear() {
        prefs.edit {
            remove(KEY_MATCH)
            remove(KEY_GROUP)
            remove(KEY_GROUP_NAME)
        }
    }

    /**
     * Le chiusure segnate e non ancora accodate (D-3): una diretta chiusa il cui lavoro di chiusura non e' stato
     * messo in coda, per esempio perche' il processo e' morto con una richiesta in volo. Al riavvio si accodano.
     */
    fun pendenti(): List<DirettaChiusuraRichiesta> =
        prefs
            .getStringSet(KEY_PENDING, emptySet())
            .orEmpty()
            .mapNotNull { voce ->
                val parti = voce.split(SEP)
                if (parti.size == 3 && parti[0].isNotEmpty() && parti[1].isNotEmpty()) {
                    DirettaChiusuraRichiesta(parti[0], parti[1], conRisultato = parti[2] == "1")
                } else {
                    null
                }
            }

    /** Segna la chiusura PRIMA di consegnarla. Scrive subito su disco: e' l'ultima cosa che un processo ucciso lascia. */
    fun segnaPendente(richiesta: DirettaChiusuraRichiesta) {
        val altre = pendenti().filter { it.matchId != richiesta.matchId } + richiesta
        prefs.edit(commit = true) { putStringSet(KEY_PENDING, altre.map(::codifica).toSet()) }
    }

    /** Toglie il segno dopo che il lavoro di chiusura e' stato accodato. */
    fun togliPendente(matchId: String) {
        val restano = pendenti().filter { it.matchId != matchId }
        prefs.edit(commit = true) {
            if (restano.isEmpty()) remove(KEY_PENDING) else putStringSet(KEY_PENDING, restano.map(::codifica).toSet())
        }
    }

    private fun codifica(r: DirettaChiusuraRichiesta) = listOf(r.matchId, r.groupId, if (r.conRisultato) "1" else "0").joinToString(SEP)

    companion object {
        const val FILE = "padel_elite_diretta"
        private const val KEY_PENDING = "chiusure_pendenti"
        private const val SEP = "|"
        private const val KEY_MATCH = "match_id"
        private const val KEY_GROUP = "group_id"
        private const val KEY_GROUP_NAME = "group_name"
    }
}
