package it.vantaggi.scoreboardessential.ui

import android.content.Context
import it.vantaggi.scoreboardessential.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Una riga della lista dello Storico: il titolo di un giorno o una partita di quel giorno. */
sealed interface RigaDelloStorico {
    data class Giorno(
        val giorno: LocalDate,
    ) : RigaDelloStorico

    data class Partita(
        val stato: MatchHistoryUiState,
    ) : RigaDelloStorico
}

/**
 * Lo Storico per giorno (revisione dei menu, passo 5b): le partite si ritrovano dal giorno in cui
 * si sono giocate, e la scheda tiene solo l'ora. Le partite arrivano dalla piu' recente
 * (ORDER BY timestamp DESC): un titolo prima della prima partita di ogni giorno.
 */
object GiorniDelloStorico {
    fun dividi(
        partite: List<MatchHistoryUiState>,
        zona: ZoneId = ZoneId.systemDefault(),
    ): List<RigaDelloStorico> {
        val righe = ArrayList<RigaDelloStorico>(partite.size + 4)
        var ultimo: LocalDate? = null
        partite.forEach { partita ->
            val giorno = Instant.ofEpochMilli(partita.matchWithTeams.match.timestamp).atZone(zona).toLocalDate()
            if (giorno != ultimo) {
                righe += RigaDelloStorico.Giorno(giorno)
                ultimo = giorno
            }
            righe += RigaDelloStorico.Partita(partita)
        }
        return righe
    }

    /** «Oggi», «Ieri», «Sabato 12 settembre»; l'anno solo se non e' quello in corso. */
    fun etichetta(
        context: Context,
        giorno: LocalDate,
        oggi: LocalDate = LocalDate.now(),
    ): String =
        when (giorno) {
            oggi -> {
                context.getString(R.string.history_today)
            }

            oggi.minusDays(1) -> {
                context.getString(R.string.history_yesterday)
            }

            else -> {
                val lingua = context.resources.configuration.locales[0]
                val schema = if (giorno.year == oggi.year) "EEEE d MMMM" else "EEEE d MMMM yyyy"
                DateTimeFormatter.ofPattern(schema, lingua).format(giorno).replaceFirstChar { it.titlecase(lingua) }
            }
        }
}
