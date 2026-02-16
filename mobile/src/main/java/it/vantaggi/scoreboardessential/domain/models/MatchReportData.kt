package it.vantaggi.scoreboardessential.domain.models

import it.vantaggi.scoreboardessential.database.PlayerWithRoles

data class MatchReportData(
    val team1Name: String,
    val team1Score: Int,
    val team1Color: Int?,
    val team1Players: List<PlayerWithRoles>,
    val team2Name: String,
    val team2Score: Int,
    val team2Color: Int?,
    val team2Players: List<PlayerWithRoles>,
    val matchEvents: List<MatchEvent>
)
