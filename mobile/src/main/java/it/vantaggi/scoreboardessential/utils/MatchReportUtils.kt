package it.vantaggi.scoreboardessential.utils

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.core.TeamInk
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

    /** La banda nel colore della squadra, con i testi sopra nell'inchiostro di [TeamInk]. */
    private fun dipingiBanda(
        context: Context,
        banda: View,
        colore: Int,
        vararg testi: TextView,
    ) {
        banda.riempiDiSquadra(colore, context.getColor(R.color.asphalt_dark))
        testi.forEach { it.setTextColor(TeamInk.on(colore)) }
    }

    /** La pagina del report, gia' riempita ma non ancora misurata ne' disegnata. */
    internal fun buildReportView(
        context: Context,
        data: MatchReportData,
        attributesScorer: Boolean,
    ): View {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.pdf_match_report, null)

        // Get Views
        val team1NameTextView = view.findViewById<TextView>(R.id.pdf_team1_name)
        val team1ScoreTextView = view.findViewById<TextView>(R.id.pdf_team1_score)
        val team2NameTextView = view.findViewById<TextView>(R.id.pdf_team2_name)
        val team2ScoreTextView = view.findViewById<TextView>(R.id.pdf_team2_score)
        val team1PlayersList = view.findViewById<LinearLayout>(R.id.pdf_team1_players_list)
        val team2PlayersList = view.findViewById<LinearLayout>(R.id.pdf_team2_players_list)
        val scorersList = view.findViewById<LinearLayout>(R.id.pdf_scorers_list)

        // Set Header Info
        team1NameTextView.text = data.team1Name
        team1ScoreTextView.text = data.team1Score.toString()
        team2NameTextView.text = data.team2Name
        team2ScoreTextView.text = data.team2Score.toString()

        // Due bande, una per squadra nel suo colore: nome e punteggio in TeamInk. Il colore grezzo
        // come testo sul fondo scuro del PDF non si leggeva, e il PDF e' l'unica cosa che esce dal
        // telefono. Senza colore si ripiega sui predefiniti (giallo e verde).
        dipingiBanda(
            context,
            view.findViewById(R.id.pdf_team1_band),
            data.team1Color ?: context.getColor(R.color.team_side_1),
            team1NameTextView,
            team1ScoreTextView,
        )
        dipingiBanda(
            context,
            view.findViewById(R.id.pdf_team2_band),
            data.team2Color ?: context.getColor(R.color.team_side_2),
            team2NameTextView,
            team2ScoreTextView,
        )

        // Populate Formations
        data.team1Players.forEach { player ->
            val playerTextView =
                TextView(context).apply {
                    text = player.player.playerName
                    setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                    setTextColor(ContextCompat.getColor(context, R.color.stencil_white))
                    setPadding(0, 4, 0, 4)
                }
            team1PlayersList.addView(playerTextView)
        }

        data.team2Players.forEach { player ->
            val playerTextView =
                TextView(context).apply {
                    text = player.player.playerName
                    setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                    setTextColor(ContextCompat.getColor(context, R.color.stencil_white))
                    setPadding(0, 4, 0, 4)
                }
            team2PlayersList.addView(playerTextView)
        }

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
                val scorerTextView =
                    TextView(context).apply {
                        text = sb.toString()
                        setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                        setTextColor(ContextCompat.getColor(context, R.color.stencil_white))
                        setPadding(0, 4, 0, 4)
                    }
                scorersList.addView(scorerTextView)
            }
        } else {
            val noScorersTextView =
                TextView(context).apply {
                    text = context.getString(R.string.report_pdf_no_scorers)
                    setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                    setTextColor(ContextCompat.getColor(context, R.color.sidewalk_gray))
                    setPadding(0, 4, 0, 4)
                }
            scorersList.addView(noScorersTextView)
        }

        return view
    }
}
