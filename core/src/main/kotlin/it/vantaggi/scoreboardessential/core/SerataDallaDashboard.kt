package it.vantaggi.scoreboardessential.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * La serata della dashboard come la descrive `v2_settings.venue_config`: il giorno della settimana e la
 * prima prenotazione (ora d'inizio e durata). E' gia' tradotta in tipi: chi legge il JSON usa
 * [ProssimaSerata.ora] e [ProssimaSerata.durata] per avere le stesse regole della dashboard.
 *
 * @property giorno il giorno della settimana come `Date.getDay()` di JavaScript: 0 = domenica, 6 = sabato
 */
data class ConfigurazioneSerata(
    val giorno: Int,
    val inizio: LocalTime = ProssimaSerata.ORA_PREDEFINITA,
    val durataMinuti: Int = ProssimaSerata.DURATA_PREDEFINITA_MIN,
) {
    init {
        require(giorno in 0..6) { "Il giorno della settimana va da 0 (domenica) a 6 (sabato)" }
    }
}

/**
 * La data della prossima serata, con la regola della dashboard (`js/nextmatch.js`, `getNextSessionDate`):
 * il prossimo giorno della settimana configurato a partire da oggi; se e' oggi e la **prima** prenotazione
 * e' gia' finita (inizio + durata), quella della settimana dopo. Ora locale del telefono, come la
 * dashboard usa l'ora locale del browser.
 *
 * Funzione pura: l'adesso arriva da fuori, cosi' si prova con un orologio finto.
 */
object ProssimaSerata {
    /** La durata di una prenotazione che non la dice, o che non si legge: 90 minuti, come la dashboard. */
    const val DURATA_PREDEFINITA_MIN = 90

    /** L'ora d'inizio quando non ci sono prenotazioni (la dashboard usa "19:00"). */
    val ORA_PREDEFINITA: LocalTime = LocalTime.of(19, 0)

    private val ORA = Regex("""^\s*(\d{1,2}):(\d{2})""")
    private val NUMERO_IN_TESTA = Regex("""^\s*([+-]?\d+)""")

    fun data(
        adesso: LocalDateTime,
        config: ConfigurazioneSerata,
    ): LocalDate {
        val oggi = adesso.toLocalDate()
        // DayOfWeek va da 1 (lunedi') a 7 (domenica); JavaScript da 0 (domenica) a 6 (sabato).
        val oggiComeJs = adesso.dayOfWeek.value % 7
        var giorni = (config.giorno - oggiComeJs + 7) % 7
        if (giorni == 0) {
            val fine = oggi.atTime(config.inizio).plusMinutes(config.durataMinuti.toLong())
            // Come la dashboard: finita vuol dire strettamente dopo la fine.
            if (adesso.isAfter(fine)) giorni = 7
        }
        return oggi.plusDays(giorni.toLong())
    }

    /**
     * La durata in minuti di una prenotazione, come `parseDurationMin` della dashboard: un numero vale
     * com'e', un testo vale per le cifre in testa ("90m" e' 90), tutto il resto (mancante, testo senza
     * cifre in testa) e' [DURATA_PREDEFINITA_MIN].
     */
    fun durata(valore: Any?): Int =
        when (valore) {
            is Number -> valore.toInt()
            is String -> cifreInTesta(valore) ?: DURATA_PREDEFINITA_MIN
            else -> DURATA_PREDEFINITA_MIN
        }

    /** Il numero intero in testa al testo, come `parseInt` di JavaScript, o null se non comincia con delle cifre. */
    private fun cifreInTesta(testo: String): Int? {
        val trovato = NUMERO_IN_TESTA.find(testo) ?: return null
        return trovato.groupValues[1].toIntOrNull()
    }

    /**
     * L'ora d'inizio da "HH:MM". Un'ora mancante o che non si legge vale [ORA_PREDEFINITA]: la dashboard in
     * quel caso non calcola niente di sensato, e le 19 sono il suo valore di partenza.
     */
    fun ora(testo: String?): LocalTime {
        val trovata = testo?.let { ORA.find(it) } ?: return ORA_PREDEFINITA
        val ore = trovata.groupValues[1].toInt()
        val minuti = trovata.groupValues[2].toInt()
        return if (ore in 0..23 && minuti in 0..59) LocalTime.of(ore, minuti) else ORA_PREDEFINITA
    }
}

/**
 * I gironi salvati dalla dashboard per una serata (`v2_settings`, chiave `gironi_serata`, formato `v: 1`):
 * liste di id di `v2_players`, gironi da 4. Valgono solo per la serata che dicono ([serata], `AAAA-MM-GG`).
 */
data class GironiSalvati(
    val serata: String,
    val gironi: List<List<Int>>,
) {
    /** Vero se sono i gironi della serata del [giorno]: quelli di un'altra serata non si usano. */
    fun valgonoPer(giorno: LocalDate): Boolean = serata == giorno.toString()
}

/** Un presente della dashboard tradotto per la serata del telefono. */
sealed interface PresenteDellaDashboard {
    /** L'id in `v2_players`. */
    val idDashboard: Int

    /** Il nome che la serata mostra: quello del giocatore locale se collegato, se no quello della dashboard. */
    val nome: String

    /** Collegato (R-1) a un giocatore dell'app: entra com'e'. */
    data class Collegato(
        override val idDashboard: Int,
        override val nome: String,
        val idLocale: Int,
    ) : PresenteDellaDashboard

    /**
     * Non collegato: entra come ospite col nome della dashboard, come un ospite scritto a mano (R-2).
     * [omonimo] e' il giocatore locale con lo stesso nome da riusare (stessa persona, nessun doppione), o
     * null se va creato.
     */
    data class Ospite(
        override val idDashboard: Int,
        override val nome: String,
        val omonimo: Int? = null,
    ) : PresenteDellaDashboard
}

/**
 * I presenti della dashboard tradotti: [voci] nell'ordine in cui sono arrivati, senza doppioni, e
 * [nonTrovati] quanti id non si sono potuti tradurre (non collegati e senza un nome nella rosa letta).
 */
data class Traduzione(
    val voci: List<PresenteDellaDashboard>,
    val nonTrovati: Int = 0,
) {
    val ospiti: Int get() = voci.count { it is PresenteDellaDashboard.Ospite }
}

/**
 * Da id della dashboard a giocatori della serata. Funzione pura: i collegamenti R-1 del gruppo, i nomi
 * della rosa della dashboard e i nomi dei giocatori locali arrivano da fuori.
 *
 * Un id collegato a un giocatore locale che c'e' diventa quel giocatore; uno non collegato con un nome
 * nella rosa diventa un ospite con quel nome; uno senza ne' l'uno ne' l'altro (la rosa non lo ha, per
 * esempio un giocatore cancellato nella dashboard) resta fuori e si conta. Un id ripetuto vale una volta.
 *
 * L'ospite riusa il giocatore locale con lo stesso nome (senza maiuscole e spazi ai lati), come l'ospite
 * scritto a mano, ma solo se e' uno solo, non e' collegato a un altro giocatore della dashboard in questo
 * gruppo e non e' gia' preso da un altro presente: altrimenti la partita andrebbe, nell'invio, al
 * giocatore sbagliato. In dubbio se ne crea uno nuovo. Non scrive niente: gli ospiti si creano solo
 * quando l'utente usa i presenti.
 */
object TraduzionePresenti {
    fun traduci(
        ids: List<Int>,
        nomiDashboard: Map<Int, String>,
        collegamenti: Map<Int, Int>,
        nomiLocali: Map<Int, String>,
    ): Traduzione {
        val collegatiQui = collegamenti.values.toSet()
        val localiPerNome = nomiLocali.entries.groupBy({ normalizza(it.value) }, { it.key })
        val presi = HashSet<Int>()
        var nonTrovati = 0
        val voci =
            ids.distinct().mapNotNull { id ->
                val locale = collegamenti[id]?.takeIf { it in nomiLocali }
                val nome = nomiDashboard[id]?.trim().orEmpty()
                when {
                    locale != null -> {
                        presi += locale
                        PresenteDellaDashboard.Collegato(id, nomiLocali.getValue(locale), locale)
                    }

                    nome.isNotEmpty() -> {
                        val omonimo =
                            localiPerNome[normalizza(nome)]
                                ?.singleOrNull()
                                ?.takeIf { it !in collegatiQui && it !in presi }
                        omonimo?.let { presi += it }
                        PresenteDellaDashboard.Ospite(id, nome, omonimo)
                    }

                    else -> {
                        nonTrovati++
                        null
                    }
                }
            }
        return Traduzione(voci, nonTrovati)
    }

    private fun normalizza(nome: String) = nome.trim().lowercase()
}
