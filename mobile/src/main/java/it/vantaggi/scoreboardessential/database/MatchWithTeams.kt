package it.vantaggi.scoreboardessential.database

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class MatchWithTeams(
    @Embedded val match: Match,
    @Relation(
        parentColumn = "team1Id",
        entityColumn = "id"
    )
    val team1: Team?,
    @Relation(
        parentColumn = "team2Id",
        entityColumn = "id"
    )
    val team2: Team?,
    @Relation(
        parentColumn = "matchId",
        entityColumn = "playerId",
        associateBy = Junction(MatchPlayerCrossRef::class)
    )
    val players: List<Player>
)
