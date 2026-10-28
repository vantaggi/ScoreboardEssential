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

/**
 * Null quando non c'e' niente da dire (nessuna diretta). Una riga sola: [chiusura] (D-3) sostituisce "Diretta chiusa"
 * mentre il lavoro di chiusura ritenta o dopo che ha rinunciato; [servizio] sostituisce "In diretta su" quando la
 * notifica fissa non c'e' (avvio negato o tetto delle 6 ore), perche' l'utente deve sapere di tenere l'app aperta.
 */
fun direttaLook(
    context: Context,
    stato: DirettaStato,
    chiusura: ChiusuraFase = ChiusuraFase.NESSUNA,
    servizio: DirettaServizioFase = DirettaServizioFase.SPENTO,
): DirettaLook? =
    when (stato.fase) {
        DirettaFase.SPENTA -> {
            null
        }

        DirettaFase.IN_CORSO -> {
            val testo =
                when (servizio) {
                    DirettaServizioFase.NON_AVVIATO -> context.getString(R.string.diretta_live_no_service, stato.gruppo)
                    DirettaServizioFase.SCADUTO -> context.getString(R.string.diretta_live_service_timeout, stato.gruppo)
                    else -> context.getString(R.string.diretta_live, stato.gruppo)
                }
            DirettaLook(testo, R.drawable.ic_sensors, R.color.elite_accent)
        }

        DirettaFase.IN_ATTESA -> {
            DirettaLook(testoDiAttesa(context, stato), R.drawable.ic_schedule, R.color.elite_warning)
        }

        DirettaFase.FERMA -> {
            DirettaLook(ferma(context, stato), R.drawable.ic_cancel, R.color.elite_error)
        }

        DirettaFase.CHIUSA -> {
            when (chiusura) {
                ChiusuraFase.IN_CORSO -> {
                    DirettaLook(context.getString(R.string.diretta_closing), R.drawable.ic_schedule, R.color.elite_text_secondary)
                }

                ChiusuraFase.FALLITA -> {
                    DirettaLook(context.getString(R.string.diretta_close_failed), R.drawable.ic_warning, R.color.elite_warning)
                }

                ChiusuraFase.NESSUNA -> {
                    DirettaLook(context.getString(R.string.diretta_closed), R.drawable.ic_check_circle, R.color.elite_text_secondary)
                }
            }
        }
    }

/** Perche' la diretta e' accesa ma non trasmette, in una frase: la riga del foglio PARTITA. */
internal fun testoDiAttesa(
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

/**
 * Lo stesso perche', breve, per la notifica fissa (debito 14): «Diretta in attesa: <motivo>». Il foglio PARTITA tiene la
 * frase lunga di [testoDiAttesa]; i motivi sono gli stessi.
 */
internal fun testoDiAttesaInNotifica(
    context: Context,
    stato: DirettaStato,
): String {
    val motivo =
        when (stato.motivo) {
            DirettaMotivo.ACCEDI_DI_NUOVO -> R.string.diretta_notif_why_login
            DirettaMotivo.SERVONO_QUATTRO -> R.string.diretta_notif_why_players
            DirettaMotivo.NOME_TROPPO_LUNGO -> R.string.diretta_notif_why_name
            DirettaMotivo.TROPPE_DIRETTE -> R.string.diretta_notif_why_full
            DirettaMotivo.CODICE_ESAURITO -> R.string.diretta_notif_why_code
            DirettaMotivo.RISPOSTA_INATTESA -> R.string.diretta_notif_why_server
            DirettaMotivo.ERRORE_INATTESO -> R.string.diretta_notif_why_error
            DirettaMotivo.GIOCATORE_SOSTITUITO -> R.string.diretta_notif_why_replaced
            else -> R.string.diretta_notif_why_network
        }
    return context.getString(R.string.diretta_notif_waiting, context.getString(motivo))
}

private fun ferma(
    context: Context,
    stato: DirettaStato,
): String =
    when (stato.motivo) {
        DirettaMotivo.NON_ADMIN -> {
            context.getString(R.string.diretta_stop_not_admin)
        }

        DirettaMotivo.FUORI_DAL_GRUPPO -> {
            context.getString(R.string.diretta_stop_left_group)
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

        DirettaMotivo.ERRORE_GRAVE -> {
            context.getString(R.string.diretta_stop_crash, stato.dettaglio.orEmpty())
        }

        else -> {
            context.getString(R.string.diretta_stop_server)
        }
    }
