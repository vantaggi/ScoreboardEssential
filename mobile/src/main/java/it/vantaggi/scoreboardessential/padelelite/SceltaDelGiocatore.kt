package it.vantaggi.scoreboardessential.padelelite

import it.vantaggi.scoreboardessential.database.PadelEliteLink
import it.vantaggi.scoreboardessential.database.Player

/** Le sezioni dell'elenco dei giocatori da mettere in squadra. */
enum class SezioneDellaScelta {
    /** La rosa del gruppo di Padel Elite scelto: si prende da qui senza averla collegata prima. */
    DASHBOARD,

    /** I giocatori che esistono solo nell'app (non collegati in questo gruppo): una partita mista resta possibile. */
    SOLO_APP,

    /** La rosa non si e' potuta leggere: restano i giocatori dell'app, e la sezione lo dice. */
    DASHBOARD_NON_LETTA,
}

/** Una riga dell'elenco: un'intestazione (non si tocca) o un giocatore. */
sealed interface VoceDellaScelta {
    data class Intestazione(
        val sezione: SezioneDellaScelta,
    ) : VoceDellaScelta

    /** Un giocatore della dashboard; [localId] e [nomeLocale] se e' gia' collegato a uno dell'app. */
    data class DallaDashboard(
        val remote: RemotePlayer,
        val localId: Int?,
        val nomeLocale: String?,
    ) : VoceDellaScelta

    data class SoloNellApp(
        val localId: Int,
        val nome: String,
    ) : VoceDellaScelta
}

/**
 * L'elenco del dialogo "Aggiungi un giocatore" quando c'e' un gruppo di Padel Elite: prima la rosa della
 * dashboard (collegati e no, nell'ordine dei nomi), poi chi c'e' solo nell'app. Funzione pura: rosa,
 * giocatori locali e collegamenti del gruppo arrivano da fuori. [esclusi] sono i giocatori dell'app gia' in
 * questa squadra, come nel dialogo di sempre: non si offrono due volte. Una [rosa] null vuol dire "non letta".
 */
object SceltaDelGiocatore {
    fun voci(
        rosa: List<RemotePlayer>?,
        locali: List<Player>,
        collegamenti: List<PadelEliteLink>,
        esclusi: Set<Int>,
    ): List<VoceDellaScelta> {
        val disponibili = locali.filter { it.playerId !in esclusi }
        if (rosa == null) {
            if (disponibili.isEmpty()) return emptyList()
            return listOf(VoceDellaScelta.Intestazione(SezioneDellaScelta.DASHBOARD_NON_LETTA)) +
                disponibili.map { VoceDellaScelta.SoloNellApp(it.playerId, it.playerName) }
        }
        val nomeLocale = locali.associate { it.playerId to it.playerName }
        val perRemoto = collegamenti.associateBy { it.remotePlayerId }
        val dallaDashboard =
            rosa
                .sortedBy { it.name.lowercase() }
                .mapNotNull { remoto ->
                    val locale = perRemoto[remoto.id]?.localPlayerId?.takeIf { it in nomeLocale }
                    if (locale != null && locale in esclusi) {
                        null
                    } else {
                        VoceDellaScelta.DallaDashboard(remoto, locale, locale?.let { nomeLocale[it] })
                    }
                }
        val collegatiQui = collegamenti.map { it.localPlayerId }.toSet()
        val soloApp =
            disponibili
                .filter { it.playerId !in collegatiQui }
                .map { VoceDellaScelta.SoloNellApp(it.playerId, it.playerName) }
        return buildList {
            if (dallaDashboard.isNotEmpty()) {
                add(VoceDellaScelta.Intestazione(SezioneDellaScelta.DASHBOARD))
                addAll(dallaDashboard)
            }
            if (soloApp.isNotEmpty()) {
                add(VoceDellaScelta.Intestazione(SezioneDellaScelta.SOLO_APP))
                addAll(soloApp)
            }
        }
    }
}
