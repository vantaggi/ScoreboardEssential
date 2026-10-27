package it.vantaggi.scoreboardessential

/**
 * Il nome della squadra da mostrare.
 *
 * Senza un nome scelto il tabellone diceva "TEAM 1" contro "TEAM 2" anche con le coppie gia'
 * composte, e a bordo campo non si capiva chi fosse chi. Se il nome e' ancora il predefinito e la
 * rosa ha uno o due giocatori, si mostrano le prime tre lettere dei cognomi: Vantaggi e Glorio
 * diventano "VAN/GLO". Un nome scelto vince sempre; con piu' di due giocatori (il calcio) resta il
 * nome, perche' cinque sigle non entrano nella meta' del tabellone.
 */
object EtichettaSquadra {
    private const val LETTERE = 3
    private const val GIOCATORI_MASSIMI = 2
    private const val SEPARATORE = "/"

    // "TEAM 1" del ViewModel, "Team 1" delle impostazioni, "Squadra 1" se qualcuno l'ha tradotto a mano.
    private val PREDEFINITO = Regex("""(team|squadra)\s*\d+""", RegexOption.IGNORE_CASE)

    fun di(
        nomeScelto: String,
        giocatori: List<String>,
    ): String {
        if (!predefinito(nomeScelto)) return nomeScelto
        if (giocatori.isEmpty() || giocatori.size > GIOCATORI_MASSIMI) return nomeScelto
        val sigle = giocatori.map(::sigla)
        if (sigle.any { it.isEmpty() }) return nomeScelto
        return sigle.joinToString(SEPARATORE)
    }

    fun predefinito(nome: String): Boolean = nome.isBlank() || PREDEFINITO.matches(nome.trim())

    /**
     * Le prime tre lettere del cognome, in maiuscolo. Il cognome e' l'ultima parola: nei giocatori
     * locali e in quelli presi da Padel Elite il nome e' spesso il solo cognome, e "Mattia Vantaggi"
     * deve dare VAN e non MAT. Apostrofi e segni restano fuori ("D'Amico" da DAM).
     */
    fun sigla(nomeGiocatore: String): String {
        val cognome = nomeGiocatore.trim().split(Regex("""\s+""")).last()
        return cognome.filter { it.isLetter() }.take(LETTERE).uppercase()
    }
}
