package it.vantaggi.scoreboardessential.padelelite

import it.vantaggi.scoreboardessential.database.PadelEliteLink

/**
 * Quali giocatori della dashboard sono collegati ai giocatori di una partita, in un gruppo, in una
 * riga di testo ("12:5,14:9": giocatore locale, giocatore della dashboard). Si scrive nello stato
 * di invio quando il file parte, e si ricalcola sui collegamenti di adesso: se le due righe
 * differiscono, la voce nella casella ha i collegamenti di prima e "Invia di nuovo" la aggiorna.
 * Vuota = nessuno dei giocatori della partita e' collegato in quel gruppo.
 */
object FirmaCollegamenti {
    fun of(
        links: Collection<PadelEliteLink>,
        groupId: String,
        playerIds: Collection<Int>,
    ): String =
        links
            .filter { it.groupId == groupId && it.localPlayerId in playerIds }
            .sortedBy { it.localPlayerId }
            .joinToString(",") { "${it.localPlayerId}:${it.remotePlayerId}" }
}
