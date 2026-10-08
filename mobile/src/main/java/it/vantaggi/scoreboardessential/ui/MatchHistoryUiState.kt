package it.vantaggi.scoreboardessential.ui

import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.RacketRules
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.padelelite.InvioInfo

data class MatchHistoryUiState(
    val matchWithTeams: MatchWithTeams,
    /** I nomi dei giocatori separati da virgola, senza l'etichetta: la parola sta nelle risorse. */
    val playerNames: String,
    /**
     * I set della partita a racchetta al meglio di piu' set («6-4 · 3-6 · 7-6 (7-4)»), calcolati
     * dal registro una volta sola fuori dal thread principale. Null dove non c'e' niente da dire.
     */
    val setLine: String? = null,
    /** Lo stato dell'invio a Padel Elite di questa partita, o null se non e' mai partita. */
    val invio: InvioInfo? = null,
    /** La funzione e' configurata: senza, nessun comando e nessuno stato. */
    val padelEliteEnabled: Boolean = false,
    /** C'e' l'accesso a Padel Elite e un gruppo scelto: solo allora "Invia di nuovo" ha senso. */
    val padelEliteAccess: Boolean = false,
    /** Un giocatore della partita ha un collegamento diverso da quello del file in casella (vedi [InvioInfo.linksChanged]). */
    val linksChanged: Boolean = false,
) {
    /**
     * Il suggerimento discreto: la voce e' in attesa, i collegamenti sono cambiati dopo l'invio e
     * "Invia di nuovo" e' disponibile. L'app non rimanda da sola: lo fa chi tocca il comando.
     */
    val showLinksHint: Boolean get() = linksChanged && canSendAgain

    /**
     * Il comando "Invia a Padel Elite": solo con la funzione configurata, solo padel chiuso con un
     * registro e con l'identificativo del file (le partite di prima della versione 14 non lo hanno
     * e il server le rifiuta), e non mentre e' in coda o gia' arrivata.
     */
    val canSendToPadelElite: Boolean
        get() =
            padelEliteEnabled && canExport && matchWithTeams.match.matchUuid != null && (invio == null || invio.canSend)

    /**
     * "Invia di nuovo": per una partita gia' nella casella e ancora in attesa (inviata, aggiornata,
     * gia' presente) o scartata dall'admin (il server la rimette in attesa), con l'accesso e il gruppo
     * della voce noto. Importata non cambia piu'. Sta nello stesso posto del comando di invio e lo
     * sostituisce: un solo bottone per volta.
     */
    val canSendAgain: Boolean
        get() = padelEliteEnabled && padelEliteAccess && canExport && matchWithTeams.match.matchUuid != null && invio?.canResend == true

    /** Lo stato da mostrare sulla card: solo con la funzione accesa. */
    val shownInvio: InvioInfo? get() = invio.takeIf { padelEliteEnabled }

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
     * La durata in millisecondi: il tempo di gioco, cioe' l'ultimo tempo del registro (e' misurato
     * dal primo punto e non avanza mentre l'app e' chiusa, come nel riassunto). Solo per le righe
     * il cui registro non ha tempi si ripiega sulla distanza fra l'inizio e la chiusura, che per una
     * partita ripresa conterebbe le ore di pausa. Null quando non c'e' nessuna delle due, o non tornano.
     */
    val durationMillis: Long?
        get() =
            matchWithTeams.match.let { match ->
                val tempoDiGioco = MatchLogCodec.decode(match.eventLog)?.mapNotNull { it.atMillis }
                if (tempoDiGioco.isNullOrEmpty()) {
                    match.startedAt?.let { match.timestamp - it }?.takeIf { it > 0 }
                } else {
                    tempoDiGioco.last().takeIf { it > 0 }
                }
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
