package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Il servizio in primo piano della diretta (D-3), detto con una parola: il foglio PARTITA lo mostra quando manca. */
enum class DirettaServizioFase {
    /** Nessuna diretta accesa, nessun servizio. */
    SPENTO,

    /** Il servizio c'e' (o sta partendo): la notifica fissa tiene viva la diretta anche con l'app in secondo piano. */
    ATTIVO,

    /** Il sistema ha negato l'avvio in primo piano (l'app era in secondo piano, per esempio al ripristino): la diretta va avanti senza servizio. */
    NON_AVVIATO,

    /** Android 15 ha fermato il servizio `dataSync` dopo 6 ore in un giorno: la diretta va avanti finche' il processo vive. */
    SCADUTO,
}

/** Chi avvia il servizio vero: un'interfaccia perche' i test provano il controllo senza Android. */
interface DirettaAvviatore {
    /** False se il sistema nega l'avvio (app in secondo piano). Non lancia mai. */
    fun avvia(): Boolean

    // Niente `ferma()`: dall'esterno il servizio non si ferma mai. `stopService()` prima che il servizio abbia chiamato
    // `startForeground()` fa cadere l'app ("startForegroundService() did not then call startForeground()"): si ferma
    // da solo, in `DirettaService`, dopo essere entrato in primo piano.
}

/**
 * Tiene il servizio in primo piano accordato alla diretta (D-3): lo avvia quando la diretta si accende o riprende.
 * Guarda solo lo stato della [Diretta]: non ha logica sua. **Non lo ferma mai**: il servizio si ferma da solo
 * (DirettaService, appena la diretta non e' piu' accesa: fine partita, scarto, cambio sport, errore definitivo,
 * interruttore, notifica), perche' uno `stopService` prima di `startForeground` fa cadere l'app. Qui si tiene
 * solo la fase detta nel foglio.
 *
 * Una diretta che passa da "in corso" a "in attesa" non riavvia niente; dopo un avvio negato o un tetto delle 6 ore
 * il servizio non si ritenta fino alla diretta dopo.
 */
class DirettaServizioControllo(
    private val stato: StateFlow<DirettaStato>,
    private val avviatore: DirettaAvviatore,
    scope: CoroutineScope,
    /** L'avviso ordinario dopo il tetto delle 6 ore (debito 15): lo mostra [scaduto], lo toglie la fine della diretta. */
    private val avviso: DirettaAvviso = NessunAvviso,
) {
    private val lock = Any()
    private var richiesto = false
    private val _fase = MutableStateFlow(DirettaServizioFase.SPENTO)

    val fase: StateFlow<DirettaServizioFase> = _fase

    init {
        scope.launch { stato.collect { sulloStato(it) } }
    }

    private fun sulloStato(stato: DirettaStato) {
        val serve = stato.fase == DirettaFase.IN_CORSO || stato.fase == DirettaFase.IN_ATTESA
        synchronized(lock) {
            if (serve && !richiesto) {
                richiesto = true
                // Un avviso della diretta di prima non vale per questa.
                avviso.togli()
                _fase.value = if (avviatore.avvia()) DirettaServizioFase.ATTIVO else DirettaServizioFase.NON_AVVIATO
            } else if (!serve && richiesto) {
                // Non si ferma il servizio da qui (vedi DirettaAvviatore): lo vede da solo e se ne va.
                richiesto = false
                avviso.togli()
                _fase.value = DirettaServizioFase.SPENTO
            }
        }
    }

    /** Il servizio e' partito ma `startForeground` e' stato negato: la diretta prosegue senza. */
    fun avvioNegato() {
        synchronized(lock) { if (richiesto) _fase.value = DirettaServizioFase.NON_AVVIATO }
    }

    /** Il sistema ha chiamato `onTimeout` (tetto delle 6 ore di `dataSync`): il servizio si ferma, la diretta no. */
    fun scaduto() {
        synchronized(lock) {
            if (!richiesto) return
            _fase.value = DirettaServizioFase.SCADUTO
            // Niente piu' notifica fissa: un avviso ordinario dice che la diretta va avanti solo con l'app aperta. Sotto lo
            // stesso blocco di `sulloStato`, cosi' una diretta appena finita non lascia un avviso che nessuno toglie.
            avviso.mostra(stato.value.gruppo)
        }
    }
}

/**
 * L'avviatore vero: `startForegroundService` verso [DirettaService]. Un avvio negato (app in secondo piano:
 * `ForegroundServiceStartNotAllowedException`, sottoclasse di `IllegalStateException`, catturata cosi' perche' la
 * classe non esiste sotto API 31) o un permesso che manca non buttano giu' il processo: si dice "non avviato".
 */
class DirettaServiceAvviatore(
    private val context: Context,
    private val avvio: (Intent) -> Unit = { ContextCompat.startForegroundService(context, it) },
) : DirettaAvviatore {
    private fun intent() = Intent(context, DirettaService::class.java)

    override fun avvia(): Boolean =
        try {
            avvio(intent())
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "avvio in primo piano negato: la diretta prosegue senza servizio", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "avvio in primo piano rifiutato: la diretta prosegue senza servizio", e)
            false
        }

    private companion object {
        const val TAG = "DirettaServizio"
    }
}
