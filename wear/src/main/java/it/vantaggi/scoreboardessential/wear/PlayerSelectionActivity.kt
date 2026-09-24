package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
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
 */
class PlayerSelectionActivity : ComponentActivity() {
    companion object {
        /** Id della voce di uscita: nessun giocatore vero puo' averlo. */
        private const val NESSUNO = -1

        /**
         * Da dove prende il canale verso il telefono. Sostituibile sotto test per la stessa ragione
         * del ViewModel: i client GMS veri muoiono sul looper di Robolectric.
         */
        @VisibleForTesting
        internal var creaSync: (Context) -> OptimizedWearDataSync = { OptimizedWearDataSync(it) }
    }

    private lateinit var playerList: WearableRecyclerView
    private lateinit var adapter: PlayerAdapter
    private var teamNumber: Int = 1
    private lateinit var sync: OptimizedWearDataSync
    private var inInvio = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_selection)

        sync = creaSync(applicationContext)
        val rawTeamNumber = intent.getIntExtra(WearConstants.EXTRA_TEAM_NUMBER, 1)
        teamNumber = if (WearDataValidator.isValidTeamNumber(rawTeamNumber)) rawTeamNumber else 1

        setupRecyclerView()
        showPlayers(PlayerData.decodeList(intent.getStringExtra(WearDataLayerService.EXTRA_PLAYERS)))
    }

    private fun setupRecyclerView() {
        playerList = findViewById(R.id.player_list)
        adapter = PlayerAdapter { player -> selectPlayer(player) }
        playerList.layoutManager = WearableLinearLayoutManager(this)
        playerList.adapter = adapter
        playerList.isEdgeItemsCenteringEnabled = true
    }

    private fun showPlayers(players: List<PlayerData>) {
        val wearPlayers = players.map { WearPlayer(it.id, it.name, it.roles) }
        // In coda alla rosa una voce per uscire senza attribuire. La schermata si apre DA SOLA
        // dopo ogni gol: senza questa voce l'unico modo di uscire era un gesto di sistema, e chi
        // non sapeva chi avesse segnato doveva comunque scegliere qualcuno.
        adapter.submitList(
            if (wearPlayers.isEmpty()) {
                wearPlayers
            } else {
                wearPlayers + WearPlayer(NESSUNO, getString(R.string.wear_nobody), emptyList())
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
                vibratore?.vibrate(VibrationEffect.createWaveform(WearViewModel.PATTERN_ERRORE, -1))
                // Il toast sopravvive alla chiusura: chi guarda il polso legge perche' ha vibrato.
                Toast.makeText(this@PlayerSelectionActivity, R.string.wear_scorer_not_sent, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    override fun onDestroy() {
        sync.cleanup()
        super.onDestroy()
    }
}

class PlayerAdapter(
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
        holder.bind(players[position], onPlayerClick)
    }

    override fun getItemCount() = players.size

    class PlayerViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val playerName: TextView = itemView.findViewById(R.id.player_name)
        private val playerRole: TextView = itemView.findViewById(R.id.player_role)

        fun bind(
            player: WearPlayer,
            onPlayerClick: (WearPlayer) -> Unit,
        ) {
            playerName.text = player.name

            val rolesText = player.roles.joinToString(", ")
            if (BuildConfig.DEBUG) {
                Log.d("PlayerAdapter", "Binding player")
            }

            if (rolesText.isEmpty()) {
                playerRole.text = itemView.context.getString(R.string.wear_no_role)
                playerRole.visibility = View.GONE
            } else {
                playerRole.text = rolesText
                playerRole.visibility = View.VISIBLE
            }

            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPlayerClick(player)
                }
            }
        }
    }
}
