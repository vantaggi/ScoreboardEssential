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
        const val CHIAVE_IN_COPPIA = "in_coppia"
        const val CHIAVE_RICEVUTO_ALLE = "received_at"
        const val CHIAVE_UUID = "match_uuid"
        const val CHIAVE_VERSIONE_STATO = "state_version"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val sportId: String get() = prefs.getString(CHIAVE_SPORT, "").orEmpty()

    val eventLog: String get() = prefs.getString(CHIAVE_LOG, "").orEmpty()

    /**
     * L'identita' della partita a cui appartiene [eventLog] (il `matchUuid` del telefono), vuota se il
     * telefono non l'aveva ancora o se i dati sono di prima di questa chiave. Senza, un orologio
     * riavviato rifaceva la coda sul registro giusto ma con l'uuid vuoto, e il telefono non poteva
     * distinguere la sua partita da un'altra che comincia allo stesso modo (L5).
     */
    val matchUuid: String get() = prefs.getString(CHIAVE_UUID, "").orEmpty()

    /**
     * La partita si gioca in coppia: il telefono manda il giocatore al servizio (1 o 2) solo
     * allora, e l'orologio non ha le rose per saperlo da solo. Campo assente = non in coppia,
     * quindi i dati scritti prima di questa chiave restano validi.
     */
    val inCoppia: Boolean get() = prefs.getBoolean(CHIAVE_IN_COPPIA, false)

    /**
     * Quando il telefono ha parlato l'ultima volta, in millisecondi; zero se non si sa.
     *
     * Serve alla riga di stato ("SCOLLEGATO · 18:42") dopo un riavvio dell'orologio: senza questo
     * l'ora ripartirebbe da niente proprio quando il telefono e' lontano.
     */
    val ricevutoAlle: Long get() = prefs.getLong(CHIAVE_RICEVUTO_ALLE, 0L)

    fun save(
        sportId: String,
        eventLog: String,
        servingSlot: Int = 0,
        matchUuid: String = "",
    ) {
        // Uno sport vuoto arriva da un telefono che parla una bozza precedente del v2: non si
        // sovrascrive quello che si sa gia' con un vuoto, perche' quel vuoto non e' informazione.
        if (sportId.isBlank()) return
        // Il flag si azzera quando la partita cambia (sport diverso o registro che riparte da
        // vuoto) e si accende appena il telefono manda un giocatore al servizio: un singolare
        // che segue un doppio non deve ereditarne le coppie.
        val stessaPartita = sportId == this.sportId && eventLog.isNotEmpty()
        val inCoppia = servingSlot != 0 || (stessaPartita && this.inCoppia)
        prefs.edit {
            putString(CHIAVE_SPORT, sportId)
            putString(CHIAVE_LOG, eventLog)
            putBoolean(CHIAVE_IN_COPPIA, inCoppia)
            putString(CHIAVE_UUID, matchUuid)
        }
    }

    /**
     * La versione piu' alta dello stato v2 che il telefono ha mandato e che l'orologio ha accettato
     * (0 se non ne ha mai vista una). Sta su disco: il servizio riceve gli stati anche ad app chiusa, e
     * dopo un riavvio dell'orologio uno stato vecchio non deve poter prendere il posto di uno recente.
     */
    val versioneStato: Long get() = prefs.getLong(CHIAVE_VERSIONE_STATO, 0L)

    /**
     * Vero se lo stato con questa versione va applicato, e in quel caso la ricorda; falso se e' piu'
     * vecchio dell'ultimo visto. Il Data Layer non garantisce l'ordine: due stati ravvicinati possono
     * arrivare invertiti, e quello superato che arriva per ultimo riporterebbe il polso indietro (L5).
     *
     * Una versione uguale passa: lo stesso stato arriva dal servizio e poi dal ViewModel, e si
     * rilegge anche al risveglio. Zero e' lo stato di un telefono che non la manda: si applica come
     * prima e non tocca quella ricordata.
     */
    fun accettaVersione(versione: Long): Boolean {
        if (versione <= 0L) return true
        if (versione < versioneStato) return false
        prefs.edit { putLong(CHIAVE_VERSIONE_STATO, versione) }
        return true
    }

    /**
     * Segna l'istante in cui il telefono ha parlato DAL VIVO. Lo chiama [WearDataLayerService],
     * non il ViewModel: il servizio riceve i v2 anche ad app chiusa, e l'ora dell'ultimo stato
     * vivo deve seguirli. Uno stato riletto dai DataItem al risveglio e' una copia vecchia e non
     * passa di qui: l'ora di prima resta quella che era.
     */
    fun segnaStatoVivo(ricevutoAlle: Long) {
        prefs.edit { putLong(CHIAVE_RICEVUTO_ALLE, ricevutoAlle) }
    }
}
