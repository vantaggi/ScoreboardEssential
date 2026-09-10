package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le statistiche si verificano su partite costruite a mano, di cui la risposta e' calcolabile
 * sulla carta -- non su sequenze casuali di cui si accetta qualunque risultato.
 *
 * La partita di riferimento: padel a set unico, golden point, ordine di servizio [10, 20, 30, 40].
 * Il lato 1 vince VENTIQUATTRO punti consecutivi. Col golden point un game si chiude a 4 punti con
 * uno scarto di 1, quindi sono esattamente sei game: 6-0, set e partita.
 *
 * Da cui, contando a mano:
 *  - il servizio ruota di uno per game, quindi i game 1..6 li servono 10, 20, 30, 40, 10, 20;
 *  - `serveIndex % 2` da' il lato: game dispari al lato 1, game pari al lato 2;
 *  - il lato 1 ha vinto TUTTI i game, quindi ha strappato il servizio nei game 2, 4 e 6 -> tre
 *    break per il lato 1, zero per il lato 2;
 *  - il giocatore 10 serve i game 1 e 5 (otto punti, tutti vinti dal suo lato) -> 100%;
 *    il 20 serve i game 2 e 6 (otto punti, zero vinti dal suo lato) -> 0%.
 */
class MatchSummaryTest {
    private val roster =
        listOf(
            MatchPlayer(10, "Anna", 1),
            MatchPlayer(20, "Bruno", 2),
            MatchPlayer(30, "Carla", 1),
            MatchPlayer(40, "Dario", 2),
        )

    private val labels =
        ReportLabels(
            vince = "Vince",
            durata = "Durata",
            punti = "Punti giocati",
            alServizio = "Al servizio",
            breakVinti = "Break",
            serieMigliore = "Serie migliore",
            marcatori = "Marcatori",
            unitaOre = "h",
            unitaMinuti = "min",
            squadra1 = "Squadra 1",
            squadra2 = "Squadra 2",
        )

    private fun padel(serveOrder: List<Int> = listOf(10, 20, 30, 40)) =
        MatchEngine(
            RacketRules(
                id = SportRegistry.PADEL,
                config =
                    SportConfig(
                        mode = ScoringMode.POINTS,
                        deuce = DeuceRule.GOLDEN_POINT,
                        sets = 1,
                        serveOrder = serveOrder,
                    ),
            ),
        )

    /** Il lato [side] vince [n] punti, uno ogni minuto a partire dal primo. */
    private fun MatchEngine.punti(
        side: Int,
        n: Int,
        daMinuto: Int = 0,
    ): Int {
        repeat(n) { i -> apply(ScoringEvent.Point(side = side), (daMinuto + i + 1) * 60_000L) }
        return daMinuto + n
    }

    @Test
    fun `una vittoria netta a sei game produce il punteggio, il vincitore e la serie`() {
        val engine = padel()
        engine.punti(side = 1, n = 24)

        val s = MatchSummarizer.summarize(engine, roster)

        assertEquals(ReportProfile.RALLY, s.profile)
        assertEquals(listOf(6, 0), s.score)
        assertEquals(1, s.winnerSide)
        assertEquals(24, s.totalPoints)
        assertEquals(24, s.longestStreak?.points)
        assertEquals(1, s.longestStreak?.side)
        // 24 punti, uno al minuto: l'ultimo cade al minuto 24.
        assertEquals(24 * 60_000L, s.durationMillis)
    }

    @Test
    fun `i break contano i game vinti da chi non serviva`() {
        val engine = padel()
        engine.punti(side = 1, n = 24)

        val s = MatchSummarizer.summarize(engine, roster)

        // Il lato 1 ha strappato i game 2, 4 e 6; il lato 2 non ha strappato niente.
        assertEquals(listOf(3, 0), s.breaks)
    }

    @Test
    fun `le percentuali al servizio seguono la rotazione a quattro`() {
        val engine = padel()
        engine.punti(side = 1, n = 24)

        val perId = MatchSummarizer.summarize(engine, roster).serveStats.associateBy { it.playerId }

        assertEquals(8, perId.getValue(10).pointsServed)
        assertEquals(8, perId.getValue(10).pointsWon)
        assertEquals(100, perId.getValue(10).percent)

        assertEquals(8, perId.getValue(20).pointsServed)
        assertEquals(0, perId.getValue(20).pointsWon)
        assertEquals(0, perId.getValue(20).percent)

        assertEquals(4, perId.getValue(30).pointsServed)
        assertEquals(100, perId.getValue(30).percent)
        assertEquals(4, perId.getValue(40).pointsServed)
        assertEquals(0, perId.getValue(40).pointsWon)
    }

    @Test
    fun `senza ordine di servizio restano i break ma spariscono i nomi`() {
        // Il lato al servizio si legge da serveIndex, che il motore tiene comunque: e' lo stesso
        // valore che il tabellone ha mostrato in campo. Senza serveOrder manca il NOME, non il lato.
        val engine = padel(serveOrder = emptyList())
        engine.punti(side = 1, n = 24)

        val s = MatchSummarizer.summarize(engine, roster)

        assertEquals(listOf(3, 0), s.breaks)
        assertTrue("senza ordine non si puo' attribuire il servizio", s.serveStats.isEmpty())
    }

    @Test
    fun `la serie piu' lunga attraversa i confini di game`() {
        val engine = padel()
        engine.punti(side = 1, n = 4) // game 1
        engine.punti(side = 2, n = 7, daMinuto = 4) // game 2, piu' tre punti del game 3
        engine.punti(side = 1, n = 2, daMinuto = 11)

        val s = MatchSummarizer.summarize(engine, roster)

        assertEquals(7, s.longestStreak?.points)
        assertEquals(2, s.longestStreak?.side)
        assertEquals(13, s.totalPoints)
        assertNull("partita non finita", s.winnerSide)
    }

    @Test
    fun `le correzioni non sono punti giocati`() {
        val engine = padel()
        engine.punti(side = 1, n = 3)
        engine.apply(ScoringEvent.Correction(side = 1), 4 * 60_000L)

        val s = MatchSummarizer.summarize(engine, roster)

        // La correzione resta nel log -- serve all'annullamento -- ma non e' un punto.
        assertEquals(3, s.totalPoints)
    }

    @Test
    fun `il calcio non inventa statistiche che non ha`() {
        val engine = MatchEngine(FootballRules)
        repeat(3) { engine.apply(ScoringEvent.Point(side = 1)) }
        engine.apply(ScoringEvent.Point(side = 2))

        val s = MatchSummarizer.summarize(engine, roster, elapsedMillis = 45 * 60_000L)

        assertEquals(ReportProfile.COUNTER, s.profile)
        assertEquals(listOf(3, 1), s.score)
        assertEquals(45 * 60_000L, s.durationMillis)
        assertTrue("il calcio non ha set", s.sets.isEmpty())
        assertTrue("ne' servizio", s.serveStats.isEmpty())
        assertTrue("ne' break: e' una lista vuota, non [0,0]", s.breaks.isEmpty())
    }

    @Test
    fun `i marcatori compaiono solo quando gli eventi li portano`() {
        val engine = MatchEngine(FootballRules)
        engine.apply(ScoringEvent.Point(side = 1, playerId = 10))
        engine.apply(ScoringEvent.Point(side = 1, playerId = 10))
        engine.apply(ScoringEvent.Point(side = 2))

        val s = MatchSummarizer.summarize(engine, roster)

        assertEquals(1, s.scorers.size)
        assertEquals(10, s.scorers[0].playerId)
        assertEquals(2, s.scorers[0].points)
    }

    @Test
    fun `il testo usa le etichette ricevute e il grassetto di WhatsApp`() {
        val engine = padel()
        engine.punti(side = 1, n = 24)

        val testo = MatchSummarizer.format(MatchSummarizer.summarize(engine, roster), labels)

        assertTrue("i nomi del lato 1", testo.contains("Anna"))
        assertTrue("i nomi del lato 2", testo.contains("Bruno"))
        assertTrue("il punteggio in grassetto", testo.contains("*"))
        assertTrue("l'etichetta del vincitore arriva da fuori", testo.contains("Vince"))
        assertTrue("quella della durata pure", testo.contains("Durata"))
        // La prova che il formatter non contiene testo proprio: cambiando le etichette, il testo
        // cambia. Se una parola restasse fissa, questa asserzione la scoprirebbe.
        val inglese = MatchSummarizer.format(MatchSummarizer.summarize(engine, roster), labels.copy(vince = "Winner"))
        assertTrue(inglese.contains("Winner"))
        assertTrue("l'italiano non deve sopravvivere", !inglese.contains("Vince"))
    }

    @Test
    fun `una partita senza punti non produce un report pieno di zeri`() {
        val s = MatchSummarizer.summarize(padel(), roster)

        assertEquals(0, s.totalPoints)
        assertNull(s.winnerSide)
        assertNull(s.longestStreak)
    }

    @Test
    fun `il report resta corto abbastanza per una chat`() {
        val engine = padel()
        engine.punti(side = 1, n = 24)

        val testo = MatchSummarizer.format(MatchSummarizer.summarize(engine, roster), labels)

        // Un messaggio che si legge senza scorrere: se un giorno crescesse oltre questa soglia,
        // vale la pena chiedersi se stia ancora servendo a qualcuno.
        assertTrue("lungo ${testo.length} caratteri", testo.length < 600)
        assertTrue("troppe righe: ${testo.lines().size}", testo.lines().size <= 12)
    }
}
