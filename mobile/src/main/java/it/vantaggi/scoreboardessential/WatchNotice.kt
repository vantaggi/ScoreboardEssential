package it.vantaggi.scoreboardessential

/**
 * L'ultima notizia sull'arretrato dell'orologio. Sta nel MainViewModel come stato e non come evento:
 * sopravvive alla ricreazione dell'Activity e arriva anche a chi guardava il campo quando un
 * messaggio di 3 secondi e' passato. Torna null solo con una partita nuova.
 */
sealed class WatchNotice {
    /** L'arretrato e' entrato: [count] sono i PUNTI, annullamenti e correzioni non contano. */
    data class Applied(
        val count: Int,
    ) : WatchNotice()

    /** Consegna rifiutata: c'e' gia' una partita in corso sul telefono. L'arretrato resta al polso. */
    data object Rejected : WatchNotice()
}
