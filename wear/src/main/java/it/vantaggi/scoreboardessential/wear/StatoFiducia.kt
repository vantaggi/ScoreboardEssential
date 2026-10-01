package it.vantaggi.scoreboardessential.wear

/**
 * Il colore di una frase di stato, come RUOLO e non come valore: a tradurlo in risorsa pensa la
 * schermata, cosi' questo file resta Kotlin puro e si prova su JVM.
 *
 * Ambra e rosso distano solo 2.14:1 fra loro: li distingue la parola, non il colore.
 */
enum class Tono {
    /** Solo cio' che chiede di intervenire. */
    ROSSO,

    /** Il punto e' salvo, ma il telefono non ce l'ha. */
    AMBRA,

    /** Informativa. */
    CHIARO,

    /** Suggerimento, il livello piu' quieto. */
    GRIGIO,
}

/**
 * La riga di stato dell'orologio: una frase sola, al massimo 18 caratteri in entrambe le lingue.
 *
 * Qui non c'e' testo, solo COSA si dice e con quali numeri: la traduzione sta nelle risorse e la
 * legge [MainActivity]. Cosi' la priorita' si prova senza Android, e le lunghezze si provano sulle
 * stringhe vere.
 */
sealed class Frase(
    val tono: Tono,
) {
    /** Posto riservato: si accende solo quando L5 porta il NACK del telefono. */
    data class Rifiutati(
        val n: Int,
    ) : Frase(Tono.ROSSO)

    /** Coda piena da almeno 10s con il telefono raggiungibile. Solo visualizzazione. */
    data class NonConsegnati(
        val n: Int,
    ) : Frase(Tono.AMBRA)

    /** Coda piena da meno di 10s con il telefono raggiungibile: sta partendo. */
    data class Invio(
        val n: Int,
    ) : Frase(Tono.CHIARO)

    data class InCoda(
        val n: Int,
    ) : Frase(Tono.AMBRA)

    /** [alle] e' l'ora dell'ultimo stato v2 ricevuto dal vivo, in millisecondi; null se non c'e'. */
    data class Scollegato(
        val alle: Long?,
    ) : Frase(Tono.AMBRA)

    data object PartitaFinita : Frase(Tono.CHIARO)

    data object TieniMeno : Frase(Tono.GRIGIO)

    data object TieniAnnulla : Frase(Tono.GRIGIO)
}

/** Messaggi che durano 2-3s e poi tornano da soli alla riga di stato di sempre. */
sealed class Transitorio(
    tono: Tono,
) : Frase(tono) {
    /** Tocco senza ricevuta: lo dice e basta, non va in coda dopo il timeout per non contare due volte. */
    data object NonConfermato : Transitorio(Tono.ROSSO)

    data class Consegnati(
        val n: Int,
    ) : Transitorio(Tono.CHIARO)

    data object Chiusura : Transitorio(Tono.CHIARO)

    data object NonChiusa : Transitorio(Tono.AMBRA)
}

/**
 * Tutto cio' che decide la riga. Il tempo non entra come orologio ma come durata gia' misurata
 * ([collegatoConCodaDaMs]): la funzione resta pura e il test non aspetta niente.
 */
data class InputFiducia(
    /** La capability risponde, e il conto e' stato rifatto ADESSO: vedi [verificaInCorso]. */
    val collegato: Boolean,
    /** Vero per al massimo 2s dopo onResume e refreshConnection: all'avvio ConnectionState vale Disconnected. */
    val verificaInCorso: Boolean,
    /** Tocchi segnati al polso e non ancora confermati dal telefono. */
    val inCoda: Int,
    /** Da quanto la coda e' non vuota DA COLLEGATI, in millisecondi; 0 se non lo e'. */
    val collegatoConCodaDaMs: Long,
    val matchOver: Boolean,
    val decrementIsUndo: Boolean,
    val ultimoStatoVivoAlle: Long?,
    val transitorio: Transitorio?,
    val rifiutati: Int = 0,
)

/**
 * Che cosa dice il polso del collegamento: funzione pura, vince la prima condizione vera.
 *
 * Sul polso la norma e' che vada tutto bene, e un segnale fisso di "ok" costa attenzione a ogni
 * sguardo e, quando mente, costa fiducia: il vecchio pallino verde restava acceso a collegamento
 * caduto. Qui si parla solo quando qualcosa non va, e lo si dice a parole.
 */
object StatoFiducia {
    /** Oltre questa attesa, con il telefono raggiungibile, la coda e' bloccata e non in viaggio. */
    const val SOGLIA_NON_CONSEGNATI_MS = 10_000L

    /** Quanto dura al massimo la verifica del collegamento: poi si dice come stanno le cose. */
    const val DURATA_VERIFICA_MS = 2_000L

    /** Quanto resta un messaggio transitorio. */
    const val DURATA_TRANSITORIO_MS = 3_000L

    fun calcola(input: InputFiducia): Frase {
        if (input.rifiutati > 0) return Frase.Rifiutati(input.rifiutati)
        input.transitorio?.let { return it }
        if (input.collegato && input.inCoda > 0) {
            // SOLO visualizzazione: nessun nuovo invio automatico, perche' L5 avverte che un
            // tentativo senza id idempotente puo' applicare la coda alla partita sbagliata.
            return if (input.collegatoConCodaDaMs >= SOGLIA_NON_CONSEGNATI_MS) {
                Frase.NonConsegnati(input.inCoda)
            } else {
                Frase.Invio(input.inCoda)
            }
        }
        // Mentre si verifica non si dice "scollegato": accendendo l'orologio lampeggerebbe a ogni
        // avvio, perche' il collegamento vale Disconnected finche' non arriva la risposta.
        if (!input.collegato && !input.verificaInCorso) {
            if (input.inCoda > 0) return Frase.InCoda(input.inCoda)
            return Frase.Scollegato(input.ultimoStatoVivoAlle)
        }
        if (input.matchOver) return Frase.PartitaFinita
        return if (input.decrementIsUndo) Frase.TieniAnnulla else Frase.TieniMeno
    }
}
