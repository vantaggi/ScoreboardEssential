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
import it.vantaggi.scoreboardessential.domain.models.MatchReportData
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object MatchReportUtils {

    fun generateAndGetShareIntent(context: Context, data: MatchReportData): Intent {
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

        // Apply Dynamic Colors
        data.team1Color?.let {
            team1NameTextView.setTextColor(it)
            team1ScoreTextView.setTextColor(it)
        }
        data.team2Color?.let {
            team2NameTextView.setTextColor(it)
            team2ScoreTextView.setTextColor(it)
        }

        // Populate Formations
        data.team1Players.forEach { player ->
            val playerTextView = TextView(context).apply {
                text = player.player.playerName
                setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                setTextColor(ContextCompat.getColor(context, R.color.stencil_white))
                setPadding(0, 4, 0, 4)
            }
            team1PlayersList.addView(playerTextView)
        }

        data.team2Players.forEach { player ->
            val playerTextView = TextView(context).apply {
                text = player.player.playerName
                setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                setTextColor(ContextCompat.getColor(context, R.color.stencil_white))
                setPadding(0, 4, 0, 4)
            }
            team2PlayersList.addView(playerTextView)
        }

        // Populate Scorers
        val scorers = data.matchEvents
            .filter { it.event == "Goal" && it.player != null }
            .map { it.player!! }
            .groupingBy { it }
            .eachCount()

        if (scorers.isNotEmpty()) {
            scorers.forEach { (playerName, goalCount) ->
                val scorerTextView = TextView(context).apply {
                    text = "$playerName ($goalCount)"
                    setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                    setTextColor(ContextCompat.getColor(context, R.color.stencil_white))
                    setPadding(0, 4, 0, 4)
                }
                scorersList.addView(scorerTextView)
            }
        } else {
            val noScorersTextView = TextView(context).apply {
                text = "Nessun marcatore"
                setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                setTextColor(ContextCompat.getColor(context, R.color.sidewalk_gray))
                setPadding(0, 4, 0, 4)
            }
            scorersList.addView(noScorersTextView)
        }

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
            pdfDocument.writeTo(FileOutputStream(pdfFile))
        } catch (e: IOException) {
            e.printStackTrace()
        }
        pdfDocument.close()

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
}
