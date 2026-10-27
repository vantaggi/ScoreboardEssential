package it.vantaggi.scoreboardessential.wear

import com.google.android.gms.wearable.DataMap
import it.vantaggi.scoreboardessential.shared.communication.WearConstants

/**
 * Chi gioca adesso e chi giocherebbe la prossima partita della serata, come li scrive il telefono
 * ([WearConstants.PATH_PAIRS]).
 *
 * Come [WearScoreState], qui non c'e' nessuna regola: la rotazione la decide il telefono, il polso legge
 * nomi e chiede di cambiarli. Un telefono che non manda il path lascia questo valore assente, e allora
 * il menu non mostra ne' le squadre ne' la prossima partita.
 */
data class CoppieDalTelefono(
    val rosa1: List<String> = emptyList(),
    val rosa2: List<String> = emptyList(),
    /** La prossima partita: numero (la prima e' 1), 0 se non ce n'e' una da proporre. */
    val numero: Int = 0,
    /** I quattro nell'ordine dei posti: 0 e 1 la prima coppia, 2 e 3 la seconda. */
    val giocatori: List<String> = emptyList(),
    /** La bozza su cui il polso chiede di cambiare: il telefono applica solo se e' ancora quella. */
    val base: String = "",
    /** Le sigle delle due coppie della prossima partita. */
    val sigle: List<String> = emptyList(),
    val panchina: List<String> = emptyList(),
    val spareggio: Boolean = false,
    /** La prossima partita e' gia' a tabellone, non ancora cominciata. */
    val consegnata: Boolean = false,
    val versione: Long = 0L,
) {
    /** C'e' una prossima partita leggibile: quattro nomi e la base per chiedere di cambiarla. */
    val haProssima: Boolean get() = numero > 0 && giocatori.size == POSTI && base.isNotEmpty()

    val haSquadre: Boolean get() = rosa1.isNotEmpty() || rosa2.isNotEmpty()

    companion object {
        const val POSTI = 4

        private fun elenco(grezzo: String): List<String> =
            if (grezzo.isEmpty()) emptyList() else grezzo.split(WearConstants.SPORT_SEPARATOR)

        /** Ogni chiave ha il suo default: un telefono piu' recente puo' aggiungerne, uno vecchio ometterne. */
        fun fromDataMap(dataMap: DataMap): CoppieDalTelefono =
            CoppieDalTelefono(
                rosa1 = elenco(dataMap.getString(WearConstants.KEY_TEAM1_ROSTER, "")),
                rosa2 = elenco(dataMap.getString(WearConstants.KEY_TEAM2_ROSTER, "")),
                numero = dataMap.getInt(WearConstants.KEY_NEXT_NUMBER, 0),
                giocatori = elenco(dataMap.getString(WearConstants.KEY_NEXT_PLAYERS, "")),
                base = dataMap.getString(WearConstants.KEY_NEXT_BASE, ""),
                sigle = elenco(dataMap.getString(WearConstants.KEY_NEXT_LABELS, "")),
                panchina = elenco(dataMap.getString(WearConstants.KEY_NEXT_BENCH, "")),
                spareggio = dataMap.getBoolean(WearConstants.KEY_NEXT_TIEBREAK, false),
                consegnata = dataMap.getBoolean(WearConstants.KEY_NEXT_DELIVERED, false),
                versione =
                    when (val v = dataMap.get<Any>(WearConstants.KEY_PAIRS_VERSION)) {
                        is Long -> v
                        is Int -> v.toLong()
                        else -> 0L
                    },
            )
    }
}

/**
 * La sequenza delle richieste del polso, UNA per il processo anche con due mittenti.
 *
 * Il telefono ricorda l'ultima sequenza vista per nodo e scarta cio' che non la supera. Il quadrante la
 * tiene nel suo ViewModel; la schermata della prossima partita chiede al telefono senza passare di li'.
 * Con due contatori indipendenti quello rimasto indietro vedrebbe i suoi messaggi scartati come "gia'
 * visti": qui ciascuno dei due scrive l'ultima che ha usato, e la prossima di ognuno supera entrambe.
 * Il quadrante legge solo quella della schermata: finche' nessuno la usa, il suo contatore va come prima.
 */
object SequenzaDelPolso {
    private var ultimaDelQuadrante = 0L
    private var ultimaDellaSchermata = 0L

    /** Il quadrante: la sua [attuale] piu' uno, o oltre l'ultima della schermata della prossima partita. */
    @Synchronized
    fun delQuadrante(attuale: Long): Long = (maxOf(attuale, ultimaDellaSchermata) + 1).also { ultimaDelQuadrante = it }

    /** La schermata della prossima partita: oltre il quadrante e oltre l'ora, come il seme del quadrante. */
    @Synchronized
    fun dellaSchermata(): Long =
        (maxOf(ultimaDelQuadrante, ultimaDellaSchermata, System.currentTimeMillis()) + 1).also { ultimaDellaSchermata = it }
}
