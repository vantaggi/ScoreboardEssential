package it.vantaggi.scoreboardessential.core

/**
 * Un evento del log con il momento in cui e' stato registrato.
 *
 * [ScoringEvent] non ha un campo tempo e non deve averlo: e' l'ingresso di una funzione pura, e
 * l'orario non cambia il punteggio. Il tempo appartiene alla cronaca, non alla regola, quindi vive
 * qui: il calcio lo lascia null, il padel lo valorizza.
 */
data class LoggedEvent(
    val event: ScoringEvent,
    /** Millisecondi dall'inizio della partita. Null quando lo sport non traccia i tempi. */
    val atMillis: Long? = null,
)

/**
 * Serializzazione del log dei punti nella colonna `matches.eventLog` (TEXT NOT NULL DEFAULT '').
 *
 * Serve a due cose: riprendere una partita dopo la morte del processo (si rifa' il fold degli
 * eventi con le regole dello sport) ed esportare la cronologia punto per punto verso Padel Elite.
 *
 * ## Formato
 *
 *     <versione>|<token>,<token>,...
 *     token := ['c']<side>[':'<playerId>]['*'<weight>]['@'<millisDallInizio>]
 *
 * Esempio: `1|1,2:7,c2,1:3*2@45000`.
 *
 * ## Perche' questi separatori
 *
 * `PlayerData` usa caratteri di controllo perche' li' i dati sono nomi liberi, digitati
 * dall'utente, e serviva un separatore che non potesse comparirvi. Qui il vocabolario e' chiuso --
 * cifre, un segno meno e i sigilli qui sotto -- quindi i separatori possono essere stampabili. E'
 * un guadagno concreto: questa stringa finisce in SQLite, dove la si legge a occhio da un browser
 * di database, viaggia nei log e verra' impacchettata in JSON verso Padel Elite, tre posti in cui
 * i caratteri di controllo sono invisibili o vanno escapati.
 *
 * ## Perche' non c'e' lo sportId
 *
 * La proposta iniziale prevedeva `fmt|sportId|tokens`. Lo sport e' pero' gia' una colonna della
 * stessa riga: ripeterlo qui creerebbe due copie che possono divergere, senza che nessun chiamante
 * sappia quale credere. Toglierlo rende anche esatta la regola sulla stringa vuota (il DEFAULT
 * della colonna e' `''`, non un log con sport ignoto) e costa meno byte per riga.
 *
 * ## Robustezza
 *
 * [decode] non lancia mai e restituisce null su qualunque cosa non capisca, versione futura
 * compresa: chi chiama ripiega sul rendering del solo punteggio finale, cosi' un bump di formato
 * degrada una riga di cronologia invece di far crashare l'app.
 */
object MatchLogCodec {
    /** Versione del formato scritta in testa. Un bump la incrementa e [decode] rifiuta il resto. */
    const val FORMAT_VERSION = 1

    private const val HEADER_SEP = '|'
    private const val TOKEN_SEP = ','
    private const val CORRECTION_PREFIX = 'c'
    private const val PLAYER_PREFIX = ':'
    private const val WEIGHT_PREFIX = '*'
    private const val TIME_PREFIX = '@'

    /**
     * Serializza il log. Il round-trip garantito e' `decode(encode(x)) == x` per ogni lista valida,
     * cioe' con `side` uguale a 1 o 2 -- gli altri valori il motore li ignora comunque.
     */
    fun encode(events: List<LoggedEvent>): String {
        val body = events.joinToString(TOKEN_SEP.toString()) { encodeToken(it) }
        return "$FORMAT_VERSION$HEADER_SEP$body"
    }

    /**
     * Deserializza il log; null se la stringa e' illeggibile o di una versione che non conosciamo.
     *
     * La stringa vuota e' il DEFAULT della colonna per tutte le partite di calcio gia' salvate:
     * vale lista vuota, non null.
     *
     * Un solo token malformato invalida l'INTERA stringa. E' la differenza rispetto a
     * `PlayerData.decodeList`, che scarta il singolo record rotto: li' un giocatore mancante nel
     * roster si vede e non fa danni, qui un punto saltato in silenzio produce una partita
     * ricostruita con un punteggio sbagliato ma credibile, che nessuno andrebbe a verificare.
     * Meglio nessuna cronologia che una cronologia falsa.
     */
    fun decode(raw: String): List<LoggedEvent>? {
        if (raw.isEmpty()) return emptyList()

        val sep = raw.indexOf(HEADER_SEP)
        if (sep <= 0) return null
        if (raw.substring(0, sep).toIntOrNull() != FORMAT_VERSION) return null

        val body = raw.substring(sep + 1)
        if (body.isEmpty()) return emptyList()

        val out = ArrayList<LoggedEvent>(body.count { it == TOKEN_SEP } + 1)
        for (token in body.split(TOKEN_SEP)) {
            out.add(decodeToken(token) ?: return null)
        }
        return out
    }

    private fun encodeToken(entry: LoggedEvent): String {
        val sb = StringBuilder(4)
        when (val event = entry.event) {
            is ScoringEvent.Correction -> {
                sb.append(CORRECTION_PREFIX).append(event.side)
            }

            is ScoringEvent.Point -> {
                sb.append(event.side)
                if (event.playerId != null) {
                    sb.append(PLAYER_PREFIX).append(event.playerId)
                }
                // Il peso e' 1 in tutti gli sport di oggi: ometterlo tiene i token a due caratteri.
                if (event.weight != 1) {
                    sb.append(WEIGHT_PREFIX).append(event.weight)
                }
            }
        }
        if (entry.atMillis != null) {
            sb.append(TIME_PREFIX).append(entry.atMillis)
        }
        return sb.toString()
    }

    private fun decodeToken(token: String): LoggedEvent? {
        var i = 0
        val isCorrection = token.startsWith(CORRECTION_PREFIX)
        if (isCorrection) i++
        if (i >= token.length) return null

        // Il side e' una singola cifra 1 o 2: cosi' il controllo di range e' anche il parsing.
        val side = token[i]
        if (side != '1' && side != '2') return null
        i++

        var playerId: Int? = null
        if (i < token.length && token[i] == PLAYER_PREFIX) {
            val end = valueEnd(token, i + 1)
            playerId = token.substring(i + 1, end).toIntOrNull() ?: return null
            i = end
        }

        var weight: Int? = null
        if (i < token.length && token[i] == WEIGHT_PREFIX) {
            val end = valueEnd(token, i + 1)
            weight = token.substring(i + 1, end).toIntOrNull() ?: return null
            i = end
        }

        var atMillis: Long? = null
        if (i < token.length && token[i] == TIME_PREFIX) {
            atMillis = token.substring(i + 1).toLongOrNull() ?: return null
            i = token.length
        }
        // Residuo: campo sconosciuto, campi fuori ordine o ripetuti. Non tiriamo a indovinare.
        if (i != token.length) return null

        val event =
            if (isCorrection) {
                // Correction non ha ne' giocatore ne' peso: se il token li porta, non e' nostro.
                if (playerId != null || weight != null) return null
                ScoringEvent.Correction(side = side - '0')
            } else {
                ScoringEvent.Point(side = side - '0', playerId = playerId, weight = weight ?: 1)
            }
        return LoggedEvent(event, atMillis)
    }

    /** Fine del valore corrente: il prossimo sigillo di campo, o la fine del token. */
    private fun valueEnd(
        token: String,
        from: Int,
    ): Int {
        var j = from
        while (j < token.length && token[j] != WEIGHT_PREFIX && token[j] != TIME_PREFIX) {
            j++
        }
        return j
    }
}
