package it.vantaggi.scoreboardessential.padelelite

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Il servizio in primo piano della diretta (D-3, tipo `dataSync`): una notifica fissa, «In diretta su <gruppo>» col
 * punteggio, che dice al sistema che l'app sta trasmettendo e lo convince a non fermare il processo con l'app in
 * secondo piano.
 *
 * **Non contiene la logica della diretta.** La [Diretta] vive nello scope dell'applicazione e resta dov'e';
 * il servizio la tiene viva (esistendo) e ne mostra lo stato. Parte e si ferma da [DirettaServizioControllo], che
 * guarda lo stato della diretta in un punto solo; qui c'e' solo la cintura: se la diretta non e' piu' accesa
 * (o non c'e' proprio) il servizio si ferma da se'.
 *
 * **Separato dal cronometro** ([it.vantaggi.scoreboardessential.service.MatchTimerService], `specialUse`): un tipo
 * e una notifica ciascuno, e nel padel il cronometro non parte.
 *
 * **Android 15.** `dataSync` ha un tetto di 6 ore al giorno: il sistema chiama [onTimeout] e il servizio DEVE
 * fermarsi. La diretta no: continua finche' il processo vive, e lo stato del servizio lo dice nel foglio.
 *
 * **Notifica.** Canale a importanza bassa (niente suono ne' vibrazione), aggiornata al piu' una volta al secondo:
 * il punteggio cambia al ritmo della pubblicazione, che e' una al secondo, e il testo uguale non si riscrive.
 */
open class DirettaService : Service() {
    /** I test mettono qui un servizio di prova; in produzione lo da' l'applicazione. */
    internal var servizi: PadelEliteServices? = null

    /** Lo scope del servizio: i test ne danno uno a tempo virtuale. */
    internal var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var aggiornamento: Job? = null
    private var ultimaChiave: Pair<String, String>? = null

    private fun servizi(): PadelEliteServices = servizi ?: (applicationContext as ScoreboardEssentialApplication).padelElite

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val s = servizi()
        val diretta = s.diretta
        val stato = diretta.stato.value
        val punteggio = diretta.punteggio.value
        // Chi ha chiamato startForegroundService() ha cinque secondi per arrivare a startForeground(): prima di tutto.
        if (!entraInPrimoPiano(costruisciNotifica(stato, punteggio))) {
            s.direttaServizio.avvioNegato()
            stopSelf()
            return START_NOT_STICKY
        }
        ultimaChiave = chiaveDi(stato, punteggio)
        if (!accesa(stato)) {
            // La diretta si e' chiusa fra la richiesta di avvio e adesso.
            esci()
            return START_NOT_STICKY
        }
        aggiornamento?.cancel()
        aggiornamento =
            scope.launch {
                combine(diretta.stato, diretta.punteggio) { st, pt -> st to pt }.conflate().collect { (st, pt) ->
                    if (!accesa(st)) {
                        esci()
                    } else {
                        aggiorna(st, pt)
                        delay(MIN_NOTIFICA_MS)
                    }
                }
            }
        // Se il sistema ferma il processo, la diretta e' morta con lui: al ripristino la riaccende l'app (e il suo servizio).
        return START_NOT_STICKY
    }

    /**
     * Il sistema ha esaurito le 6 ore al giorno del tipo `dataSync` (Android 15): fermarsi subito, o il processo cade
     * in ANR. La diretta continua; il foglio PARTITA dice che la notifica fissa non c'e' piu'.
     */
    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        Log.w(TAG, "tetto delle 6 ore di dataSync: il servizio si ferma, la diretta prosegue finche' il processo vive")
        servizi().direttaServizio.scaduto()
        esci()
    }

    override fun onDestroy() {
        aggiornamento?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    /** Il sistema puo' negare `startForeground` (app in secondo piano): niente crash, il servizio rinuncia. */
    internal open fun entraInPrimoPiano(notifica: Notification): Boolean =
        try {
            ServiceCompat.startForeground(this, ID_NOTIFICA, notifica, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) e' una sottoclasse di IllegalStateException.
            Log.w(TAG, "startForeground negato: la diretta prosegue senza servizio", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "startForeground rifiutato: la diretta prosegue senza servizio", e)
            false
        }

    private fun esci() {
        aggiornamento?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun accesa(stato: DirettaStato) = stato.fase == DirettaFase.IN_CORSO || stato.fase == DirettaFase.IN_ATTESA

    private fun chiaveDi(
        stato: DirettaStato,
        punteggio: String,
    ) = titolo(stato) to testo(stato, punteggio)

    private fun titolo(stato: DirettaStato) = getString(R.string.diretta_live, stato.gruppo).trim()

    /** Il punteggio, o il motivo per cui non si trasmette: la stessa frase del foglio PARTITA. */
    private fun testo(
        stato: DirettaStato,
        punteggio: String,
    ) = if (stato.fase == DirettaFase.IN_ATTESA) testoDiAttesa(this, stato) else punteggio

    private fun aggiorna(
        stato: DirettaStato,
        punteggio: String,
    ) {
        val chiave = chiaveDi(stato, punteggio)
        if (chiave == ultimaChiave) return
        ultimaChiave = chiave
        // Senza il permesso l'avviso e' sospeso dal sistema: riscriverlo non serve a niente.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(ID_NOTIFICA, costruisciNotifica(stato, punteggio))
    }

    internal fun costruisciNotifica(
        stato: DirettaStato,
        punteggio: String,
    ): Notification =
        NotificationCompat
            .Builder(this, ScoreboardEssentialApplication.CHANNEL_ID_DIRETTA)
            .setSmallIcon(R.drawable.notifica_sensors)
            .setContentTitle(titolo(stato))
            .setContentText(testo(stato, punteggio).ifEmpty { null })
            .setContentIntent(pendingIntentPer(DirettaAzione.APRI))
            .addAction(0, getString(R.string.diretta_notif_stop), pendingIntentPer(DirettaAzione.INTERROMPI))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun pendingIntentPer(azione: DirettaAzione): PendingIntent =
        PendingIntent.getActivity(
            this,
            azione.ordinal,
            DirettaIntent.per(this, azione),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val ID_NOTIFICA = 3
        private const val TAG = "DirettaService"

        /** Una notifica al secondo al piu': non c'e' motivo di scrivere piu' spesso di quanto la diretta pubblichi. */
        const val MIN_NOTIFICA_MS = 1_000L
    }
}
