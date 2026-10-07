package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import it.vantaggi.scoreboardessential.R

/** Come un [InvioInfo] si dice sulla card: la parola, l'icona e il colore dell'icona. Mai il solo colore. */
data class InvioLook(
    val text: String,
    @DrawableRes val icon: Int,
    @ColorRes val tint: Int,
)

fun invioLook(
    context: Context,
    info: InvioInfo,
): InvioLook =
    when (info.state) {
        InvioState.QUEUED -> {
            InvioLook(
                context.getString(
                    if (info.reason ==
                        InvioReason.INBOX_FULL
                    ) {
                        R.string.padel_elite_status_queued_full
                    } else {
                        R.string.padel_elite_status_queued
                    },
                ),
                R.drawable.ic_schedule,
                R.color.elite_text_secondary,
            )
        }

        InvioState.SENT -> {
            InvioLook(context.getString(R.string.padel_elite_status_sent), R.drawable.ic_hourglass_empty, R.color.elite_text_secondary)
        }

        InvioState.IMPORTED -> {
            InvioLook(context.getString(R.string.padel_elite_status_imported), R.drawable.ic_check_circle, R.color.elite_lime)
        }

        InvioState.DISCARDED -> {
            InvioLook(context.getString(R.string.padel_elite_status_discarded), R.drawable.ic_warning, R.color.elite_warning)
        }

        InvioState.LOGIN_AGAIN -> {
            InvioLook(context.getString(R.string.padel_elite_status_login_again), R.drawable.ic_lock, R.color.elite_warning)
        }

        InvioState.UNSENDABLE -> {
            InvioLook(unsendableText(context, info), R.drawable.ic_cancel, R.color.elite_error)
        }
    }

private fun unsendableText(
    context: Context,
    info: InvioInfo,
): String =
    when (info.reason) {
        InvioReason.INVALID_PAYLOAD -> {
            if (info.detail != null) {
                context.getString(R.string.padel_elite_status_unsendable_invalid, info.detail)
            } else {
                context.getString(R.string.padel_elite_status_unsendable_invalid_plain)
            }
        }

        InvioReason.TOO_LARGE -> {
            context.getString(R.string.padel_elite_status_unsendable_too_large)
        }

        InvioReason.NOT_MEMBER -> {
            context.getString(R.string.padel_elite_status_unsendable_not_member)
        }

        InvioReason.SERVER -> {
            context.getString(R.string.padel_elite_status_unsendable_server)
        }

        InvioReason.NO_FILE, InvioReason.INBOX_FULL, null -> {
            context.getString(R.string.padel_elite_status_unsendable_no_file)
        }
    }
