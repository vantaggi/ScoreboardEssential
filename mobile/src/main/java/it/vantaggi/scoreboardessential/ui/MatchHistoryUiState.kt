package it.vantaggi.scoreboardessential.ui

import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.MatchWithTeams

data class MatchHistoryUiState(
    val matchWithTeams: MatchWithTeams,
    val formattedPlayers: String,
) {
    /**
     * Il comando "Esporta" dello storico: solo padel, solo a partita chiusa, solo con un
     * registro. Una partita salvata col solo punteggio finale non ha niente che il file possa
     * raccontare, e la riga viva si esporta dalla schermata di gioco.
     */
    val canExport: Boolean
        get() =
            matchWithTeams.match.let {
                it.sportId == SportRegistry.PADEL && !it.isActive && it.eventLog.isNotEmpty()
            }
}
