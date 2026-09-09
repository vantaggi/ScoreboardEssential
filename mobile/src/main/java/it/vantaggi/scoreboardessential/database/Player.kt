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
     * Identificativo dello stesso giocatore nel gruppo di Padel Elite (`v2_players.id`).
     *
     * Null finche' nessuno l'ha collegato: i due progetti hanno spazi di identificatori
     * scollegati, e l'associazione la fa una persona una volta sola. Non si tenta il confronto
     * per NOME -- e' lo stesso errore gia' corretto per il marcatore che arrivava dall'orologio:
     * due omonimi sono indistinguibili e un nome si puo' cambiare.
     *
     * Nullable e senza valore di default, quindi lo schema atteso da Room combacia con
     * l'ALTER TABLE della migrazione senza bisogno di @ColumnInfo.
     */
    var padelPlayerId: Int? = null,
)
