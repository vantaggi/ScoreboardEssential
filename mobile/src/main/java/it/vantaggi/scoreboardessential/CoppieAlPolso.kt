package it.vantaggi.scoreboardessential

import it.vantaggi.scoreboardessential.core.Composizione
import it.vantaggi.scoreboardessential.core.EtichettaSquadra
import it.vantaggi.scoreboardessential.core.Serata
import it.vantaggi.scoreboardessential.shared.communication.WearConstants

/**
 * Che cosa l'orologio sa delle coppie: chi gioca adesso e chi giocherebbe la prossima partita della
 * serata, gia' scritto ([WearConstants.PATH_PAIRS]).
 *
 * Funzione pura: i nomi arrivano da chi chiama (la rosa in memoria del ViewModel), cosi' il contenuto
 * si prova su JVM e la pubblicazione non aspetta il database. L'orologio non conosce la regola della
 * rotazione: legge nomi, e chiede al telefono di ruotare, scambiare o cominciare.
 */
object CoppieAlPolso {
    /** La bozza come la scrive SerataCodec: e' la base su cui il polso chiede di cambiarla. */
    fun base(c: Composizione): String = c.squadra1.joinToString(",") + "/" + c.squadra2.joinToString(",")

    /**
     * Le chiavi del DataItem, senza la versione (la assegna chi pubblica, nell'ordine di cattura).
     *
     * [serata] e' null fuori da uno sport a coppie: la prossima partita allora non si propone ([KEY_NEXT_NUMBER] 0).
     * Con una partita cominciata la proposta resta (il polso la mostra dopo), ma il telefono rifiuta di cambiarla.
     */
    fun dati(
        rosa1: List<String>,
        rosa2: List<String>,
        serata: Serata?,
        nomeDi: (Int) -> String?,
    ): Map<String, Any> {
        val bozza = serata?.bozza
        val nomi = bozza?.posti?.map { nomeDi(it).orEmpty() }.orEmpty()
        // Senza tutti e quattro i nomi (la rosa non e' ancora caricata) il polso non saprebbe chi e' chi:
        // meglio nessuna proposta che un posto senza nome da scambiare.
        val proposta = serata?.takeIf { bozza != null && nomi.size == 4 && nomi.none { it.isBlank() } }
        val coppie =
            if (proposta == null) {
                ""
            } else {
                val uno = nomi.subList(0, 2)
                val due = nomi.subList(2, 4)
                listOf(EtichettaSquadra.di("", uno, due), EtichettaSquadra.di("", due, uno))
                    .joinToString(WearConstants.SPORT_SEPARATOR) { pulito(it) }
            }
        return mapOf(
            WearConstants.KEY_TEAM1_ROSTER to elenco(rosa1),
            WearConstants.KEY_TEAM2_ROSTER to elenco(rosa2),
            WearConstants.KEY_NEXT_NUMBER to (proposta?.numeroDellaProssima ?: 0),
            WearConstants.KEY_NEXT_PLAYERS to if (proposta == null) "" else elenco(nomi),
            WearConstants.KEY_NEXT_BASE to (bozza?.takeIf { proposta != null }?.let(::base) ?: ""),
            WearConstants.KEY_NEXT_LABELS to coppie,
            WearConstants.KEY_NEXT_BENCH to (proposta?.let { p -> elenco(p.panchina.mapNotNull(nomeDi)) } ?: ""),
            WearConstants.KEY_NEXT_TIEBREAK to (proposta?.spareggio != null),
            WearConstants.KEY_NEXT_DELIVERED to (proposta != null && proposta.inGioco == bozza),
        )
    }

    /** I nomi in fila col separatore, che dentro un nome diventa uno spazio: un nome non diventa mai due. */
    private fun elenco(nomi: List<String>): String = nomi.joinToString(WearConstants.SPORT_SEPARATOR) { pulito(it) }

    private fun pulito(nome: String): String = nome.replace(WearConstants.SPORT_SEPARATOR, " ").trim()
}
