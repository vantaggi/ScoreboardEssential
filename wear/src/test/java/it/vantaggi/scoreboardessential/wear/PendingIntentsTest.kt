package it.vantaggi.scoreboardessential.wear

import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * La coda e' l'unico posto in cui vive una partita segnata col telefono lontano.
 *
 * Se sbaglia, l'utente perde un'ora di gioco senza che nessun altro test se ne accorga: il
 * punteggio lo calcola il telefono, e il telefono non vede mai cio' che non gli arriva.
 */
@RunWith(RobolectricTestRunner::class)
class PendingIntentsTest {
    private lateinit var coda: PendingIntents

    @Before
    fun setup() {
        val app = RuntimeEnvironment.getApplication()
        app
            .getSharedPreferences("wear_pending_intents", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        coda = PendingIntents(app)
    }

    @Test
    fun `una coda nuova e' vuota`() {
        assertEquals(0, coda.size)
        assertTrue(coda.all().isEmpty())
    }

    @Test
    fun `le voci tornano nell'ordine in cui sono entrate`() {
        coda.add(PendingIntent(WearConstants.INTENT_POINT, 1, 1_000L))
        coda.add(PendingIntent(WearConstants.INTENT_POINT, 2, 2_000L))
        coda.add(PendingIntent(WearConstants.INTENT_CORRECTION, 1, 3_000L))

        val voci = coda.all()
        assertEquals(3, voci.size)
        assertEquals(listOf(1, 2, 1), voci.map { it.side })
        assertEquals(listOf(1_000L, 2_000L, 3_000L), voci.map { it.atMillis })
        assertEquals(WearConstants.INTENT_CORRECTION, voci[2].kind)
    }

    @Test
    fun `la coda sopravvive a una istanza nuova`() {
        coda.add(PendingIntent(WearConstants.INTENT_POINT, 1, 1_000L))
        // E' il caso che conta davvero: l'orologio viene spento e riacceso a meta' partita.
        val riaperta = PendingIntents(RuntimeEnvironment.getApplication())
        assertEquals(1, riaperta.size)
        assertEquals(1_000L, riaperta.all()[0].atMillis)
    }

    @Test
    fun `si rimuove solo cio' che e' stato consegnato`() {
        repeat(5) { i -> coda.add(PendingIntent(WearConstants.INTENT_POINT, 1, (i + 1) * 1_000L)) }

        // Il telefono conferma le prime tre; nel frattempo ne sono arrivate altre due.
        coda.removeFirst(3)

        val restano = coda.all()
        assertEquals(2, restano.size)
        // Svuotare tutto invece di rimuovere per quantita' cancellerebbe anche queste.
        assertEquals(listOf(4_000L, 5_000L), restano.map { it.atMillis })
    }

    @Test
    fun `rimuovere zero o meno non tocca niente`() {
        coda.add(PendingIntent(WearConstants.INTENT_POINT, 1, 1_000L))
        coda.removeFirst(0)
        coda.removeFirst(-3)
        assertEquals(1, coda.size)
    }

    @Test
    fun `una voce illeggibile non porta giu' le altre`() {
        coda.add(PendingIntent(WearConstants.INTENT_POINT, 1, 1_000L))
        val prefs =
            RuntimeEnvironment
                .getApplication()
                .getSharedPreferences("wear_pending_intents", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("queue", "point,1,1000;spazzatura;point,2,2000").commit()

        val voci = PendingIntents(RuntimeEnvironment.getApplication()).all()

        // Due su tre: la riga rotta si salta, la partita non si perde.
        assertEquals(2, voci.size)
        assertEquals(listOf(1, 2), voci.map { it.side })
    }

    @Test
    fun `oltre il tetto si smette di accodare e lo si dice`() {
        repeat(2000) { i -> assertTrue(coda.add(PendingIntent(WearConstants.INTENT_POINT, 1, i.toLong()))) }
        // Il chiamante deve poter distinguere "registrato" da "non registrato": e' la differenza
        // fra la vibrazione che conferma e quella che avverte.
        assertFalse(coda.add(PendingIntent(WearConstants.INTENT_POINT, 1, 9_999L)))
        assertEquals(2000, coda.size)
    }

    /**
     * L5 (bassa): le rifiutate si accumulavano senza tetto a ogni rifiuto. Hanno lo stesso tetto della
     * coda, e oltre si tengono le piu' recenti.
     */
    @Test
    fun `le rifiutate hanno il tetto della coda e si tengono le piu' recenti`() {
        // 2000 rifiutate gia' da parte (i tocchi con istante 0..1999), scritte direttamente: e' lo stato
        // dopo molti rifiuti, senza rifare duemila aggiunte.
        RuntimeEnvironment
            .getApplication()
            .getSharedPreferences("wear_pending_intents", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("rifiutate", (0 until 2000).joinToString(";") { "${WearConstants.INTENT_POINT},1,$it" })
            .commit()
        assertEquals(2000, coda.rifiutateSize)
        listOf(10_000L, 10_001L, 10_002L).forEach { coda.add(PendingIntent(WearConstants.INTENT_POINT, 2, it)) }

        coda.rifiutaCoda()

        assertEquals("il tetto vale anche per le rifiutate", 2000, coda.rifiutateSize)
        val tenute = coda.rifiutate()
        assertEquals("le tre piu' vecchie sono uscite", 3L, tenute.first().atMillis)
        assertEquals("le piu' recenti restano, in ordine", listOf(10_000L, 10_001L, 10_002L), tenute.takeLast(3).map { it.atMillis })
        assertEquals("la coda riparte vuota", 0, coda.size)
    }

    @Test
    fun `sotto il tetto un secondo rifiuto non cancella il primo`() {
        coda.add(PendingIntent(WearConstants.INTENT_POINT, 1, 1_000L))
        coda.rifiutaCoda()
        coda.add(PendingIntent(WearConstants.INTENT_POINT, 2, 2_000L))
        coda.rifiutaCoda()

        assertEquals(listOf(1_000L, 2_000L), coda.rifiutate().map { it.atMillis })
    }
}
