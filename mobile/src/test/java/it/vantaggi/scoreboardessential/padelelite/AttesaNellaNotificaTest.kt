package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * La notifica fissa quando la diretta non trasmette (debito 14 di D-3): «Diretta in attesa: <motivo>», un motivo breve
 * per caso, in italiano e in inglese, senza apostrofi. La riga del foglio PARTITA tiene la sua frase.
 */
@RunWith(AndroidJUnit4::class)
class AttesaNellaNotificaTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun attesa(motivo: DirettaMotivo?) =
        DirettaStato(DirettaFase.IN_ATTESA, motivo, dettaglio = "Giovanni", gruppo = "Padel", matchId = "m-1")

    private fun notifica(motivo: DirettaMotivo?) = testoDiAttesaInNotifica(context, attesa(motivo))

    @Test
    @Config(qualifiers = "it")
    fun `in italiano dice Diretta in attesa e il motivo breve`() {
        assertEquals("Diretta in attesa: manca la rete", notifica(DirettaMotivo.RETE))
        assertEquals("Diretta in attesa: manca la rete", notifica(null))
        assertEquals("Diretta in attesa: accedi di nuovo", notifica(DirettaMotivo.ACCEDI_DI_NUOVO))
        assertEquals("Diretta in attesa: servono quattro giocatori", notifica(DirettaMotivo.SERVONO_QUATTRO))
        assertEquals("Diretta in attesa: un nome supera i 60 caratteri", notifica(DirettaMotivo.NOME_TROPPO_LUNGO))
        assertEquals("Diretta in attesa: troppe dirette nel gruppo", notifica(DirettaMotivo.TROPPE_DIRETTE))
        assertEquals("Diretta in attesa: nessun codice libero", notifica(DirettaMotivo.CODICE_ESAURITO))
        assertEquals("Diretta in attesa: risposta inattesa del server", notifica(DirettaMotivo.RISPOSTA_INATTESA))
        assertEquals("Diretta in attesa: errore imprevisto", notifica(DirettaMotivo.ERRORE_INATTESO))
        assertEquals("Diretta in attesa: un giocatore è stato sostituito", notifica(DirettaMotivo.GIOCATORE_SOSTITUITO))
    }

    @Test
    @Config(qualifiers = "en")
    fun `in inglese dice Live on hold e il motivo breve`() {
        assertEquals("Live on hold: no network", notifica(DirettaMotivo.RETE))
        assertEquals("Live on hold: sign in again", notifica(DirettaMotivo.ACCEDI_DI_NUOVO))
        assertEquals("Live on hold: four players needed", notifica(DirettaMotivo.SERVONO_QUATTRO))
        assertEquals("Live on hold: a name is over 60 characters", notifica(DirettaMotivo.NOME_TROPPO_LUNGO))
        assertEquals("Live on hold: too many live matches in the group", notifica(DirettaMotivo.TROPPE_DIRETTE))
        assertEquals("Live on hold: no free code", notifica(DirettaMotivo.CODICE_ESAURITO))
        assertEquals("Live on hold: unexpected server answer", notifica(DirettaMotivo.RISPOSTA_INATTESA))
        assertEquals("Live on hold: unexpected error", notifica(DirettaMotivo.ERRORE_INATTESO))
        assertEquals("Live on hold: a player was replaced", notifica(DirettaMotivo.GIOCATORE_SOSTITUITO))
    }

    @Test
    @Config(qualifiers = "it")
    fun `ogni motivo di attesa ha la sua frase, senza apostrofi`() {
        val inAttesa = DirettaMotivo.entries.takeWhile { it != DirettaMotivo.NON_ADMIN }
        val frasi = inAttesa.map(::notifica)

        assertEquals("una frase diversa per motivo", inAttesa.size, frasi.toSet().size)
        frasi.forEach { assertFalse(it, it.contains('\'')) }
    }

    @Test
    @Config(qualifiers = "it")
    fun `il foglio PARTITA non cambia`() {
        val riga = direttaLook(context, attesa(DirettaMotivo.RETE))!!

        assertEquals("Non trasmette: manca la rete. Riprovo da solo", riga.text)
        assertEquals(context.getString(R.string.diretta_wait_network), testoDiAttesa(context, attesa(DirettaMotivo.RETE)))
    }
}
