package it.vantaggi.scoreboardessential.padelelite

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** L'elenco del dialogo "Aggiungi un giocatore" con un gruppo di Padel Elite: per quale gruppo e con quali voci. */
data class ElencoDellaScelta(
    val gruppo: String,
    val nomeGruppo: String,
    val voci: List<VoceDellaScelta>,
)

/**
 * I giocatori della dashboard nella scelta dei giocatori di una partita: non serve collegarli prima nella
 * schermata Padel Elite, la copia nell'app nasce (gia' collegata) quando li si sceglie.
 *
 * La rosa si tiene in memoria per gruppo e si rilegge in sottofondo ([prepara], e a ogni apertura del
 * dialogo): chi tocca "Aggiungi" non aspetta la rete, se non la prima volta, e mai piu' di [attesaMassimaMs].
 * Una lettura che non arriva in tempo continua per la volta dopo; intanto l'elenco dice che la dashboard non
 * ha risposto e offre i giocatori dell'app. Solo letture sulla dashboard: le scritture sono nel telefono.
 */
class GiocatoriDalGruppo(
    private val account: PadelEliteAccount,
    private val rosa: RosaGruppo,
    /** Dove vive la lettura: deve poter finire anche dopo che il dialogo ha smesso di aspettarla. */
    private val scope: CoroutineScope,
    private val attesaMassimaMs: Long = ATTESA_MASSIMA_MS,
) {
    private var inMemoria: Pair<String, List<RemotePlayer>>? = null
    private var inVolo: Pair<String, Deferred<List<RemotePlayer>?>>? = null

    /** Il gruppo scelto, solo con l'accesso fatto. La sessione e' cifrata col Keystore: si legge fuori dal thread principale. */
    private suspend fun gruppo(): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            try {
                account.selectedGroup()?.takeIf { account.isEnabled && account.session() != null }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    /** Rilegge la rosa in sottofondo, senza aspettarla: all'apertura del dialogo e' gia' pronta. */
    suspend fun prepara() {
        val g = gruppo() ?: return
        lettura(g.first)
    }

    /** L'elenco per la squadra che ha gia' [esclusi]; null senza accesso o senza gruppo (allora vale il dialogo di sempre). */
    suspend fun elenco(esclusi: Set<Int>): ElencoDellaScelta? {
        val (id, nome) = gruppo() ?: return null
        val letta = inMemoria?.takeIf { it.first == id }?.second
        val nuova = lettura(id)
        val giocatori = letta ?: withTimeoutOrNull(attesaMassimaMs) { nuova.await() }
        val voci = SceltaDelGiocatore.voci(giocatori, rosa.localPlayers(), rosa.linksOf(id), esclusi)
        return ElencoDellaScelta(id, nome, voci)
    }

    suspend fun scegli(
        gruppo: String,
        remote: RemotePlayer,
    ): SceltaDallaDashboard = rosa.scegliPerLaPartita(gruppo, remote)

    suspend fun unisci(
        gruppo: String,
        remote: RemotePlayer,
        localId: Int,
    ): Int = rosa.unisci(gruppo, remote, localId)

    suspend fun creaNuovo(
        gruppo: String,
        remote: RemotePlayer,
    ): Int = rosa.creaNuovo(gruppo, remote)

    /** Una lettura per gruppo alla volta: chi arriva mentre una e' in volo aspetta quella. */
    private fun lettura(groupId: String): Deferred<List<RemotePlayer>?> {
        inVolo?.takeIf { it.first == groupId && it.second.isActive }?.let { return it.second }
        val nuova =
            scope.async {
                val esito =
                    try {
                        account.roster(groupId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        RosterOutcome.Network
                    }
                val giocatori = (esito as? RosterOutcome.Ok)?.players ?: return@async null
                try {
                    // Come la schermata Padel Elite: i nomi cambiati si aggiornano e chi non c'e' piu' si scollega.
                    rosa.sync(groupId, giocatori)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Unit
                }
                inMemoria = groupId to giocatori
                giocatori
            }
        inVolo = groupId to nuova
        return nuova
    }

    companion object {
        /** Quanto il dialogo aspetta la prima lettura della rosa prima di aprirsi senza. */
        const val ATTESA_MASSIMA_MS = 4_000L
    }
}
