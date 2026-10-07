package it.vantaggi.scoreboardessential.utils

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.pdf.PdfDocument
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import it.vantaggi.scoreboardessential.domain.models.MatchReportData
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

object MatchReportUtils {
    /**
     * Scrive il PDF e restituisce l'Intent per condividerlo, oppure null se il file non si e'
     * potuto scrivere: condividere comunque manderebbe un PDF troncato, o quello precedente.
     *
     * [attributesScorer] viene dalle capacita' dello sport: dove non si chiede chi ha segnato
     * (padel) formazioni e tabellino non hanno niente di vero da mostrare e si nascondono.
     */
    fun generateAndGetShareIntent(
        context: Context,
        data: MatchReportData,
        attributesScorer: Boolean,
    ): Intent? {
        val view = buildReportView(context, data, attributesScorer)

        // PDF Generation
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val measureWidth = View.MeasureSpec.makeMeasureSpec(pageInfo.pageWidth, View.MeasureSpec.EXACTLY)
        val measureHeight = View.MeasureSpec.makeMeasureSpec(pageInfo.pageHeight, View.MeasureSpec.UNSPECIFIED)
        view.measure(measureWidth, measureHeight)
        view.layout(0, 0, pageInfo.pageWidth, view.measuredHeight)

        view.draw(canvas)
        pdfDocument.finishPage(page)

        val pdfFile = File(context.cacheDir, "match_report.pdf")
        try {
            return shareIntentFor(context, data, pdfFile) { pdfDocument.writeTo(it) }
        } finally {
            pdfDocument.close()
        }
    }

    /**
     * Scrive [pdfFile] con [write] e prepara l'Intent di condivisione. Lo stream si chiude
     * sempre; se la scrittura fallisce si restituisce null invece di condividere il file.
     */
    internal fun shareIntentFor(
        context: Context,
        data: MatchReportData,
        pdfFile: File,
        write: (OutputStream) -> Unit,
    ): Intent? {
        try {
            FileOutputStream(pdfFile).use(write)
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }

        val pdfUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile)

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, "Match Report: ${data.team1Name} vs ${data.team2Name}")
            val shareText = "Ecco il report del match tra ${data.team1Name} e ${data.team2Name}."
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
    }

    /**
     * Marcature per giocatore. Contano solo quelle attribuite: [MatchEvent.player] da solo non
     * basta, perche' quando il marcatore manca contiene il nome della squadra.
     */
    internal fun countScorers(events: List<MatchEvent>): Map<String, Int> {
        val scorers = mutableMapOf<String, Int>()
        events.forEach { event ->
            if (event.type == MatchEventType.SCORE && event.playerId != null && event.player != null) {
                scorers[event.player] = (scorers[event.player] ?: 0) + 1
            }
        }
        return scorers
    }

    /**
     * Il contesto con cui si gonfia la pagina: tema dell'app, densita' 160 e carattere al 100%. La
     * pagina A4 e' larga 595 punti e `view.draw` disegna un pixel per punto: con la densita' del
     * telefono (2,6 su un Pixel) 16sp diventavano 42 punti e la pagina conteneva venti caratteri per
     * riga. A densita' 1 un dp e' un punto, i corpi sono quelli della scala (16sp = 16pt), e il
     * carattere ingrandito del telefono non cambia un foglio che si stampa.
     */
    private fun contestoDellaPagina(context: Context): Context {
        val configurazione =
            Configuration(context.resources.configuration).apply {
                densityDpi = DisplayMetrics.DENSITY_MEDIUM
                fontScale = 1f
            }
        return ContextThemeWrapper(context.createConfigurationContext(configurazione), R.style.Theme_ScoreboardEssential)
    }

    /**
     * Una riga di testo di un gruppo: Inter 400 a 14sp con cifre tabulari, spaziata dal suo vicino.
     * Il colore e' sempre quello dell'inchiostro della carta, mai quello della squadra.
     */
    private fun riga(
        context: Context,
        testo: String,
        colore: Int,
    ): TextView =
        TextView(context).apply {
            text = testo
            setTextAppearance(R.style.TextAppearance_App_BodyMedium)
            setTextColor(colore)
            fontFeatureSettings = CIFRE_TABULARI
            val spazio = resources.getDimensionPixelSize(R.dimen.space_4)
            setPadding(0, spazio, 0, spazio)
        }

    /** I nomi di una squadra, uno per riga; senza nessuno, lo stato vuoto in testo secondario. */
    private fun riempiLaColonna(
        context: Context,
        colonna: LinearLayout,
        nomi: List<String>,
    ) {
        if (nomi.isEmpty()) {
            colonna.addView(riga(context, context.getString(R.string.report_pdf_no_players), context.getColor(R.color.print_text_secondary)))
            return
        }
        nomi.forEach { colonna.addView(riga(context, it, context.getColor(R.color.print_text_primary))) }
    }

    /** La pagina del report, gia' riempita ma non ancora misurata ne' disegnata. */
    internal fun buildReportView(
        context: Context,
        data: MatchReportData,
        attributesScorer: Boolean,
    ): View {
        val pagina = contestoDellaPagina(context)
        val inflater = pagina.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.pdf_match_report, null)

        // Get Views
        val team1NameTextView = view.findViewById<TextView>(R.id.pdf_team1_name)
        val team1ScoreTextView = view.findViewById<TextView>(R.id.pdf_team1_score)
        val team2NameTextView = view.findViewById<TextView>(R.id.pdf_team2_name)
        val team2ScoreTextView = view.findViewById<TextView>(R.id.pdf_team2_score)
        val team1PlayersTitle = view.findViewById<TextView>(R.id.pdf_team1_players_title)
        val team2PlayersTitle = view.findViewById<TextView>(R.id.pdf_team2_players_title)
        val team1PlayersList = view.findViewById<LinearLayout>(R.id.pdf_team1_players_list)
        val team2PlayersList = view.findViewById<LinearLayout>(R.id.pdf_team2_players_list)
        val scorersList = view.findViewById<LinearLayout>(R.id.pdf_scorers_list)

        // Set Header Info
        team1NameTextView.text = data.team1Name
        team1ScoreTextView.text = data.team1Score.toString()
        team2NameTextView.text = data.team2Name
        team2ScoreTextView.text = data.team2Score.toString()
        team1PlayersTitle.text = data.team1Name
        team2PlayersTitle.text = data.team2Name

        // Il colore di squadra e' solo la barretta accanto al nome, come nella Cronaca: il colore con
        // cui si e' giocato (lime e ciano se la squadra non l'ha cambiato), scurito da TeamInk fino a
        // 3:1 sul gruppo perche' la pagina e' chiara. Il testo resta nell'inchiostro della carta, quindi
        // non dipende dal colore scelto. Senza colore si ripiega sui predefiniti dei lati.
        val gruppo = pagina.getColor(R.color.print_surface)
        val colore1 = data.team1Color ?: pagina.getColor(R.color.team_side_1)
        val colore2 = data.team2Color ?: pagina.getColor(R.color.team_side_2)
        val primario = pagina.getColor(R.color.print_text_primary)
        val secondario = pagina.getColor(R.color.print_text_secondary)
        team1NameTextView.etichettaConBarretta(colore1, gruppo, primario, sfondoChiaro = true)
        team2NameTextView.etichettaConBarretta(colore2, gruppo, primario, sfondoChiaro = true)
        team1PlayersTitle.etichettaConBarretta(colore1, gruppo, secondario, sfondoChiaro = true)
        team2PlayersTitle.etichettaConBarretta(colore2, gruppo, secondario, sfondoChiaro = true)

        // Populate Formations
        riempiLaColonna(pagina, team1PlayersList, data.team1Players.map { it.player.playerName })
        riempiLaColonna(pagina, team2PlayersList, data.team2Players.map { it.player.playerName })

        if (!attributesScorer) {
            view.findViewById<View>(R.id.pdf_formations_title).visibility = View.GONE
            view.findViewById<View>(R.id.pdf_formations).visibility = View.GONE
            view.findViewById<View>(R.id.pdf_scorers_title).visibility = View.GONE
            scorersList.visibility = View.GONE
            return view
        }

        // Populate Scorers
        val scorers = countScorers(data.matchEvents)

        if (scorers.isNotEmpty()) {
            val sb = StringBuilder()
            scorers.forEach { (playerName, goalCount) ->
                sb.setLength(0)
                sb
                    .append(playerName)
                    .append(" (")
                    .append(goalCount)
                    .append(")")
                scorersList.addView(riga(pagina, sb.toString(), primario))
            }
        } else {
            scorersList.addView(riga(pagina, pagina.getString(R.string.report_pdf_no_scorers), secondario))
        }

        return view
    }

    private const val CIFRE_TABULARI = "tnum"
}
