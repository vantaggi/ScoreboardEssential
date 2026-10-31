package it.vantaggi.scoreboardessential.wear

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.isNotEmpty
import com.google.android.material.card.MaterialCardView
import it.vantaggi.scoreboardessential.core.TeamInk

/** Una squadra a tabellone, per il gruppo "In campo" del menu: il nome mostrato, la rosa, il colore (ARGB). */
data class SquadraInCampo(
    val nome: String,
    val giocatori: List<String>,
    val colore: Int,
)

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
 * [ConfermaSulPosto], chiude. Se nessuno tocca niente, il menu si chiude da solo dopo 20 secondi.
 *
 * Le righe vanno a tutta larghezza e sul tondo si stringono vicino al bordo ([ListaTonda]); ognuna
 * ha l'icona del suo concetto (tabella G-3 di DESIGN.md).
 */
class MenuActivity : ComponentActivity() {
    companion object {
        const val EXTRA_AZIONE = "menu_azione"
        const val AZIONE_SPORT = "sport"
        const val AZIONE_FINE = "fine"
        const val AZIONE_SCARTA = "scarta"
        const val AZIONE_PROSSIMA = "prossima"
        const val AZIONE_REGOLA_40 = "regola_40"
        const val AZIONE_PORTIERE = "portiere"
        const val AZIONE_SERVIZIO = "servizio"

        /**
         * Senza nessun tocco ne' scorrimento la schermata torna al quadrante da sola. Erano 10 secondi:
         * con le impostazioni in fondo la lista e' sempre piu' lunga di uno schermo, e chi leggeva
         * "In campo" se la vedeva sparire.
         */
        const val CHIUSURA_AUTOMATICA_MS = 20_000L

        /** Voci il cui titolo o sottotitolo cambia a ogni tocco: TalkBack deve dirlo. */
        private val VOCI_CHE_CAMBIANO_SUL_POSTO =
            setOf(IdVoce.FINE_PARTITA, IdVoce.SCARTA_CODA, IdVoce.VIBRAZIONE, IdVoce.SCHERMO_ACCESO)

        private const val EXTRA_IN_CODA = "menu_in_coda"
        private const val EXTRA_COLLEGATO = "menu_collegato"
        private const val EXTRA_PARTITA_INIZIATA = "menu_partita_iniziata"
        private const val EXTRA_ELENCO_SPORT = "menu_elenco_sport"
        private const val EXTRA_SPORT = "menu_sport"
        private const val EXTRA_RISULTATO = "menu_risultato"
        private const val EXTRA_RIFIUTATI = "menu_rifiutati"
        private const val EXTRA_PROSSIMA = "menu_prossima"
        private const val EXTRA_SQUADRE_NOMI = "menu_squadre_nomi"
        private const val EXTRA_SQUADRE_GIOCATORI = "menu_squadre_giocatori"
        private const val EXTRA_SQUADRE_COLORI = "menu_squadre_colori"
        private const val EXTRA_REGOLA_40 = "menu_regola_40"
        private const val EXTRA_PORTIERE = "menu_portiere"
        private const val EXTRA_VIBRAZIONE = "menu_vibrazione"
        private const val EXTRA_SCHERMO_ACCESO = "menu_schermo_acceso"
        private const val EXTRA_TEMPO_AVVIATO = "menu_tempo_avviato"
        private const val EXTRA_CHI_SERVE = "menu_chi_serve"
        private const val SEPARATORE_GIOCATORI = "\n"

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
            squadre: List<SquadraInCampo> = emptyList(),
        ): Intent =
            Intent(context, MenuActivity::class.java)
                .putExtra(EXTRA_PROSSIMA, input.prossima)
                .putExtra(EXTRA_REGOLA_40, input.regola40)
                .putExtra(EXTRA_PORTIERE, input.portiere)
                .putExtra(EXTRA_VIBRAZIONE, input.vibrazionePunti)
                .putExtra(EXTRA_SCHERMO_ACCESO, input.schermoAcceso)
                .putExtra(EXTRA_SQUADRE_NOMI, squadre.map { it.nome }.toTypedArray())
                .putExtra(EXTRA_SQUADRE_GIOCATORI, squadre.map { it.giocatori.joinToString(SEPARATORE_GIOCATORI) }.toTypedArray())
                .putExtra(EXTRA_SQUADRE_COLORI, squadre.map { it.colore }.toIntArray())
                .putExtra(EXTRA_IN_CODA, input.inCoda)
                .putExtra(EXTRA_COLLEGATO, input.collegato)
                .putExtra(EXTRA_PARTITA_INIZIATA, input.partitaIniziata)
                .putExtra(EXTRA_ELENCO_SPORT, input.haElencoSport)
                .putExtra(EXTRA_SPORT, input.sport)
                .putExtra(EXTRA_RISULTATO, input.risultato)
                .putExtra(EXTRA_RIFIUTATI, input.rifiutati)
                .putExtra(EXTRA_TEMPO_AVVIATO, input.tempoAvviato)
                .putExtra(EXTRA_CHI_SERVE, input.chiServe)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val conferma = ConfermaSulPosto()

    /** Quale voce e' armata: la conferma e' una sola, e armarne un'altra disarma questa. */
    private var armataId: IdVoce? = null
    private lateinit var input: InputMenu
    private val righe = mutableListOf<Riga>()

    private class Riga(
        var voce: Voce,
        val card: MaterialCardView,
    ) {
        val icona: ImageView = card.findViewById(R.id.voce_icona)
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
                prossima = intent.getStringExtra(EXTRA_PROSSIMA).orEmpty(),
                regola40 = intent.getStringExtra(EXTRA_REGOLA_40).orEmpty(),
                portiere = intent.getStringExtra(EXTRA_PORTIERE).orEmpty(),
                vibrazionePunti = intent.getBooleanExtra(EXTRA_VIBRAZIONE, true),
                schermoAcceso = intent.getBooleanExtra(EXTRA_SCHERMO_ACCESO, false),
                tempoAvviato = intent.getBooleanExtra(EXTRA_TEMPO_AVVIATO, false),
                chiServe = intent.getStringExtra(EXTRA_CHI_SERVE).orEmpty(),
            )

        aggiungiVoci(findViewById(R.id.menu_voci), MenuVoci.calcola(input))
        mostraLeSquadre()
        // Le impostazioni sotto le squadre, con le stesse righe delle voci: il gruppo c'e' sempre,
        // perche' la vibrazione e' del polso e si cambia anche senza telefono.
        aggiungiVoci(findViewById(R.id.menu_impostazioni), MenuVoci.impostazioni(input))
        disegna()

        // Sul tondo le righe si stringono vicino al bordo: alla prima impaginazione e a ogni scorrimento.
        val scorrimento = findViewById<ScrollView>(R.id.menu_scroll)
        val bordo =
            ListaTonda(scorrimento) {
                listOf(R.id.menu_voci, R.id.menu_squadre, R.id.menu_impostazioni).flatMap { id ->
                    val gruppo = findViewById<LinearLayout>(id)
                    (0 until gruppo.childCount).map(gruppo::getChildAt)
                }
            }
        scorrimento.viewTreeObserver.addOnGlobalLayoutListener { bordo.aggiorna() }
        // Anche scorrere con la corona e' un'interazione: non deve chiudere il menu sotto le dita.
        scorrimento.setOnScrollChangeListener { _, _, _, _, _ ->
            bordo.aggiorna()
            riarmaChiusura()
        }
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

    private fun aggiungiVoci(
        contenitore: LinearLayout,
        voci: List<Voce>,
    ) {
        voci.forEach { voce ->
            val card = LayoutInflater.from(this).inflate(R.layout.item_sport_wear, contenitore, false) as MaterialCardView
            val riga = Riga(voce, card)
            card.setOnClickListener { alTocco(riga.voce) }
            // Armarsi e rientrare cambia titolo e sottotitolo: TalkBack deve dirlo, perche' chi non
            // vede la card rossa non saprebbe che il tocco e' gia' armato. La vibrazione cambia sul posto.
            if (voce.id in VOCI_CHE_CAMBIANO_SUL_POSTO) {
                card.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            contenitore.addView(card)
            righe += riga
        }
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

            IdVoce.PROSSIMA_PARTITA -> {
                chiudiConAzione(AZIONE_PROSSIMA)
            }

            IdVoce.REGOLA_40 -> {
                chiudiConAzione(AZIONE_REGOLA_40)
            }

            IdVoce.PORTIERE -> {
                chiudiConAzione(AZIONE_PORTIERE)
            }

            IdVoce.CHI_SERVE -> {
                chiudiConAzione(AZIONE_SERVIZIO)
            }

            // Del polso: si cambia qui, senza uscire, e il sottotitolo dice com'e' adesso. Riaccesa,
            // un tick lo fa sentire.
            IdVoce.VIBRAZIONE -> {
                val accesa = !input.vibrazionePunti
                PreferenzeDelPolso(this).vibrazionePunti = accesa
                input = input.copy(vibrazionePunti = accesa)
                righe.filter { it.voce.id == IdVoce.VIBRAZIONE }.forEach {
                    it.voce = it.voce.copy(sottotitolo = SottotitoloVoce.Vibrazione(accesa))
                }
                if (accesa) VibratoreWearHaptics.di(this).tick()
                disegna()
            }

            // Del polso, come la vibrazione: si cambia qui e MainActivity lo applica al ritorno.
            IdVoce.SCHERMO_ACCESO -> {
                val acceso = !input.schermoAcceso
                PreferenzeDelPolso(this).schermoAcceso = acceso
                input = input.copy(schermoAcceso = acceso)
                righe.filter { it.voce.id == IdVoce.SCHERMO_ACCESO }.forEach {
                    it.voce = it.voce.copy(sottotitolo = SottotitoloVoce.SchermoAcceso(acceso))
                }
                disegna()
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

    /**
     * Le squadre in campo, sotto le voci: la barra del colore (portato a 3:1 sul nero, come la striscia
     * del quadrante), la sigla e chi la compone. Senza rose (il calcio senza giocatori, un telefono non
     * aggiornato) il gruppo non c'e'.
     */
    private fun mostraLeSquadre() {
        val nomi = intent.getStringArrayExtra(EXTRA_SQUADRE_NOMI).orEmpty()
        val giocatori = intent.getStringArrayExtra(EXTRA_SQUADRE_GIOCATORI).orEmpty()
        val colori = intent.getIntArrayExtra(EXTRA_SQUADRE_COLORI) ?: IntArray(0)
        val gruppo = findViewById<LinearLayout>(R.id.menu_squadre)
        nomi.indices
            .filter { giocatori.getOrNull(it).orEmpty().isNotBlank() }
            .forEach { i ->
                val riga = LayoutInflater.from(this).inflate(R.layout.item_squadra_wear, gruppo, false)
                val chi = giocatori[i].split(SEPARATORE_GIOCATORI).joinToString(", ")
                riga.findViewById<TextView>(R.id.squadra_nome).text = nomi[i]
                riga.findViewById<TextView>(R.id.squadra_giocatori).text = chi
                colori.getOrNull(i)?.let {
                    riga.findViewById<View>(R.id.squadra_barra).setBackgroundColor(TeamInk.graphicOnBlack(it) or TeamInk.NERO)
                }
                riga.contentDescription = getString(R.string.cd_squadra, nomi[i], chi)
                gruppo.addView(riga)
            }
        val visibile = if (gruppo.isNotEmpty()) View.VISIBLE else View.GONE
        gruppo.visibility = visibile
        findViewById<View>(R.id.menu_squadre_titolo).visibility = visibile
    }

    private fun chiudiConAzione(azione: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_AZIONE, azione))
        finish()
    }

    private fun colore(id: Int) = ContextCompat.getColor(this, id)

    /**
     * Le voci sono righe di un gruppo tonale: sullo stesso tono del gruppo (background_surface), e solo
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
                    armata -> colore(R.color.status_error)
                    else -> colore(R.color.background_surface)
                }
            riga.card.setCardBackgroundColor(fondo)
            riga.card.isEnabled = voce.attiva

            riga.titolo.text = titoloDi(voce, armata)
            val inchiostro =
                colore(
                    when {
                        armata -> R.color.background_watch
                        !voce.attiva -> R.color.text_disabled
                        voce.id == IdVoce.FINE_PARTITA || voce.id == IdVoce.SCARTA_CODA -> R.color.status_error
                        else -> R.color.text_primary
                    },
                )
            riga.titolo.setTextColor(inchiostro)
            // L'icona ha il colore del nome: spenta con lui, rossa con lui, nera sulla voce armata.
            riga.icona.setImageResource(iconaDi(voce.id))
            riga.icona.imageTintList = ColorStateList.valueOf(inchiostro)
            riga.icona.visibility = View.VISIBLE

            val sotto = if (armata) getString(R.string.wear_menu_tap_again) else voce.sottotitolo.testo(this)
            riga.sottotitolo.text = sotto
            riga.sottotitolo.visibility = if (sotto.isBlank()) View.GONE else View.VISIBLE
            riga.sottotitolo.setTextColor(
                colore(
                    when {
                        armata -> R.color.background_watch
                        voce.sottotitolo.eDaConsegnare() -> R.color.status_warning
                        else -> R.color.text_secondary
                    },
                ),
            )
        }
    }

    /** Il simbolo di ogni voce, dalla tabella concetto -> icona (G-3). */
    @DrawableRes
    private fun iconaDi(id: IdVoce): Int =
        when (id) {
            IdVoce.SPORT -> R.drawable.ic_sports
            IdVoce.PROSSIMA_PARTITA -> R.drawable.ic_groups
            IdVoce.REGOLA_40 -> R.drawable.ic_sports_tennis
            IdVoce.VIBRAZIONE -> R.drawable.ic_vibration
            IdVoce.SCHERMO_ACCESO -> R.drawable.ic_light_mode
            IdVoce.PORTIERE -> R.drawable.ic_sports_handball
            IdVoce.CHI_SERVE -> R.drawable.ic_person
            IdVoce.FINE_PARTITA -> R.drawable.ic_flag
            IdVoce.SCARTA_CODA -> R.drawable.ic_delete
        }

    /** I due sottotitoli che parlano di tocchi non consegnati, in ambra. */
    private fun SottotitoloVoce.eDaConsegnare(): Boolean =
        this is SottotitoloVoce.PrimaConsegna ||
            this is SottotitoloVoce.CodaRifiutata ||
            (this is SottotitoloVoce.ValoreBloccato && perche.eDaConsegnare())

    private fun titoloDi(
        voce: Voce,
        armata: Boolean,
    ): String =
        when (voce.id) {
            IdVoce.SPORT -> {
                getString(R.string.wear_sport)
            }

            IdVoce.PROSSIMA_PARTITA -> {
                getString(R.string.wear_next_match)
            }

            IdVoce.REGOLA_40 -> {
                getString(R.string.wear_setting_deuce)
            }

            IdVoce.VIBRAZIONE -> {
                getString(R.string.wear_setting_vibration)
            }

            IdVoce.SCHERMO_ACCESO -> {
                getString(R.string.wear_setting_screen)
            }

            IdVoce.PORTIERE -> {
                getString(R.string.wear_setting_keeper)
            }

            IdVoce.CHI_SERVE -> {
                getString(R.string.wear_menu_serve)
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

        is SottotitoloVoce.CoppieProposte -> {
            coppie
        }

        is SottotitoloVoce.Valore -> {
            testo
        }

        is SottotitoloVoce.ValoreBloccato -> {
            "$valore · ${perche.testo(context)}"
        }

        is SottotitoloVoce.Vibrazione -> {
            context.getString(if (accesa) R.string.wear_setting_vibration_on else R.string.wear_setting_vibration_off)
        }

        is SottotitoloVoce.SchermoAcceso -> {
            context.getString(if (acceso) R.string.wear_setting_screen_on else R.string.wear_setting_screen_off)
        }
    }
