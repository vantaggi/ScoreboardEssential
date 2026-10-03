package it.vantaggi.scoreboardessential

import it.vantaggi.scoreboardessential.core.MatchEngine
import it.vantaggi.scoreboardessential.core.MatchNarrative
import it.vantaggi.scoreboardessential.core.RacketRules
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportConfig
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * La fusione del registro a schermo con le righe dei game, come funzione pura: le righe dei punti
 * escono, i game entrano dal motore, e le righe informative restano dove erano.
 */
class RegistroAGameTest {
    private fun motore(vararg lati: Int): MatchEngine {
        val engine = MatchEngine(RacketRules(config = SportConfig()))
        lati.forEach { engine.apply(ScoringEvent.Point(it)) }
        return engine
    }

    private fun punto(
        indice: Int,
        lato: Int,
    ) = MatchEvent("", "Goal", team = lato, player = "T$lato", type = MatchEventType.SCORE, engineIndex = indice)

    private fun info(testo: String) = MatchEvent("", testo)

    private fun fusione(
        registroCronologico: List<MatchEvent>,
        engine: MatchEngine,
    ): List<MatchEvent> = registroAGame(registroCronologico.asReversed(), MatchNarrative.of(engine), "Rossi", "Bianchi").asReversed()

    private fun quattro(
        da: Int,
        lato: Int,
    ) = (da until da + 4).map { punto(it, lato) }

    @Test
    fun `senza narrativa il registro e' lo stesso oggetto`() {
        val registro = listOf(punto(0, 1), info("x"))

        assertSame(registro, registroAGame(registro, null, "Rossi", "Bianchi"))
    }

    @Test
    fun `i punti escono e restano i game, col nome del vincitore`() {
        val engine = motore(1, 1, 1, 1, 2, 2, 2, 2, 1, 1)
        val registro = quattro(0, 1) + quattro(4, 2) + listOf(punto(8, 1), punto(9, 1))

        val righe = fusione(registro, engine)

        assertEquals(listOf(MatchEventType.GAME, MatchEventType.GAME), righe.map { it.type })
        assertEquals(listOf("Rossi", "Bianchi"), righe.map { it.player })
        assertEquals(listOf(3, 7), righe.map { it.engineIndex })
        assertEquals(listOf("Game 1", "Game 2"), righe.map { it.event })
    }

    @Test
    fun `una riga informativa sta dopo il punto che la precede e prima di quello che la segue`() {
        val engine = motore(1, 1, 1, 1, 2, 2, 2, 2)
        // "pronta" prima di tutto; "x" a meta' del secondo game, fra il punto 5 e il punto 6.
        val registro = listOf(info("pronta")) + quattro(0, 1) + listOf(punto(4, 2), punto(5, 2), info("x"), punto(6, 2), punto(7, 2))

        val righe = fusione(registro, engine)

        assertEquals(listOf("pronta", "Game 1", "x", "Game 2"), righe.map { it.event })
    }

    @Test
    fun `una riga informativa appena dopo un game sta sopra quel game e sotto il successivo`() {
        val engine = motore(1, 1, 1, 1, 2, 2, 2, 2)
        val registro = quattro(0, 1) + listOf(info("dopo il primo game")) + quattro(4, 2)

        assertEquals(listOf("Game 1", "dopo il primo game", "Game 2"), fusione(registro, engine).map { it.event })
    }

    @Test
    fun `due righe informative di fila tengono il loro ordine`() {
        val engine = motore(1, 1, 1, 1)
        val registro = quattro(0, 1) + listOf(info("a"), info("b"))

        assertEquals(listOf("Game 1", "a", "b"), fusione(registro, engine).map { it.event })
    }

    @Test
    fun `un game riaperto dall'annullamento non ha piu' la riga anche se il registro la ricorda`() {
        val engine = motore(1, 1, 1, 1)
        val registro = quattro(0, 1) + listOf(punto(0, 1))
        engine.undo()

        // Il motore ha tre punti: nessun game chiuso, qualunque righe di punti il registro si porti dietro.
        assertEquals(emptyList<MatchEvent>(), fusione(registro.take(3), engine))
    }

    @Test
    fun `l'ordine e' il cronologico inverso, il piu' recente in testa`() {
        val engine = motore(1, 1, 1, 1, 2, 2, 2, 2)
        val registro = quattro(0, 1) + quattro(4, 2)

        val righe = registroAGame(registro.asReversed(), MatchNarrative.of(engine), "Rossi", "Bianchi")

        assertEquals(listOf("Game 2", "Game 1"), righe.map { it.event })
    }
}
