package it.vantaggi.scoreboardessential.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Il collegamento fra un giocatore dell'app e un giocatore di un gruppo di Padel Elite (R-1).
 *
 * E' una tabella a parte e non la colonna `players.padelPlayerId`, che e' dismessa: li' stanno
 * vecchi valori senza gruppo, e un id della dashboard vale solo dentro il suo gruppo. Qui la
 * coppia (giocatore locale, gruppo) e' unica, e lo e' anche (gruppo, giocatore della dashboard):
 * un giocatore locale ha al massimo un collegamento per gruppo, e un giocatore della dashboard
 * non e' collegato a due giocatori locali. [remoteName] e' il nome che la dashboard aveva
 * l'ultima volta che la rosa e' stata letta: serve a mostrare il collegamento, non ad altro.
 *
 * Cancellare il giocatore locale cancella i suoi collegamenti (CASCADE).
 */
@Entity(
    tableName = "padel_elite_links",
    primaryKeys = ["localPlayerId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = Player::class,
            parentColumns = ["playerId"],
            childColumns = ["localPlayerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["groupId", "remotePlayerId"], unique = true)],
)
data class PadelEliteLink(
    val localPlayerId: Int,
    /** L'UUID del gruppo di Padel Elite, come testo. */
    val groupId: String,
    /** `v2_players.id` nella dashboard. */
    val remotePlayerId: Int,
    val remoteName: String,
)
