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
        const val AZIONE_SCARTA = "scarta"

        /** Senza nessun tocco ne' scorrimento la schermata torna al quadrante da sola. */
        const val CHIUSURA_AUTOMATICA_MS = 10_000L

        private const val EXTRA_IN_CODA = "menu_in_coda"
        private const val EXTRA_COLLEGATO = "menu_collegato"
        private const val EXTRA_PARTITA_INIZIATA = "menu_partita_iniziata"
        private const val EXTRA_ELENCO_SPORT = "menu_elenco_sport"
        private const val EXTRA_SPORT = "menu_sport"
        private const val EXTRA_RISULTATO = "menu_risultato"
        private const val EXTRA_RIFIUTATI = "menu_rifiutati"

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
                .putExtra(EXTRA_ELENCO_SPORT, input.haElencoSport)
                .putExtra(EXTRA_SPORT, input.sport)
                .putExtra(EXTRA_RISULTATO, input.risultato)
                .putExtra(EXTRA_RIFIUTATI, input.rifiutati)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val conferma = ConfermaSulPosto()

    /** Quale voce e' armata: la conferma e' una sola, e armarne un'altra disarma questa. */
    private var armataId: IdVoce? = null
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
            armataId = null
            disegna()
        }

    private val chiusuraAutomatica =
        Runnable {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MovimentoRidotto.applica(this, theme)
        setContentView(R.layout.activity_menu)

        input =
            InputMenu(
                inCoda = intent.getIntExtra(EXTRA_IN_CODA, 0),
                collegato = intent.getBooleanExtra(EXTRA_COLLEGATO, false),
                partitaIniziata = intent.getBooleanExtra(EXTRA_PARTITA_INIZIATA, false),
                haElencoSport = intent.getBooleanExtra(EXTRA_ELENCO_SPORT, false),
                sport = intent.getStringExtra(EXTRA_SPORT).orEmpty(),
                risultato = intent.getStringExtra(EXTRA_RISULTATO).orEmpty(),
                rifiutati = intent.getIntExtra(EXTRA_RIFIUTATI, 0),
            )

        val contenitore = findViewById<LinearLayout>(R.id.menu_voci)
        MenuVoci.calcola(input).forEach { voce ->
            val card = LayoutInflater.from(this).inflate(R.layout.item_sport_wear, contenitore, false) as MaterialCardView
            card.setOnClickListener { alTocco(voce) }
            // Armarsi e rientrare cambia titolo e sottotitolo: TalkBack deve dirlo, perche' chi non
            // vede la card rossa non saprebbe che il tocco e' gia' armato.
            if (voce.id != IdVoce.SPORT) card.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
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

    // Anche la rotazione della corona passa di qui: Activity.dispatchGenericMotionEvent chiama
    // onUserInteraction prima di consegnare l'evento, anche quando lo ScrollView non ha dove
    // scorrere (lo prova il test sulla corona). Un override dedicato sarebbe codice morto.
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
            IdVoce.SPORT -> {
                chiudiConAzione(AZIONE_SPORT)
            }

            // Le due voci che distruggono qualcosa (chiudere la partita, scartare la coda) si armano
            // allo stesso modo: il primo tocco arma, il secondo conferma dentro la finestra.
            IdVoce.FINE_PARTITA, IdVoce.SCARTA_CODA -> {
                // Armare l'altra voce disarma questa: un secondo tocco su una voce diversa e' un primo tocco.
                if (armataId != voce.id) conferma.disarma()
                when (conferma.tocca(orologio())) {
                    ConfermaSulPosto.Esito.ARMATA -> {
                        armataId = voce.id
                        handler.removeCallbacks(rientro)
                        handler.postDelayed(rientro, ConfermaSulPosto.SCADENZA_MS)
                        disegna()
                    }

                    ConfermaSulPosto.Esito.IGNORATO -> {
                        Unit
                    }

                    ConfermaSulPosto.Esito.CONFERMATA -> {
                        armataId = null
                        chiudiConAzione(if (voce.id == IdVoce.FINE_PARTITA) AZIONE_FINE else AZIONE_SCARTA)
                    }
                }
            }
        }
    }

    private fun chiudiConAzione(azione: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_AZIONE, azione))
        finish()
    }

    private fun colore(id: Int) = ContextCompat.getColor(this, id)

    /**
     * Le voci sono righe di un gruppo tonale: sullo stesso tono del gruppo (elite_surface), e solo
     * la voce armata si riempie, di status-error. Una voce spenta cambia il titolo in text-disabled
     * (la Constitution lo ammette per le etichette spente); il perche' sta nel sottotitolo, che
     * resta text-secondary e si legge a 4,5:1.
     */
    private fun disegna() {
        righe.forEach { riga ->
            val voce = riga.voce
            val armata = conferma.armata && armataId == voce.id
            val fondo =
                when {
                    armata -> colore(R.color.elite_error)
                    else -> colore(R.color.elite_surface)
                }
            riga.card.setCardBackgroundColor(fondo)
            riga.card.isEnabled = voce.attiva

            riga.titolo.text = titoloDi(voce, armata)
            riga.titolo.setTextColor(
                colore(
                    when {
                        armata -> R.color.ink_black
                        !voce.attiva -> R.color.elite_text_disabled
                        voce.id == IdVoce.FINE_PARTITA || voce.id == IdVoce.SCARTA_CODA -> R.color.elite_error
                        else -> R.color.elite_text_primary
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
                        voce.sottotitolo.eDaConsegnare() -> R.color.elite_warning
                        else -> R.color.elite_text_secondary
                    },
                ),
            )
        }
    }

    /** I due sottotitoli che parlano di tocchi non consegnati, in ambra. */
    private fun SottotitoloVoce.eDaConsegnare() = this is SottotitoloVoce.PrimaConsegna || this is SottotitoloVoce.CodaRifiutata

    private fun titoloDi(
        voce: Voce,
        armata: Boolean,
    ): String =
        when (voce.id) {
            IdVoce.SPORT -> {
                getString(R.string.wear_sport)
            }

            IdVoce.FINE_PARTITA -> {
                if (armata) getString(R.string.wear_menu_end_confirm, input.risultato) else getString(R.string.wear_menu_end)
            }

            IdVoce.SCARTA_CODA -> {
                getString(if (armata) R.string.wear_menu_discard_confirm else R.string.wear_menu_discard)
            }
        }
}

/**
 * Il sottotitolo nella lingua dell'orologio. Fuori dalla schermata per la stessa ragione di
 * [testo] sulle frasi di stato: un test lo chiama sulle risorse vere, in italiano e in inglese.
 */
internal fun SottotitoloVoce.testo(context: Context): String =
    when (this) {
        is SottotitoloVoce.SportInUso -> {
            sport
        }

        SottotitoloVoce.PartitaInCorso -> {
            context.getString(R.string.wear_sport_locked)
        }

        is SottotitoloVoce.PrimaConsegna -> {
            context.resources.getQuantityString(R.plurals.wear_menu_deliver_first, punti, punti)
        }

        is SottotitoloVoce.CodaRifiutata -> {
            context.resources.getQuantityString(R.plurals.wear_menu_rejected_points, punti, punti)
        }

        SottotitoloVoce.ServeIlTelefono -> {
            context.getString(R.string.wear_menu_phone_needed)
        }

        SottotitoloVoce.NienteDaSalvare -> {
            context.getString(R.string.wear_menu_nothing_to_save)
        }

        is SottotitoloVoce.SalvaRisultato -> {
            context.getString(R.string.wear_menu_end_saves, risultato)
        }
    }
