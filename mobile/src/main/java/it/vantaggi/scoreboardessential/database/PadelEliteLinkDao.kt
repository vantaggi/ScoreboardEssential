package it.vantaggi.scoreboardessential.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PadelEliteLinkDao {
    @Query("SELECT * FROM padel_elite_links WHERE groupId = :groupId")
    suspend fun linksOf(groupId: String): List<PadelEliteLink>

    /** Tutti i collegamenti, in ogni gruppo, e la lista si rinnova a ogni cambiamento (lo storico suggerisce "Invia di nuovo"). */
    @Query("SELECT * FROM padel_elite_links")
    fun observeAll(): Flow<List<PadelEliteLink>>

    /** Tutti i collegamenti di un giocatore locale, in ogni gruppo: servono a rimetterli se si annulla la cancellazione. */
    @Query("SELECT * FROM padel_elite_links WHERE localPlayerId = :localPlayerId")
    suspend fun linksOfPlayer(localPlayerId: Int): List<PadelEliteLink>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(link: PadelEliteLink)

    /** Rimette i collegamenti di un giocatore ripristinato; quelli che occuperebbero un'unicita' gia' presa si saltano. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restore(links: List<PadelEliteLink>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlayer(player: Player): Long

    @Query("DELETE FROM padel_elite_links WHERE localPlayerId = :localPlayerId AND groupId = :groupId")
    suspend fun unlinkLocal(
        localPlayerId: Int,
        groupId: String,
    )

    @Query("DELETE FROM padel_elite_links WHERE groupId = :groupId AND remotePlayerId = :remotePlayerId")
    suspend fun unlinkRemote(
        groupId: String,
        remotePlayerId: Int,
    )

    @Query("UPDATE padel_elite_links SET remoteName = :name WHERE groupId = :groupId AND remotePlayerId = :remotePlayerId")
    suspend fun renameRemote(
        groupId: String,
        remotePlayerId: Int,
        name: String,
    )

    /**
     * Collega, tutto o niente: toglie prima cio' che occuperebbe una delle due unicita' (l'altro
     * collegamento di questo giocatore locale nel gruppo, e l'altro giocatore locale di questo
     * giocatore della dashboard) e poi scrive.
     */
    @Transaction
    suspend fun link(link: PadelEliteLink) {
        unlinkLocal(link.localPlayerId, link.groupId)
        unlinkRemote(link.groupId, link.remotePlayerId)
        insert(link)
    }

    /** Crea il giocatore locale col nome della dashboard e lo collega, in un colpo solo. Restituisce l'id locale. */
    @Transaction
    suspend fun createAndLink(
        groupId: String,
        remotePlayerId: Int,
        name: String,
    ): Int {
        val localId = insertPlayer(Player(playerName = name, appearances = 0, goals = 0)).toInt()
        link(PadelEliteLink(localId, groupId, remotePlayerId, name))
        return localId
    }
}
