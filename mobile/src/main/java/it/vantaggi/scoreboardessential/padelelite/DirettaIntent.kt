package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import android.content.Intent
import it.vantaggi.scoreboardessential.MainActivity

/** Cosa chiede la notifica fissa della diretta quando apre l'app. */
enum class DirettaAzione {
    /** Il tocco sulla notifica: l'app si apre sul foglio PARTITA, dove sta il gruppo Diretta. */
    APRI,

    /** «Interrompi diretta»: l'app si apre sul foglio e mostra la stessa conferma dell'interruttore. */
    INTERROMPI,
}

/**
 * Il passaggio fra la notifica e [MainActivity].
 *
 * **Perche' un intento verso la schermata di gioco e non un'activity di conferma trasparente.** Una notifica non
 * puo' mostrare un dialogo. Un'activity trasparente a parte avrebbe voluto un'altra voce nel manifest, un tema
 * suo e una seconda copia del dialogo; l'intento con un extra riusa il dialogo dell'interruttore (parole, bottoni,
 * "Continua" che lascia tutto com'e'), e porta l'utente dove la diretta si vede e si governa. Costo: l'app passa in
 * primo piano. Per uno stop definitivo e' un pregio, non un difetto: si vede il punteggio prima di chiudere.
 */
object DirettaIntent {
    private const val EXTRA = "it.vantaggi.scoreboardessential.DIRETTA_AZIONE"

    fun per(
        context: Context,
        azione: DirettaAzione,
    ): Intent =
        Intent(context, MainActivity::class.java)
            // L'azione rende i due PendingIntent distinti (gli extra non contano per l'uguaglianza fra intenti).
            .setAction("it.vantaggi.scoreboardessential.DIRETTA_${azione.name}")
            .putExtra(EXTRA, azione.name)
            // MainActivity e' standard: con SINGLE_TOP + CLEAR_TOP la schermata gia' aperta riceve onNewIntent invece di raddoppiare.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** L'azione chiesta da [intent], o null se non viene dalla notifica. */
    fun azioneDi(intent: Intent?): DirettaAzione? {
        val nome = intent?.getStringExtra(EXTRA) ?: return null
        return DirettaAzione.entries.firstOrNull { it.name == nome }
    }

    /** Toglie l'azione dall'intento: una rotazione ricrea l'activity con lo stesso intento e il dialogo non deve ricomparire. */
    fun consuma(intent: Intent?) {
        intent?.removeExtra(EXTRA)
    }
}
