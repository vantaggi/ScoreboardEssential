package it.vantaggi.scoreboardessential.wear

/** Le due voci del menu partita, nell'ordine in cui compaiono. */
enum class IdVoce {
    SPORT,
    FINE_PARTITA,
}

/**
 * Che cosa dice la riga piccola sotto una voce, come COSA e non come testo: la traduzione sta
 * nelle risorse e la legge [MenuActivity], cosi' i blocchi si provano su JVM senza Android.
 */
sealed class SottotitoloVoce {
    /** Voce attiva: lo sport in uso. */
    data class SportInUso(
        val sport: String,
    ) : SottotitoloVoce()

    data object PartitaInCorso : SottotitoloVoce()

    /** Il polso ha punti che il telefono non ha: prima si consegnano, poi si decide. */
    data class PrimaConsegna(
        val punti: Int,
    ) : SottotitoloVoce()

    data object ServeIlTelefono : SottotitoloVoce()

    /** Nel calcio la chiusura dal polso e' bloccata finche' VALIDAZIONE L4 non e' corretta. */
    data object ChiudiDalTelefono : SottotitoloVoce()

    /** Voce attiva: il risultato che verra' salvato, gia' impaginato ("3–2"). */
    data class SalvaRisultato(
        val risultato: String,
    ) : SottotitoloVoce()
}

/** Una voce gia' decisa: [attiva] falso vuol dire che il tocco non fa niente e il sottotitolo dice perche'. */
data class Voce(
    val id: IdVoce,
    val attiva: Boolean,
    val sottotitolo: SottotitoloVoce,
)

/** Tutto cio' che decide le voci. Nessun tempo e nessun Context: sono fatti, non letture. */
data class InputMenu(
    /** Tocchi segnati al polso e non ancora consegnati. */
    val inCoda: Int,
    /** Il telefono risponde adesso. */
    val collegato: Boolean,
    /** Il telefono dice che il registro non e' vuoto: il cambio sport verrebbe rifiutato. */
    val partitaIniziata: Boolean,
    /** Sport a calcio e stato v2 gia' arrivato: la chiusura dal polso perderebbe la partita (L4). */
    val calcioConV2: Boolean,
    /** Il telefono ha mandato un elenco fra cui scegliere. Senza, la voce SPORT non compare. */
    val haElencoSport: Boolean,
    /** Lo sport in uso, gia' tradotto dal telefono. */
    val sport: String,
    /** Il risultato a schermo, gia' impaginato. */
    val risultato: String,
)

/**
 * Le voci del menu partita e i loro blocchi: funzione pura, vince il primo blocco vero.
 *
 * Un comando che distrugge o che non puo' riuscire non deve sembrare un comando: invece di far
 * partire una richiesta che si sa gia' come finisce (era il Toast "Partita in corso" e il dialogo
 * "Finire la partita?"), la voce si spegne e dice a parole perche'.
 */
object MenuVoci {
    fun calcola(input: InputMenu): List<Voce> =
        listOfNotNull(
            if (input.haElencoSport) voceSport(input) else null,
            voceFine(input),
        )

    private fun voceSport(input: InputMenu): Voce {
        // L'arretrato non dice di che sport e': con punti in coda non si cambia sport dal polso.
        val blocco =
            when {
                input.partitaIniziata -> SottotitoloVoce.PartitaInCorso
                input.inCoda > 0 -> SottotitoloVoce.PrimaConsegna(input.inCoda)
                else -> null
            }
        return Voce(IdVoce.SPORT, blocco == null, blocco ?: SottotitoloVoce.SportInUso(input.sport))
    }

    private fun voceFine(input: InputMenu): Voce {
        // L'ordine e' quello del design. Con la coda piena si fonderebbero due partite (L4 alta);
        // da scollegati la chiusura non arriverebbe; nel calcio perderebbe la partita (L4 alta).
        val blocco =
            when {
                input.inCoda > 0 -> SottotitoloVoce.PrimaConsegna(input.inCoda)
                !input.collegato -> SottotitoloVoce.ServeIlTelefono
                input.calcioConV2 -> SottotitoloVoce.ChiudiDalTelefono
                else -> null
            }
        return Voce(IdVoce.FINE_PARTITA, blocco == null, blocco ?: SottotitoloVoce.SalvaRisultato(input.risultato))
    }
}

/**
 * La conferma sul posto della fine partita, senza dialogo: il primo tocco arma, il secondo
 * conferma, ma solo dentro una finestra.
 *
 * Il tempo entra come istante passato da chi chiama: la finestra si prova su JVM spostando un
 * numero, senza aspettare. Prima di [ATTESA_MIN_MS] il secondo tocco e' quello di un dito che
 * rimbalza o di un doppio colpo involontario, e non conta; dopo [SCADENZA_MS] la card e' gia'
 * tornata com'era, quindi il tocco e' un primo tocco.
 */
class ConfermaSulPosto {
    companion object {
        const val ATTESA_MIN_MS = 600L
        const val SCADENZA_MS = 5_000L
    }

    enum class Esito {
        /** Primo tocco: la card cambia e chiede di toccare di nuovo. */
        ARMATA,

        /** Secondo tocco troppo presto: non succede niente, la finestra continua. */
        IGNORATO,

        /** Secondo tocco nella finestra: si chiude la partita. */
        CONFERMATA,
    }

    private var armataDal: Long? = null

    val armata: Boolean
        get() = armataDal != null

    fun tocca(adesso: Long): Esito {
        val dal = armataDal
        if (dal == null || adesso - dal > SCADENZA_MS) {
            armataDal = adesso
            return Esito.ARMATA
        }
        if (adesso - dal < ATTESA_MIN_MS) return Esito.IGNORATO
        armataDal = null
        return Esito.CONFERMATA
    }

    /** La card e' tornata com'era: il prossimo tocco e' di nuovo il primo. */
    fun disarma() {
        armataDal = null
    }
}
