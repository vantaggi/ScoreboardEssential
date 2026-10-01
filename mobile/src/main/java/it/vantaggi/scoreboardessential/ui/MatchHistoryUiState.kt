package it.vantaggi.scoreboardessential.ui

import it.vantaggi.scoreboardessential.core.RacketRules
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.MatchWithTeams

data class MatchHistoryUiState(
    val matchWithTeams: MatchWithTeams,
    /** I nomi dei giocatori separati da virgola, senza l'etichetta: la parola sta nelle risorse. */
    val playerNames: String,
    /**
     * I set della partita a racchetta al meglio di piu' set («6-4 · 3-6 · 7-6 (7-4)»), calcolati
     * dal registro una volta sola fuori dal thread principale. Null dove non c'e' niente da dire.
     */
    val setLine: String? = null,
) {
    /**
     * Il vincitore dal punteggio di testata: 1, 2 o null per un pareggio. E' lo stesso confronto
     * con cui [MatchDao.getPlayerWinCounts] conta le vittorie, quindi vale per ogni sport.
     */
    val winnerSide: Int?
        get() =
            matchWithTeams.match.let {
                when {
                    it.team1Score > it.team2Score -> 1
                    it.team2Score > it.team1Score -> 2
                    else -> null
                }
            }

    /**
     * La durata in millisecondi, dal primo punto alla chiusura. Null quando la partita e' stata
     * salvata prima che si tenesse l'inizio, o quando i due istanti non tornano.
     */
    val durationMillis: Long?
        get() =
            matchWithTeams.match.let { match ->
                match.startedAt?.let { match.timestamp - it }?.takeIf { it > 0 }
            }

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

    /**
     * Il comando "Cronaca": ogni sport con racchetta (la Cronaca e' fatta di game e set, e il
     * tennis la ha gratis dallo stesso calcolo), solo a partita chiusa e solo con un registro,
     * perche' la cronaca si rigioca dai punti. Uno sport sconosciuto ripiega sul calcio e resta
     * fuori.
     */
    val canOpenChronicle: Boolean
        get() =
            matchWithTeams.match.let {
                SportRegistry.byId(it.sportId) is RacketRules && !it.isActive && it.eventLog.isNotEmpty()
            }
}
