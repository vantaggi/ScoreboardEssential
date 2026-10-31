package it.vantaggi.scoreboardessential.core

/*
 * Chi serve, letto dal tabellone: le due domande che telefono e polso si fanno uguali. Qui e non in un
 * modulo solo perche' il polso riceve lo stesso ScoreDisplay (in stringhe) e deve rispondere allo
 * stesso modo: una proposta di inizio set che compare su uno e non sull'altro sarebbe un difetto.
 */

/** Il separatore dei set nei secondari di [RacketRules]: middot fra spazi, in escape come la sua. */
private const val SEPARATORE_DEI_SET = " · "

/**
 * Siamo al primo punto di un set, in uno sport che modella il servizio: i punti del game sono 0-0 e
 * cosi' i game del set in corso (l'ultima parte dei secondari, che manca prima del primo game). Nel
 * tie-break i punti ripartono da 0-0 ma i game sono 6-6, quindi non e' un inizio di set.
 */
fun inizioDelSet(display: ScoreDisplay): Boolean {
    if (display.matchOver || display.servingSide == null) return false
    if (display.side1Primary != "0" || display.side2Primary != "0") return false
    val gameDelSet = display.side1Secondary?.split(SEPARATORE_DEI_SET)?.lastOrNull() ?: return true
    return gameDelSet == "0-0"
}

/**
 * Le prime tre lettere del cognome di chi serve, come nel resto dell'app quando si accorcia, o null
 * se non si sa chi e' (nessuna rosa, o una rosa piu' corta del giocatore che serve). Le rose sono
 * nell'ordine del servizio: il primo giocatore di ciascuna e' il primo che serve. Le sigle si
 * distinguono su tutti e quattro, come nei nomi delle coppie.
 */
fun siglaDiChiServe(
    display: ScoreDisplay,
    giocatori1: List<String>,
    giocatori2: List<String>,
): String? = siglaDiChiServe(display.servingSide, display.servingPlayerSlot, giocatori1, giocatori2)

/** Come sopra, dal lato (1 o 2) e dal giocatore (1 o 2, null nel singolare) che servono. */
fun siglaDiChiServe(
    lato: Int?,
    giocatore: Int?,
    giocatori1: List<String>,
    giocatori2: List<String>,
): String? {
    if (lato != 1 && lato != 2) return null
    val squadra = if (lato == 2) giocatori2 else giocatori1
    val indice = if (squadra.size >= 2) (giocatore ?: 1) - 1 else 0
    if (indice !in squadra.indices) return null
    val sigle = EtichettaSquadra.sigle(giocatori1 + giocatori2)
    val posizione = (if (lato == 2) giocatori1.size else 0) + indice
    return sigle.getOrNull(posizione)?.takeIf { it.isNotEmpty() }
}
