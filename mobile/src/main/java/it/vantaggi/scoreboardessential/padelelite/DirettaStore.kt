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
 * Lo stato persistito della diretta: `matchId`, gruppo, e "accesa" (la presenza del valore). Non c'e'
 * altro: il punteggio vive nel registro della partita, la diretta si ricostruisce da quello.
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

    companion object {
        const val FILE = "padel_elite_diretta"
        private const val KEY_MATCH = "match_id"
        private const val KEY_GROUP = "group_id"
        private const val KEY_GROUP_NAME = "group_name"
    }
}
