package it.vantaggi.scoreboardessential.domain.model

data class PlayerStatsDTO(
    val playerId: Int,
    val playerName: String,
    val goals: Int,
    val appearances: Int,
    val winRate: Float,
    val roles: List<it.vantaggi.scoreboardessential.database.Role> = emptyList(),
)
