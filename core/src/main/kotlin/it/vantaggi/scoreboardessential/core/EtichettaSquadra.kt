package it.vantaggi.scoreboardessential.core

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

    /**
     * [avversari] sono gli altri in campo: le sigle si distinguono su tutti, cosi' "Prova 1" e "Prova 2"
     * non fanno "PRO/PRO" (vedi [sigle]).
     */
    fun di(
        nomeScelto: String,
        giocatori: List<String>,
        avversari: List<String> = emptyList(),
    ): String {
        if (!predefinito(nomeScelto)) return nomeScelto
        if (giocatori.isEmpty() || giocatori.size > GIOCATORI_MASSIMI) return nomeScelto
        val sigle = sigle(giocatori + avversari).take(giocatori.size)
        if (sigle.any { it.isEmpty() }) return nomeScelto
        return sigle.joinToString(SEPARATORE)
    }

    fun predefinito(nome: String): Boolean = nome.isBlank() || PREDEFINITO.matches(nome.trim())

    /**
     * Il nome da scrivere dove non c'e' una rosa da cui prendere le sigle (storico, impostazioni,
     * anteprima del colore). Il predefinito resta "Team 1" nei dati, perche' e' il segnale che dice
     * "nessun nome scelto" anche all'orologio e alle partite gia' salvate; a schermo diventa il
     * predefinito della lingua in uso, cosi' lo storico non dice "Team 1" e la Cronaca "Squadra 1".
     */
    fun mostrato(
        nome: String?,
        predefinitoLocale: String,
    ): String = if (nome == null || predefinito(nome)) predefinitoLocale else nome

    /**
     * Le prime tre lettere del cognome, in maiuscolo. Il cognome e' la prima parola: nella rosa i
     * giocatori sono scritti col solo cognome (come nella dashboard) o cognome e nome, e "Ferrario
     * Stefano" deve dare FER e non STE. Apostrofi e segni restano fuori ("D'Amico" da DAM).
     */
    fun sigla(nomeGiocatore: String): String = cognome(nomeGiocatore).take(LETTERE).uppercase()

    /**
     * Le sigle di chi e' in campo insieme, diverse fra loro dove si puo'. Al giro sui telefoni di prova
     * "Prova 1".."Prova 4" davano "PRO/PRO - PRO/PRO" e non si capiva chi fosse chi; succede anche con
     * cognomi che cominciano uguale. Chi si ripete prende una quarta lettera del cognome (Rossi e Rossetti:
     * ROSS e ROSS, Bianchi e Bianco: BIAN e BIAN restano uguali), poi le prime tre piu' l'iniziale della
     * seconda parola (PRO1, PRO2; ROSM, ROSL per Rossi Mario e Rossi Luca). Due nomi identici restano uguali.
     * Sigle piu' corte di cosi' sul polso entrano gia'; piu' lunghe no.
     */
    fun sigle(nomi: List<String>): List<String> {
        val risultato = nomi.map(::sigla).toMutableList()
        val quattro = { nome: String -> cognome(nome).take(LETTERE + 1).uppercase() }
        val passi =
            listOf(
                quattro,
                { nome -> iniziale(nome).takeIf { it.isNotEmpty() }?.let { sigla(nome) + it } ?: quattro(nome) },
            )
        for (passo in passi) {
            val ripetute =
                risultato
                    .filter { it.isNotEmpty() }
                    .groupingBy { it }
                    .eachCount()
                    .filterValues { it > 1 }
                    .keys
            if (ripetute.isEmpty()) break
            for (i in nomi.indices) {
                if (risultato[i] in ripetute) risultato[i] = passo(nomi[i])
            }
        }
        return risultato
    }

    private fun parole(nome: String): List<String> = nome.trim().split(Regex("""\s+"""))

    private fun cognome(nome: String): String = parole(nome).first().filter { it.isLetter() }

    /** La prima lettera o cifra della seconda parola, in maiuscolo; niente se c'e' una parola sola. */
    private fun iniziale(nome: String): String =
        parole(nome)
            .drop(1)
            .firstOrNull()
            ?.firstOrNull { it.isLetterOrDigit() }
            ?.uppercase()
            .orEmpty()
}
