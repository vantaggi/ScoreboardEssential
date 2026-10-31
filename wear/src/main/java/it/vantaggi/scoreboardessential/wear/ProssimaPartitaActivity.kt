package it.vantaggi.scoreboardessential.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.DataMap
import com.google.android.material.button.MaterialButton
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * La prossima partita della serata, dal polso: le coppie che propone il telefono, Ruota, lo scambio di
 * due giocatori e Inizia.
 *
 * La regola della rotazione resta sul telefono, come quella del punteggio: qui si mostrano nomi e si
 * CHIEDE ([WearConstants.MSG_SERATA_INTENT]). La risposta e' l'elenco delle coppie che torna con una
 * versione nuova; finche' non torna i comandi sono fermi e la riga d'avviso dice "Aspetto il telefono",
 * e se non torna entro [ATTESA_MS] lo dice e vibra come un tocco non confermato. Ogni richiesta porta la
 * bozza su cui e' stata decisa: se intanto sul telefono e' cambiata, il telefono non applica niente e
 * rimanda quella nuova, che si vede qui.
 *
 * Lo scambio: un tocco sceglie un nome (la riga si alza di tono e il nome diventa lime), un tocco su un
 * altro nome li scambia, un secondo tocco sullo stesso lo lascia. Inizia mette la partita a tabellone e
 * riporta al quadrante quando il telefono conferma.
 */
class ProssimaPartitaActivity : ComponentActivity() {
    companion object {
        /** Quanto si aspetta la risposta del telefono prima di dire che non e' arrivata. */
        internal const val ATTESA_MS = 3_000L

        private const val EXTRA_PAYLOAD = "prossima_payload"
        private const val EXTRA_COLORE1 = "prossima_colore1"
        private const val EXTRA_COLORE2 = "prossima_colore2"

        /** Come [PlayerSelectionActivity.creaSync]: sotto test i client GMS veri non ci sono. */
        @VisibleForTesting
        internal var creaSync: (Context) -> OptimizedWearDataSync = { OptimizedWearDataSync(it) }

        fun intent(
            context: Context,
            coppie: CoppieDalTelefono,
            colore1: Int,
            colore2: Int,
        ): Intent =
            Intent(context, ProssimaPartitaActivity::class.java)
                .putExtra(EXTRA_PAYLOAD, comeDataMap(coppie).toByteArray())
                .putExtra(EXTRA_COLORE1, colore1)
                .putExtra(EXTRA_COLORE2, colore2)

        /** Le coppie con le chiavi del filo: la schermata le rilegge con la stessa [CoppieDalTelefono.fromDataMap]. */
        internal fun comeDataMap(c: CoppieDalTelefono): DataMap =
            DataMap().apply {
                val sep = WearConstants.SPORT_SEPARATOR
                putString(WearConstants.KEY_TEAM1_ROSTER, c.rosa1.joinToString(sep))
                putString(WearConstants.KEY_TEAM2_ROSTER, c.rosa2.joinToString(sep))
                putInt(WearConstants.KEY_NEXT_NUMBER, c.numero)
                putString(WearConstants.KEY_NEXT_PLAYERS, c.giocatori.joinToString(sep))
                putString(WearConstants.KEY_NEXT_BASE, c.base)
                putString(WearConstants.KEY_NEXT_LABELS, c.sigle.joinToString(sep))
                putString(WearConstants.KEY_NEXT_BENCH, c.panchina.joinToString(sep))
                putBoolean(WearConstants.KEY_NEXT_TIEBREAK, c.spareggio)
                putBoolean(WearConstants.KEY_NEXT_DELIVERED, c.consegnata)
                putLong(WearConstants.KEY_PAIRS_VERSION, c.versione)
            }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sync: OptimizedWearDataSync
    private lateinit var haptics: WearHaptics
    private lateinit var coppie: CoppieDalTelefono

    /** Un invio alla volta, nell'ordine: il telefono scarta una sequenza che non supera l'ultima vista. */
    private val invii = Mutex()

    /** Il posto scelto per uno scambio (0..3), o null. */
    private var scelto: Int? = null

    /** L'azione chiesta al telefono e non ancora vista tornare; null quando non si aspetta niente. */
    private var inAttesa: String? = null

    /** L'ultima richiesta non ha avuto risposta: l'avviso lo dice finche' non si tocca altro. */
    private var senzaRisposta = false

    private val scadenza =
        Runnable {
            inAttesa = null
            senzaRisposta = true
            haptics.suona(WearPatterns.NON_CONFERMATO)
            disegna()
        }

    private val ricevitore =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val payload = intent.getByteArrayExtra(WearDataLayerService.EXTRA_PAIRS_PAYLOAD) ?: return
                aggiorna(CoppieDalTelefono.fromDataMap(DataMap.fromByteArray(payload)))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MovimentoRidotto.applica(this, theme)
        setContentView(R.layout.activity_prossima_partita)
        sync = creaSync(applicationContext)
        haptics = HapticsDelPolso.di(this)
        coppie = CoppieDalTelefono.fromDataMap(DataMap.fromByteArray(intent.getByteArrayExtra(EXTRA_PAYLOAD) ?: ByteArray(0)))
        if (!coppie.haProssima) {
            finish()
            return
        }
        findViewById<MaterialButton>(R.id.prossima_ruota).setOnClickListener { chiedi(WearConstants.SERATA_ROTATE) }
        findViewById<MaterialButton>(R.id.prossima_inizia).setOnClickListener {
            if (coppie.consegnata) finish() else chiedi(WearConstants.SERATA_START)
        }
        // Sul tondo ogni blocco della colonna (titolo, coppie, bottoni) si stringe vicino al bordo.
        val scorrimento = findViewById<ScrollView>(R.id.prossima_scroll)
        val colonna = findViewById<LinearLayout>(R.id.prossima_colonna)
        val bordo = ListaTonda(scorrimento) { (0 until colonna.childCount).map(colonna::getChildAt) }
        scorrimento.viewTreeObserver.addOnGlobalLayoutListener { bordo.aggiorna() }
        scorrimento.setOnScrollChangeListener { _, _, _, _, _ -> bordo.aggiorna() }
        // Il focus serve alla corona: senza, ruotarla non scorre niente.
        scorrimento.requestFocus()
        LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(ricevitore, IntentFilter(WearDataLayerService.ACTION_PAIRS_UPDATE))
        disegna()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(ricevitore)
        if (::sync.isInitialized) sync.cleanup()
        super.onDestroy()
    }

    /**
     * Le coppie nuove dal telefono. Una versione piu' vecchia di quella a schermo e' superata. Se si
     * aspettava una risposta, e' questa: la ricevuta vibra, e dopo Inizia, con la partita a tabellone, si
     * torna al quadrante. Senza piu' una proposta (serata chiusa, partita cominciata altrove) la
     * schermata non ha niente da mostrare e si chiude.
     */
    internal fun aggiorna(nuove: CoppieDalTelefono) {
        if (nuove.versione in 1 until coppie.versione) return
        val eraInAttesa = inAttesa
        if (nuove.base != coppie.base) scelto = null
        coppie = nuove
        if (eraInAttesa != null) {
            handler.removeCallbacks(scadenza)
            inAttesa = null
            senzaRisposta = false
            haptics.tick()
            if (eraInAttesa == WearConstants.SERATA_START && nuove.consegnata) {
                finish()
                return
            }
        }
        if (!nuove.haProssima) {
            finish()
            return
        }
        disegna()
    }

    private fun tocca(posto: Int) {
        if (inAttesa != null) return
        senzaRisposta = false
        val primo = scelto
        when {
            primo == null -> {
                scelto = posto
            }

            primo == posto -> {
                scelto = null
            }

            else -> {
                scelto = null
                chiedi(WearConstants.SERATA_SWAP, primo, posto)
                return
            }
        }
        disegna()
    }

    private fun chiedi(
        azione: String,
        da: Int = -1,
        a: Int = -1,
    ) {
        if (inAttesa != null) return
        inAttesa = azione
        senzaRisposta = false
        scelto = null
        disegna()
        val payload =
            DataMap().apply {
                putString(WearConstants.KEY_SERATA_ACTION, azione)
                putString(WearConstants.KEY_NEXT_BASE, coppie.base)
                putLong(WearConstants.KEY_SEQ, SequenzaDelPolso.dellaSchermata())
                if (azione == WearConstants.SERATA_SWAP) {
                    putInt(WearConstants.KEY_SWAP_FROM, da)
                    putInt(WearConstants.KEY_SWAP_TO, a)
                }
            }
        handler.postDelayed(scadenza, ATTESA_MS)
        lifecycleScope.launch {
            val consegnato = invii.withLock { sync.sendMessage(WearConstants.MSG_SERATA_INTENT, payload.toByteArray()) }
            // Non arrivato: inutile aspettare la scadenza, lo si dice subito.
            if (!consegnato && inAttesa == azione) {
                handler.removeCallbacks(scadenza)
                scadenza.run()
            }
        }
    }

    private fun colore(id: Int) = ContextCompat.getColor(this, id)

    private fun disegna() {
        val titolo = findViewById<TextView>(R.id.prossima_titolo)
        titolo.text =
            getString(if (coppie.spareggio) R.string.wear_next_match_tiebreak else R.string.wear_next_match_title, coppie.numero)

        val avviso = findViewById<TextView>(R.id.prossima_avviso)
        val nomeScelto = scelto?.let { coppie.giocatori[it] }
        avviso.text =
            when {
                inAttesa != null -> getString(R.string.wear_next_waiting)
                senzaRisposta -> getString(R.string.wear_next_no_answer)
                nomeScelto != null -> getString(R.string.wear_next_pick, nomeScelto)
                coppie.consegnata -> getString(R.string.wear_next_delivered)
                else -> getString(R.string.wear_next_hint)
            }
        avviso.setTextColor(colore(if (senzaRisposta) R.color.status_warning else R.color.text_secondary))

        findViewById<TextView>(R.id.prossima_sigla1).text = coppie.sigle.getOrNull(0).orEmpty()
        findViewById<TextView>(R.id.prossima_sigla2).text = coppie.sigle.getOrNull(1).orEmpty()
        val colori =
            listOf(
                intent.getIntExtra(EXTRA_COLORE1, colore(R.color.team_side_1)),
                intent.getIntExtra(EXTRA_COLORE2, colore(R.color.team_side_2)),
            )
        listOf(R.id.prossima_coppia1, R.id.prossima_coppia2).forEachIndexed { coppia, id ->
            val gruppo = findViewById<LinearLayout>(id)
            gruppo.removeAllViews()
            (0..1).forEach { i -> gruppo.addView(riga(coppia * 2 + i, coppia, colori[coppia], gruppo)) }
        }

        val panchina = findViewById<TextView>(R.id.prossima_panchina)
        panchina.text = getString(R.string.wear_next_bench, coppie.panchina.joinToString(", "))
        panchina.visibility = if (coppie.panchina.isEmpty()) View.GONE else View.VISIBLE

        val libero = inAttesa == null
        findViewById<MaterialButton>(R.id.prossima_ruota).isEnabled = libero
        findViewById<MaterialButton>(R.id.prossima_inizia).apply {
            isEnabled = libero
            text = getString(if (coppie.consegnata) R.string.wear_next_back else R.string.wear_next_start)
        }
    }

    /** Una riga giocatore: la barra della coppia e il nome; scelta, si alza di tono e il nome e' lime a 600. */
    private fun riga(
        posto: Int,
        coppia: Int,
        coloreCoppia: Int,
        gruppo: LinearLayout,
    ): View {
        val riga = LayoutInflater.from(this).inflate(R.layout.item_player_wear, gruppo, false)
        val nome = coppie.giocatori[posto]
        val eScelto = scelto == posto
        riga.findViewById<View>(R.id.player_bar).setBackgroundColor(TeamInk.graphicOnBlack(coloreCoppia) or TeamInk.NERO)
        riga.findViewById<TextView>(R.id.player_name).apply {
            text = nome
            setTextColor(colore(if (eScelto) R.color.accent_default else R.color.text_primary))
        }
        riga.setBackgroundColor(colore(if (eScelto) R.color.background_elevated else R.color.background_surface))
        riga.isSelected = eScelto
        riga.isEnabled = inAttesa == null
        riga.contentDescription =
            getString(if (eScelto) R.string.cd_next_player_selected else R.string.cd_next_player, nome, coppia + 1)
        riga.setOnClickListener { tocca(posto) }
        return riga
    }
}
