package it.vantaggi.scoreboardessential.padelelite

import it.vantaggi.scoreboardessential.core.ConfigurazioneSerata
import it.vantaggi.scoreboardessential.core.GironiSalvati
import it.vantaggi.scoreboardessential.core.ProssimaSerata
import it.vantaggi.scoreboardessential.core.Traduzione
import it.vantaggi.scoreboardessential.core.TraduzionePresenti
import it.vantaggi.scoreboardessential.database.PadelEliteLinkDao
import it.vantaggi.scoreboardessential.database.PlayerDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/** Un girone salvato nella dashboard, tradotto come i presenti. */
data class GironeDellaDashboard(
    val traduzione: Traduzione,
)

/**
 * La serata della dashboard come la vede la schermata Serata. [Spenta] vuol dire che la riga non c'e':
 * senza configurazione, senza accesso o senza un gruppo scelto la Serata e' quella di sempre.
 */
sealed interface SerataDashboard {
    data object Spenta : SerataDashboard

    data object InLettura : SerataDashboard

    /** Il gruppo non ha una serata configurata nella dashboard (`venue_config` assente o senza giorno). */
    data object NonConfigurata : SerataDashboard

    /**
     * La serata del [giorno]: i [presenti] confermati, tradotti, e i [gironi] salvati per QUESTA serata.
     * [gironiDiUnAltraSerata] e' vero se c'erano gironi salvati ma per un'altra data: non si usano.
     * [gruppo] e' il gruppo letto: gli ospiti nuovi nascono gia' collegati a lui (null: senza collegamento).
     */
    data class Pronta(
        val giorno: LocalDate,
        val presenti: Traduzione,
        val gironi: List<GironeDellaDashboard> = emptyList(),
        val gironiDiUnAltraSerata: Boolean = false,
        val gruppo: String? = null,
    ) : SerataDashboard

    /** La rete o il server non hanno risposto: si puo' riprovare. */
    data object Errore : SerataDashboard

    /** La sessione e' scaduta e il rinnovo e' stato rifiutato: serve rifare l'accesso (come R-1). */
    data object AccessoScaduto : SerataDashboard

    /** 403: l'account non legge piu' questo gruppo. */
    data object FuoriDalGruppo : SerataDashboard
}

/** Da dove la schermata Serata prende la serata della dashboard. Un'interfaccia: i test ne danno una finta. */
fun interface FonteSerataDashboard {
    /**
     * Vero se c'e' qualcosa da leggere (accesso e gruppo scelto): solo allora la schermata mostra il gruppo e
     * "in lettura". Cosi' chi non ha l'accesso non vede comparire e sparire niente.
     */
    suspend fun attiva(): Boolean = true

    suspend fun leggi(): SerataDashboard
}

/**
 * Legge la prossima serata del gruppo scelto: la configurazione (`venue_config`) per la data, i confermati di
 * quella data (`v2_rsvp`), i gironi salvati (`gironi_serata`) e la rosa del gruppo (`v2_players`, per i nomi
 * di chi non e' collegato); poi traduce gli id coi collegamenti R-1 del gruppo. Solo letture: non scrive
 * niente, ne' nella dashboard ne' nell'app (gli ospiti nascono quando l'utente usa i presenti).
 */
class LettoreSerataDashboard(
    private val account: PadelEliteAccount,
    private val collegamenti: PadelEliteLinkDao,
    private val giocatori: PlayerDao,
    private val adesso: () -> LocalDateTime = { LocalDateTime.now() },
) : FonteSerataDashboard {
    override suspend fun attiva(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                account.isEnabled && account.session() != null && account.selectedGroup() != null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Una sessione che non si decifra vale come nessuna sessione.
                false
            }
        }

    override suspend fun leggi(): SerataDashboard =
        withContext(Dispatchers.IO) {
            try {
                leggiDavvero()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Una sessione che non si decifra, un database che non risponde: per la schermata e' un errore che si
                // puo' riprovare, non un crash nel mezzo della serata.
                SerataDashboard.Errore
            }
        }

    private suspend fun leggiDavvero(): SerataDashboard {
        // La sessione e' cifrata col Keystore: si legge qui, fuori dal thread principale.
        if (!account.isEnabled || account.session() == null) return SerataDashboard.Spenta
        val gruppo = account.selectedGroup()?.first ?: return SerataDashboard.Spenta

        val config: ConfigurazioneSerata =
            when (val esito = account.venueConfig(gruppo)) {
                is LetturaDashboard.Ok -> esito.valore ?: return SerataDashboard.NonConfigurata
                else -> return fallita(esito)
            }
        val giorno = ProssimaSerata.data(adesso(), config)
        val confermati =
            when (val esito = account.confirmed(gruppo, giorno)) {
                is LetturaDashboard.Ok -> esito.valore
                else -> return fallita(esito)
            }
        val salvati: GironiSalvati? =
            when (val esito = account.gironi(gruppo)) {
                is LetturaDashboard.Ok -> esito.valore
                else -> return fallita(esito)
            }
        val nomiDashboard =
            when (val esito = account.roster(gruppo)) {
                is RosterOutcome.Ok -> esito.players.associate { it.id to it.name }
                RosterOutcome.NeedLogin -> return SerataDashboard.AccessoScaduto
                RosterOutcome.NotAuthorized -> return SerataDashboard.FuoriDalGruppo
                RosterOutcome.Network -> return SerataDashboard.Errore
            }

        val perId = collegamenti.linksOf(gruppo).associate { it.remotePlayerId to it.localPlayerId }
        val nomiLocali = giocatori.getAllPlayers().first().associate { it.player.playerId to it.player.playerName }

        fun traduci(ids: List<Int>) = TraduzionePresenti.traduci(ids, nomiDashboard, perId, nomiLocali)

        val valgono = salvati?.takeIf { it.valgonoPer(giorno) }
        return SerataDashboard.Pronta(
            giorno = giorno,
            presenti = traduci(confermati),
            gironi =
                valgono
                    ?.gironi
                    .orEmpty()
                    .map { GironeDellaDashboard(traduci(it)) }
                    .filter { it.traduzione.voci.isNotEmpty() },
            gironiDiUnAltraSerata = salvati != null && valgono == null && salvati.gironi.isNotEmpty(),
            gruppo = gruppo,
        )
    }

    private fun fallita(esito: LetturaDashboard<*>): SerataDashboard =
        when (esito) {
            LetturaDashboard.NotAuthenticated -> SerataDashboard.AccessoScaduto
            LetturaDashboard.NotAuthorized -> SerataDashboard.FuoriDalGruppo
            else -> SerataDashboard.Errore
        }
}
