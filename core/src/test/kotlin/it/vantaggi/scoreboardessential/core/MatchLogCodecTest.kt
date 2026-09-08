package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchLogCodecTest {
    private fun roundTrip(events: List<LoggedEvent>) {
        val encoded = MatchLogCodec.encode(events)
        assertEquals("round-trip fallito su <$encoded>", events, MatchLogCodec.decode(encoded))
    }

    @Test
    fun roundTripPuntiNudi() {
        roundTrip(
            listOf(
                LoggedEvent(ScoringEvent.Point(side = 1)),
                LoggedEvent(ScoringEvent.Point(side = 2)),
                LoggedEvent(ScoringEvent.Point(side = 2)),
            ),
        )
    }

    @Test
    fun roundTripConPlayerIdETimestamp() {
        roundTrip(
            listOf(
                LoggedEvent(ScoringEvent.Point(side = 1, playerId = 7), atMillis = 0L),
                LoggedEvent(ScoringEvent.Point(side = 2, playerId = 12), atMillis = 45_000L),
                LoggedEvent(ScoringEvent.Point(side = 1), atMillis = 90_500L),
                LoggedEvent(ScoringEvent.Point(side = 2, playerId = 3)),
            ),
        )
    }

    @Test
    fun roundTripConCorrezioniEPesi() {
        roundTrip(
            listOf(
                LoggedEvent(ScoringEvent.Point(side = 1, weight = 3)),
                LoggedEvent(ScoringEvent.Correction(side = 1)),
                LoggedEvent(ScoringEvent.Correction(side = 2), atMillis = 12_000L),
                LoggedEvent(ScoringEvent.Point(side = 2, playerId = 4, weight = 2), atMillis = 13_000L),
            ),
        )
    }

    @Test
    fun listaVuotaMantieneLIntestazione() {
        val encoded = MatchLogCodec.encode(emptyList())
        assertEquals("1|", encoded)
        assertEquals(emptyList<LoggedEvent>(), MatchLogCodec.decode(encoded))
    }

    /** E' il DEFAULT della colonna per ogni partita di calcio gia' salvata: lista vuota, non null. */
    @Test
    fun stringaVuotaEUnLogVuoto() {
        assertEquals(emptyList<LoggedEvent>(), MatchLogCodec.decode(""))
    }

    @Test
    fun versioneFuturaRestituisceNull() {
        assertNull(MatchLogCodec.decode("2|1,2"))
        assertNull(MatchLogCodec.decode("99|1"))
    }

    @Test
    fun intestazioneIllegibileRestituisceNull() {
        assertNull(MatchLogCodec.decode("x|1,2"))
        assertNull(MatchLogCodec.decode("|1,2"))
        assertNull(MatchLogCodec.decode("1,2"))
        assertNull(MatchLogCodec.decode(" "))
    }

    @Test
    fun tokenTroncatoOConCampiNonNumericiRestituisceNull() {
        assertNull(MatchLogCodec.decode("1|1:"))
        assertNull(MatchLogCodec.decode("1|1:@1000"))
        assertNull(MatchLogCodec.decode("1|1:sette"))
        assertNull(MatchLogCodec.decode("1|1@"))
        assertNull(MatchLogCodec.decode("1|1@millis"))
        assertNull(MatchLogCodec.decode("1|c"))
    }

    @Test
    fun sideFuoriRangeRestituisceNull() {
        assertNull(MatchLogCodec.decode("1|3"))
        assertNull(MatchLogCodec.decode("1|0"))
        assertNull(MatchLogCodec.decode("1|12"))
    }

    /** Correction non porta giocatore ne' peso: un token che li dichiara non e' stato scritto da noi. */
    @Test
    fun correzioneConCampiDiPuntoRestituisceNull() {
        assertNull(MatchLogCodec.decode("1|c1:5"))
        assertNull(MatchLogCodec.decode("1|c2*2"))
    }

    @Test
    fun campiFuoriOrdineORipetutiRestituisconoNull() {
        assertNull(MatchLogCodec.decode("1|1@1000:7"))
        assertNull(MatchLogCodec.decode("1|1*2*3"))
        assertNull(MatchLogCodec.decode("1|1?9"))
    }

    /**
     * Scelta esplicita: un token rotto in mezzo a token validi invalida l'intera stringa. Una
     * cronologia a cui manca un punto ricostruirebbe un punteggio sbagliato ma credibile.
     */
    @Test
    fun tokenRottoInMezzoScartaTuttaLaStringa() {
        assertNull(MatchLogCodec.decode("1|1,2,zz,1,2"))
        assertNull(MatchLogCodec.decode("1|1,,2"))
        assertNull(MatchLogCodec.decode("1|1,2,"))
    }

    @Test
    fun trecentoPuntiSenzaTempiStannoSottoIlKilobyte() {
        val events = List(300) { LoggedEvent(ScoringEvent.Point(side = if (it % 2 == 0) 1 else 2)) }
        val bytes = MatchLogCodec.encode(events).toByteArray(Charsets.UTF_8).size
        assertTrue("300 eventi occupano $bytes byte", bytes < 1024)
        roundTrip(events)
    }

    /**
     * Stessa partita con giocatore e cronometro: i timestamp assoluti in millisecondi sono il costo
     * dominante (sette cifre dopo il primo quarto d'ora). Il limite qui e' documentazione della
     * dimensione reale della colonna per il padel, non un obiettivo di compressione.
     */
    @Test
    fun trecentoPuntiConGiocatoreETempiRestanoSottoIQuattroKilobyte() {
        val events =
            List(300) {
                LoggedEvent(
                    ScoringEvent.Point(side = if (it % 2 == 0) 1 else 2, playerId = it % 4 + 1),
                    atMillis = it * 30_000L,
                )
            }
        val bytes = MatchLogCodec.encode(events).toByteArray(Charsets.UTF_8).size
        assertTrue("300 eventi con tempi occupano $bytes byte", bytes < 4096)
        roundTrip(events)
    }
}
