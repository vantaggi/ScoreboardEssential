package it.vantaggi.scoreboardessential

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La schermata di gioco, montata davvero.
 *
 * **Perche' e' un test strumentato.** Sotto Robolectric `ActivityScenario.launch` falliva con
 * `PackageParserException`, e le due prove qui sotto vivevano con un `@Ignore` che lo diceva.
 * Montare questa Activity richiede il vero package manager: qui ce l'ha.
 *
 * **Due cose che queste prove NON facevano piu'.**
 *
 * La prima girava su tre `Configuration` diverse, ma le applicava con
 * `activity.resources.configuration.updateFrom(config)` DOPO il launch: cambiare quell'oggetto
 * non rifa' il layout, quindi misurava tre volte la stessa schermata e chiamava "tre dimensioni
 * di schermo" una sola. Per giunta pretendeva 280dp di altezza anche in orizzontale, dove ora
 * `values-land` ne prevede 180 di proposito. Il ciclo e' stato tolto: resta la verifica che il
 * bersaglio primario ci sia e sia grande abbastanza, che e' cio' che davvero non deve regredire.
 *
 * La seconda era racchiusa in un `if (dialogFragment != null)`: se il dialogo NON compariva, il
 * test passava in silenzio. Non poteva fallire, quindi non provava niente -- proprio mentre il
 * listener che lo apre era stato tolto e la card continuava ad accendersi sotto il dito.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLayoutTest {
    /**
     * Spegne il tutorial di primo avvio prima di montare la schermata.
     *
     * Su un'installazione pulita `MainActivity` lancia subito `OnboardingActivity`, che la mette
     * in PAUSA: lo stato del FragmentManager risulta gia' salvato, e un tocco che apre un dialogo
     * non puo' essere eseguito. Senza questo, il test non misurava la schermata di gioco --
     * misurava una schermata gia' coperta da un'altra.
     */
    @Before
    fun spegniIlTutorial() {
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_completed", true)
            .commit()
    }

    @Test
    fun ilComandoPrimario_esiste_ed_e_un_bersaglio_vero() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val sezione = activity.findViewById<View>(R.id.score_section)
                assertTrue("la sezione del punteggio deve essere visibile", sezione.visibility == View.VISIBLE)

                val piu = activity.findViewById<View>(R.id.team1_add_button_card)
                assertTrue("il + deve essere cliccabile", piu.isClickable)
                val minimo = (48 * activity.resources.displayMetrics.density).toInt()
                assertTrue("il + e' largo ${piu.width}px, sotto i $minimo minimi", piu.width >= minimo)
                assertTrue("il + e' alto ${piu.height}px, sotto i $minimo minimi", piu.height >= minimo)
            }
        }
    }

    @Test
    fun toccareIlNomeSquadra_apre_il_dialogo_di_rinomina() {
        // I due contenitori del nome hanno il ripple e una contentDescription che promette di
        // poterli toccare. Per un po' non avevano nessun listener: questa e' la prova che la
        // promessa e' mantenuta, ed e' scritta in modo da diventare rossa se sparisce di nuovo.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.team1_name_container).performClick()
                activity.supportFragmentManager.executePendingTransactions()

                val dialogo = activity.supportFragmentManager.findFragmentByTag(TeamNameDialogFragment.TAG)
                assertNotNull("toccare il nome deve aprire il dialogo di rinomina", dialogo)
                assertTrue("e deve essere un dialogo", dialogo is DialogFragment)
            }
        }
    }

    @Test
    fun inNessunaPosizioneDiScorrimento_unFabCopreIComandiDiFinePartita() {
        // In posizione di riposo la riga HISTORY / END MATCH / SHARE cadeva sotto i FAB. La prova
        // scorre dall'inizio alla fine e a ogni passo pretende due cose: se un comando e' nella
        // fascia di un FAB, quel FAB non e' visibile; se nessun comando lo e', i FAB ci sono.
        // La geometria e' calcolata qui con Rect, non con la funzione che decide nell'activity.
        val azioni = listOf(R.id.match_history_button, R.id.reset_scores_button, R.id.share_match_button)
        val fab = listOf(R.id.stats_fab, R.id.players_fab)
        val strumentazione = InstrumentationRegistry.getInstrumentation()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            strumentazione.waitForIdleSync()
            var scorrimento: NestedScrollView? = null
            var massimo = 0
            scenario.onActivity { activity ->
                // Il contenitore che scorre e' diverso in verticale e in orizzontale: si risale
                // dal pulsante invece di dipendere da un id.
                var genitore = activity.findViewById<View>(R.id.share_match_button).parent
                while (genitore !is NestedScrollView) genitore = (genitore as View).parent
                scorrimento = genitore
                massimo = maxOf(0, genitore.getChildAt(0).height - genitore.height)
            }

            val passi = 12
            for (passo in 0..passi) {
                val y = massimo * passo / passi
                scenario.onActivity { scorrimento!!.scrollTo(0, y) }
                strumentazione.waitForIdleSync()
                // Il tempo delle animazioni di hide()/show().
                Thread.sleep(600)
                strumentazione.waitForIdleSync()

                scenario.onActivity { activity ->
                    // Posizione nella finestra anche per i pulsanti, non l'area visibile: un pulsante
                    // fuori schermo darebbe un rettangolo visibile senza significato.
                    fun rettangolo(id: Int): Rect {
                        val vista = activity.findViewById<View>(id)
                        val xy = IntArray(2)
                        vista.getLocationInWindow(xy)
                        return Rect(xy[0], xy[1], xy[0] + vista.width, xy[1] + vista.height)
                    }
                    val rettangoliAzioni = azioni.map { rettangolo(it) }
                    val coperto = fab.any { f -> rettangoliAzioni.any { Rect.intersects(it, rettangolo(f)) } }
                    for (id in fab) {
                        val visibile = activity.findViewById<View>(id).visibility == View.VISIBLE
                        if (coperto) {
                            assertFalse("a scorrimento $y un FAB copre un comando di fine partita", visibile)
                        } else {
                            assertTrue("a scorrimento $y la riga e' lontana ma il FAB non c'e'", visibile)
                        }
                    }
                }
            }
        }
    }
}
