package it.vantaggi.scoreboardessential.wear

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La riga di stato e' una tabella: un caso per ogni livello di priorita', piu' i conflitti fra
 * livelli vicini, che sono dove una funzione del genere sbaglia.
 *
 * Kotlin puro, niente Robolectric: la funzione non conosce Android.
 */
class StatoFiduciaTest {
    /** Un polso collegato, senza niente da dire: il caso di base, da cui ogni riga si scosta di una cosa. */
    private val tranquillo =
        InputFiducia(
            collegato = true,
            verificaInCorso = false,
            inCoda = 0,
            collegatoConCodaDaMs = 0,
            matchOver = false,
            decrementIsUndo = false,
            ultimoStatoVivoAlle = null,
            transitorio = null,
        )

    private class Caso(
        val nome: String,
        val input: InputFiducia,
        val atteso: Frase,
    )

    private fun verifica(casi: List<Caso>) {
        casi.forEach { assertEquals(it.nome, it.atteso, StatoFiducia.calcola(it.input)) }
    }

    @Test
    fun `un caso per ogni livello di priorita'`() {
        verifica(
            listOf(
                Caso("1 rifiutati", tranquillo.copy(rifiutati = 3), Frase.Rifiutati(3)),
                Caso("2 transitorio", tranquillo.copy(transitorio = Transitorio.NonConfermato), Transitorio.NonConfermato),
                Caso(
                    "3 collegato, coda da 10s",
                    tranquillo.copy(inCoda = 2, collegatoConCodaDaMs = 10_000),
                    Frase.NonConsegnati(2),
                ),
                Caso(
                    "4 collegato, coda da meno di 10s",
                    tranquillo.copy(inCoda = 2, collegatoConCodaDaMs = 9_999),
                    Frase.Invio(2),
                ),
                Caso("5 scollegato con coda", tranquillo.copy(collegato = false, inCoda = 2), Frase.InCoda(2)),
                Caso(
                    "6 scollegato, coda vuota, con l'ora",
                    tranquillo.copy(collegato = false, ultimoStatoVivoAlle = 1_000L),
                    Frase.Scollegato(1_000L),
                ),
                Caso("6 scollegato, coda vuota, senza ora", tranquillo.copy(collegato = false), Frase.Scollegato(null)),
                Caso("7 partita finita", tranquillo.copy(matchOver = true), Frase.PartitaFinita),
                Caso("8 suggerimento del meno uno", tranquillo, Frase.TieniMeno),
                Caso("8 suggerimento dell'annulla", tranquillo.copy(decrementIsUndo = true), Frase.TieniAnnulla),
            ),
        )
    }

    @Test
    fun `i quattro messaggi transitori sono frasi a se'`() {
        verifica(
            listOf(
                Caso("consegnati", tranquillo.copy(transitorio = Transitorio.Consegnati(4)), Transitorio.Consegnati(4)),
                Caso("chiusura", tranquillo.copy(transitorio = Transitorio.Chiusura), Transitorio.Chiusura),
                Caso("non chiusa", tranquillo.copy(transitorio = Transitorio.ChiusuraNonConfermata), Transitorio.ChiusuraNonConfermata),
            ),
        )
    }

    @Test
    fun `coda e scollegato danno IN CODA anche se il conto da collegati era vecchio`() {
        // Il conto "da collegati" non si azzera da solo nella funzione: lo fa il chiamante. Qui si
        // prova che la funzione non si fida di un conto vecchio quando il telefono e' sparito.
        val frase =
            StatoFiducia.calcola(
                tranquillo.copy(collegato = false, inCoda = 2, collegatoConCodaDaMs = 60_000),
            )

        assertEquals(Frase.InCoda(2), frase)
    }

    @Test
    fun `la coda da collegati per 10s da' NON CONSEGNATI e prima di allora INVIO`() {
        verifica(
            listOf(
                Caso("appena collegato", tranquillo.copy(inCoda = 1, collegatoConCodaDaMs = 0), Frase.Invio(1)),
                Caso("un attimo prima", tranquillo.copy(inCoda = 1, collegatoConCodaDaMs = 9_999), Frase.Invio(1)),
                Caso("alla soglia", tranquillo.copy(inCoda = 1, collegatoConCodaDaMs = 10_000), Frase.NonConsegnati(1)),
                Caso("ben oltre", tranquillo.copy(inCoda = 1, collegatoConCodaDaMs = 600_000), Frase.NonConsegnati(1)),
            ),
        )
    }

    @Test
    fun `la verifica in corso non mostra SCOLLEGATO ne' IN CODA`() {
        verifica(
            listOf(
                Caso(
                    "scollegato, coda vuota",
                    tranquillo.copy(collegato = false, verificaInCorso = true),
                    Frase.TieniMeno,
                ),
                Caso(
                    "scollegato, con coda",
                    tranquillo.copy(collegato = false, verificaInCorso = true, inCoda = 2),
                    Frase.TieniMeno,
                ),
                Caso(
                    "scollegato, partita finita",
                    tranquillo.copy(collegato = false, verificaInCorso = true, matchOver = true),
                    Frase.PartitaFinita,
                ),
                Caso(
                    "finita la verifica dice SCOLLEGATO",
                    tranquillo.copy(collegato = false, verificaInCorso = false),
                    Frase.Scollegato(null),
                ),
            ),
        )
    }

    @Test
    fun `la verifica non nasconde la coda da collegati`() {
        // Con il telefono raggiungibile la coda bloccata si dice sempre: la verifica riguarda solo
        // la parola "scollegato".
        val frase =
            StatoFiducia.calcola(
                tranquillo.copy(verificaInCorso = true, inCoda = 3, collegatoConCodaDaMs = 12_000),
            )

        assertEquals(Frase.NonConsegnati(3), frase)
    }

    @Test
    fun `partita finita senza anomalie dice PARTITA FINITA`() {
        assertEquals(Frase.PartitaFinita, StatoFiducia.calcola(tranquillo.copy(matchOver = true, decrementIsUndo = true)))
    }

    @Test
    fun `un'anomalia vince su PARTITA FINITA`() {
        verifica(
            listOf(
                Caso(
                    "scollegato",
                    tranquillo.copy(matchOver = true, collegato = false),
                    Frase.Scollegato(null),
                ),
                Caso(
                    "coda in viaggio",
                    tranquillo.copy(matchOver = true, inCoda = 1),
                    Frase.Invio(1),
                ),
                Caso(
                    "transitorio",
                    tranquillo.copy(matchOver = true, transitorio = Transitorio.Chiusura),
                    Transitorio.Chiusura,
                ),
            ),
        )
    }

    @Test
    fun `i rifiutati vincono su tutto`() {
        val frase =
            StatoFiducia.calcola(
                tranquillo.copy(
                    rifiutati = 1,
                    transitorio = Transitorio.NonConfermato,
                    inCoda = 2,
                    collegatoConCodaDaMs = 20_000,
                ),
            )

        assertEquals(Frase.Rifiutati(1), frase)
    }

    @Test
    fun `il transitorio vince su coda e scollegato`() {
        val frase =
            StatoFiducia.calcola(
                tranquillo.copy(collegato = false, inCoda = 2, transitorio = Transitorio.Consegnati(2)),
            )

        assertEquals(Transitorio.Consegnati(2), frase)
    }

    @Test
    fun `i colori seguono il significato`() {
        assertEquals(Tono.ROSSO, Transitorio.NonConfermato.tono)
        assertEquals(Tono.ROSSO, Frase.Rifiutati(1).tono)
        assertEquals(Tono.AMBRA, Frase.NonConsegnati(1).tono)
        assertEquals(Tono.AMBRA, Frase.InCoda(1).tono)
        assertEquals(Tono.AMBRA, Frase.Scollegato(null).tono)
        assertEquals(Tono.AMBRA, Transitorio.ChiusuraNonConfermata.tono)
        assertEquals(Tono.CHIARO, Frase.Invio(1).tono)
        assertEquals(Tono.CHIARO, Frase.PartitaFinita.tono)
        assertEquals(Tono.CHIARO, Transitorio.Consegnati(1).tono)
        assertEquals(Tono.CHIARO, Transitorio.Chiusura.tono)
        assertEquals(Tono.GRIGIO, Frase.TieniMeno.tono)
        assertEquals(Tono.GRIGIO, Frase.TieniAnnulla.tono)
    }
}
