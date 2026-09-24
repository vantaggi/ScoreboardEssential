package it.vantaggi.scoreboardessential.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true)
    val playerId: Int = 0,
    val playerName: String,
    var appearances: Int,
    var goals: Int,
    /**
     * Colonna DISMESSA dal 24 settembre 2026: nessuna schermata la mostra e nessun export la
     * legge. L'app e' a se', e chi importa il file sceglie i giocatori da solo.
     *
     * Resta nell'entita' solo perche' la colonna resta nel database (migrazione 12 -> 13):
     * toglierla da qui cambierebbe lo schema atteso da Room e chiederebbe una migrazione che
     * ricostruisce la tabella `players`, esattamente il tipo di migrazione che ha gia' fatto
     * perdere dati alla v7. I valori scritti prima restano dove sono, intoccati.
     */
    var padelPlayerId: Int? = null,
)
