package it.vantaggi.scoreboardessential.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import it.vantaggi.scoreboardessential.MainActivity
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication

/**
 * Il telefono resta vivo, e aggiornato, mentre c'e' una partita: anche in tasca.
 *
 * Il punto segnato sull'orologio arriva al telefono come messaggio, e il punteggio nuovo torna all'orologio solo
 * quando il motore (il MainViewModel) lo ha applicato e ripubblicato. Un processo senza servizio in primo piano
 * e' "in cache" per il sistema: Samsung lo congela a schermo spento, e il motore smette di rispondere finche'
 * l'app non si riapre. Un servizio in primo piano lo toglie dalla cache: resta un processo vivo, col motore dentro.
 *
 * Non fa nulla da solo: la sua utilita' e' esistere. La notifica e' la contropartita obbligatoria, fissa e muta;
 * non porta il punteggio perche' il punteggio e' nell'app, a un tocco, e una notifica che non si aggiorna
 * sarebbe un numero sbagliato proprio dove ci si fida.
 */
class PartitaInCorsoServizio : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (!entraInPrimoPiano(costruisciNotifica())) stopSelf()
        // Non e' STICKY apposta: un servizio risorto da solo, senza il motore e senza nessuno che sappia se la
        // partita c'e' ancora, lascerebbe una notifica per una partita che nessuno gioca piu'.
        return START_NOT_STICKY
    }

    /** Il sistema puo' negare `startForeground` (app in secondo piano): niente crash, il servizio rinuncia. */
    internal fun entraInPrimoPiano(notifica: Notification): Boolean =
        try {
            ServiceCompat.startForeground(this, ID_NOTIFICA, notifica, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) e' una sottoclasse di IllegalStateException.
            Log.w(TAG, "startForeground negato: la partita prosegue senza servizio", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "startForeground rifiutato: la partita prosegue senza servizio", e)
            false
        }

    internal fun costruisciNotifica(): Notification {
        val tocco =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat
            .Builder(this, ScoreboardEssentialApplication.CHANNEL_ID_PARTITA)
            .setSmallIcon(R.drawable.notifica_partita)
            .setContentTitle(getString(R.string.partita_in_corso_title))
            .setContentText(getString(R.string.partita_in_corso_text))
            .setContentIntent(tocco)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }

    companion object {
        private const val TAG = "PartitaInCorsoServizio"

        /** 1 e' il cronometro, 2 l'allarme del portiere, 3 la diretta, 4 il suo avviso. */
        const val ID_NOTIFICA = 5

        /** Come si fa partire il servizio. Iniettabile: sotto test non c'e' un'app in primo piano da cui partire. */
        internal var avvia: (Context, Intent) -> Unit = { context, intent ->
            ContextCompat.startForegroundService(context, intent)
        }

        /** Come si ferma. Iniettabile come [avvia]. */
        internal var ferma: (Context, Intent) -> Unit = { context, intent -> context.stopService(intent) }

        /** Vero dall'ultimo avvio riuscito: i cambi del registro sono tanti, il servizio si chiede una volta. */
        @Volatile
        private var acceso = false

        /** Solo per i test: il processo di prova e' uno solo, e il valore ricordato non deve passare da un test all'altro. */
        internal fun dimentica() {
            acceso = false
        }

        /**
         * Allinea il servizio a "c'e' una partita in corso". Fermarlo si puo' sempre; avviarlo il sistema lo consente
         * solo a un'app visibile: da background lancia un'eccezione che qui si inghiotte, e `acceso` resta falso, cosi'
         * la richiesta successiva (la schermata che torna visibile) ci riprova.
         */
        fun imposta(
            context: Context,
            attiva: Boolean,
        ) {
            val intent = Intent(context, PartitaInCorsoServizio::class.java)
            if (!attiva) {
                if (acceso) {
                    ferma(context, intent)
                    acceso = false
                }
                return
            }
            if (acceso) return
            try {
                avvia(context, intent)
                acceso = true
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: app giudicata in background. La partita funziona come prima.
                Log.w(TAG, "Servizio in primo piano non avviabile", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Servizio in primo piano non consentito", e)
            }
        }
    }
}
