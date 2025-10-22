package it.vantaggi.scoreboardessential.domain.models

import it.vantaggi.scoreboardessential.database.PlayerWithRoles

/**
* Rappresenta una formazione tattica
*/
data class Formation(
val goalkeeper: List<PlayerWithRoles>,
val defenders: List<PlayerWithRoles>,
val midfielders: List<PlayerWithRoles>,
val forwards: List<PlayerWithRoles>
) {
/**
* Ritorna la formazione in formato stringa (es. "4-4-2")
*/
fun getFormationString(): String {
return "${defenders.size}-${midfielders.size}-${forwards.size}"
}

/**
* Verifica se la formazione è valida (almeno un portiere)
*/
fun isValid(): Boolean {
return goalkeeper.isNotEmpty()
}

companion object {
/**
* Crea una formazione dai giocatori, raggruppandoli per categoria di ruolo
*/
fun fromPlayers(players: List<PlayerWithRoles>): Formation {
val goalkeeper = mutableListOf<PlayerWithRoles>()
val defenders = mutableListOf<PlayerWithRoles>()
val midfielders = mutableListOf<PlayerWithRoles>()
val forwards = mutableListOf<PlayerWithRoles>()

players.forEach { player ->
// Prendi il primo ruolo come principale
val primaryRole = player.roles.firstOrNull()
when (primaryRole?.category) {
"PORTA" -> goalkeeper.add(player)
"DIFESA" -> defenders.add(player)
"CENTROCAMPO" -> midfielders.add(player)
"ATTACCO" -> forwards.add(player)
else -> {
// Fallback: se non ha ruolo, metti a centrocampo
if (player.roles.isEmpty()) midfielders.add(player)
}
}
}

return Formation(
goalkeeper = goalkeeper,
defenders = defenders,
midfielders = midfielders,
forwards = forwards
)
}
}
}