package it.vantaggi.scoreboardessential.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.core.ExportOrigin
import it.vantaggi.scoreboardessential.core.ExportProblem
import it.vantaggi.scoreboardessential.core.ExportResult
import it.vantaggi.scoreboardessential.core.MatchEngine
import it.vantaggi.scoreboardessential.core.MatchExporter
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.MatchPlayer
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.Match
import java.io.File
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Motivi per cui una partita non si puo' esportare. Stanno qui, e non presso chi preme il
 * pulsante, perche' l'export e' uno solo e il messaggio deve essere sempre lo stesso.
 */
enum class ExportBlocked {
    NO_MATCH,
    NEEDS_FOUR,
}

object MatchExportUtils {
    // Data prima del nome: il file finisce nei download di qualcuno e ci resta, in ordine
    private const val FILE_NAME_STAMP = "yyyy-MM-dd_HHmm"
    private const val MAX_SLUG_LENGTH = 40

    /**
     * L'export di una partita gia' chiusa, ricostruito dallo storico.
     *
     * Il motore si rifa' dal registro salvato con lo sport e l'ordine di servizio della riga:
     * sono le stesse regole con cui la partita si e' giocata, perche' la configurazione di uno
     * sport sta nel registro di `:core` e dalla partita dipende solo l'ordine di servizio. Id e
     * inizio sono quelli salvati al primo punto, quindi il file coincide con quello che si
     * sarebbe esportato dal vivo.
     *
     * Un registro illeggibile vale come "niente da esportare": meglio nessun file che un file
     * con una partita ricostruita a meta'.
     */
    fun savedMatchExport(
        match: Match,
        lineup: List<MatchPlayer>,
        appVersion: String,
        zone: ZoneId,
    ): ExportResult {
        val engine = savedEngine(match) ?: return ExportResult.Incomplete(listOf(ExportProblem.NoPoints))
        return MatchExporter.build(engine, lineup, ExportOrigin(match.matchUuid, match.startedAt, zone, appVersion))
    }

    /**
     * Il motore di una partita dello storico, rifatto dal registro con lo sport e l'ordine di
     * servizio della riga. Lo usano l'export e la Cronaca: la stessa partita deve dare lo stesso
     * file e la stessa cronaca. Null se il registro non si legge.
     */
    fun savedEngine(match: Match): MatchEngine? {
        val registro = MatchLogCodec.decode(match.eventLog) ?: return null
        val engine = MatchEngine(SportRegistry.forMatch(match.sportId, Match.decodeServeOrder(match.serveOrder)))
        engine.restoreLog(registro)
        return engine
    }

    /**
     * Condivide il file se l'export e' pronto, altrimenti dice perche' no. Lo usano la partita in
     * corso e lo storico: stesso file, stessi messaggi.
     *
     * Del motivo se ne dice uno solo, il primo da risolvere: e' piu' utile che elencarli tutti a
     * chi e' in piedi a bordo campo.
     */
    fun shareExport(
        context: Context,
        result: ExportResult,
        matchName: String,
        atMillis: Long = System.currentTimeMillis(),
    ) {
        when (result) {
            is ExportResult.Ready -> {
                shareMatchJson(context, MatchExporter.toJson(result.export), matchName, atMillis)
            }

            is ExportResult.Incomplete -> {
                val motivo =
                    if (result.problems.any { it is ExportProblem.NoPoints }) ExportBlocked.NO_MATCH else ExportBlocked.NEEDS_FOUR
                showBlocked(context, motivo)
            }
        }
    }

    /**
     * Scrive il JSON gia' pronto in un file e apre il selettore di condivisione.
     *
     * [context] deve essere quello di un'Activity: il selettore parte dal task in corso.
     * [matchName] finisce nel nome del file, quindi vi si includa lo sport. [atMillis] e' la
     * data nel nome: dallo storico e' quella della partita, non quella dell'export.
     */
    fun shareMatchJson(
        context: Context,
        json: String,
        matchName: String,
        atMillis: Long = System.currentTimeMillis(),
    ) {
        val stamp = SimpleDateFormat(FILE_NAME_STAMP, Locale.US).format(Date(atMillis))
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

    /**
     * Condivide il riassunto come TESTO.
     *
     * text/plain e non un file: WhatsApp, Telegram e i messaggi lo incollano direttamente nella
     * conversazione, mentre un allegato costringerebbe chi lo riceve ad aprirlo. Il report esiste
     * per essere letto nel gruppo, non archiviato.
     */
    fun shareMatchText(
        context: Context,
        text: String,
    ) {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_choose_title)))
    }

    fun showBlocked(
        context: Context,
        reason: ExportBlocked,
    ) {
        val message =
            when (reason) {
                ExportBlocked.NO_MATCH -> context.getString(R.string.export_needs_match)
                ExportBlocked.NEEDS_FOUR -> context.getString(R.string.export_needs_four)
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
