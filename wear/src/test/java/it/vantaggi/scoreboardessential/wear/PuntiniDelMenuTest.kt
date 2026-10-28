package it.vantaggi.scoreboardessential.wear

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * I tre puntini del menu stanno in basso, dove corre l'anello del portiere: sull'emulatore tondo
 * i due esterni toccavano il bordo interno dell'anello. Il test rifa' il conto sul disegno: l'anello
 * ha il raggio interno a 192/innerRadiusRatio dal centro; il glifo (24dp, puntini a +-8dp dal centro
 * e di 2dp di raggio) sta al centro della banda fra le due guide, alzato dal margine sotto.
 *
 * Gira dalla cartella del modulo (`wear/`).
 */
class PuntiniDelMenuTest {
    private val res = File("src/main/res")

    private fun guida(id: String): Double {
        val layout = File(res, "layout/activity_main.xml").readText()
        val blocco = layout.substringAfter("android:id=\"@+id/$id\"").substringBefore("/>")
        return Regex("""layout_constraintGuide_percent="([0-9.]+)"""").find(blocco)!!.groupValues[1].toDouble()
    }

    private fun dimenInDp(nome: String): Double {
        val testo = File(res, "values/dimens.xml").readText()
        return Regex("""name="$nome">([0-9.]+)dp""").find(testo)!!.groupValues[1].toDouble()
    }

    @Test
    fun `i puntini esterni stanno dentro l'anello del portiere con almeno 3dp d'aria`() {
        val quadrante = 192.0
        val rapporto =
            Regex("""innerRadiusRatio="([0-9.]+)"""")
                .find(File(res, "drawable/circular_progress_bar.xml").readText())!!
                .groupValues[1]
                .toDouble()
        val raggioInterno = quadrante / rapporto
        val alto = guida("guide_168") * quadrante
        val basso = guida("guide_188") * quadrante
        // Con il margine sotto il glifo si centra nella banda ridotta: il centro sale di meta' margine.
        val centroY = (alto + basso) / 2 - dimenInDp("face_menu_glyph_lift") / 2
        val distanzaDalCentro = centroY - quadrante / 2
        val puntinoEsterno = hypot(8.0, distanzaDalCentro) + 2.0
        assertTrue(
            "il puntino esterno arriva a %.1fdp dal centro, l'anello comincia a %.1fdp".format(puntinoEsterno, raggioInterno),
            puntinoEsterno <= raggioInterno - 3.0,
        )
    }
}
