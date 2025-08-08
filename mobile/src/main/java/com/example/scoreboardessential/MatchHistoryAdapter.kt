package com.example.scoreboardessential

import android.view.LayoutInflater
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scoreboardessential.database.Match
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date

import com.example.scoreboardessential.database.MatchWithTeams

class MatchHistoryAdapter : ListAdapter<MatchWithTeams, MatchHistoryAdapter.MatchViewHolder>(MatchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.match_item, parent, false)
        return MatchViewHolder(view)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        val match = getItem(position)
        holder.bind(match, this)
    }

    class MatchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val team1NameTextView: TextView = itemView.findViewById(R.id.team1_name_textview)
        private val team2NameTextView: TextView = itemView.findViewById(R.id.team2_name_textview)
        private val team1ScoreTextView: TextView = itemView.findViewById(R.id.team1_score_textview)
        private val team2ScoreTextView: TextView = itemView.findViewById(R.id.team2_score_textview)
        private val timestampTextView: TextView = itemView.findViewById(R.id.timestamp_textview)

        fun bind(matchWithTeams: MatchWithTeams, adapter: MatchHistoryAdapter) {
            team1NameTextView.text = matchWithTeams.team1.name
            team2NameTextView.text = matchWithTeams.team2.name
            team1ScoreTextView.text = matchWithTeams.match.team1Score.toString()
            team2ScoreTextView.text = matchWithTeams.match.team2Score.toString()
            timestampTextView.text = Date(matchWithTeams.match.timestamp).toString()

            itemView.findViewById<View>(R.id.download_pdf_button).setOnClickListener {
                adapter.generatePdf(itemView.context as Activity, itemView.context, matchWithTeams)
            }
        }
    }

    fun generatePdf(activity: Activity, context: Context, matchWithTeams: MatchWithTeams) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), WRITE_EXTERNAL_STORAGE_REQUEST_CODE)
                return
            }
        }

        val view = LayoutInflater.from(context).inflate(R.layout.pdf_scoreboard, null)
        view.findViewById<TextView>(R.id.pdf_team1_name).text = matchWithTeams.team1.name
        view.findViewById<TextView>(R.id.pdf_team2_name).text = matchWithTeams.team2.name
        view.findViewById<TextView>(R.id.pdf_team1_score).text = matchWithTeams.match.team1Score.toString()
        view.findViewById<TextView>(R.id.pdf_team2_score).text = matchWithTeams.match.team2Score.toString()
        view.findViewById<TextView>(R.id.pdf_date).text = Date(matchWithTeams.match.timestamp).toString()

        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.getRealMetrics(displayMetrics)
        } else {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(displayMetrics)
        }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(displayMetrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(displayMetrics.heightPixels, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(view.measuredWidth, view.measuredHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        view.draw(canvas)
        document.finishPage(page)

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, "scoreboard_${matchWithTeams.match.id}.pdf")
        try {
            val fos = FileOutputStream(file)
            document.writeTo(fos)
            document.close()
            fos.close()
            Log.d("PDF", "PDF saved to ${file.absolutePath}")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 101
    }
}

class MatchDiffCallback : DiffUtil.ItemCallback<MatchWithTeams>() {
    override fun areItemsTheSame(oldItem: MatchWithTeams, newItem: MatchWithTeams): Boolean {
        return oldItem.match.id == newItem.match.id
    }

    override fun areContentsTheSame(oldItem: MatchWithTeams, newItem: MatchWithTeams): Boolean {
        return oldItem == newItem
    }
}
