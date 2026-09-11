package it.vantaggi.scoreboardessential.wear

import android.content.Context
import androidx.core.content.edit

/**
 * L'ultima partita che il telefono ha raccontato: quale sport, e il registro degli eventi.
 *
 * E' il punto di partenza da cui il polso rifa' il conto quando resta solo. Sta su disco e non in
 * memoria perche' il caso che conta e' proprio quello scomodo: l'orologio si riavvia a meta'
 * partita, col telefono in borsa, e senza questo il punteggio ripartirebbe da zero anche se i
 * tocchi in coda ci sono tutti.
 *
 * Non e' una seconda verita' sul punteggio: e' una COPIA di quella del telefono, che viene
 * riscritta a ogni aggiornamento e buttata via appena il telefono ne manda una piu' recente.
 */
class LastKnownMatch(
    context: Context,
) {
    private companion object {
        const val PREFS = "wear_last_known_match"
        const val CHIAVE_SPORT = "sport_id"
        const val CHIAVE_LOG = "event_log"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val sportId: String get() = prefs.getString(CHIAVE_SPORT, "").orEmpty()

    val eventLog: String get() = prefs.getString(CHIAVE_LOG, "").orEmpty()

    fun save(
        sportId: String,
        eventLog: String,
    ) {
        // Uno sport vuoto arriva da un telefono che parla una bozza precedente del v2: non si
        // sovrascrive quello che si sa gia' con un vuoto, perche' quel vuoto non e' informazione.
        if (sportId.isBlank()) return
        prefs.edit {
            putString(CHIAVE_SPORT, sportId)
            putString(CHIAVE_LOG, eventLog)
        }
    }
}
