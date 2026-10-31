package it.vantaggi.scoreboardessential.wear

/** Le voci del menu partita, nell'ordine in cui compaiono. */
enum class IdVoce {
    /** Compare solo a partita ferma, quando il telefono propone la prossima partita della serata. */
    PROSSIMA_PARTITA,
    SPORT,

    /** Solo negli sport col servizio, a partita non finita: la correzione di chi serve. */
    CHI_SERVE,
    FINE_PARTITA,

    /** Compare solo quando il telefono ha rifiutato la coda (L5): e' il modo di scartarla. */
    SCARTA_CODA,

    // Le impostazioni essenziali, in un gruppo loro sotto "In campo" ([MenuVoci.impostazioni]).

    /** Solo negli sport col 40-40, e cambia solo a partita ferma, come lo sport. */
    REGOLA_40,

    /** Dell'orologio: tocco dopo tocco accende o spegne la vibrazione dei punti. */
    VIBRAZIONE,

    /** Dell'orologio: tiene lo schermo acceso mentre la partita e' in corso, senza ambient e senza quadrante. */
    SCHERMO_ACCESO,

    /** Solo dove c'e' il portiere (il calcio): la durata del suo timer. */
    PORTIERE,
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

    /** Il telefono ha rifiutato questi tocchi: non sono della sua partita, e fino a che non si scartano il resto e' bloccato. */
    data class CodaRifiutata(
        val punti: Int,
    ) : SottotitoloVoce()

    data object ServeIlTelefono : SottotitoloVoce()

    /** Registro vuoto e cronometro a zero: il telefono non salverebbe niente, e la chiusura non avrebbe cosa confermare. */
    data object NienteDaSalvare : SottotitoloVoce()

    /** Voce attiva: le due coppie della prossima partita, gia' in sigle ("VAN/GLO – ROS/BIA"). */
    data class CoppieProposte(
        val coppie: String,
    ) : SottotitoloVoce()

    /** Voce attiva: il valore in uso, gia' scritto ("Killer point", "30 s"). */
    data class Valore(
        val testo: String,
    ) : SottotitoloVoce()

    /** Una voce spenta che dice anche il valore in uso: "Killer point · Partita in corso". */
    data class ValoreBloccato(
        val valore: String,
        val perche: SottotitoloVoce,
    ) : SottotitoloVoce()

    /** La vibrazione dei punti: accesa, o spenta con i soli avvisi. */
    data class Vibrazione(
        val accesa: Boolean,
    ) : SottotitoloVoce()

    /** Lo schermo acceso in partita: acceso, o come decide il sistema. */
    data class SchermoAcceso(
        val acceso: Boolean,
    ) : SottotitoloVoce()

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
    /** Il telefono ha mandato un elenco fra cui scegliere. Senza, la voce SPORT non compare. */
    val haElencoSport: Boolean,
    /** Lo sport in uso, gia' tradotto dal telefono. */
    val sport: String,
    /** Il risultato a schermo, gia' impaginato. */
    val risultato: String,
    /** Quanti tocchi in coda il telefono ha rifiutato (L5); 0 se la coda non e' rifiutata. */
    val rifiutati: Int = 0,
    /** Le coppie della prossima partita della serata, gia' in sigle; vuoto se il telefono non ne propone. */
    val prossima: String = "",
    /** La regola del 40-40 in uso, gia' tradotta; vuota dove il telefono non offre la scelta. */
    val regola40: String = "",
    /** La durata del portiere, gia' scritta; vuota dove non c'e' il portiere. */
    val portiere: String = "",
    /** La vibrazione dei punti dell'orologio. */
    val vibrazionePunti: Boolean = true,
    /** Lo schermo resta acceso mentre la partita e' in corso. */
    val schermoAcceso: Boolean = false,
    /**
     * Il cronometro della partita e' partito: anche a registro vuoto c'e' una partita da salvare (lo 0-0
     * giocato a tempo), con lo stesso criterio di endMatch sul telefono.
     */
    val tempoAvviato: Boolean = false,
    /** Chi serve adesso, gia' scritto ("Serve ROS"); vuoto dove non c'e' servizio o a partita finita. */
    val chiServe: String = "",
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
            // Prima delle altre: a partita ferma con una serata, la prossima partita e' la cosa da fare.
            // A partita cominciata non c'e': le coppie sono quelle del motore, e il telefono rifiuterebbe.
            if (input.prossima.isNotEmpty() && !input.partitaIniziata) voceProssima(input) else null,
            if (input.haElencoSport) voceSport(input) else null,
            if (input.chiServe.isNotEmpty()) voceChiServe(input) else null,
            voceFine(input),
            // Una coda rifiutata non si rimanda e non si applica: si scarta, e finche' non lo si fa le
            // altre voci dicono perche' sono spente.
            if (input.rifiutati > 0) {
                Voce(IdVoce.SCARTA_CODA, true, SottotitoloVoce.CodaRifiutata(input.rifiutati))
            } else {
                null
            },
        )

    /**
     * Le impostazioni essenziali, nell'ordine: la regola del 40-40, la vibrazione, lo schermo acceso, il portiere. Il resto
     * si sceglie una volta sola dal telefono. Le due che chiede il telefono hanno i suoi blocchi; la
     * vibrazione e lo schermo acceso sono del polso e si cambiano sempre.
     */
    fun impostazioni(input: InputMenu): List<Voce> =
        listOfNotNull(
            if (input.regola40.isNotEmpty()) voceRegola40(input) else null,
            Voce(IdVoce.VIBRAZIONE, true, SottotitoloVoce.Vibrazione(input.vibrazionePunti)),
            Voce(IdVoce.SCHERMO_ACCESO, true, SottotitoloVoce.SchermoAcceso(input.schermoAcceso)),
            if (input.portiere.isNotEmpty()) vocePortiere(input) else null,
        )

    private fun voceRegola40(input: InputMenu): Voce {
        // Gli stessi blocchi e nello stesso ordine dello sport: e' una regola dello sport.
        val blocco =
            when {
                input.rifiutati > 0 -> SottotitoloVoce.CodaRifiutata(input.rifiutati)
                input.inCoda > 0 -> SottotitoloVoce.PrimaConsegna(input.inCoda)
                input.partitaIniziata -> SottotitoloVoce.PartitaInCorso
                !input.collegato -> SottotitoloVoce.ServeIlTelefono
                else -> null
            }
        // Spenta dice anche quale regola vale: a meta' partita e' proprio quello che si vuole sapere.
        return Voce(
            IdVoce.REGOLA_40,
            blocco == null,
            if (blocco == null) SottotitoloVoce.Valore(input.regola40) else SottotitoloVoce.ValoreBloccato(input.regola40, blocco),
        )
    }

    private fun vocePortiere(input: InputMenu): Voce {
        // La durata vale dal prossimo cambio anche a partita in corso, come sul telefono: serve solo lui.
        val blocco = if (!input.collegato) SottotitoloVoce.ServeIlTelefono else null
        return Voce(IdVoce.PORTIERE, blocco == null, blocco ?: SottotitoloVoce.Valore(input.portiere))
    }

    /**
     * La correzione di chi serve: la decide il telefono, quindi gli stessi blocchi della prossima
     * partita (la coda prima, poi il telefono assente). A partita in corso e' accesa: e' fatta per quello.
     */
    private fun voceChiServe(input: InputMenu): Voce {
        val blocco =
            when {
                input.rifiutati > 0 -> SottotitoloVoce.CodaRifiutata(input.rifiutati)
                input.inCoda > 0 -> SottotitoloVoce.PrimaConsegna(input.inCoda)
                !input.collegato -> SottotitoloVoce.ServeIlTelefono
                else -> null
            }
        return Voce(
            IdVoce.CHI_SERVE,
            blocco == null,
            if (blocco == null) SottotitoloVoce.Valore(input.chiServe) else SottotitoloVoce.ValoreBloccato(input.chiServe, blocco),
        )
    }

    private fun voceProssima(input: InputMenu): Voce {
        // Come le altre: prima la coda (una partita nuova sopra punti non consegnati fonderebbe due
        // partite), poi il telefono assente (le coppie le cambia lui).
        val blocco =
            when {
                input.rifiutati > 0 -> SottotitoloVoce.CodaRifiutata(input.rifiutati)
                input.inCoda > 0 -> SottotitoloVoce.PrimaConsegna(input.inCoda)
                !input.collegato -> SottotitoloVoce.ServeIlTelefono
                else -> null
            }
        return Voce(IdVoce.PROSSIMA_PARTITA, blocco == null, blocco ?: SottotitoloVoce.CoppieProposte(input.prossima))
    }

    private fun voceSport(input: InputMenu): Voce {
        // Stesso ordine di FINE PARTITA, cosi' le due voci non si contraddicono: prima la coda
        // (il numero dei punti e' piu' utile del motivo generico, e l'arretrato non dice di che
        // sport e'), poi la partita cominciata (il telefono rifiuterebbe il cambio), poi il
        // telefono assente (la richiesta non arriverebbe).
        val blocco =
            when {
                input.rifiutati > 0 -> SottotitoloVoce.CodaRifiutata(input.rifiutati)
                input.inCoda > 0 -> SottotitoloVoce.PrimaConsegna(input.inCoda)
                input.partitaIniziata -> SottotitoloVoce.PartitaInCorso
                !input.collegato -> SottotitoloVoce.ServeIlTelefono
                else -> null
            }
        return Voce(IdVoce.SPORT, blocco == null, blocco ?: SottotitoloVoce.SportInUso(input.sport))
    }

    private fun voceFine(input: InputMenu): Voce {
        // L'ordine e' quello del design. Con la coda piena si fonderebbero due partite (L4 alta);
        // a partita non cominciata, registro vuoto e cronometro a zero, non c'e' niente da salvare
        // (il telefono rifiuta e il polso aspetterebbe invano la conferma); col tempo avviato uno
        // 0-0 si salva come sul telefono; da scollegati la chiusura non arriverebbe. Nel
        // calcio la voce e' accesa come negli altri sport: la chiusura e' un'intenzione con
        // sequenza e il telefono salva col proprio registro (L4).
        val blocco =
            when {
                input.rifiutati > 0 -> SottotitoloVoce.CodaRifiutata(input.rifiutati)
                input.inCoda > 0 -> SottotitoloVoce.PrimaConsegna(input.inCoda)
                !input.partitaIniziata && !input.tempoAvviato -> SottotitoloVoce.NienteDaSalvare
                !input.collegato -> SottotitoloVoce.ServeIlTelefono
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
