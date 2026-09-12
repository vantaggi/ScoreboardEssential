package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    /**
     * Spegne i blocchi dell'intestazione come farebbero le capacita' di uno sport, aspetta il
     * layout e ritorna l'altezza della card in dp.
     *
     * Le visibilita' si impostano a mano invece di passare da `selectSport`: cambiare sport
     * scriverebbe la scelta nelle impostazioni del dispositivo (e verrebbe rifiutato a partita
     * iniziata), quindi la misura dipenderebbe da cosa e' rimasto dalla volta prima. Qui si prova
     * il layout, che e' cio' che deve restare compatto; chi spegne cosa lo decide applyCapabilities.
     */
    private fun altezzaIntestazioneDp(conCronometro: Boolean): Pair<Float, Float> {
        var altezza = 0f
        var start = 0f
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val cronometro = if (conCronometro) View.VISIBLE else View.GONE
                activity.findViewById<View>(R.id.match_time_label).visibility = cronometro
                activity.findViewById<View>(R.id.timer_textview).visibility = cronometro
                activity.findViewById<View>(R.id.timer_controls_row).visibility = cronometro
                activity.findViewById<View>(R.id.keeper_timer_label).visibility = View.GONE
                activity.findViewById<View>(R.id.keeper_timer_textview).visibility = View.GONE
                activity.findViewById<View>(R.id.match_period_textview).visibility = View.GONE
                activity.findViewById<View>(R.id.undo_goal_button).visibility = View.GONE
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val densita = activity.resources.displayMetrics.density
                altezza = activity.findViewById<View>(R.id.timer_card).height / densita
                start = activity.findViewById<View>(R.id.timer_start_button).height / densita
            }
        }
        return altezza to start
    }

    @Test
    fun senzaCronometro_l_intestazione_e_una_riga_sola() {
        // Padel e tennis: dentro restano ingranaggio e icona dell'orologio. Prima la card era alta
        // circa 100dp di cui due terzi vuoti, e sta FISSA in cima: ogni dp e' tolto al registro.
        // 80 e non 72: agli angoli tagliati MaterialCardView aggiunge 6dp sopra e sotto (misurato
        // in IntestazioneCronometroTest: 76dp), che una stima fatta sommando l'XML non vede.
        val (altezza, _) = altezzaIntestazioneDp(conCronometro = false)
        assertTrue("l'intestazione senza cronometro e' alta ${altezza}dp, oltre gli 80 ammessi", altezza <= 80f)
    }

    @Test
    fun conCronometro_tempo_e_comandi_stanno_compatti_ma_restano_bersagli() {
        // Calcio: prima etichetta, tempo e pulsanti erano impilati e la card superava i 230dp.
        // Il limite lascia la riga di testa (48), una riga di tempo (etichetta 18 + tempo 52),
        // i margini interni, i 12dp degli angoli della card e un po' di tolleranza per le
        // metriche del carattere: in JVM con i caratteri veri misura 159dp.
        val (altezza, start) = altezzaIntestazioneDp(conCronometro = true)
        assertTrue("l'intestazione col cronometro e' alta ${altezza}dp, oltre i 175 ammessi", altezza <= 175f)
        assertTrue("START e' alto ${start}dp, sotto i 48 minimi", start >= 48f)
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
}
