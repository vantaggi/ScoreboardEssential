package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import it.vantaggi.scoreboardessential.shared.utils.WearDataValidator
import kotlinx.coroutines.launch

data class WearPlayer(
    val id: Int,
    val name: String,
    val roles: List<String>,
)

/**
 * Lets the watch user pick which player scored. The roster is passed in via the launching
 * Intent ([WearDataLayerService.EXTRA_PLAYERS]); the selection is sent back to the phone as a
 * [WearConstants.MSG_SCORER_SELECTED] message, which the phone attributes to the goal.
 *
 * Non si apre mai da sola: la apre il tocco sul bersaglio CHI?, che compare per qualche secondo
 * dopo un gol confermato (WearViewModel.finestraChi). E' una schermata di gioco: su nero, con SALTA
 * in cima, righe da 52dp e la barra del colore della squadra che ha segnato. Per 400ms
 * dall'apertura i tocchi non scelgono nessuno: il dito che stava segnando e' ancora in aria, e il
 * rimbalzo non deve attribuire un gol a chi capita sotto. Dopo 15s senza input si chiude come
 * SALTA.
 */
class PlayerSelectionActivity : ComponentActivity() {
    companion object {
        /** Id della voce di uscita: nessun giocatore vero puo' averlo. */
        private const val NESSUNO = -1

        /** Il colore della squadra che ha segnato (ARGB), per la barra: va portato a 3:1 sul nero. */
        const val EXTRA_COLOR = "scorer_color"

        /** Il punteggio dopo il gol, per l'intestazione: dice QUALE gol si sta attribuendo. */
        const val EXTRA_RISULTATO = "scorer_score"

        /** Nei primi 400ms un tocco non sceglie nessuno, SALTA compreso. */
        internal const val GUARDIA_APERTURA_MS = 400L

        /** Senza input la schermata torna al quadrante da sola, come se si fosse scelto SALTA. */
        internal const val CHIUSURA_AUTOMATICA_MS = 15_000L

        /**
         * Da dove prende il canale verso il telefono. Sostituibile sotto test per la stessa ragione
         * del ViewModel: i client GMS veri muoiono sul looper di Robolectric.
         */
        @VisibleForTesting
        internal var creaSync: (Context) -> OptimizedWearDataSync = { OptimizedWearDataSync(it) }

        /**
         * L'istante con cui si misura la guardia dei 400ms. Iniettabile come
         * [MenuActivity.orologio]: un test sposta un numero invece di aspettare. La chiusura dei
         * 15s passa invece dal looper, che i test spostano a parte.
         */
        @VisibleForTesting
        internal var orologio: () -> Long = SystemClock::uptimeMillis

        fun intent(
            context: Context,
            lato: Int,
            giocatori: List<PlayerData>,
            colore: Int,
            risultato: String,
        ): Intent =
            Intent(context, PlayerSelectionActivity::class.java)
                .putExtra(WearConstants.EXTRA_TEAM_NUMBER, lato)
                .putExtra(WearDataLayerService.EXTRA_PLAYERS, PlayerData.encodeList(giocatori))
                .putExtra(EXTRA_COLOR, colore)
                .putExtra(EXTRA_RISULTATO, risultato)
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var playerList: WearableRecyclerView
    private lateinit var adapter: PlayerAdapter
    private var teamNumber: Int = 1
    private lateinit var sync: OptimizedWearDataSync
    private var inInvio = false
    private var apertaAlle = 0L

    private val chiusuraAutomatica = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_selection)
        apertaAlle = orologio()

        sync = creaSync(applicationContext)
        val rawTeamNumber = intent.getIntExtra(WearConstants.EXTRA_TEAM_NUMBER, 1)
        teamNumber = if (WearDataValidator.isValidTeamNumber(rawTeamNumber)) rawTeamNumber else 1

        val colore = coloreBarra(intent.getIntExtra(EXTRA_COLOR, defaultColore(teamNumber)))
        mostraIntestazione(colore, intent.getStringExtra(EXTRA_RISULTATO).orEmpty())
        setupRecyclerView(colore)
        showPlayers(PlayerData.decodeList(intent.getStringExtra(WearDataLayerService.EXTRA_PLAYERS)))
        riarmaChiusura()
    }

    /** Il colore della squadra e' grafica: portato a 3:1 sul nero, mai colore di un testo. */
    private fun coloreBarra(colore: Int): Int = TeamInk.graphicOnBlack(colore) or TeamInk.NERO

    private fun defaultColore(lato: Int): Int =
        ContextCompat.getColor(this, if (lato == 2) R.color.team_electric_green else R.color.team_spray_yellow)

    private fun mostraIntestazione(
        colore: Int,
        risultato: String,
    ) {
        findViewById<TextView>(R.id.goal_header).text =
            if (risultato.isBlank()) {
                getString(R.string.wear_goal_header_plain)
            } else {
                getString(R.string.wear_goal_header, risultato)
            }
        findViewById<View>(R.id.goal_bar).setBackgroundColor(colore)
    }

    private fun setupRecyclerView(colore: Int) {
        playerList = findViewById(R.id.player_list)
        adapter = PlayerAdapter(colore) { player -> selectPlayer(player) }
        playerList.layoutManager = WearableLinearLayoutManager(this)
        playerList.adapter = adapter
        playerList.isEdgeItemsCenteringEnabled = true
        // Anche scorrere con la corona e' input: non deve chiudere la lista sotto le dita.
        playerList.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(
                    recyclerView: RecyclerView,
                    dx: Int,
                    dy: Int,
                ) {
                    riarmaChiusura()
                }
            },
        )
        // Il focus serve alla corona: senza, ruotarla non scorre niente.
        playerList.requestFocus()
    }

    private fun showPlayers(players: List<PlayerData>) {
        val wearPlayers = players.map { WearPlayer(it.id, it.name, it.roles) }
        // SALTA in cima: chi non sa chi ha segnato, o non vuole dirlo adesso, ha la via d'uscita
        // sotto il pollice senza scorrere. Prima NESSUNO stava in fondo alla rosa.
        adapter.submitList(
            if (wearPlayers.isEmpty()) {
                wearPlayers
            } else {
                listOf(WearPlayer(NESSUNO, getString(R.string.wear_skip), emptyList())) + wearPlayers
            },
        )

        val emptyStateText = findViewById<TextView>(R.id.empty_state_text)
        if (wearPlayers.isEmpty()) {
            playerList.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
        } else {
            playerList.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
        }
    }

    private fun selectPlayer(player: WearPlayer) {
        // Il dito che ha segnato e' ancora vicino: un tocco appena aperta la lista e' un rimbalzo.
        if (orologio() - apertaAlle < GUARDIA_APERTURA_MS) return
        if (player.id == NESSUNO) {
            // Il gol resta, il marcatore no: il telefono ne registra gia' uno senza nome.
            finish()
            return
        }
        val rolesString = player.roles.joinToString(",")
        // L'id e' un QUARTO campo aggiunto in coda, non una sostituzione del nome: un
        // telefono non aggiornato legge i primi tre e ignora il resto, quindi la coppia
        // mista continua a funzionare in entrambe le direzioni.
        val message = "${player.name}|$rolesString|$teamNumber|${player.id}"
        sendMessageToMobile(WearConstants.MSG_SCORER_SELECTED, message)
    }

    /**
     * Spedisce la scelta e la chiude SOLO dopo l'esito, che al polso si sente.
     *
     * Prima si chiedevano i connectedNodes a mano e con zero nodi non succedeva niente: nessuna
     * vibrazione, nessuna scritta, e la schermata si chiudeva come se il nome fosse partito.
     * sendMessage usa lo stesso criterio del pallino e dice se almeno un nodo ha ricevuto.
     */
    private fun sendMessageToMobile(
        path: String,
        message: String,
    ) {
        if (inInvio) return
        // Un secondo tocco durante l'attesa manderebbe un secondo marcatore per lo stesso gol.
        inInvio = true
        lifecycleScope.launch {
            val consegnato = sync.sendMessage(path, message.toByteArray())
            val vibratore = ContextCompat.getSystemService(this@PlayerSelectionActivity, Vibrator::class.java)
            if (consegnato) {
                vibratore?.vibrate(VibrationEffect.createWaveform(HapticFeedbackManager.PATTERN_CONFIRM, -1))
            } else {
                vibratore?.vibrate(VibrationEffect.createWaveform(WearPatterns.NON_CONFERMATO, -1))
                // Il toast sopravvive alla chiusura: chi guarda il polso legge perche' ha vibrato.
                Toast.makeText(this@PlayerSelectionActivity, R.string.wear_scorer_not_sent, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    // Anche la rotazione della corona passa di qui: Activity.dispatchGenericMotionEvent chiama
    // onUserInteraction prima di consegnare l'evento (stessa ragione di MenuActivity).
    override fun onUserInteraction() {
        super.onUserInteraction()
        riarmaChiusura()
    }

    private fun riarmaChiusura() {
        handler.removeCallbacks(chiusuraAutomatica)
        handler.postDelayed(chiusuraAutomatica, CHIUSURA_AUTOMATICA_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        sync.cleanup()
        super.onDestroy()
    }
}

class PlayerAdapter(
    private val coloreBarra: Int,
    private val onPlayerClick: (WearPlayer) -> Unit,
) : RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {
    private var players: List<WearPlayer> = emptyList()

    fun submitList(newPlayers: List<WearPlayer>) {
        players = newPlayers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): PlayerViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_player_wear, parent, false)
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: PlayerViewHolder,
        position: Int,
    ) {
        // La prima voce e' SALTA: grigia e senza barra, perche' non e' di nessuna squadra.
        holder.bind(players[position], coloreBarra, position == 0, onPlayerClick)
    }

    override fun getItemCount() = players.size

    class PlayerViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val playerName: TextView = itemView.findViewById(R.id.player_name)
        private val playerBar: View = itemView.findViewById(R.id.player_bar)

        fun bind(
            player: WearPlayer,
            coloreBarra: Int,
            salta: Boolean,
            onPlayerClick: (WearPlayer) -> Unit,
        ) {
            playerName.text = player.name
            playerName.setTextColor(
                ContextCompat.getColor(itemView.context, if (salta) R.color.sidewalk_gray else R.color.ink_white),
            )
            // INVISIBLE e non GONE: SALTA resta allineato ai nomi.
            playerBar.visibility = if (salta) View.INVISIBLE else View.VISIBLE
            playerBar.setBackgroundColor(coloreBarra)

            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPlayerClick(player)
                }
            }
        }
    }
}
