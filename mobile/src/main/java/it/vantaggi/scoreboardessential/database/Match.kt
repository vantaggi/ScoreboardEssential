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
)
