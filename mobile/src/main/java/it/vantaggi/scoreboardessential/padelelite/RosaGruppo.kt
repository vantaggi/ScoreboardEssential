package it.vantaggi.scoreboardessential.padelelite

import it.vantaggi.scoreboardessential.database.PadelEliteLink
import it.vantaggi.scoreboardessential.database.PadelEliteLinkDao
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
import kotlinx.coroutines.flow.first
import java.util.Locale

/** Un giocatore locale che il nome identico fa proporre per un giocatore della dashboard. */
data class NameProposal(
    val remote: RemotePlayer,
    val local: Player,
)

/**
 * I giocatori di un gruppo di Padel Elite e il loro collegamento coi giocatori dell'app (R-1).
 *
 * E' la logica, senza schermata e senza rete: la rosa arriva da [PadelEliteAccount.roster] e qui
 * si decide cosa e' collegato, cosa si propone e cosa si scrive. Tutto per gruppo: un id della
 * dashboard vale solo dentro il suo gruppo. Nessun collegamento nasce in silenzio: [proposals]
 * calcola soltanto, e [linkProposals] gira quando l'utente lo comanda.
 */
class RosaGruppo(
    private val links: PadelEliteLinkDao,
    private val players: PlayerDao,
) {
    suspend fun linksOf(groupId: String): List<PadelEliteLink> = links.linksOf(groupId)

    suspend fun localPlayers(): List<Player> = players.getAllPlayers().first().map { it.player }

    /**
     * Allinea i collegamenti alla rosa appena letta: il nome della dashboard si aggiorna, e un
     * collegamento a un giocatore che la dashboard non ha piu' si toglie. Una rosa vuota non
     * cancella niente: o il gruppo e' davvero vuoto, o la lettura non ha visto le righe (un
     * permesso che manca), e in dubbio un collegamento si tiene. Lo stesso per una rosa di
     * [MAX_ROWS] righe o piu': PostgREST ne restituisce al massimo 1000, quindi puo' essere
     * troncata e chi non c'e' potrebbe solo non essere stato letto; allora si aggiornano i nomi e
     * non si scollega nessuno.
     */
    suspend fun sync(
        groupId: String,
        roster: List<RemotePlayer>,
    ) {
        if (roster.isEmpty()) return
        val troncata = roster.size >= MAX_ROWS
        val perId = roster.associateBy { it.id }
        for (collegamento in links.linksOf(groupId)) {
            val remoto = perId[collegamento.remotePlayerId]
            when {
                remoto == null -> if (!troncata) links.unlinkRemote(groupId, collegamento.remotePlayerId)
                remoto.name != collegamento.remoteName -> links.renameRemote(groupId, remoto.id, remoto.name)
            }
        }
    }

    /** Collega [localPlayerId] a [remote]: toglie prima gli altri collegamenti che occuperebbero le due unicita'. */
    suspend fun link(
        groupId: String,
        remote: RemotePlayer,
        localPlayerId: Int,
    ) = links.link(PadelEliteLink(localPlayerId, groupId, remote.id, remote.name))

    suspend fun unlink(
        groupId: String,
        remote: RemotePlayer,
    ) = links.unlinkRemote(groupId, remote.id)

    /** "Crea in locale": un giocatore dell'app col nome della dashboard, gia' collegato. Restituisce il suo id locale. */
    suspend fun createLocalAndLink(
        groupId: String,
        remote: RemotePlayer,
    ): Int = links.createAndLink(groupId, remote.id, remote.name)

    /** I nomi uguali, da confermare: vedi [proposals]. */
    suspend fun proposals(
        groupId: String,
        roster: List<RemotePlayer>,
    ): List<NameProposal> = proposals(roster, localPlayers(), links.linksOf(groupId))

    /** Applica le proposte che l'utente ha confermato con il comando. Restituisce quanti collegamenti ha scritto. */
    suspend fun linkProposals(
        groupId: String,
        roster: List<RemotePlayer>,
    ): Int {
        val proposte = proposals(groupId, roster)
        proposte.forEach { link(groupId, it.remote, it.local.playerId) }
        return proposte.size
    }

    companion object {
        /** Il tetto di righe di una risposta di PostgREST (`max_rows`): a questo punto la rosa puo' essere troncata. */
        const val MAX_ROWS = 1000

        /** Maiuscole e spazi non contano: "  marco   rossi" e "Marco Rossi" sono lo stesso nome. */
        fun normalizeName(name: String): String = name.filterNot { it.isWhitespace() }.lowercase(Locale.ROOT)

        /**
         * Per ogni giocatore della dashboard non ancora collegato, il giocatore locale non ancora
         * collegato in questo gruppo che ha lo stesso nome (senza maiuscole e spazi). L'unicita' del
         * nome si conta su TUTTA la rosa e su TUTTI i giocatori locali, anche quelli gia' collegati:
         * se un omonimo esiste da una delle due parti (due Marco nella rosa, o due Marco in locale,
         * uno dei quali gia' collegato) non si propone niente, perche' indovinare l'omonimo sbagliato
         * manderebbe una partita sul giocatore sbagliato, e chi ha omonimi li collega a mano.
         */
        fun proposals(
            roster: List<RemotePlayer>,
            locals: List<Player>,
            links: List<PadelEliteLink>,
        ): List<NameProposal> {
            val remotiPerNome = roster.groupBy { normalizeName(it.name) }
            val localiPerNome = locals.groupBy { normalizeName(it.playerName) }
            return roster
                .filter { r -> links.none { it.remotePlayerId == r.id } }
                .mapNotNull { remoto ->
                    val nome = normalizeName(remoto.name)
                    val locale = localiPerNome[nome].orEmpty().singleOrNull()
                    val libero = locale != null && links.none { it.localPlayerId == locale.playerId }
                    if (nome.isNotEmpty() && remotiPerNome[nome].orEmpty().size == 1 && locale != null && libero) {
                        NameProposal(remoto, locale)
                    } else {
                        null
                    }
                }
        }
    }
}
