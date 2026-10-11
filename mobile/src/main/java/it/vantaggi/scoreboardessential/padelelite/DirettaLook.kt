package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import it.vantaggi.scoreboardessential.R

/** Come la diretta si dice nel foglio: la parola, l'icona e il colore dell'icona. Mai il solo colore. */
data class DirettaLook(
    val text: String,
    @DrawableRes val icon: Int,
    @ColorRes val tint: Int,
)

/** Null quando non c'e' niente da dire (nessuna diretta). */
fun direttaLook(
    context: Context,
    stato: DirettaStato,
): DirettaLook? =
    when (stato.fase) {
        DirettaFase.SPENTA -> {
            null
        }

        DirettaFase.IN_CORSO -> {
            DirettaLook(context.getString(R.string.diretta_live, stato.gruppo), R.drawable.ic_sensors, R.color.elite_lime)
        }

        DirettaFase.IN_ATTESA -> {
            DirettaLook(attesa(context, stato), R.drawable.ic_schedule, R.color.elite_warning)
        }

        DirettaFase.FERMA -> {
            DirettaLook(ferma(context, stato), R.drawable.ic_cancel, R.color.elite_error)
        }

        DirettaFase.CHIUSA -> {
            DirettaLook(context.getString(R.string.diretta_closed), R.drawable.ic_check_circle, R.color.elite_text_secondary)
        }
    }

private fun attesa(
    context: Context,
    stato: DirettaStato,
): String =
    when (stato.motivo) {
        DirettaMotivo.ACCEDI_DI_NUOVO -> context.getString(R.string.diretta_wait_login)
        DirettaMotivo.SERVONO_QUATTRO -> context.getString(R.string.diretta_wait_players)
        DirettaMotivo.NOME_TROPPO_LUNGO -> context.getString(R.string.diretta_wait_name, stato.dettaglio.orEmpty())
        DirettaMotivo.TROPPE_DIRETTE -> context.getString(R.string.diretta_wait_full)
        DirettaMotivo.CODICE_ESAURITO -> context.getString(R.string.diretta_wait_code)
        DirettaMotivo.RISPOSTA_INATTESA -> context.getString(R.string.diretta_wait_server)
        DirettaMotivo.ERRORE_INATTESO -> context.getString(R.string.diretta_wait_error, stato.dettaglio.orEmpty())
        DirettaMotivo.GIOCATORE_SOSTITUITO -> context.getString(R.string.diretta_wait_replaced)
        else -> context.getString(R.string.diretta_wait_network)
    }

private fun ferma(
    context: Context,
    stato: DirettaStato,
): String =
    when (stato.motivo) {
        DirettaMotivo.NON_ADMIN -> {
            context.getString(R.string.diretta_stop_not_admin)
        }

        DirettaMotivo.FILE_NON_VALIDO -> {
            if (stato.dettaglio != null) {
                context.getString(R.string.diretta_stop_invalid_detail, stato.dettaglio)
            } else {
                context.getString(R.string.diretta_stop_invalid)
            }
        }

        DirettaMotivo.FILE_TROPPO_GRANDE -> {
            context.getString(R.string.diretta_stop_large)
        }

        DirettaMotivo.TROPPI_PUNTI -> {
            context.getString(R.string.diretta_stop_points)
        }

        DirettaMotivo.ALTRO_ADMIN -> {
            context.getString(R.string.diretta_stop_other_admin)
        }

        DirettaMotivo.GIA_CHIUSA -> {
            context.getString(R.string.diretta_stop_already_closed)
        }

        else -> {
            context.getString(R.string.diretta_stop_server)
        }
    }
