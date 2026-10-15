package it.vantaggi.scoreboardessential.padelelite

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication

/**
 * L'avviso che resta quando il servizio in primo piano non c'e' piu' ma la diretta continua (D-3, debito 15): dopo il tetto delle
 * 6 ore di `dataSync` (Android 15) la notifica fissa sparisce e la diretta continua solo finche' l'app vive. Il foglio PARTITA
 * lo dice; questo lo dice anche fuori dall'app. Un'interfaccia perche' [DirettaServizioControllo], che sa quando mostrarlo e
 * quando toglierlo, si prova senza Android.
 */
interface DirettaAvviso {
    /** Mostra l'avviso per la diretta verso [gruppo]. Non lancia mai. */
    fun mostra(gruppo: String)

    /** Lo toglie (la diretta e' finita, o il servizio e' ripartito). Non fa niente se non c'e'. */
    fun togli()
}

/** Nessun avviso: i test del controllo che non lo guardano. */
object NessunAvviso : DirettaAvviso {
    override fun mostra(gruppo: String) = Unit

    override fun togli() = Unit
}

/**
 * L'avviso vero: una notifica **ordinaria** (non fissa, si puo' scorrere via, tocco = apre il foglio PARTITA) sullo stesso
 * canale a importanza bassa della notifica fissa, quindi senza suono ne' vibrazione. Il testo dice la condizione («la diretta
 * continua solo con l'app aperta»), non lo stato: se il processo muore l'avviso resta nel cassetto fino al prossimo avvio
 * ([ScoreboardEssentialApplication.onCreate] lo toglie), e fino ad allora non deve dire una cosa falsa.
 */
class DirettaAvvisoNotifica(
    private val context: Context,
) : DirettaAvviso {
    override fun mostra(gruppo: String) {
        // Senza il permesso l'avviso e' sospeso dal sistema: non serve costruirlo.
        if (!puoNotificare()) return
        val testo = context.getString(R.string.diretta_avviso_text)
        val notifica =
            NotificationCompat
                .Builder(context, ScoreboardEssentialApplication.CHANNEL_ID_DIRETTA)
                .setSmallIcon(R.drawable.notifica_sensors)
                .setContentTitle(context.getString(R.string.diretta_avviso_title, gruppo).trim())
                .setContentText(testo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        DirettaAzione.APRI.ordinal,
                        DirettaIntent.per(context, DirettaAzione.APRI),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_AVVISO, notifica)
        } catch (e: SecurityException) {
            // Il permesso e' stato tolto fra il controllo e la notifica: l'avviso e' un di piu', la diretta non ne dipende.
        }
    }

    override fun togli() {
        NotificationManagerCompat.from(context).cancel(ID_AVVISO)
    }

    private fun puoNotificare(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** Distinto da quello della notifica fissa ([DirettaService.ID_NOTIFICA]): le due non si sostituiscono. */
        const val ID_AVVISO = 4
    }
}
