package it.vantaggi.scoreboardessential.ui.chronicle

import android.content.res.Resources
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.core.KeyMoment
import it.vantaggi.scoreboardessential.core.TeamInk

/**
 * Le parole della Cronaca. `:core` consegna i momenti chiave come dati tipizzati e qui diventano
 * frasi, in values e values-it: le regole del padel non cambiano con la lingua, le frasi si'.
 *
 * Le frasi sono quelle della dashboard di Padel Elite (`riferimento-match-log.js`, `moments`):
 * ognuna nasce da un numero, niente aggettivi che il dato non giustifica. I set si contano da 1
 * nelle frasi e da 0 nei dati.
 */
object ChronicleText {
    private const val MS_PER_SECOND = 1000.0
    private const val SECONDS_PER_MINUTE = 60.0
    private const val MINUTES_PER_HOUR = 60L
    private const val GRAPHIC_MIN = 3.0

    /** [teams] sono i nomi dei due lati, lato 1 per primo. */
    fun moment(
        res: Resources,
        moment: KeyMoment,
        teams: List<String>,
    ): String {
        fun team(side: Int) = teams[side - 1]
        return when (moment) {
            is KeyMoment.MatchPointsSaved -> {
                res.getQuantityString(R.plurals.chronicle_moment_match_points, moment.count, team(moment.side), moment.count)
            }

            is KeyMoment.MatchTurnedAround -> {
                res.getString(R.string.chronicle_moment_match_turned, team(moment.side))
            }

            is KeyMoment.SetTurnedAround -> {
                moment.comeback.let {
                    res.getString(
                        R.string.chronicle_moment_set_comeback,
                        it.set + 1,
                        team(it.side),
                        it.from[0],
                        it.from[1],
                        it.to[0],
                        it.to[1],
                    )
                }
            }

            is KeyMoment.TieBreakWon -> {
                res.getString(R.string.chronicle_moment_tiebreak, moment.set + 1, team(moment.side), moment.score[0], moment.score[1])
            }

            is KeyMoment.LongStreak -> {
                moment.streak.let {
                    res.getQuantityString(
                        R.plurals.chronicle_moment_streak,
                        it.length,
                        it.length,
                        team(it.side),
                        it.set + 1,
                    )
                }
            }

            is KeyMoment.SetPointsSaved -> {
                res.getQuantityString(R.plurals.chronicle_moment_set_points, moment.count, team(moment.side), moment.count)
            }

            is KeyMoment.DecidingPoints -> {
                val leader = moment.leader
                if (leader == null) {
                    res.getString(R.string.chronicle_moment_deciding_even, moment.won[0], moment.played)
                } else {
                    res.getString(R.string.chronicle_moment_deciding_leader, team(leader), moment.won[leader - 1], moment.played)
                }
            }
        }
    }

    /** Il lato a cui appartiene il momento, per la barretta colorata; null se e' di tutti e due. */
    fun side(moment: KeyMoment): Int? =
        when (moment) {
            is KeyMoment.MatchPointsSaved -> moment.side
            is KeyMoment.MatchTurnedAround -> moment.side
            is KeyMoment.SetTurnedAround -> moment.comeback.side
            is KeyMoment.TieBreakWon -> moment.side
            is KeyMoment.LongStreak -> moment.streak.side
            is KeyMoment.SetPointsSaved -> moment.side
            is KeyMoment.DecidingPoints -> moment.leader
        }

    /**
     * "1 h 12 min", "38 min", "45 s", arrotondati come `formatDuration` della dashboard: la
     * stessa partita deve dare la stessa durata sui due lati.
     */
    fun duration(
        res: Resources,
        ms: Long?,
    ): String {
        if (ms == null) return res.getString(R.string.chronicle_no_value)
        val totalSec = Math.round(ms / MS_PER_SECOND)
        if (totalSec < SECONDS_PER_MINUTE) return res.getString(R.string.chronicle_duration_seconds, totalSec.toInt())
        val min = Math.round(totalSec / SECONDS_PER_MINUTE)
        if (min < MINUTES_PER_HOUR) return res.getString(R.string.chronicle_duration_minutes, min.toInt())
        return res.getString(R.string.chronicle_duration_hours, (min / MINUTES_PER_HOUR).toInt(), (min % MINUTES_PER_HOUR).toInt())
    }

    /**
     * Il colore di squadra come GRAFICA su un fondo scuro [background], con almeno 3:1 (WCAG
     * 1.4.11). [TeamInk.graphicOnBlack] misura contro il nero; il contrasto con un fondo piu'
     * chiaro del colore e' quello col nero diviso per il contrasto fondo/nero, quindi chiedere
     * contro il nero 3 volte quel rapporto garantisce 3:1 contro il fondo. Col 3 nudo il blu notte
     * #1A237E sulla card #1E1E1E si fermerebbe sotto 2,4:1.
     */
    fun graphicOn(
        paint: Int,
        background: Int,
    ): Int = TeamInk.graphicOnBlack(paint, GRAPHIC_MIN * TeamInk.contrast(background, TeamInk.NERO))
}
