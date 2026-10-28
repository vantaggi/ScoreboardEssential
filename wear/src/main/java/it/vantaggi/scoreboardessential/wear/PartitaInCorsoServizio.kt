package it.vantaggi.scoreboardessential.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

/**
 * Il tabellone resta raggiungibile dal quadrante mentre si gioca.
 *
 * Wear OS non tiene un'app a schermo: allo spegnimento (timeout, o fine dell'always-on) la
 * attivita' va in background e al risveglio il sistema mostra il quadrante. L'app non e' chiusa, ma
 * per riaprirla serve cercarla. Una Ongoing Activity e' il rimedio del sistema: una notifica in
 * corso con un'icona sul quadrante che, toccata, riporta qui, e l'app in cima ai recenti.
 *
 * Sta in un servizio in primo piano e non in una notifica sola perche' una notifica senza servizio
 * resta sul quadrante anche dopo la morte del processo, e perche' il servizio tiene vivo il
 * processo: lo stato in memoria (e gli ack in attesa, L5) non va perso con lo schermo spento.
 *
 * Il testo e' fisso ("Partita in corso") e non porta il punteggio: a schermo spento l'attivita' non
 * ascolta piu' il telefono, e un punteggio sul quadrante fermo all'ultima accensione sarebbe un
 * numero sbagliato proprio dove si guarda per fidarsi. Il punteggio e' nell'app, a un tocco.
 */
class PartitaInCorsoServizio : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Sistema ripartito senza l'attivita': nessuno ha detto che la partita c'e' ancora.
        // Non e' STICKY apposta: un servizio resuscitato da solo lascerebbe l'icona sul quadrante
        // per una partita che nessuno gioca piu'.
        startForegroundConNotifica()
        return START_NOT_STICKY
    }

    private fun startForegroundConNotifica() {
        creaCanale(this)
        val tocco =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notifica =
            NotificationCompat
                .Builder(this, CANALE)
                .setSmallIcon(R.drawable.notifica_partita)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.wear_ongoing_status))
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(tocco)
        OngoingActivity
            .Builder(applicationContext, ID_NOTIFICA, notifica)
            .setStaticIcon(R.drawable.notifica_partita)
            .setTouchIntent(tocco)
            .setStatus(Status.forPart(Status.TextPart(getString(R.string.wear_ongoing_status))))
            .build()
            .apply(applicationContext)
        ServiceCompat.startForeground(
            this,
            ID_NOTIFICA,
            notifica.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    companion object {
        private const val TAG = "PartitaInCorsoServizio"
        internal const val CANALE = "partita_in_corso"
        internal const val ID_NOTIFICA = 1

        /**
         * Allinea il servizio a "c'e' una partita in corso". Si chiama dall'attivita': il sistema
         * consente di far partire un servizio in primo piano solo da un'app visibile, quindi
         * [attiva] vale solo a schermo acceso, e la chiamata a schermo spento non parte mai (al
         * prossimo onResume l'attivita' lo richiede). Fermarlo si puo' sempre.
         */
        fun imposta(
            context: Context,
            attiva: Boolean,
            visibile: Boolean,
        ) {
            val intent = Intent(context, PartitaInCorsoServizio::class.java)
            if (!attiva) {
                context.stopService(intent)
                return
            }
            if (!visibile) return
            try {
                context.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: il sistema ha giudicato l'app in
                // background. Niente icona sul quadrante, ma il tabellone funziona come prima.
                Log.w(TAG, "Servizio in primo piano non avviabile", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Servizio in primo piano non consentito", e)
            }
        }

        private fun creaCanale(context: Context) {
            val gestore = context.getSystemService(NotificationManager::class.java)
            // Importanza minima che il sistema accetta per una notifica in corso: niente suono,
            // niente vibrazione, niente schermo che si accende per una notifica che non e' nuova.
            gestore.createNotificationChannel(
                NotificationChannel(
                    CANALE,
                    context.getString(R.string.wear_ongoing_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }
}
