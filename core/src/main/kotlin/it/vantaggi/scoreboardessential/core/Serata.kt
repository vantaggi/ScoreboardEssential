package it.vantaggi.scoreboardessential.core

/**
 * Una partita composta: due coppie di due giocatori, con l'ordine dei posti.
 *
 * Il primo posto di ogni coppia serve per primo (e' la stessa regola delle rose del foglio PARTITA:
 * l'ordine di servizio A1, B1, A2, B2 si ricava da `squadra1[0], squadra2[0], squadra1[1], squadra2[1]`).
 * I posti si numerano 0..3: 0 e 1 sono la prima coppia, 2 e 3 la seconda.
 *
 * Gli id sono quelli dei giocatori locali. Un ospite e' un giocatore locale come gli altri, quindi
 * qui non c'e' niente di speciale per lui.
 */
data class Composizione(
    val squadra1: List<Int>,
    val squadra2: List<Int>,
) {
    init {
        require(squadra1.size == 2 && squadra2.size == 2) { "Servono due giocatori per coppia" }
        require((squadra1 + squadra2).toSet().size == 4) { "Un giocatore non puo' stare in due posti" }
    }

    /** I quattro id nell'ordine dei posti. */
    val posti: List<Int> get() = squadra1 + squadra2

    /** Le due coppie senza ordine ne' lato: due composizioni con le stesse coppie sono la stessa partita. */
    val coppie: Set<Set<Int>> get() = setOf(squadra1.toSet(), squadra2.toSet())

    /** Scambia i giocatori di due posti (anche fra coppie diverse). */
    fun scambia(
        posto: Int,
        con: Int,
    ): Composizione {
        require(posto in 0..3 && con in 0..3) { "I posti sono da 0 a 3" }
        val nuovi = posti.toMutableList()
        nuovi[posto] = posti[con]
        nuovi[con] = posti[posto]
        return daiPosti(nuovi)
    }

    /** Le due coppie cambiano lato, ciascuna con il suo ordine. */
    fun scambiaLati(): Composizione = Composizione(squadra2, squadra1)

    /** Mette [giocatore], che non gioca, al posto di chi sta in [posto]. */
    fun sostituisci(
        posto: Int,
        giocatore: Int,
    ): Composizione {
        require(posto in 0..3) { "I posti sono da 0 a 3" }
        require(giocatore !in posti) { "Il giocatore gioca gia'" }
        val nuovi = posti.toMutableList()
        nuovi[posto] = giocatore
        return daiPosti(nuovi)
    }

    private fun daiPosti(p: List<Int>) = Composizione(p.subList(0, 2), p.subList(2, 4))
}

/**
 * Chi gioca con chi alla prossima partita di una serata.
 *
 * **La regola** (una sola, scritta anche in `DESIGN.md`):
 *
 * 1. **Chi gioca.** Fra i presenti giocano i quattro che hanno giocato meno partite; a parita' chi
 *    ha giocato piu' tempo fa (chi non ha mai giocato per primo); a parita' chi e' arrivato prima.
 *    Con quattro presenti giocano tutti.
 * 2. **Con chi.** I quattro possono comporre tre coppie di coppie (A+B/C+D, A+C/B+D, A+D/B+C, con
 *    A..D nell'ordine di arrivo). Si sceglie quella che ripete meno coppie gia' fatte; a parita'
 *    quella giocata meno di recente (mai giocata per prima); a parita' la prima nell'ordine di
 *    sopra. Con quattro presenti questo e' un ciclo di tre che non ripete una combinazione prima
 *    di averle fatte tutte.
 * 3. **Posti.** Dentro ogni coppia il primo posto, che serve per primo, e' di chi e' arrivato prima;
 *    la coppia col primo arrivato e' la prima.
 *
 * Funzione pura: dipende solo dai presenti e dalla storia.
 */
object RotazioneSerata {
    const val MINIMO_PRESENTI = 4

    /**
     * La prossima composizione, o null con meno di quattro presenti.
     *
     * [storia] sono le partite da considerare gia' fatte, la piu' vecchia per prima. Chi ha lasciato
     * la serata e' nella storia ma non fra i [presenti]: conta solo chi c'e'.
     */
    fun prossima(
        presenti: List<Int>,
        storia: List<Composizione>,
    ): Composizione? {
        if (presenti.size < MINIMO_PRESENTI) return null
        val giocano = scegliChiGioca(presenti, storia)
        return scegliLeCoppie(giocano, storia)
    }

    /** La panchina: i presenti che non sono in [composizione], nell'ordine di arrivo. */
    fun inPanchina(
        presenti: List<Int>,
        composizione: Composizione?,
    ): List<Int> = presenti.filter { composizione == null || it !in composizione.posti }

    /** Il miglior sostituto in panchina: lo stesso criterio del punto 1 della regola. */
    fun primoDellaPanchina(
        presenti: List<Int>,
        storia: List<Composizione>,
        panchina: List<Int>,
    ): Int? = panchina.sortedWith(ordineDiChiGioca(presenti, storia)).firstOrNull()

    private fun scegliChiGioca(
        presenti: List<Int>,
        storia: List<Composizione>,
    ): List<Int> {
        val scelti = presenti.sortedWith(ordineDiChiGioca(presenti, storia)).take(MINIMO_PRESENTI)
        // Poi nell'ordine di arrivo, che fissa chi e' A, B, C, D.
        return presenti.filter { it in scelti }
    }

    private fun ordineDiChiGioca(
        presenti: List<Int>,
        storia: List<Composizione>,
    ): Comparator<Int> {
        val partite = presenti.associateWith { id -> storia.count { id in it.posti } }
        val ultima = presenti.associateWith { id -> storia.indexOfLast { id in it.posti } }
        val arrivo = presenti.withIndex().associate { (i, id) -> id to i }
        return compareBy<Int>({ partite.getValue(it) }, { ultima.getValue(it) }, { arrivo.getValue(it) })
    }

    private fun scegliLeCoppie(
        quattro: List<Int>,
        storia: List<Composizione>,
    ): Composizione {
        val (a, b, c, d) = quattro
        val candidate =
            listOf(
                Composizione(listOf(a, b), listOf(c, d)),
                Composizione(listOf(a, c), listOf(b, d)),
                Composizione(listOf(a, d), listOf(b, c)),
            )
        val voltePerCoppia = HashMap<Set<Int>, Int>()
        storia.forEach { partita -> partita.coppie.forEach { voltePerCoppia.merge(it, 1, Int::plus) } }
        return candidate
            .withIndex()
            .minWith(
                compareBy<IndexedValue<Composizione>>(
                    { (_, comp) -> comp.coppie.sumOf { voltePerCoppia[it] ?: 0 } },
                    { (_, comp) -> storia.indexOfLast { it.coppie == comp.coppie } },
                    { it.index },
                ),
            ).value
    }
}

/**
 * Una serata: chi c'e', cosa si e' giocato, cosa si gioca dopo.
 *
 * Valore immutabile; ogni comando ne restituisce uno nuovo. Non sa niente di Android, ne' del database:
 * [it.vantaggi.scoreboardessential.core.SerataCodec] la scrive e la rilegge, e l'app la tiene finche'
 * l'utente non la chiude.
 *
 * Cosa vuol dire "modificabile": fra una partita e l'altra si toglie e si aggiunge un presente, si
 * scambiano giocatori e lati, si ruota, si rigioca con le stesse coppie. Mentre una partita e'
 * in corso la composizione e' quella del motore (scambio solo a registro vuoto), e la serata si
 * limita a ricordarla in [inGioco].
 *
 * @property presenti gli id dei giocatori presenti, nell'ordine di arrivo
 * @property giocate le partite giocate davvero, la piu' vecchia per prima
 * @property bozza la prossima partita come e' composta adesso (null con meno di quattro presenti)
 * @property saltate le proposte scartate con [ruota] dall'ultima partita: contano come fatte per
 *   proporre la successiva, cosi' premere Ruota percorre tutte le combinazioni invece di
 *   rimbalzare fra due
 * @property inGioco la composizione consegnata al motore e non ancora chiusa
 */
data class Serata(
    val presenti: List<Int>,
    val giocate: List<Composizione> = emptyList(),
    val bozza: Composizione? = null,
    val saltate: List<Composizione> = emptyList(),
    val inGioco: Composizione? = null,
) {
    init {
        require(presenti.toSet().size == presenti.size) { "Un presente non si ripete" }
    }

    /** Chi non e' nella bozza, nell'ordine di arrivo. */
    val panchina: List<Int> get() = RotazioneSerata.inPanchina(presenti, bozza)

    /** Il numero della partita che si compone adesso (la prima e' 1). */
    val numeroDellaProssima: Int get() = giocate.size + 1

    /** Quante partite ha giocato [id] in questa serata. */
    fun partiteDi(id: Int): Int = giocate.count { id in it.posti }

    private val storia: List<Composizione> get() = giocate + saltate

    /** Aggiunge un presente (e non fa niente se c'e' gia'). Con quattro la bozza nasce. */
    fun aggiungi(id: Int): Serata {
        if (id in presenti) return this
        val nuovi = presenti + id
        val nuovaBozza = bozza ?: RotazioneSerata.prossima(nuovi, storia)
        return copy(presenti = nuovi, bozza = nuovaBozza)
    }

    /**
     * Toglie un presente. Se era nella bozza lo sostituisce il primo della panchina (e senza panchina
     * la bozza decade: servono quattro presenti).
     */
    fun togli(id: Int): Serata {
        if (id !in presenti) return this
        val restanti = presenti - id
        if (restanti.size < RotazioneSerata.MINIMO_PRESENTI) return copy(presenti = restanti, bozza = null)
        val attuale = bozza ?: return copy(presenti = restanti, bozza = RotazioneSerata.prossima(restanti, storia))
        if (id !in attuale.posti) return copy(presenti = restanti)
        val sostituto =
            RotazioneSerata.primoDellaPanchina(restanti, storia, RotazioneSerata.inPanchina(restanti, attuale))
                ?: return copy(presenti = restanti, bozza = RotazioneSerata.prossima(restanti, storia))
        return copy(presenti = restanti, bozza = attuale.sostituisci(attuale.posti.indexOf(id), sostituto))
    }

    /** La rotazione successiva: la bozza di adesso conta come proposta saltata. */
    fun ruota(): Serata {
        if (presenti.size < RotazioneSerata.MINIMO_PRESENTI) return this
        val saltata = if (bozza != null) saltate + bozza else saltate
        val nuova = RotazioneSerata.prossima(presenti, giocate + saltata) ?: return this
        return copy(bozza = nuova, saltate = saltata)
    }

    /** Si rigioca con le coppie dell'ultima partita, se sono ancora tutti presenti. */
    fun stesseCoppie(): Serata {
        val ultima = giocate.lastOrNull() ?: return this
        if (!puoRigiocareLeStesseCoppie()) return this
        return copy(bozza = ultima)
    }

    /** Vero quando c'e' una partita giocata e i suoi quattro sono ancora presenti. */
    fun puoRigiocareLeStesseCoppie(): Boolean = giocate.lastOrNull()?.posti?.all { it in presenti } == true

    /** Scambia due posti della bozza. */
    fun scambia(
        posto: Int,
        con: Int,
    ): Serata = bozza?.let { copy(bozza = it.scambia(posto, con)) } ?: this

    /** Scambia i lati delle due coppie della bozza. */
    fun scambiaLati(): Serata = bozza?.let { copy(bozza = it.scambiaLati()) } ?: this

    /** Manda in panchina chi sta in [posto] e mette al suo posto [giocatore], che sta in panchina. */
    fun sostituisci(
        posto: Int,
        giocatore: Int,
    ): Serata {
        val attuale = bozza ?: return this
        if (giocatore !in panchina) return this
        return copy(bozza = attuale.sostituisci(posto, giocatore))
    }

    /** La bozza va al motore: da qui la partita e' [inGioco]. */
    fun consegna(): Serata = if (bozza == null) this else copy(inGioco = bozza)

    /**
     * La partita in corso e' stata salvata: entra fra le giocate (con le coppie con cui si e' giocato
     * davvero, che possono differire dalla bozza) e si propone la successiva. Se la partita non
     * veniva dalla serata ([giocata] null e niente in gioco) non cambia niente.
     */
    fun chiudiPartita(giocata: Composizione? = inGioco): Serata {
        if (giocata == null) return this
        val tutte = giocate + giocata
        return copy(
            giocate = tutte,
            saltate = emptyList(),
            inGioco = null,
            bozza = RotazioneSerata.prossima(presenti, tutte),
        )
    }

    /** La partita e' stata scartata: non conta, e la bozza resta com'era. */
    fun annullaPartita(): Serata = if (inGioco == null) this else copy(inGioco = null)

    companion object {
        /** Una serata nuova con questi presenti nell'ordine di arrivo. */
        fun nuova(presenti: List<Int>): Serata =
            Serata(presenti = presenti, bozza = RotazioneSerata.prossima(presenti, emptyList()))
    }
}

/**
 * La serata come testo, per ricordarla fra due aperture dell'app.
 *
 * Una riga per campo, `chiave:valore`; le coppie sono `a,b/c,d` e le liste di coppie separate da `;`.
 * Un testo illeggibile vale come serata assente ([decode] torna null): si perde la serata, non si
 * rompe l'apertura dell'app. Il formato ha un numero di versione per poterlo cambiare.
 */
object SerataCodec {
    private const val VERSIONE = "serata1"

    fun encode(serata: Serata): String =
        listOf(
            VERSIONE,
            "p:" + serata.presenti.joinToString(","),
            "g:" + serata.giocate.joinToString(";") { scrivi(it) },
            "s:" + serata.saltate.joinToString(";") { scrivi(it) },
            "b:" + (serata.bozza?.let { scrivi(it) } ?: ""),
            "i:" + (serata.inGioco?.let { scrivi(it) } ?: ""),
        ).joinToString("\n")

    fun decode(testo: String?): Serata? {
        if (testo.isNullOrBlank()) return null
        return try {
            val righe = testo.lines()
            if (righe.firstOrNull() != VERSIONE) return null
            val campi = righe.drop(1).associate { it.substringBefore(':') to it.substringAfter(':', "") }
            Serata(
                presenti = leggiGliId(campi.getValue("p")),
                giocate = leggiLeComposizioni(campi.getValue("g")),
                saltate = leggiLeComposizioni(campi.getValue("s")),
                bozza = leggiUna(campi.getValue("b")),
                inGioco = leggiUna(campi.getValue("i")),
            )
        } catch (_: RuntimeException) {
            // Chiave mancante, numero o coppia mal scritti: un testo che non si legge e' una serata persa.
            null
        }
    }

    private fun scrivi(c: Composizione) = c.squadra1.joinToString(",") + "/" + c.squadra2.joinToString(",")

    private fun leggiGliId(s: String): List<Int> = if (s.isEmpty()) emptyList() else s.split(',').map { it.toInt() }

    private fun leggiUna(s: String): Composizione? {
        if (s.isEmpty()) return null
        val (uno, due) = s.split('/')
        return Composizione(leggiGliId(uno), leggiGliId(due))
    }

    private fun leggiLeComposizioni(s: String): List<Composizione> =
        if (s.isEmpty()) emptyList() else s.split(';').map { checkNotNull(leggiUna(it)) }
}
