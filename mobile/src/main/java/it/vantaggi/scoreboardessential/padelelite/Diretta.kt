package it.vantaggi.scoreboardessential.padelelite

import it.vantaggi.scoreboardessential.core.ExportOrigin
import it.vantaggi.scoreboardessential.core.ExportResult
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchEngine
import it.vantaggi.scoreboardessential.core.MatchExporter
import it.vantaggi.scoreboardessential.core.MatchPlayer
import it.vantaggi.scoreboardessential.core.SportRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A che punto e' la diretta. Sempre detta con una parola (e un'icona), mai col solo colore. */
enum class DirettaFase {
    /** Non c'e' una diretta. */
    SPENTA,

    /** Trasmette (o sta per farlo): "In diretta su <gruppo>". */
    IN_CORSO,

    /** Accesa ma ferma per ora: si riprende da sola quando il motivo passa ([DirettaMotivo]). */
    IN_ATTESA,

    /** Spenta con un perche': non riparte da sola. */
    FERMA,

    /** Chiusa (partita finita, scartata o diretta interrotta): per quella partita non si riapre. */
    CHIUSA,
}

enum class DirettaMotivo {
    // In attesa
    RETE,
    ACCEDI_DI_NUOVO,
    SERVONO_QUATTRO,
    NOME_TROPPO_LUNGO,
    TROPPE_DIRETTE,
    CODICE_ESAURITO,
    RISPOSTA_INATTESA,
    ERRORE_INATTESO,
    GIOCATORE_SOSTITUITO,

    // Ferma
    NON_ADMIN,
    FILE_NON_VALIDO,
    FILE_TROPPO_GRANDE,
    TROPPI_PUNTI,
    ALTRO_ADMIN,
    GIA_CHIUSA,
    SERVER,
}

data class DirettaStato(
    val fase: DirettaFase = DirettaFase.SPENTA,
    val motivo: DirettaMotivo? = null,
    /** Il pezzo che non torna (il campo del file, l'inizio del nome troppo lungo), se si sa. */
    val dettaglio: String? = null,
    val gruppo: String = "",
    val matchId: String? = null,
)

/**
 * Cio' che serve a costruire il file parziale, catturato sul thread principale e immutabile: il file si
 * rifa' poi altrove, rigiocando il registro con un motore a parte (mai quello vivo). [log] e' la copia
 * difensiva di `MatchEngine.log`.
 */
class DirettaFoto(
    val matchId: String,
    val rules: SportRules,
    val log: List<LoggedEvent>,
    val players: List<MatchPlayer>,
    val origin: ExportOrigin,
)

/** La chiusura da consegnare al lavoro in coda: [conRisultato] vero = col file finale, falso = senza (scartata, interrotta). */
data class DirettaChiusuraRichiesta(
    val matchId: String,
    val groupId: String,
    val conRisultato: Boolean,
)

/**
 * La diretta di una partita: pubblica il file parziale a ogni cambiamento del registro (D-2).
 *
 * **Come pubblica.** Un solo consumatore su un canale CONFLATED: le richieste vanno in serie (mai due in
 * volo per la stessa partita), con almeno [minIntervalMs] fra una e l'altra (una al secondo), e un
 * segnale arrivato mentre se ne fa una si fonde con gli altri: quando tocca, si costruisce il file dall'
 * ULTIMA foto, quindi l'ultimo stato vince e quelli intermedi non partono. Nessuna coda persistente: conta
 * solo l'ultimo stato. Offline la richiesta salta e si ritenta con attesa crescente (o al punto dopo).
 *
 * **Dove vive.** In uno scope dell'applicazione, non del ViewModel: sopravvive a rotazione e a un
 * ViewModel ricreato. Con l'app in background durante un padel (il cronometro non e' un servizio in
 * primo piano: nel padel non c'e') il sistema puo' sospendere il processo, e con lui la diretta.
 *
 * **Cosa persiste.** Solo [DirettaSalvata] (partita, gruppo): al ripristino [riprendi] la riaccende.
 *
 * La chiusura non passa di qui: [chiudi] ferma la trasmissione e consegna la richiesta a [allaChiusura]
 * (WorkManager, unico per partita), perche' il risultato finale non deve perdersi.
 */
class Diretta(
    private val account: PadelEliteAccount,
    private val store: DirettaStore,
    private val scope: CoroutineScope,
    /** I collegamenti (id locale -> id della dashboard) del gruppo di destinazione, per `padelPlayerId`. */
    private val collegamenti: suspend (groupId: String) -> Map<Int, Int> = { emptyMap() },
    private val allaChiusura: (DirettaChiusuraRichiesta) -> Unit = {},
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    private val retryBaseMs: Long = RETRY_BASE_MS,
    private val retryLentoMs: Long = RETRY_LENTO_MS,
) {
    private class Attiva(
        val matchId: String,
        val groupId: String,
        val groupName: String,
        val segnale: Channel<Unit> = Channel(Channel.CONFLATED),
    )

    private val lock = Any()

    @Volatile private var attiva: Attiva? = null

    @Volatile private var ultima: DirettaFoto? = null
    private var ciclo: Job? = null
    private var ritentativo: Job? = null

    private val _stato = MutableStateFlow(DirettaStato())
    val stato: StateFlow<DirettaStato> = _stato

    /** C'e' una diretta accesa (anche in attesa): il ViewModel puo' risparmiarsi di costruire la foto se no. */
    val acceso: Boolean get() = attiva != null

    fun matchIdAcceso(): String? = attiva?.matchId

    /**
     * Accende la diretta di [foto] verso [groupId]. False se una c'e' gia' o se per questa partita e' gia'
     * stata chiusa (la chiusura e' definitiva anche sul server).
     */
    fun accendi(
        groupId: String,
        groupName: String,
        foto: DirettaFoto,
    ): Boolean =
        synchronized(lock) {
            val corrente = _stato.value
            if (attiva != null) return false
            if (corrente.fase == DirettaFase.CHIUSA && corrente.matchId == foto.matchId) return false
            store.save(DirettaSalvata(foto.matchId, groupId, groupName))
            avvia(Attiva(foto.matchId, groupId, groupName), foto)
            true
        }

    /**
     * Al ripristino della partita: se la diretta era accesa per QUESTA partita ([foto]) riprende, e se era
     * accesa per un'altra (o per nessuna partita che si possa riprendere) quella e' abbandonata e si
     * chiude senza risultato. Senza diretta salvata non fa niente.
     */
    fun riprendi(foto: DirettaFoto?) {
        synchronized(lock) {
            val corrente = attiva
            if (corrente != null) {
                // Stesso processo, ViewModel nuovo: la diretta c'e' gia', basta darle la foto di adesso.
                if (foto != null && foto.matchId == corrente.matchId) cambiata(foto) else chiudi(conRisultato = false)
                return
            }
            val salvata = store.load() ?: return
            if (foto != null && foto.matchId == salvata.matchId) {
                avvia(Attiva(salvata.matchId, salvata.groupId, salvata.groupName), foto)
            } else {
                store.clear()
                allaChiusura(DirettaChiusuraRichiesta(salvata.matchId, salvata.groupId, conRisultato = false))
            }
        }
    }

    /** Il registro (o le rose) della partita sono cambiati: l'ultimo stato vince. Ignorata se non c'e' una diretta per quella partita. */
    fun cambiata(foto: DirettaFoto) {
        synchronized(lock) {
            val a = attiva ?: return
            if (foto.matchId != a.matchId) return
            ultima = foto
            a.segnale.trySend(Unit)
        }
    }

    /** Spegne la diretta a mano: si chiude senza risultato e per questa partita non si riapre. */
    fun spegni() = chiudi(conRisultato = false)

    /**
     * Ferma la trasmissione e consegna la chiusura: [conRisultato] a partita finita (il lavoro in coda
     * rifa' il file finale dal database), senza se scartata o interrotta. Dopo questa chiamata nessuna
     * pubblicazione parte piu' per quella partita. Senza una diretta accesa non fa niente.
     */
    fun chiudi(conRisultato: Boolean) {
        synchronized(lock) {
            val a = attiva ?: return
            val vecchio = fermaIlCiclo()
            store.clear()
            _stato.value = DirettaStato(DirettaFase.CHIUSA, gruppo = a.groupName, matchId = a.matchId)
            chiudiDopo(vecchio, DirettaChiusuraRichiesta(a.matchId, a.groupId, conRisultato))
        }
    }

    /** La diretta chiusa non occupa piu' il foglio: si nasconde al primo punto della partita dopo. */
    fun dimenticaLaChiusa() {
        synchronized(lock) {
            if (attiva == null && _stato.value.fase == DirettaFase.CHIUSA) _stato.value = DirettaStato()
        }
    }

    private fun avvia(
        nuova: Attiva,
        foto: DirettaFoto,
    ) {
        fermaIlCiclo()
        attiva = nuova
        ultima = foto
        _stato.value = DirettaStato(DirettaFase.IN_CORSO, gruppo = nuova.groupName, matchId = nuova.matchId)
        ciclo = scope.launch { trasmetti(nuova) }
        nuova.segnale.trySend(Unit)
    }

    /** Ferma il ciclo e restituisce il suo lavoro: una richiesta in volo non e' annullabile, la chiusura la aspetta. */
    private fun fermaIlCiclo(): Job? {
        val vecchio = ciclo
        vecchio?.cancel()
        ritentativo?.cancel()
        ciclo = null
        ritentativo = null
        attiva?.segnale?.close()
        attiva = null
        ultima = null
        return vecchio
    }

    /**
     * Consegna la chiusura DOPO la fine dell'eventuale richiesta in volo: una pubblicazione che crea la riga
     * non deve arrivare al server dopo l'end (la diretta chiusa e' definitiva, ma la riga nascerebbe aperta).
     */
    private fun chiudiDopo(
        vecchio: Job?,
        richiesta: DirettaChiusuraRichiesta,
    ) {
        if (vecchio == null || vecchio.isCompleted) {
            allaChiusura(richiesta)
        } else {
            scope.launch {
                vecchio.join()
                allaChiusura(richiesta)
            }
        }
    }

    /** Un'accensione finita male: ferma e non riparte da sola. [chiudiSulServer] solo se il file c'era e il server lo sa. */
    private fun ferma(
        a: Attiva,
        motivo: DirettaMotivo,
        dettaglio: String? = null,
        chiudiSulServer: Boolean = false,
    ) {
        synchronized(lock) {
            if (attiva !== a) return
            val vecchio = fermaIlCiclo()
            store.clear()
            _stato.value = DirettaStato(DirettaFase.FERMA, motivo, dettaglio, a.groupName, a.matchId)
            if (chiudiSulServer) chiudiDopo(vecchio, DirettaChiusuraRichiesta(a.matchId, a.groupId, conRisultato = false))
        }
    }

    private fun inAttesa(
        a: Attiva,
        motivo: DirettaMotivo,
        dettaglio: String? = null,
    ) {
        synchronized(lock) {
            if (attiva === a) _stato.value = DirettaStato(DirettaFase.IN_ATTESA, motivo, dettaglio, a.groupName, a.matchId)
        }
    }

    private fun inCorso(a: Attiva) {
        synchronized(lock) {
            if (attiva === a) _stato.value = DirettaStato(DirettaFase.IN_CORSO, gruppo = a.groupName, matchId = a.matchId)
        }
    }

    /** Riprova dopo [dopoMs], a meno che nel frattempo non arrivi un punto (che riaccende il segnale per conto suo). */
    private fun riprovaTra(
        a: Attiva,
        dopoMs: Long,
    ) {
        synchronized(lock) {
            if (attiva !== a) return
            ritentativo?.cancel()
            ritentativo =
                scope.launch {
                    delay(dopoMs)
                    a.segnale.trySend(Unit)
                }
        }
    }

    /** Quanto la diretta ha gia' provato e fallito: serve al ritentativo con attesa crescente e ai file identici. */
    private class Andamento {
        var ultimoInviato: String? = null
        var inattese = 0
        var falliti = 0
    }

    /** Il consumatore: un segnale alla volta, in serie, con la pausa minima fra una richiesta e l'altra. */
    private suspend fun trasmetti(a: Attiva) {
        val andamento = Andamento()
        for (segnale in a.segnale) {
            if (attiva !== a) return
            val foto = ultima ?: continue
            try {
                if (!giro(a, foto, andamento)) return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Un'eccezione qualunque (il Keystore nel rinnovo del token, un file che non si costruisce) non deve
                // buttare giu' l'app a meta' partita: la diretta aspetta e riprova.
                if (attiva !== a) return
                attesaCrescente(a, andamento, DirettaMotivo.ERRORE_INATTESO, e.javaClass.simpleName)
            }
            delay(minIntervalMs)
        }
    }

    private fun attesaCrescente(
        a: Attiva,
        andamento: Andamento,
        motivo: DirettaMotivo,
        dettaglio: String? = null,
    ) {
        andamento.falliti++
        inAttesa(a, motivo, dettaglio)
        riprovaTra(a, (retryBaseMs shl (andamento.falliti - 1).coerceAtMost(MAX_SHIFT)).coerceAtMost(retryLentoMs))
    }

    /** Una pubblicazione. False = la diretta e' finita (ferma): il ciclo esce. */
    private suspend fun giro(
        a: Attiva,
        foto: DirettaFoto,
        andamento: Andamento,
    ): Boolean {
        val file =
            when (val costruito = costruisci(a, foto)) {
                is Costruito.Pronto -> {
                    costruito.json
                }

                is Costruito.Attesa -> {
                    inAttesa(a, costruito.motivo, costruito.dettaglio)
                    return true
                }

                is Costruito.Ferma -> {
                    ferma(a, costruito.motivo, costruito.dettaglio, chiudiSulServer = true)
                    return false
                }
            }
        // Un file uguale all'ultimo consegnato non rifa' la richiesta (il server direbbe written: false).
        if (file == andamento.ultimoInviato) {
            inCorso(a)
            return true
        }
        // La richiesta in volo non si annulla: interrotta durante il rinnovo del token perderebbe il refresh token
        // ruotato, e l'utente verrebbe scollegato. La chiusura aspetta che finisca ([chiudiDopo]).
        val esito = withContext(NonCancellable) { account.publishLive(a.groupId, file) }
        if (attiva !== a) return false
        when (esito) {
            is LiveResult.Published -> {
                andamento.inattese = 0
                andamento.falliti = 0
                if (!esito.written && esito.live?.status == "ended") {
                    // Definitiva: la diretta di questa partita e' chiusa sul server.
                    ferma(a, DirettaMotivo.GIA_CHIUSA)
                    return false
                }
                andamento.ultimoInviato = file
                inCorso(a)
            }

            LiveResult.NotAuthenticated -> {
                inAttesa(a, DirettaMotivo.ACCEDI_DI_NUOVO)
                riprovaTra(a, retryLentoMs)
            }

            LiveResult.NotAuthorized -> {
                ferma(a, DirettaMotivo.NON_ADMIN)
                return false
            }

            is LiveResult.InvalidPayload -> {
                ferma(a, DirettaMotivo.FILE_NON_VALIDO, esito.details, chiudiSulServer = true)
                return false
            }

            LiveResult.PayloadTooLarge -> {
                ferma(a, DirettaMotivo.FILE_TROPPO_GRANDE, chiudiSulServer = true)
                return false
            }

            LiveResult.LiveConflict -> {
                // E' la diretta di un altro admin: non la si chiude.
                ferma(a, DirettaMotivo.ALTRO_ADMIN)
                return false
            }

            LiveResult.LiveFull -> {
                inAttesa(a, DirettaMotivo.TROPPE_DIRETTE)
                riprovaTra(a, retryLentoMs)
            }

            LiveResult.JoinCodeExhausted -> {
                inAttesa(a, DirettaMotivo.CODICE_ESAURITO)
                riprovaTra(a, retryLentoMs)
            }

            LiveResult.Network -> {
                attesaCrescente(a, andamento, DirettaMotivo.RETE)
            }

            is LiveResult.Ended, LiveResult.NotFound, is LiveResult.Unexpected -> {
                // Fuori contratto (un 404 se la migrazione 67 non c'e' ancora): cinque giri, poi si ferma.
                andamento.inattese++
                if (andamento.inattese >= MAX_INATTESE) {
                    ferma(a, DirettaMotivo.SERVER)
                    return false
                }
                inAttesa(a, DirettaMotivo.RISPOSTA_INATTESA)
                riprovaTra(a, retryBaseMs)
            }
        }
        return true
    }

    private sealed interface Costruito {
        class Pronto(
            val json: String,
        ) : Costruito

        class Attesa(
            val motivo: DirettaMotivo,
            val dettaglio: String? = null,
        ) : Costruito

        class Ferma(
            val motivo: DirettaMotivo,
            val dettaglio: String? = null,
        ) : Costruito
    }

    /**
     * Il file parziale della foto, o perche' non c'e'. Lo costruisce `MatchExporter.buildPartial` con un
     * motore nuovo rigiocato dal registro (lo stesso rigioco dell'invio e dell'export, nessuna logica doppia).
     * I tetti del server si controllano QUI, prima della rete: i nomi (60) si rifiutano, non si tagliano.
     */
    private suspend fun costruisci(
        a: Attiva,
        foto: DirettaFoto,
    ): Costruito {
        val lungo = foto.players.firstOrNull { it.name.length > MAX_NOME }
        if (lungo != null) return Costruito.Attesa(DirettaMotivo.NOME_TROPPO_LUNGO, lungo.name.take(ANTEPRIMA_NOME))
        val legami =
            try {
                collegamenti(a.groupId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyMap()
            }
        return withContext(Dispatchers.Default) {
            val motore = MatchEngine(foto.rules).also { it.restoreLog(foto.log) }
            when (val esito = MatchExporter.buildPartial(motore, foto.players, foto.origin, legami)) {
                is ExportResult.Incomplete -> {
                    Costruito.Attesa(DirettaMotivo.SERVONO_QUATTRO)
                }

                is ExportResult.Ready -> {
                    val ids =
                        esito.export.players
                            .map { it.localId }
                            .toSet()
                    val fuori =
                        esito.export.config.serveOrder
                            .any { it !in ids } ||
                            esito.export.timeline.any { it.servingPlayerId != null && it.servingPlayerId !in ids }
                    if (fuori) {
                        // Un giocatore sostituito a meta' partita: l'ordine di servizio ha ancora il vecchio id e il server
                        // rifiuterebbe il file (invalid_payload, e la chiusura sarebbe definitiva). Si aspetta, senza chiudere.
                        Costruito.Attesa(DirettaMotivo.GIOCATORE_SOSTITUITO)
                    } else if (esito.export.timeline.size > MAX_PUNTI) {
                        Costruito.Ferma(DirettaMotivo.TROPPI_PUNTI)
                    } else {
                        val json = MatchExporter.toJson(esito.export)
                        if (json.toByteArray(Charsets.UTF_8).size > InvioRunner.MAX_PAYLOAD_BYTES) {
                            Costruito.Ferma(DirettaMotivo.FILE_TROPPO_GRANDE)
                        } else {
                            Costruito.Pronto(json)
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Al massimo una richiesta al secondo. */
        const val MIN_INTERVAL_MS = 1_000L
        const val RETRY_BASE_MS = 5_000L
        const val RETRY_LENTO_MS = 30_000L
        private const val MAX_SHIFT = 4
        private const val MAX_INATTESE = 5

        /** I tetti del server: nomi, punti (200 KB sono `InvioRunner.MAX_PAYLOAD_BYTES`). */
        const val MAX_NOME = 60
        const val MAX_PUNTI = 3_000
        private const val ANTEPRIMA_NOME = 24
    }
}
