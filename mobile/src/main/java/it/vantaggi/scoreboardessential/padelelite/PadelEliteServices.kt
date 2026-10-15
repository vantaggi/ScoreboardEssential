package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import android.util.Log
import androidx.work.await
import it.vantaggi.scoreboardessential.core.ExportResult
import it.vantaggi.scoreboardessential.core.MatchExporter
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.repository.MatchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicBoolean

/** Cosa succede al comando "Invia a Padel Elite". */
enum class SendOutcome {
    /** Funzione spenta (nessuna configurazione): il comando non dovrebbe nemmeno esserci. */
    DISABLED,

    /** Senza accesso: la schermata di accesso. */
    NEED_LOGIN,

    /** Accesso fatto ma nessun gruppo scelto: la schermata del gruppo. */
    NEED_GROUP,

    /** In coda: parte da sola. */
    QUEUED,

    /** Voce in attesa di cui non si conosce il gruppo (stato di una versione precedente): non si rimanda. */
    NOT_RESENDABLE,
}

/**
 * Tutto cio' che serve per l'invio, in un posto solo: configurazione, account, stati, coda.
 * Lo tiene l'applicazione; con la funzione spenta non apre rete, preferenze ne' Keystore (le
 * parti pigre non si toccano finche' nessuno le chiede).
 */
class PadelEliteServices(
    private val context: Context,
    val config: PadelEliteConfig,
    private val repository: MatchRepository,
    accountFactory: () -> PadelEliteAccount = {
        PadelEliteAccount(config, PadelEliteApi(config), SessionStore.create(context))
    },
    invioStoreFactory: () -> InvioStore = {
        InvioStore(context.getSharedPreferences(InvioStore.FILE, Context.MODE_PRIVATE))
    },
    direttaStoreFactory: () -> DirettaStore = {
        DirettaStore(context.getSharedPreferences(DirettaStore.FILE, Context.MODE_PRIVATE))
    },
    /** Dove vive la diretta: lo scope dell'applicazione (sopravvive a rotazione e a un ViewModel ricreato). I test ne danno uno loro. */
    private val direttaScope: CoroutineScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                // Rete di sicurezza: la diretta gia' prende le sue eccezioni (aspetta e riprova), ma una che sfuggisse
                // non deve buttare giu' l'app a meta' partita.
                CoroutineExceptionHandler { _, errore -> Log.e("Diretta", "eccezione non gestita nella diretta", errore) },
        ),
    private val direttaMinIntervalMs: Long = Diretta.MIN_INTERVAL_MS,
    /** Chi avvia il servizio in primo piano della diretta (D-3): i test ne danno uno finto. */
    private val direttaAvviatore: DirettaAvviatore = DirettaServiceAvviatore(context),
    /** L'avviso ordinario dopo il tetto delle 6 ore del servizio (debito 15): i test ne danno uno finto. */
    private val direttaAvviso: DirettaAvviso = DirettaAvvisoNotifica(context),
) {
    /** La funzione e' accesa solo con la configurazione completa. */
    val isEnabled: Boolean get() = config.isConfigured

    val account: PadelEliteAccount by lazy(accountFactory)
    val invii: InvioStore by lazy(invioStoreFactory)

    val runner: InvioRunner by lazy {
        InvioRunner(account, invii) { uuid, groupId ->
            // Una lettura sola dei collegamenti: il file e la sua firma non possono divergere.
            val salvato = repository.buildSavedExportByUuidWithLinks(uuid, groupId)
            when (val esito = salvato?.result) {
                null -> {
                    PayloadOutcome.Missing
                }

                is ExportResult.Incomplete -> {
                    PayloadOutcome.Incomplete
                }

                is ExportResult.Ready -> {
                    // Senza matchId il server rifiuta il file: meglio dirlo qui che dopo la rete.
                    if (esito.export.matchId.isNullOrEmpty()) {
                        PayloadOutcome.Incomplete
                    } else {
                        PayloadOutcome.Json(
                            MatchExporter.toJson(esito.export),
                            FirmaCollegamenti.of(salvato?.links.orEmpty(), groupId, esito.export.players.map { it.localId }),
                        )
                    }
                }
            }
        }
    }

    /**
     * La diretta (D-2). Pigra: con la funzione spenta o senza ruolo non si crea e non apre rete. La chiusura passa
     * da WorkManager (unica per partita); le pubblicazioni dei punti no, sono un flusso vivo legato alla partita.
     */
    val diretta: Diretta get() = direttaEServizio.first

    /**
     * Il servizio in primo piano della diretta (D-3), nato insieme alla diretta: guarda il suo stato e lo avvia e lo
     * ferma. Nato qui e non a parte perche' deve esserci dalla prima accensione, anche se nessuno lo chiede.
     */
    val direttaServizio: DirettaServizioControllo get() = direttaEServizio.second

    private val direttaEServizio: Pair<Diretta, DirettaServizioControllo> by lazy {
        val d =
            Diretta(
                account,
                direttaStoreFactory(),
                direttaScope,
                collegamenti = { repository.padelLinkMap(it) },
                // Ritorna quando il lavoro e' scritto in WorkManager: solo allora il segno della chiusura in sospeso si toglie.
                allaChiusura = { DirettaChiusuraWorker.enqueue(context, it).await() },
                minIntervalMs = direttaMinIntervalMs,
            )
        d to DirettaServizioControllo(d.stato, direttaAvviatore, direttaScope, direttaAvviso)
    }

    /** Come sta la chiusura finale della diretta di [matchId] (lavoro in coda): "in corso" mentre ritenta, "fallita" se rinuncia. */
    fun chiusuraDellaDiretta(matchId: String): Flow<ChiusuraFase> = DirettaChiusuraWorker.fase(context, matchId)

    private val ruoloLetto = AtomicBoolean(false)

    /**
     * Rilegge il ruolo nel gruppo scelto, UNA volta per avvio dell'app (D-3). Chi aveva scelto il gruppo prima di D-2 non ha
     * il ruolo salvato e non vedrebbe l'interruttore; un admin declassato lo vedrebbe ancora. Una richiesta sola, nessun errore
     * mostrato se fallisce (rete, sessione scaduta, qualunque eccezione): resta l'ultimo ruolo letto, e il server comanda
     * comunque (`not_authorized`). Senza configurazione, accesso o gruppo scelto non fa niente, ne' consuma la lettura.
     * Se la lettura riesce e il gruppo scelto non c'e' piu' nell'elenco (l'account e' stato tolto dal gruppo, debito 16) il gruppo
     * scelto si dimentica e la diretta verso di esso si spegne ([Diretta.fuoriDalGruppo]); un errore di rete non vale come "tolto".
     * True se il ruolo salvato e' cambiato o il gruppo e' stato dimenticato (l'interfaccia puo' ridisegnarsi).
     */
    suspend fun aggiornaIlRuolo(): Boolean {
        if (!isEnabled || account.session() == null) return false
        val scelto = account.selectedGroup()?.first ?: return false
        if (!ruoloLetto.compareAndSet(false, true)) return false
        val prima = account.selectedGroupRole()
        return try {
            val esito = account.groups()
            if (esito !is GroupsOutcome.Ok) return false
            val gruppo = esito.groups.firstOrNull { it.id == scelto }
            // Nel frattempo l'utente puo' aver scelto un altro gruppo, o essere uscito: il risultato vecchio non si scrive.
            if (account.session() == null || account.selectedGroup()?.first != scelto) return false
            if (gruppo == null) {
                // Una lettura riuscita che non lo trova piu' nell'elenco: e' stato tolto dal gruppo (un errore di rete non arriva
                // fin qui, e' `GroupsOutcome.Network`). Il gruppo scelto si dimentica, come per la sessione persa, e la diretta
                // verso quel gruppo si spegne col suo motivo invece di aspettare che il server dica `not_authorized`.
                account.clearSelectedGroup()
                diretta.fuoriDalGruppo(scelto)
                return true
            }
            account.selectGroup(gruppo)
            gruppo.role != prima
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    val chiusuraDiretta: DirettaChiusuraRunner by lazy {
        DirettaChiusuraRunner(account) { matchId, groupId ->
            // La riga viva (partita non ancora salvata, registro a meta') non e' il file finale: si aspetta closeMatch.
            val salvato = if (repository.isClosedByUuid(matchId)) repository.buildSavedExportByUuidWithLinks(matchId, groupId) else null
            when (val esito = salvato?.result) {
                null -> {
                    PayloadOutcome.Missing
                }

                is ExportResult.Incomplete -> {
                    PayloadOutcome.Incomplete
                }

                // Anche una partita interrotta senza vincitore porta il suo file (winnerTeam nullo): il server lo accetta.
                is ExportResult.Ready -> {
                    PayloadOutcome.Json(MatchExporter.toJson(esito.export))
                }
            }
        }
    }

    /**
     * "Trasmetti in diretta" c'e' solo con la funzione accesa, l'accesso fatto, un gruppo scelto di cui si e'
     * **owner o admin** (lo dice l'ultimo ruolo letto: il server comanda comunque, `not_authorized`) e lo
     * sport padel. In tutti gli altri casi l'app e' identica a oggi: nessun comando, nessuna richiesta.
     */
    fun puoTrasmettere(sportId: String): Boolean =
        isEnabled &&
            sportId == SportRegistry.PADEL &&
            account.session() != null &&
            account.selectedGroup() != null &&
            account.selectedGroupRole() in RUOLI_CHE_TRASMETTONO

    /**
     * Il comando dall'interfaccia: mette in coda la partita, oppure, se manca l'accesso o il gruppo,
     * apre la schermata di Padel Elite. Lo stesso per la card dello storico e il dialogo di fine partita.
     */
    fun sendOrOpenLogin(
        activity: Context,
        matchUuid: String,
    ) {
        when (send(matchUuid)) {
            SendOutcome.NEED_LOGIN, SendOutcome.NEED_GROUP -> activity.startActivity(PadelEliteActivity.intent(activity))
            SendOutcome.QUEUED, SendOutcome.DISABLED, SendOutcome.NOT_RESENDABLE -> Unit
        }
    }

    /**
     * Il comando: mette in coda la partita o dice dove andare per poterlo fare.
     *
     * Per una partita gia' nella casella e ancora in attesa e' "Invia di nuovo": va **al gruppo della
     * voce** ([InvioInfo.group]), non a quello scelto adesso (un gruppo cambiato creerebbe un doppione
     * in un'altra casella), e il lavoro rifa' il file coi collegamenti di quel gruppo. Se della voce non
     * si conosce il gruppo (stato di una versione precedente) non si rimanda: [SendOutcome.NOT_RESENDABLE].
     */
    fun send(matchUuid: String): SendOutcome {
        if (!isEnabled) return SendOutcome.DISABLED
        if (account.session() == null) return SendOutcome.NEED_LOGIN
        val voce = invii.get(matchUuid)?.takeIf { it.hasEntryToResend }
        if (voce != null) {
            val gruppoDellaVoce = voce.group ?: return SendOutcome.NOT_RESENDABLE
            InvioWorker.enqueue(context, invii, matchUuid, gruppoDellaVoce)
            return SendOutcome.QUEUED
        }
        val gruppo = account.selectedGroup() ?: return SendOutcome.NEED_GROUP
        InvioWorker.enqueue(context, invii, matchUuid, gruppo.first)
        return SendOutcome.QUEUED
    }

    private companion object {
        val RUOLI_CHE_TRASMETTONO = setOf("owner", "admin")
    }

    /** Con l'accesso fatto "Invia di nuovo" puo' partire (va al gruppo della voce, non serve un gruppo scelto); senza, la card non lo offre. */
    fun hasAccess(): Boolean = isEnabled && account.session() != null
}
