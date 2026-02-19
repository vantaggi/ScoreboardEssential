package it.vantaggi.scoreboardessential.ui

import it.vantaggi.scoreboardessential.database.MatchWithTeams

data class MatchHistoryUiState(
    val matchWithTeams: MatchWithTeams,
    val formattedPlayers: String,
)
