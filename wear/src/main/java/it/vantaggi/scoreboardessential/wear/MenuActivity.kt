package it.vantaggi.scoreboardessential.wear

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView

/**
 * Il menu partita: lo sport e la fine della partita, al posto dei due bottoni sempre visibili.
 *
 * Come [SportSelectionActivity], non sa niente della partita: riceve i fatti dalla schermata
 * principale ([InputMenu]) e restituisce solo CHE COSA si e' scelto. Le azioni vere restano di
 * [MainActivity], che ha il ViewModel: un ViewModel creato qui avrebbe un suo contatore di
 * sequenza, e due contatori sullo stesso nodo sono il modo in cui un messaggio nuovo viene
 * scartato come "gia' visto".
 *
 * Quali voci ci sono e quali sono spente lo decide [MenuVoci]. La fine partita non chiede conferma
 * con un dialogo: la card si riempie di rosso e il secondo tocco, dentro la finestra di
 * [ConfermaSulPosto], chiude. Se nessuno tocca niente, il menu si chiude da solo dopo 10 secondi.
 */
class MenuActivity : ComponentActivity() {
    companion object {
        const val EXTRA_AZIONE = "menu_azione"
        const val AZIONE_SPORT = "sport"
        const val AZIONE_FINE = "fine"

        /** Senza nessun tocco ne' scorrimento la schermata torna al quadrante da sola. */
        const val CHIUSURA_AUTOMATICA_MS = 10_000L

        private const val EXTRA_IN_CODA = "menu_in_coda"
        private const val EXTRA_COLLEGATO = "menu_collegato"
        private const val EXTRA_PARTITA_INIZIATA = "menu_partita_iniziata"
        private const val EXTRA_CALCIO_V2 = "menu_calcio_v2"
        private const val EXTRA_ELENCO_SPORT = "menu_elenco_sport"
        private const val EXTRA_SPORT = "menu_sport"
        private const val EXTRA_RISULTATO = "menu_risultato"

        /**
         * L'istante con cui si misura la finestra della conferma. Iniettabile come
         * [PlayerSelectionActivity.creaSync]: un test sposta un numero invece di aspettare.
         * I rientri della card e la chiusura automatica passano dal looper, che i test spostano
         * a parte.
         */
        internal var orologio: () -> Long = SystemClock::uptimeMillis

        fun intent(
            context: Context,
            input: InputMenu,
        ): Intent =
            Intent(context, MenuActivity::class.java)
                .putExtra(EXTRA_IN_CODA, input.inCoda)
                .putExtra(EXTRA_COLLEGATO, input.collegato)
                .putExtra(EXTRA_PARTITA_INIZIATA, input.partitaIniziata)
                .putExtra(EXTRA_CALCIO_V2, input.calcioConV2)
                .putExtra(EXTRA_ELENCO_SPORT, input.haElencoSport)
                .putExtra(EXTRA_SPORT, input.sport)
                .putExtra(EXTRA_RISULTATO, input.risultato)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val conferma = ConfermaSulPosto()
    private lateinit var input: InputMenu
    private val righe = mutableListOf<Riga>()

    private class Riga(
        val voce: Voce,
        val card: MaterialCardView,
    ) {
        val titolo: TextView = card.findViewById(R.id.sport_name)
        val sottotitolo: TextView = card.findViewById(R.id.sport_current)
    }

    /** Dopo 5 secondi la card torna com'era, e il prossimo tocco e' di nuovo il primo. */
    private val rientro =
        Runnable {
            conferma.disarma()
            disegna()
        }

    private val chiusuraAutomatica =
        Runnable {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        input =
            InputMenu(
                inCoda = intent.getIntExtra(EXTRA_IN_CODA, 0),
                collegato = intent.getBooleanExtra(EXTRA_COLLEGATO, false),
                partitaIniziata = intent.getBooleanExtra(EXTRA_PARTITA_INIZIATA, false),
                calcioConV2 = intent.getBooleanExtra(EXTRA_CALCIO_V2, false),
                haElencoSport = intent.getBooleanExtra(EXTRA_ELENCO_SPORT, false),
                sport = intent.getStringExtra(EXTRA_SPORT).orEmpty(),
                risultato = intent.getStringExtra(EXTRA_RISULTATO).orEmpty(),
            )

        val contenitore = findViewById<LinearLayout>(R.id.menu_voci)
        MenuVoci.calcola(input).forEach { voce ->
            val card = LayoutInflater.from(this).inflate(R.layout.item_sport_wear, contenitore, false) as MaterialCardView
            card.setOnClickListener { alTocco(voce) }
            contenitore.addView(card)
            righe += Riga(voce, card)
        }
        disegna()

        // Anche scorrere con la corona e' un'interazione: non deve chiudere il menu sotto le dita.
        val scorrimento = findViewById<ScrollView>(R.id.menu_scroll)
        scorrimento.setOnScrollChangeListener { _, _, _, _, _ -> riarmaChiusura() }
        // Il focus serve alla corona: senza, ruotarla non scorre niente.
        scorrimento.requestFocus()
        riarmaChiusura()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        riarmaChiusura()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun riarmaChiusura() {
        handler.removeCallbacks(chiusuraAutomatica)
        handler.postDelayed(chiusuraAutomatica, CHIUSURA_AUTOMATICA_MS)
    }

    private fun alTocco(voce: Voce) {
        // Una voce spenta non fa niente e non dice niente: perche' sia spenta lo dice il suo
        // sottotitolo, e un Toast sul tondo era proprio cio' che il design toglie.
        if (!voce.attiva) return
        when (voce.id) {
            IdVoce.SPORT -> chiudiConAzione(AZIONE_SPORT)
            IdVoce.FINE_PARTITA ->
                when (conferma.tocca(orologio())) {
                    ConfermaSulPosto.Esito.ARMATA -> {
                        handler.removeCallbacks(rientro)
                        handler.postDelayed(rientro, ConfermaSulPosto.SCADENZA_MS)
                        disegna()
                    }

                    ConfermaSulPosto.Esito.IGNORATO -> Unit

                    ConfermaSulPosto.Esito.CONFERMATA -> chiudiConAzione(AZIONE_FINE)
                }
        }
    }

    private fun chiudiConAzione(azione: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_AZIONE, azione))
        finish()
    }

    private fun colore(id: Int) = ContextCompat.getColor(this, id)

    /**
     * Voce spenta: la card a meta' opacita' ma il testo no. Con l'alpha sulla vista intera anche
     * il grigio dei sottotitoli scenderebbe sotto il 4.5:1; l'alpha sul solo fondo lascia il testo
     * com'e'.
     */
    private fun disegna() {
        righe.forEach { riga ->
            val voce = riga.voce
            val armata = voce.id == IdVoce.FINE_PARTITA && conferma.armata
            val fondo =
                when {
                    armata -> colore(R.color.error_red)
                    voce.attiva -> colore(R.color.concrete_gray)
                    else -> ColorUtils.setAlphaComponent(colore(R.color.concrete_gray), 128)
                }
            riga.card.setCardBackgroundColor(fondo)
            riga.card.isEnabled = voce.attiva

            riga.titolo.text = titoloDi(voce, armata)
            riga.titolo.setTextColor(
                colore(
                    when {
                        armata -> R.color.ink_black
                        !voce.attiva -> R.color.sidewalk_gray
                        voce.id == IdVoce.FINE_PARTITA -> R.color.error_text
                        else -> R.color.stencil_white
                    },
                ),
            )

            val sotto = if (armata) getString(R.string.wear_menu_tap_again) else voce.sottotitolo.testo(this)
            riga.sottotitolo.text = sotto
            riga.sottotitolo.visibility = if (sotto.isBlank()) View.GONE else View.VISIBLE
            riga.sottotitolo.setTextColor(
                colore(
                    when {
                        armata -> R.color.ink_black
                        voce.sottotitolo is SottotitoloVoce.PrimaConsegna -> R.color.signal_amber
                        else -> R.color.sidewalk_gray
                    },
                ),
            )
        }
    }

    private fun titoloDi(
        voce: Voce,
        armata: Boolean,
    ): String =
        when (voce.id) {
            IdVoce.SPORT -> getString(R.string.wear_sport)
            IdVoce.FINE_PARTITA ->
                if (armata) getString(R.string.wear_menu_end_confirm, input.risultato) else getString(R.string.wear_menu_end)
        }
}

/**
 * Il sottotitolo nella lingua dell'orologio. Fuori dalla schermata per la stessa ragione di
 * [testo] sulle frasi di stato: un test lo chiama sulle risorse vere, in italiano e in inglese.
 */
internal fun SottotitoloVoce.testo(context: Context): String =
    when (this) {
        is SottotitoloVoce.SportInUso -> sport
        SottotitoloVoce.PartitaInCorso -> context.getString(R.string.wear_sport_locked)
        is SottotitoloVoce.PrimaConsegna ->
            context.resources.getQuantityString(R.plurals.wear_menu_deliver_first, punti, punti)
        SottotitoloVoce.ServeIlTelefono -> context.getString(R.string.wear_menu_phone_needed)
        SottotitoloVoce.ChiudiDalTelefono -> context.getString(R.string.wear_menu_football_on_phone)
        is SottotitoloVoce.SalvaRisultato -> context.getString(R.string.wear_menu_end_saves, risultato)
    }
