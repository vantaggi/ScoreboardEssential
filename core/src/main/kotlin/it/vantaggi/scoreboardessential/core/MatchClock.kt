package it.vantaggi.scoreboardessential.core

/**
 * Traduce l'orologio di sistema nel tempo della partita.
 *
 * [LoggedEvent.atMillis] e' **relativo all'inizio della partita**, non un epoch: [MatchSummarizer]
 * legge l'ultimo valore come durata e calcola quella dei set per differenza, e
 * [MatchExporter] lo scrive tale e quale nella cronologia punto per punto. Un epoch infilato li'
 * dentro non e' "un valore un po' diverso": fa dire al riassunto che la partita e' durata
 * cinquantacinque anni, e manda a Padel Elite tempi che non somigliano a niente.
 *
 * Chi produce gli eventi conosce solo l'epoch -- l'orologio al polso, per esempio, non sa quando
 * e' cominciata la partita -- quindi la conversione deve stare in un posto solo, ed e' questo.
 *
 * Non e' thread-safe: vive dentro un ViewModel, sul thread principale, accanto al motore.
 */
class MatchClock {
    private var startEpoch: Long? = null

    /** Vero quando la partita ha gia' un inizio, cioe' e' gia' arrivato almeno un evento. */
    val started: Boolean get() = startEpoch != null

    /**
     * Il tempo di partita di un evento avvenuto a [atEpoch].
     *
     * Il PRIMO evento definisce l'inizio e vale zero. Un evento con un epoch precedente all'inizio
     * -- possibile se l'orologio del polso e quello del telefono non sono allineati -- viene
     * schiacciato a zero invece di produrre un tempo negativo, che a valle diventerebbe una durata
     * negativa e un set di durata negativa.
     */
    fun relative(atEpoch: Long): Long {
        val inizio = startEpoch ?: atEpoch.also { startEpoch = it }
        return (atEpoch - inizio).coerceAtLeast(0L)
    }

    /**
     * Riaggancia l'orologio a una partita ripresa dalla persistenza.
     *
     * [lastRelative] e' il tempo dell'ultimo evento gia' registrato. L'inizio viene spostato in
     * modo che "adesso" coincida con quel tempo: il periodo in cui l'app e' rimasta chiusa NON
     * finisce nella durata della partita, che e' l'unica lettura sensata -- nessuno ha giocato
     * mentre l'app era spenta.
     */
    fun resume(
        lastRelative: Long,
        nowEpoch: Long,
    ) {
        startEpoch = nowEpoch - lastRelative
    }

    /** Partita nuova: il prossimo evento ridefinisce l'inizio. */
    fun reset() {
        startEpoch = null
    }
}
