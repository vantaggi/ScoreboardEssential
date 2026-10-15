package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * La serata dalla dashboard, senza rete ne' Android: la data della prossima serata con la regola di
 * `js/nextmatch.js`, la durata e l'ora come le legge la dashboard, i gironi di un'altra serata e la
 * traduzione degli id della dashboard in giocatori della serata.
 *
 * Il calendario di prova: giovedi' 15 ottobre 2026. Martedi' e' 2, giovedi' 4, sabato 6, domenica 0.
 */
class SerataDallaDashboardTest {
    private val giovedi = LocalDate.of(2026, 10, 15)

    private fun alle(
        ore: Int,
        minuti: Int = 0,
        giorno: LocalDate = giovedi,
    ): LocalDateTime = giorno.atTime(ore, minuti)

    private fun ilGiovedi(
        inizio: LocalTime = LocalTime.of(19, 0),
        durata: Int = 90,
    ) = ConfigurazioneSerata(giorno = 4, inizio = inizio, durataMinuti = durata)

    // ---- Data della prossima serata ----

    @Test
    fun `oggi prima della fine della prima prenotazione la serata e' oggi`() {
        assertEquals(giovedi, ProssimaSerata.data(alle(10), ilGiovedi()))
        // Durante il gioco e' ancora la serata di oggi.
        assertEquals(giovedi, ProssimaSerata.data(alle(20, 0), ilGiovedi()))
    }

    @Test
    fun `alla fine esatta e' ancora oggi, un minuto dopo e' la settimana dopo`() {
        // 19:00 + 90 minuti = 20:30. La dashboard passa alla settimana dopo solo strettamente dopo.
        assertEquals(giovedi, ProssimaSerata.data(alle(20, 30), ilGiovedi()))
        assertEquals(giovedi.plusDays(7), ProssimaSerata.data(alle(20, 31), ilGiovedi()))
    }

    @Test
    fun `un altro giorno della settimana e' il prossimo di quel giorno, anche oltre la domenica`() {
        // Sabato 17, domenica 18, martedi' 20.
        assertEquals(LocalDate.of(2026, 10, 17), ProssimaSerata.data(alle(23), ConfigurazioneSerata(giorno = 6)))
        assertEquals(LocalDate.of(2026, 10, 18), ProssimaSerata.data(alle(23), ConfigurazioneSerata(giorno = 0)))
        assertEquals(LocalDate.of(2026, 10, 20), ProssimaSerata.data(alle(23), ConfigurazioneSerata(giorno = 2)))
        // Mercoledi' 14 ottobre: la serata del giovedi' e' domani, anche a tarda sera.
        assertEquals(giovedi, ProssimaSerata.data(alle(23, 59, LocalDate.of(2026, 10, 14)), ilGiovedi()))
    }

    @Test
    fun `il cambio di settimana passa il mese e l'anno`() {
        // Giovedi' 31 dicembre 2026 dopo la serata: giovedi' 7 gennaio 2027.
        val capodanno = LocalDate.of(2026, 12, 31)
        assertEquals(LocalDate.of(2027, 1, 7), ProssimaSerata.data(alle(22, giorno = capodanno), ilGiovedi()))
    }

    @Test
    fun `la domenica e' il giorno zero anche quando oggi e' domenica`() {
        val domenica = LocalDate.of(2026, 10, 18)
        val laDomenica = ConfigurazioneSerata(giorno = 0)
        assertEquals(domenica, ProssimaSerata.data(alle(9, giorno = domenica), laDomenica))
        assertEquals(domenica.plusDays(7), ProssimaSerata.data(alle(21, giorno = domenica), laDomenica))
    }

    @Test
    fun `una prenotazione che passa la mezzanotte tiene la serata fino alla sua fine di oggi`() {
        // 23:00 + 90 = 00:30 di domani: alle 23:59 di giovedi' la serata e' ancora oggi.
        assertEquals(giovedi, ProssimaSerata.data(alle(23, 59), ilGiovedi(inizio = LocalTime.of(23, 0))))
    }

    // ---- Durata e ora come le legge la dashboard ----

    @Test
    fun `la durata mancante o illeggibile vale 90 minuti, il resto come parseInt`() {
        assertEquals(90, ProssimaSerata.durata(null))
        assertEquals(90, ProssimaSerata.durata("lunga"))
        assertEquals(90, ProssimaSerata.durata(true))
        assertEquals(60, ProssimaSerata.durata("60m"))
        assertEquals(120, ProssimaSerata.durata(" 120"))
        assertEquals(75, ProssimaSerata.durata(75))
        assertEquals(45, ProssimaSerata.durata(45.0))
    }

    @Test
    fun `la durata mancante allunga la serata di 90 minuti`() {
        val senzaDurata = ConfigurazioneSerata(giorno = 4, inizio = LocalTime.of(19, 0), durataMinuti = ProssimaSerata.durata(null))
        assertEquals(giovedi, ProssimaSerata.data(alle(20, 30), senzaDurata))
        assertEquals(giovedi.plusDays(7), ProssimaSerata.data(alle(20, 31), senzaDurata))
        // Con 60 minuti, alle 20:15 e' gia' finita.
        assertEquals(giovedi.plusDays(7), ProssimaSerata.data(alle(20, 15), ilGiovedi(durata = ProssimaSerata.durata("60m"))))
    }

    @Test
    fun `l'ora si legge da HH-MM, e mancante o sbagliata vale le 19`() {
        assertEquals(LocalTime.of(21, 30), ProssimaSerata.ora("21:30"))
        assertEquals(LocalTime.of(7, 5), ProssimaSerata.ora("7:05"))
        assertEquals(LocalTime.of(19, 0), ProssimaSerata.ora(null))
        assertEquals(LocalTime.of(19, 0), ProssimaSerata.ora("sera"))
        assertEquals(LocalTime.of(19, 0), ProssimaSerata.ora("25:00"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un giorno fuori da 0-6 non e' una configurazione`() {
        ConfigurazioneSerata(giorno = 7)
    }

    // ---- Gironi di un'altra serata ----

    @Test
    fun `i gironi valgono solo per la serata che dicono`() {
        val salvati = GironiSalvati("2026-10-15", listOf(listOf(1, 2, 3, 4)))
        assertTrue(salvati.valgonoPer(giovedi))
        assertFalse("quelli della settimana prima si ignorano", salvati.valgonoPer(giovedi.plusDays(7)))
        assertFalse(GironiSalvati("2026-10-08", salvati.gironi).valgonoPer(giovedi))
    }

    // ---- Traduzione degli id ----

    private val rosa = mapOf(10 to "Vantaggi", 11 to "Trinari", 12 to "Nudi", 13 to "Porcacchia")

    @Test
    fun `i collegati diventano i giocatori locali col nome dell'app, gli altri ospiti col nome della dashboard`() {
        val traduzione =
            TraduzionePresenti.traduci(
                ids = listOf(12, 10, 13),
                nomiDashboard = rosa,
                collegamenti = mapOf(10 to 1, 12 to 3),
                nomiLocali = mapOf(1 to "Mattia", 3 to "Nudi"),
            )

        assertEquals(
            listOf(
                PresenteDellaDashboard.Collegato(12, "Nudi", 3),
                PresenteDellaDashboard.Collegato(10, "Mattia", 1),
                PresenteDellaDashboard.Ospite(13, "Porcacchia"),
            ),
            traduzione.voci,
        )
        assertEquals(1, traduzione.ospiti)
        assertEquals(0, traduzione.nonTrovati)
    }

    @Test
    fun `un collegamento a un giocatore locale che non c'e' piu' fa entrare l'ospite`() {
        val traduzione = TraduzionePresenti.traduci(listOf(10), rosa, collegamenti = mapOf(10 to 99), nomiLocali = emptyMap())
        assertEquals(listOf(PresenteDellaDashboard.Ospite(10, "Vantaggi")), traduzione.voci)
    }

    @Test
    fun `un id senza collegamento e senza nome resta fuori e si conta, un doppione vale una volta`() {
        val traduzione = TraduzionePresenti.traduci(listOf(10, 77, 10, 11), rosa + (11 to "  "), emptyMap(), emptyMap())
        assertEquals(listOf(PresenteDellaDashboard.Ospite(10, "Vantaggi")), traduzione.voci)
        assertEquals("77 non e' nella rosa, 11 ha il nome vuoto", 2, traduzione.nonTrovati)
    }

    @Test
    fun `l'ospite riusa il giocatore locale con lo stesso nome, come l'ospite scritto a mano`() {
        val traduzione = TraduzionePresenti.traduci(listOf(13), rosa, emptyMap(), nomiLocali = mapOf(7 to " porcacchia "))
        assertEquals(listOf(PresenteDellaDashboard.Ospite(13, "Porcacchia", omonimo = 7)), traduzione.voci)
    }

    @Test
    fun `l'omonimo collegato a un altro giocatore della dashboard, o non unico, non si riusa`() {
        // Il Nudi locale (3) e' collegato a un altro giocatore della dashboard (99): la partita andrebbe a lui.
        val collegatoAltrove = TraduzionePresenti.traduci(listOf(12), rosa, mapOf(99 to 3), mapOf(3 to "Nudi"))
        assertEquals(listOf(PresenteDellaDashboard.Ospite(12, "Nudi", omonimo = null)), collegatoAltrove.voci)
        // Due Nudi in locale: non si indovina.
        val dueNudi = TraduzionePresenti.traduci(listOf(12), rosa, emptyMap(), mapOf(3 to "Nudi", 4 to "nudi"))
        assertEquals(null, (dueNudi.voci.single() as PresenteDellaDashboard.Ospite).omonimo)
    }

    @Test
    fun `due presenti con lo stesso nome non prendono lo stesso giocatore locale`() {
        val traduzione =
            TraduzionePresenti.traduci(listOf(20, 21), mapOf(20 to "Marco", 21 to "Marco"), emptyMap(), mapOf(5 to "Marco"))
        assertEquals(
            listOf(PresenteDellaDashboard.Ospite(20, "Marco", omonimo = 5), PresenteDellaDashboard.Ospite(21, "Marco", omonimo = null)),
            traduzione.voci,
        )
    }

    // ---- I presenti della dashboard nella serata ----

    @Test
    fun `una serata vuota prende i presenti nel loro ordine e compone la bozza`() {
        val serata = Serata(presenti = emptyList()).conIPresenti(listOf(5, 6, 7, 8, 9))
        assertEquals(listOf(5, 6, 7, 8, 9), serata.presenti)
        assertEquals(Serata.nuova(listOf(5, 6, 7, 8, 9)).bozza, serata.bozza)
    }

    @Test
    fun `una serata gia' giocata tiene le partite, perde gli assenti e aggiunge i nuovi in coda`() {
        val giocata = Serata.nuova(listOf(1, 2, 3, 4)).consegna().chiudiPartita()

        val nuova = giocata.conIPresenti(listOf(2, 1, 4, 5, 5))

        assertEquals(listOf(1, 2, 4, 5), nuova.presenti)
        assertEquals(giocata.giocate, nuova.giocate)
        assertEquals(setOf(1, 2, 4, 5), nuova.bozza!!.posti.toSet())
    }

    @Test
    fun `con meno di quattro presenti la bozza non c'e'`() {
        val serata = Serata.nuova(listOf(1, 2, 3, 4)).conIPresenti(listOf(1, 2, 3))
        assertEquals(listOf(1, 2, 3), serata.presenti)
        assertEquals(null, serata.bozza)
    }
}
