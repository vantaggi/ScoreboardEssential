package it.vantaggi.scoreboardessential

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.padelelite.FonteSerataDashboard
import it.vantaggi.scoreboardessential.padelelite.LettoreSerataDashboard
import it.vantaggi.scoreboardessential.padelelite.PadelEliteActivity
import it.vantaggi.scoreboardessential.padelelite.SerataDashboard
import it.vantaggi.scoreboardessential.repository.SerataPrefsStore
import it.vantaggi.scoreboardessential.ui.InsetDividerDecoration
import kotlinx.coroutines.launch

/**
 * Serata: chi gioca la prossima. Si apre dal foglio PARTITA (o dal dialogo di fine partita) e torna con
 * RESULT_OK quando l'utente preme "Inizia la partita": la serata e' gia' ricordata, e
 * [MainViewModel.avviaPartitaDellaSerata] mette le coppie nelle rose. Il resto lo fa [SerataViewModel].
 *
 * [EXTRA_PARTITA_IN_CORSO] la rende di sola lettura: mentre si gioca le coppie sono quelle della partita.
 *
 * Con l'accesso a Padel Elite e un gruppo scelto c'e' anche il gruppo "Dalla dashboard": la prossima serata
 * del gruppo, i presenti confermati e i gironi salvati, che diventano i presenti solo al tocco (e "Annulla"
 * li toglie). Con la funzione spenta la schermata non apre la rete.
 */
class SerataActivity : AppCompatActivity() {
    private lateinit var viewModel: SerataViewModel
    private lateinit var posti: SerataRigheAdapter
    private lateinit var comandi: SerataRigheAdapter
    private lateinit var panchina: SerataRigheAdapter
    private lateinit var presenti: SerataRigheAdapter
    private lateinit var proposte: SerataRigheAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_serata)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.title_serata)
        }

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val database = AppDatabase.getDatabase(application)
        viewModel =
            ViewModelProvider(
                this,
                SerataViewModel.Factory(
                    database.playerDao(),
                    SerataPrefsStore(prefs),
                    intent.getBooleanExtra(EXTRA_PARTITA_IN_CORSO, false),
                    fonteDiProva ?: fonteDellaDashboard(database),
                    database.padelEliteLinkDao(),
                    database.matchDao(),
                ),
            )[SerataViewModel::class.java]

        posti = SerataRigheAdapter { chiave -> ChiaviSerata.postoDa(chiave)?.let(viewModel::toccaIlPosto) }
        comandi =
            SerataRigheAdapter { chiave ->
                when (chiave) {
                    ChiaviSerata.RUOTA -> viewModel.ruota()
                    ChiaviSerata.STESSE_COPPIE -> viewModel.stesseCoppie()
                    ChiaviSerata.SCAMBIA_LATI -> viewModel.scambiaILati()
                }
            }
        panchina = SerataRigheAdapter { chiave -> ChiaviSerata.panchinaDa(chiave)?.let(viewModel::toccaLaPanchina) }
        presenti =
            SerataRigheAdapter { chiave ->
                if (chiave ==
                    ChiaviSerata.OSPITE
                ) {
                    mostraIlDialogoDellOspite()
                } else {
                    ChiaviSerata.presenteDa(chiave)?.let(viewModel::scambiaPresente)
                }
            }
        proposte =
            SerataRigheAdapter { chiave ->
                when (chiave) {
                    ChiaviSerata.DASHBOARD_PRESENTI -> viewModel.usaIPresenti()
                    else -> ChiaviSerata.gironeDa(chiave)?.let(viewModel::usaIlGirone)
                }
            }
        collega(R.id.serata_seats, posti)
        collega(R.id.serata_commands, comandi)
        collega(R.id.serata_bench, panchina)
        collega(R.id.serata_present, presenti)
        collega(R.id.serata_dashboard_rows, proposte)
        findViewById<MaterialButton>(R.id.serata_dashboard_action).setOnClickListener {
            if (viewModel.stato.value.dashboard == SerataDashboard.Errore) {
                viewModel.riprovaLaDashboard()
            } else {
                startActivity(PadelEliteActivity.intent(this))
            }
        }

        findViewById<MaterialButton>(R.id.serata_start_button).setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }
        findViewById<MaterialButton>(R.id.serata_close_button).setOnClickListener { chiediDiChiudere() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.stato.collect(::mostra) }
                launch { viewModel.rapporti.collect(::diciCosaEArrivato) }
            }
        }
    }

    /**
     * La fonte della serata della dashboard, solo con la funzione accesa (URL e chiave in local.properties):
     * spenta, nessuna rete. Accesso e gruppo li controlla il lettore, fuori dal thread principale.
     */
    private fun fonteDellaDashboard(database: AppDatabase): FonteSerataDashboard? {
        val padelElite = (application as ScoreboardEssentialApplication).padelElite
        if (!padelElite.isEnabled) return null
        return LettoreSerataDashboard(padelElite.account, database.padelEliteLinkDao(), database.playerDao())
    }

    /** Il rapporto di una proposta usata: quanti presenti e quanti ospiti, con "Annulla". */
    private fun diciCosaEArrivato(rapporto: RapportoDashboard) {
        val testo =
            buildString {
                append(resources.getQuantityString(R.plurals.serata_dashboard_used, rapporto.presenti, rapporto.presenti))
                if (rapporto.ospiti > 0) {
                    append(resources.getQuantityString(R.plurals.serata_dashboard_used_guests, rapporto.ospiti, rapporto.ospiti))
                }
            }
        Snackbar
            .make(findViewById(R.id.serata_scroll), testo, Snackbar.LENGTH_LONG)
            .setAnchorView(R.id.serata_start_bar)
            .setAnimationMode(Snackbar.ANIMATION_MODE_FADE)
            .setAction(R.string.undo) { viewModel.annullaLaDashboard() }
            .show()
    }

    override fun onStart() {
        super.onStart()
        // Tornando sulla schermata la serata puo' essere cambiata: una partita chiusa, anche dall'orologio.
        viewModel.ricarica()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun collega(
        id: Int,
        adapter: SerataRigheAdapter,
    ) {
        findViewById<RecyclerView>(id).apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(this@SerataActivity)
            addItemDecoration(InsetDividerDecoration(this@SerataActivity))
            itemAnimator = null
        }
    }

    /** Disegna lo stato: i gruppi che servono, con le loro righe. */
    private fun mostra(stato: StatoDellaSerata) {
        mostraLaDomanda(stato.domanda)
        val serata = stato.serata
        val bozza = serata?.bozza
        findViewById<View>(R.id.serata_readonly_notice).visibility = if (stato.inCorso) View.VISIBLE else View.GONE

        findViewById<View>(R.id.serata_next_group).visibility = if (bozza != null) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.serata_next_title).text = getString(R.string.serata_next_title, serata?.numeroDellaProssima ?: 1)
        posti.submitList(RigheDellaSerata.posti(this, stato))
        comandi.submitList(RigheDellaSerata.comandi(this, stato))

        val inPanchina = RigheDellaSerata.panchina(this, stato)
        findViewById<View>(R.id.serata_bench_group).visibility = if (bozza != null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.serata_bench_empty).visibility = if (inPanchina.isEmpty()) View.VISIBLE else View.GONE
        panchina.submitList(inPanchina)

        val numero = serata?.presenti?.size ?: 0
        findViewById<TextView>(R.id.serata_present_title).text =
            if (numero == 0) {
                getString(R.string.serata_present_title_none)
            } else {
                resources.getQuantityString(R.plurals.serata_present_title, numero, numero)
            }
        findViewById<View>(R.id.serata_present_empty).visibility = if (stato.rosa.isEmpty()) View.VISIBLE else View.GONE
        presenti.submitList(RigheDellaSerata.presenti(this, stato))

        mostraLaDashboard(stato)

        findViewById<View>(R.id.serata_close_button).visibility = if (serata != null && !stato.inCorso) View.VISIBLE else View.GONE

        val avvio = findViewById<MaterialButton>(R.id.serata_start_button)
        avvio.isEnabled = stato.puoIniziare
        val mancanti = stato.presentiMancanti
        findViewById<TextView>(R.id.serata_start_hint).apply {
            text = if (mancanti > 0) resources.getQuantityString(R.plurals.serata_start_missing, mancanti, mancanti) else ""
            visibility = if (mancanti > 0) View.VISIBLE else View.GONE
        }
    }

    /** Il gruppo "Dalla dashboard": la riga di stato, le proposte, e il comando che serve (Riprova o Accedi). */
    private fun mostraLaDashboard(stato: StatoDellaSerata) {
        val dashboard = stato.dashboard
        findViewById<View>(R.id.serata_dashboard_group).visibility =
            if (dashboard == SerataDashboard.Spenta) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.serata_dashboard_status).text = RigheDellaSerata.statoDashboard(this, stato).orEmpty()
        proposte.submitList(RigheDellaSerata.dashboard(this, stato))
        val comando =
            when (dashboard) {
                SerataDashboard.Errore -> R.string.padel_elite_retry
                SerataDashboard.AccessoScaduto -> R.string.padel_elite_sign_in
                SerataDashboard.FuoriDalGruppo -> R.string.serata_dashboard_open
                else -> null
            }
        findViewById<MaterialButton>(R.id.serata_dashboard_action).apply {
            visibility = if (comando != null) View.VISIBLE else View.GONE
            if (comando != null) setText(comando)
        }
    }

    /** La domanda aperta sugli omonimi e il dialogo che la mostra; dopo una rotazione la domanda torna dallo stato. */
    private var domandaMostrata: DomandaOmonimo? = null
    private var dialogoDellaDomanda: AlertDialog? = null

    /**
     * "Rossi e' gia' nell'app?" per un presente della dashboard con omonimi: nella dashboard spesso c'e' solo il
     * cognome, quindi si chiede invece di indovinare. Un omonimo = "e' lui" (si collega), l'ultima riga = un giocatore
     * nuovo; Annulla, indietro o un tocco fuori lasciano cadere la proposta senza scrivere niente.
     */
    private fun mostraLaDomanda(domanda: DomandaOmonimo?) {
        if (domanda == domandaMostrata) return
        domandaMostrata = domanda
        dialogoDellaDomanda?.setOnCancelListener(null)
        dialogoDellaDomanda?.dismiss()
        dialogoDellaDomanda = null
        if (domanda == null) return
        dialogoDellaDomanda =
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.scelta_same_person_title, domanda.nome))
                .setAdapter(RigheDelDialogo(this, righeDellaDomanda(this, domanda.nome, domanda.candidati))) { _, which ->
                    // La riga 0 e' la spiegazione (non si tocca); poi gli omonimi; l'ultima e' "un'altra persona".
                    if (which == 0) return@setAdapter
                    viewModel.rispondi(domanda.candidati.getOrNull(which - 1)?.localId)
                }.setNegativeButton(R.string.cancel) { _, _ -> viewModel.annullaLaDomanda() }
                .setOnCancelListener { viewModel.annullaLaDomanda() }
                .show()
    }

    private fun mostraIlDialogoDellOspite() {
        val vista = layoutInflater.inflate(R.layout.dialog_serata_guest, null)
        val campo = vista.findViewById<TextInputLayout>(R.id.serata_guest_layout)
        val testo = vista.findViewById<TextInputEditText>(R.id.serata_guest_input)
        val dialogo =
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.serata_add_guest)
                .setView(vista)
                .setPositiveButton(R.string.serata_guest_add, null)
                .setNegativeButton(R.string.cancel, null)
                .show()
        // Un nome vuoto non chiude il dialogo: lo dice sotto il campo e tiene quello che c'e'.
        dialogo.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            if (viewModel.aggiungiOspite(testo.text?.toString().orEmpty())) {
                dialogo.dismiss()
            } else {
                campo.error = getString(R.string.player_name_empty)
            }
        }
    }

    private fun chiediDiChiudere() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.serata_close_title)
            .setMessage(R.string.serata_close_message)
            .setPositiveButton(R.string.serata_close) { _, _ -> viewModel.chiudiLaSerata() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        /** Vero quando c'e' una partita in corso: la schermata e' di sola lettura. */
        const val EXTRA_PARTITA_IN_CORSO = "partita_in_corso"

        /** Solo per i test: la serata della dashboard al posto di quella dell'applicazione (che e' spenta senza local.properties). */
        @VisibleForTesting
        internal var fonteDiProva: FonteSerataDashboard? = null
    }
}
