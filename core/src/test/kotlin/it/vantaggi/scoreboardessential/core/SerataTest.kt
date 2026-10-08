package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La serata a coppie che ruotano: la regola di rotazione, i comandi del compositore, l'ospite e
 * la persistenza. Tutto su valori, senza Android: gli id sono numeri (1 = A, 2 = B, 3 = C, 4 = D...).
 */
class SerataTest {
    private fun comp(
        a: Int,
        b: Int,
        c: Int,
        d: Int,
    ) = Composizione(listOf(a, b), listOf(c, d))

    /** Le coppie senza ordine: la partita come la vedono i giocatori. */
    private fun Composizione?.coppie(): Set<Set<Int>> = this!!.coppie

    private fun coppie(
        a: Int,
        b: Int,
        c: Int,
        d: Int,
    ): Set<Set<Int>> = setOf(setOf(a, b), setOf(c, d))

    /** Gioca [n] partite proposte una dopo l'altra, come chi preme sempre "Inizia la partita". */
    private fun Serata.gioca(n: Int): Serata = (1..n).fold(this) { s, _ -> s.consegna().chiudiPartita() }

    // ---- Con quattro presenti: tre combinazioni in ciclo ----

    @Test
    fun `con quattro presenti le tre combinazioni escono in ciclo senza ripetersi prima del giro`() {
        var serata = Serata.nuova(listOf(1, 2, 3, 4))
        val proposte = mutableListOf<Set<Set<Int>>>()
        repeat(6) {
            proposte += serata.bozza.coppie()
            serata = serata.consegna().chiudiPartita()
        }
        assertEquals(
            listOf(coppie(1, 2, 3, 4), coppie(1, 3, 2, 4), coppie(1, 4, 2, 3)),
            proposte.take(3),
        )
        // Il giro ricomincia dalla prima: ogni gruppo di tre ha le tre combinazioni diverse.
        assertEquals(proposte.take(3), proposte.drop(3))
        assertEquals(3, proposte.take(3).toSet().size)
    }

    @Test
    fun `la prima proposta ha i primi arrivati insieme e il primo arrivato serve per primo`() {
        val s = Serata.nuova(listOf(10, 20, 30, 40))
        assertEquals(comp(10, 20, 30, 40), s.bozza)
        // Ordine di servizio A1, B1, A2, B2: primo posto della prima coppia, primo posto della seconda...
        val b = s.bozza!!
        assertEquals(listOf(10, 30, 20, 40), listOf(b.squadra1[0], b.squadra2[0], b.squadra1[1], b.squadra2[1]))
    }

    @Test
    fun `Ruota percorre tutte e tre le combinazioni e torna alla prima`() {
        var s = Serata.nuova(listOf(1, 2, 3, 4))
        val viste = mutableListOf(s.bozza.coppie())
        repeat(3) {
            s = s.ruota()
            viste += s.bozza.coppie()
        }
        assertEquals(
            listOf(coppie(1, 2, 3, 4), coppie(1, 3, 2, 4), coppie(1, 4, 2, 3), coppie(1, 2, 3, 4)),
            viste,
        )
    }

    @Test
    fun `Ruota non conta come partita giocata e la prossima dopo una partita riparte dalla storia vera`() {
        // Ruota due volte: la bozza e' la terza combinazione, ma non e' stata giocata niente.
        val s = Serata.nuova(listOf(1, 2, 3, 4)).ruota().ruota()
        assertEquals(0, s.giocate.size)
        assertEquals(1, s.numeroDellaProssima)
        // Si gioca davvero quella: la proposta dopo e' la prima, che nessuno ha ancora fatto.
        val dopo = s.consegna().chiudiPartita()
        assertEquals(coppie(1, 2, 3, 4), dopo.bozza.coppie())
        assertTrue(dopo.saltate.isEmpty())
    }

    @Test
    fun `stesse coppie rigioca l'ultima partita come era, con i posti`() {
        val s = Serata.nuova(listOf(1, 2, 3, 4)).consegna().chiudiPartita()
        assertNotEquals(s.giocate.last(), s.bozza)
        val stesse = s.stesseCoppie()
        assertEquals(s.giocate.last(), stesse.bozza)
        // Ed e' una partita vera per la regola: la successiva non la ripete e riprende il giro.
        val dopo = stesse.consegna().chiudiPartita()
        assertEquals(coppie(1, 3, 2, 4), dopo.bozza.coppie())
    }

    @Test
    fun `stesse coppie non c'e' senza partite giocate, ne' se uno dei quattro se n'e' andato`() {
        val nuova = Serata.nuova(listOf(1, 2, 3, 4, 5))
        assertFalse(nuova.puoRigiocareLeStesseCoppie())
        assertEquals(nuova, nuova.stesseCoppie())
        val giocata = nuova.consegna().chiudiPartita()
        assertTrue(giocata.puoRigiocareLeStesseCoppie())
        val uno =
            giocata.giocate
                .last()
                .posti
                .first()
        assertFalse(giocata.togli(uno).puoRigiocareLeStesseCoppie())
    }

    // ---- Con cinque e sei presenti: chi ha giocato meno ----

    @Test
    fun `con cinque presenti chi e' stato fermo gioca, e dopo cinque partite hanno giocato tutti quattro volte`() {
        var s = Serata.nuova(listOf(1, 2, 3, 4, 5))
        assertEquals(listOf(5), s.panchina)
        assertEquals(setOf(1, 2, 3, 4), s.bozza!!.posti.toSet())
        s = s.consegna().chiudiPartita()
        // Il 5 non aveva giocato: gioca, e con lui chi era fermo da piu' tempo (a parita', chi e' arrivato prima).
        assertTrue(5 in s.bozza!!.posti)
        assertEquals(setOf(5, 1, 2, 3), s.bozza!!.posti.toSet())
        repeat(4) { s = s.consegna().chiudiPartita() }
        // Cinque partite da quattro posti fanno venti posti: quattro a testa.
        assertEquals(5, s.giocate.size)
        assertEquals(listOf(4, 4, 4, 4, 4), (1..5).map { s.partiteDi(it) })
    }

    @Test
    fun `con sei presenti alla terza partita hanno giocato tutti due volte`() {
        val s = Serata.nuova(listOf(1, 2, 3, 4, 5, 6)).gioca(3)
        assertEquals(listOf(2, 2, 2, 2, 2, 2), (1..6).map { s.partiteDi(it) })
        // E nelle tre partite nessuna coppia si e' ripetuta: sei giocatori, nove posti di coppia possibili.
        val tutte = s.giocate.flatMap { it.coppie }
        assertEquals(tutte.size, tutte.toSet().size)
    }

    @Test
    fun `con piu' di quattro presenti la differenza di partite fra due giocatori non supera una`() {
        var s = Serata.nuova((1..7).toList())
        repeat(12) {
            s = s.consegna().chiudiPartita()
            val giocate = (1..7).map { s.partiteDi(it) }
            assertTrue("dopo ${it + 1} partite: $giocate", giocate.max() - giocate.min() <= 1)
        }
    }

    // ---- Aggiunta e uscita a meta' serata ----

    @Test
    fun `un presente che arriva a meta' serata entra in panchina e poi gioca per primo`() {
        var s = Serata.nuova(listOf(1, 2, 3, 4)).gioca(2)
        val bozzaPrima = s.bozza
        s = s.aggiungi(5)
        assertEquals(listOf(1, 2, 3, 4, 5), s.presenti)
        // La bozza gia' composta non si rompe: chi arriva sta in panchina finche' non la si cambia.
        assertEquals(bozzaPrima, s.bozza)
        assertEquals(listOf(5), s.panchina)
        // Dopo quella partita il nuovo, che non ha giocato, gioca.
        assertTrue(
            5 in
                s
                    .consegna()
                    .chiudiPartita()
                    .bozza!!
                    .posti,
        )
    }

    @Test
    fun `aggiungere due volte lo stesso presente non lo raddoppia`() {
        val s = Serata.nuova(listOf(1, 2, 3, 4))
        assertEquals(s, s.aggiungi(3))
    }

    @Test
    fun `chi esce dalla bozza viene sostituito dal primo della panchina e il resto non si tocca`() {
        val s = Serata.nuova(listOf(1, 2, 3, 4, 5)).gioca(1)
        // Dopo la prima partita la bozza e' 5, 1, 2, 3 in qualche ordine; il 4 e' in panchina.
        assertEquals(listOf(4), s.panchina)
        val senzaUno = s.togli(1)
        assertEquals(listOf(2, 3, 4, 5), senzaUno.presenti)
        assertEquals(setOf(2, 3, 4, 5), senzaUno.bozza!!.posti.toSet())
        // Sta nello stesso posto di chi e' uscito.
        assertEquals(s.bozza!!.posti.indexOf(1), senzaUno.bozza!!.posti.indexOf(4))
        assertTrue(senzaUno.panchina.isEmpty())
    }

    @Test
    fun `chi esce dalla panchina non cambia la bozza`() {
        val s = Serata.nuova(listOf(1, 2, 3, 4, 5))
        val senzaCinque = s.togli(5)
        assertEquals(s.bozza, senzaCinque.bozza)
        assertEquals(listOf(1, 2, 3, 4), senzaCinque.presenti)
    }

    @Test
    fun `con tre presenti non c'e' bozza, e al quarto torna`() {
        val s = Serata.nuova(listOf(1, 2, 3, 4)).gioca(1)
        val tre = s.togli(2)
        assertNull(tre.bozza)
        assertTrue(tre.panchina.size == 3)
        // Fermi: ruota, scambi e stesse coppie non fanno niente e non rompono.
        assertEquals(tre, tre.ruota())
        assertEquals(tre, tre.scambiaLati())
        assertEquals(tre, tre.scambia(0, 1))
        val quattro = tre.aggiungi(9)
        assertEquals(setOf(1, 3, 4, 9), quattro.bozza!!.posti.toSet())
    }

    @Test
    fun `chi esce a meta' serata resta nella storia ma non gioca piu', e la rotazione va avanti con gli altri`() {
        var s = Serata.nuova(listOf(1, 2, 3, 4, 5)).gioca(2)
        s = s.togli(3)
        repeat(4) {
            s = s.consegna().chiudiPartita()
            assertFalse(3 in s.bozza!!.posti)
        }
        assertEquals("le sue due partite restano nella storia", 2, s.partiteDi(3))
    }

    // ---- Ospite ----

    @Test
    fun `un ospite e' un id come gli altri, entra in rotazione e conta le sue partite`() {
        // L'ospite e' un Player creato al volo: qui e' l'id 99, arrivato per ultimo.
        var s = Serata.nuova(listOf(1, 2, 3, 4)).aggiungi(99)
        assertEquals(listOf(99), s.panchina)
        s = s.consegna().chiudiPartita()
        assertTrue(99 in s.bozza!!.posti)
        s = s.consegna().chiudiPartita()
        assertEquals(1, s.partiteDi(99))
        // E se se ne va, la serata continua senza di lui.
        val dopo = s.togli(99)
        assertFalse(99 in dopo.presenti)
    }

    // ---- Comandi del compositore ----

    @Test
    fun `scambiare due posti dentro la coppia cambia chi serve per primo`() {
        val s = Serata.nuova(listOf(1, 2, 3, 4)).scambia(0, 1)
        assertEquals(Composizione(listOf(2, 1), listOf(3, 4)), s.bozza)
    }

    @Test
    fun `scambiare due posti fra coppie le rimescola`() {
        val s = Serata.nuova(listOf(1, 2, 3, 4)).scambia(1, 2)
        assertEquals(Composizione(listOf(1, 3), listOf(2, 4)), s.bozza)
    }

    @Test
    fun `scambio di lato porta ogni coppia dall'altra parte con i suoi posti`() {
        val s = Serata.nuova(listOf(1, 2, 3, 4)).scambiaLati()
        assertEquals(Composizione(listOf(3, 4), listOf(1, 2)), s.bozza)
        assertEquals(Serata.nuova(listOf(1, 2, 3, 4)), s.scambiaLati())
    }

    @Test
    fun `scambio con la panchina manda fuori chi stava nel posto e fa entrare chi aspettava`() {
        val s = Serata.nuova(listOf(1, 2, 3, 4, 5)).sostituisci(2, 5)
        assertEquals(Composizione(listOf(1, 2), listOf(5, 4)), s.bozza)
        assertEquals(listOf(3), s.panchina)
    }

    @Test
    fun `scambiare con uno che non e' in panchina non fa niente`() {
        val s = Serata.nuova(listOf(1, 2, 3, 4, 5))
        assertEquals(s, s.sostituisci(0, 2))
        assertEquals(s, s.sostituisci(0, 77))
    }

    @Test
    fun `la partita giocata registra le coppie vere, anche se la bozza era stata cambiata dopo la consegna`() {
        val s = Serata.nuova(listOf(1, 2, 3, 4)).consegna()
        val giocata = comp(2, 1, 4, 3)
        val dopo = s.chiudiPartita(giocata)
        assertEquals(listOf(giocata), dopo.giocate)
        assertNull(dopo.inGioco)
    }

    @Test
    fun `scartare la partita non la conta e lascia la bozza`() {
        val s = Serata.nuova(listOf(1, 2, 3, 4)).consegna()
        assertEquals(s.bozza, s.inGioco)
        val scartata = s.annullaPartita()
        assertNull(scartata.inGioco)
        assertEquals(s.bozza, scartata.bozza)
        assertTrue(scartata.giocate.isEmpty())
    }

    @Test
    fun `chiudere una partita che non veniva dalla serata non cambia la serata`() {
        val s = Serata.nuova(listOf(1, 2, 3, 4))
        assertEquals(s, s.chiudiPartita())
    }

    @Test
    fun `una composizione rifiuta posti doppi e coppie incomplete`() {
        val doppio = runCatching { comp(1, 1, 2, 3) }
        assertTrue(doppio.isFailure)
        assertTrue(runCatching { Composizione(listOf(1), listOf(2, 3, 4)) }.isFailure)
    }

    // ---- Persistenza ----

    @Test
    fun `la serata scritta e riletta e' la stessa, a meta' serata con tutto dentro`() {
        var s =
            Serata
                .nuova(listOf(11, 22, 33, 44, 55))
                .gioca(2)
                .ruota()
                .aggiungi(66)
        s = s.consegna().scambiaLati()
        assertEquals(s, SerataCodec.decode(SerataCodec.encode(s)))
        // Anche vuota e con meno di quattro presenti.
        val vuota = Serata(presenti = emptyList())
        assertEquals(vuota, SerataCodec.decode(SerataCodec.encode(vuota)))
        val tre = Serata(presenti = listOf(1, 2, 3))
        assertEquals(tre, SerataCodec.decode(SerataCodec.encode(tre)))
    }

    @Test
    fun `un testo che non si legge e' una serata assente, non un'eccezione`() {
        assertNull(SerataCodec.decode(null))
        assertNull(SerataCodec.decode(""))
        assertNull(SerataCodec.decode("altro"))
        assertNull(SerataCodec.decode("serata1\np:1,2,x"))
        assertNull(SerataCodec.decode("serata1\np:1,2,3,4\ng:1,2/3\ns:\nb:\ni:"))
        assertNull(SerataCodec.decode("serata1\np:1,2,3,4\ng:\ns:\nb:1,2,3,4\ni:"))
        // Un presente doppio e' un dato rotto.
        assertNull(SerataCodec.decode("serata1\np:1,1,2,3\ng:\ns:\nb:\ni:"))
    }

    @Test
    fun `la serata ripresa dopo il riavvio continua dalla stessa rotazione`() {
        val prima = Serata.nuova(listOf(1, 2, 3, 4)).gioca(2)
        val ripresa = SerataCodec.decode(SerataCodec.encode(prima))!!
        assertEquals(prima.bozza, ripresa.bozza)
        assertEquals(coppie(1, 4, 2, 3), ripresa.bozza.coppie())
        assertEquals(
            coppie(1, 2, 3, 4),
            ripresa
                .consegna()
                .chiudiPartita()
                .bozza
                .coppie(),
        )
    }

    // ---- La regola in se' ----

    @Test
    fun `la rotazione e' una funzione dei presenti e della storia`() {
        val storia = listOf(comp(1, 2, 3, 4), comp(1, 3, 2, 4))
        assertEquals(RotazioneSerata.prossima(listOf(1, 2, 3, 4), storia), RotazioneSerata.prossima(listOf(1, 2, 3, 4), storia))
        assertEquals(coppie(1, 4, 2, 3), RotazioneSerata.prossima(listOf(1, 2, 3, 4), storia).coppie())
        assertNull(RotazioneSerata.prossima(listOf(1, 2, 3), storia))
    }
}
