package it.vantaggi.scoreboardessential.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "matches",
    indices = [Index(value = ["sportId", "timestamp"])],
)
data class Match(
    @PrimaryKey(autoGenerate = true)
    val matchId: Int = 0,
    val team1Id: Int,
    val team2Id: Int,
    /**
     * Punteggio "di testata": per il calcio sono i gol, per gli sport con racchetta sono i set
     * vinti (o i game, se la partita e' a set unico). Definirlo cosi' e' il motivo per cui
     * [MatchDao.getPlayerWinCounts], che decide il vincitore confrontando questi due interi,
     * resta corretto anche per padel e tennis senza una riga di modifica.
     */
    val team1Score: Int,
    val team2Score: Int,
    val timestamp: Long,
    val isActive: Boolean = false,
    /**
     * Sport della partita. TEXT non interpretato dal database: la verita' sulle regole vive nel
     * registro in :core, non qui.
     *
     * Il default e' dichiarato con @ColumnInfo e non solo come default di Kotlin: quelli di
     * Kotlin NON diventano default SQL, quindi lo schema atteso da Room non ne avrebbe avuto uno
     * mentre la migrazione crea la colonna con DEFAULT -- e il confronto degli schemi sarebbe
     * fallito all'avvio, per sempre.
     */
    @ColumnInfo(defaultValue = "football")
    val sportId: String = "football",
    /**
     * Cronologia degli eventi serializzata, o stringa vuota per "solo punteggio finale".
     *
     * Non e' una tabella perche' oggi nessuna schermata legge dati per-evento: questa colonna da'
     * le due cose che servono davvero -- riprendere una partita dopo la morte del processo e
     * annullare rifacendo il fold -- con una INSERT-per-punto in meno e nessun DAO nuovo.
     * Promuoverla a tabella, quando una query la chiedera', sara' una derivazione.
     */
    @ColumnInfo(defaultValue = "''")
    val eventLog: String = "",
    /**
     * L'ordine di servizio con cui si e' giocato: id dei giocatori separati da virgola, nell'ordine
     * A1, B1, A2, B2, o stringa vuota se non era impostato (e per ogni partita salvata prima della
     * versione 14 dello schema).
     *
     * Serve a rigiocare il registro dallo storico: il servitore di ogni punto non e' nel
     * registro, si ricava dalle regole con questo ordine. Si scrive quando nasce la riga viva,
     * cioe' al primo punto, dopo il quale l'ordine non puo' piu' cambiare.
     */
    @ColumnInfo(defaultValue = "''")
    val serveOrder: String = "",
    /**
     * Epoch del primo punto. [timestamp] non basta: alla chiusura [MatchDao.finalizeMatch] lo
     * sovrascrive con l'ora di fine. Null per le partite salvate prima della versione 14.
     */
    val startedAt: Long? = null,
    /**
     * L'identificativo della partita nel file esportato (`matchId` del formato 2): un UUID
     * generato al primo punto, cosi' che due export della stessa partita, dal vivo o dallo
     * storico, portino lo stesso id. Non si chiama `matchId` perche' quel nome e' gia' la
     * chiave primaria. Null per le partite salvate prima della versione 14.
     */
    val matchUuid: String? = null,
) {
    companion object {
        /** Il formato di [serveOrder]: una coppia di funzioni sola, cosi' scrittura e lettura non divergono. */
        fun encodeServeOrder(order: List<Int>): String = order.joinToString(",")

        /** Un valore illeggibile vale come ordine assente: un dato in meno, non una partita persa. */
        fun decodeServeOrder(raw: String): List<Int> {
            if (raw.isBlank()) return emptyList()
            val ids = raw.split(',').map { it.trim().toIntOrNull() }
            return if (ids.any { it == null }) emptyList() else ids.filterNotNull()
        }
    }
}
