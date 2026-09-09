package it.vantaggi.scoreboardessential.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import it.vantaggi.scoreboardessential.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Motivi per cui una partita non si puo' esportare. Stanno qui, e non presso chi preme il
 * pulsante, perche' l'export e' uno solo e il messaggio deve essere sempre lo stesso.
 */
enum class ExportBlocked {
    NO_MATCH,
    NEEDS_FOUR,
    NEEDS_LINK,
}

object MatchExportUtils {
    // Data prima del nome: il file finisce nei download di qualcuno e ci resta, in ordine
    private const val FILE_NAME_STAMP = "yyyy-MM-dd_HHmm"
    private const val MAX_SLUG_LENGTH = 40

    /**
     * Scrive il JSON gia' pronto in un file e apre il selettore di condivisione.
     *
     * [context] deve essere quello di un'Activity: il selettore parte dal task in corso.
     * [matchName] finisce nel nome del file, quindi vi si includa lo sport.
     */
    fun shareMatchJson(
        context: Context,
        json: String,
        matchName: String,
    ) {
        val stamp = SimpleDateFormat(FILE_NAME_STAMP, Locale.US).format(Date())
        val file = File(context.cacheDir, "${stamp}_${slug(matchName)}.json")
        file.writeText(json)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.export_match)))
        Toast.makeText(context, R.string.export_ready, Toast.LENGTH_SHORT).show()
    }

    /** [unlinkedNames] serve solo a [ExportBlocked.NEEDS_LINK]: dice chi non e' ancora collegato. */
    fun showBlocked(
        context: Context,
        reason: ExportBlocked,
        unlinkedNames: String = "",
    ) {
        val message =
            when (reason) {
                ExportBlocked.NO_MATCH -> context.getString(R.string.export_needs_match)
                ExportBlocked.NEEDS_FOUR -> context.getString(R.string.export_needs_four)
                ExportBlocked.NEEDS_LINK -> context.getString(R.string.export_needs_link, unlinkedNames)
            }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun slug(matchName: String): String =
        matchName
            .lowercase(Locale.US)
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
            .take(MAX_SLUG_LENGTH)
            .ifEmpty { "match" }
}
