package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.database.PlayerWithRoles

data class TeamRoster(
    val team1Players: List<PlayerWithRoles> = emptyList(),
    val team2Players: List<PlayerWithRoles> = emptyList(),
)
