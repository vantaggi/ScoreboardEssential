package it.vantaggi.scoreboardessential.utils

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import it.vantaggi.scoreboardessential.domain.models.MatchReportData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Il report PDF: nel padel non inventa un tabellino con le squadre al posto dei marcatori,
 * e se il file non si scrive non lo condivide lo stesso.
 *
 * La scrittura si prova con [MatchReportUtils.shareIntentFor] e uno scrittore finto: sotto
 * Robolectric PdfDocument nasce gia' chiuso, anche con la grafica NATIVE.
 */
@RunWith(RobolectricTestRunner::class)
class MatchReportUtilsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_ScoreboardEssential)
    }

    /** Un punto senza marcatore: come lo scrive addScorer, con il nome della squadra in player. */
    private fun puntoDiSquadra(squadra: String) =
        MatchEvent(timestamp = "10:00", event = "GOL", team = 1, player = squadra, type = MatchEventType.SCORE)

    private fun golDi(
        nome: String,
        id: Int,
    ) = MatchEvent(timestamp = "10:00", event = "GOL", team = 1, player = nome, type = MatchEventType.SCORE, playerId = id)

    private fun report(eventi: List<MatchEvent>) =
        MatchReportData(
            team1Name = "SQUADRA1",
            team1Score = 3,
            team1Color = null,
            team1Players = emptyList(),
            team2Name = "SQUADRA2",
            team2Score = 0,
            team2Color = null,
            team2Players = emptyList(),
            matchEvents = eventi,
        )

    private fun testi(lista: LinearLayout) = (0 until lista.childCount).map { (lista.getChildAt(it) as TextView).text.toString() }

    @Test
    fun `nel tabellino contano solo le marcature attribuite`() {
        val eventi = listOf(puntoDiSquadra("SQUADRA1"), golDi("Marco", 3), golDi("Marco", 3))

        assertEquals(mapOf("Marco" to 2), MatchReportUtils.countScorers(eventi))
    }

    @Test
    fun `un gol non attribuito non finisce nel tabellino sotto il nome della squadra`() {
        val view = MatchReportUtils.buildReportView(context, report(listOf(puntoDiSquadra("SQUADRA1"))), attributesScorer = true)

        val tabellino = view.findViewById<LinearLayout>(R.id.pdf_scorers_list)
        assertEquals(listOf(context.getString(R.string.report_pdf_no_scorers)), testi(tabellino))
    }

    @Test
    fun `nel padel formazioni e tabellino non compaiono`() {
        val punti = List(3) { puntoDiSquadra("SQUADRA1") }
        val view = MatchReportUtils.buildReportView(context, report(punti), attributesScorer = false)

        listOf(R.id.pdf_formations_title, R.id.pdf_formations, R.id.pdf_scorers_title, R.id.pdf_scorers_list).forEach {
            assertEquals(context.resources.getResourceEntryName(it), View.GONE, view.findViewById<View>(it).visibility)
        }
    }

    @Test
    fun `i titoli del report vengono dalle risorse`() {
        val view = MatchReportUtils.buildReportView(context, report(emptyList()), attributesScorer = true)

        assertEquals(
            context.getString(R.string.report_pdf_scorers_title),
            view.findViewById<TextView>(R.id.pdf_scorers_title).text.toString(),
        )
        assertEquals(
            context.getString(R.string.label_formations),
            view.findViewById<TextView>(R.id.pdf_formations_title).text.toString(),
        )
    }

    private val pdf get() = File(context.cacheDir, "match_report.pdf")

    @Test
    fun `se il PDF si scrive si condivide`() {
        // FileProvider confronta i percorsi cercando '/': su un host Windows non trova mai la
        // radice della cache e lancia. Il caso si prova solo dove il separatore e' quello Android.
        assumeTrue(File.separatorChar == '/')

        val intent = MatchReportUtils.shareIntentFor(context, report(emptyList()), pdf) { it.write(byteArrayOf(1, 2, 3)) }

        assertNotNull(intent)
        assertEquals(3L, pdf.length())
    }

    @Test
    fun `se la scrittura si interrompe non si condivide niente`() {
        // Come con il disco pieno: una parte del file e' scritta, poi arriva l'errore.
        val intent =
            MatchReportUtils.shareIntentFor(context, report(emptyList()), pdf) {
                it.write(byteArrayOf(1))
                throw IOException("disco pieno")
            }

        assertNull(intent)
    }

    @Test
    fun `se il file non si apre non si condivide niente`() {
        // Una cartella al posto del file: FileOutputStream non riesce nemmeno ad aprirlo.
        pdf.mkdirs()

        assertNull(MatchReportUtils.shareIntentFor(context, report(emptyList()), pdf) { it.write(1) })
    }

    @Test
    fun `lo stream del PDF viene chiuso`() {
        // Anche quando la scrittura fallisce: e' il caso in cui prima il descrittore restava aperto.
        var stream: OutputStream? = null
        MatchReportUtils.shareIntentFor(context, report(emptyList()), pdf) {
            stream = it
            throw IOException("disco pieno")
        }

        // Su un FileOutputStream chiuso ogni scrittura lancia.
        assertThrows(IOException::class.java) { stream!!.write(1) }
    }
}
