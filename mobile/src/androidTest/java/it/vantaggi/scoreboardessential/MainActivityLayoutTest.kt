package it.vantaggi.scoreboardessential

import android.Manifest
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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

    /**
     * Concede il permesso delle notifiche prima di montare la schermata.
     *
     * Senza, da Android 13 MainActivity chiede il permesso con un dialogo di sistema che la mette
     * in pausa: le prove con tocchi e indietro veri (Espresso) non trovavano nessuna activity attiva.
     */
    @Before
    fun concediLeNotifiche() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val strumentazione = InstrumentationRegistry.getInstrumentation()
            strumentazione.uiAutomation.grantRuntimePermission(
                strumentazione.targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
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

    private fun statoDelFoglio(scenario: ActivityScenario<MainActivity>): Int {
        var stato = -1
        scenario.onActivity { activity ->
            stato = BottomSheetBehavior.from(activity.findViewById<View>(R.id.match_sheet)).state
        }
        return stato
    }

    /**
     * Aspetta che il foglio si fermi nello stato atteso. L'animazione del behavior non e' una
     * risorsa che Espresso conosce, quindi si interroga lo stato fino a 5 secondi.
     */
    private fun aspettaStato(
        scenario: ActivityScenario<MainActivity>,
        atteso: Int,
    ): Int {
        val strumentazione = InstrumentationRegistry.getInstrumentation()
        var stato = statoDelFoglio(scenario)
        var tentativi = 50
        while (stato != atteso && tentativi-- > 0) {
            Thread.sleep(100)
            strumentazione.waitForIdleSync()
            stato = statoDelFoglio(scenario)
        }
        return stato
    }

    @Test
    fun ilFoglioPartita_e_nascosto_all_avvio() {
        // Mentre si gioca la schermata e' solo la colonna di gioco: rose, registro e azioni
        // stanno nel foglio, che non deve coprire le zone da toccare finche' nessuno lo chiede.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(BottomSheetBehavior.STATE_HIDDEN, statoDelFoglio(scenario))
        }
    }

    @Test
    fun ilPulsantePartita_apre_il_foglio() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.match_sheet_button)).perform(click())
            assertEquals(
                "il pulsante della barra deve aprire il foglio del tutto",
                BottomSheetBehavior.STATE_EXPANDED,
                aspettaStato(scenario, BottomSheetBehavior.STATE_EXPANDED),
            )
        }
    }

    @Test
    fun indietro_chiude_prima_il_foglio_e_poi_esce() {
        // Senza il callback, indietro a foglio aperto chiudeva l'app: si perdeva la schermata
        // di gioco proprio mentre si guardava il registro.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.match_sheet_button)).perform(click())
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, aspettaStato(scenario, BottomSheetBehavior.STATE_EXPANDED))

            pressBack()
            assertEquals(
                "il primo indietro deve richiudere il foglio",
                BottomSheetBehavior.STATE_HIDDEN,
                aspettaStato(scenario, BottomSheetBehavior.STATE_HIDDEN),
            )
            assertEquals("e l'app deve restare aperta", Lifecycle.State.RESUMED, scenario.state)

            // Il secondo esce: pressBackUnconditionally perche' pressBack fallisce se l'app si chiude.
            pressBackUnconditionally()
            var tentativi = 50
            while (scenario.state != Lifecycle.State.DESTROYED && tentativi-- > 0) Thread.sleep(100)
            assertEquals("a foglio chiuso indietro deve uscire", Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun nelPadel_le_rose_restano_per_assegnare_i_giocatori() {
        // Nel padel la card delle rose era spenta insieme alle formazioni: il pulsante "aggiungi
        // giocatore" sta li' dentro, quindi nessuno poteva assegnare i quattro giocatori ai lati e
        // l'export verso Padel Elite era sempre incompleto.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val applica =
                    MainActivity::class.java.getDeclaredMethod(
                        "applyCapabilities",
                        it.vantaggi.scoreboardessential.core.SportCapabilities::class.java,
                    )
                applica.isAccessible = true
                applica.invoke(
                    activity,
                    it.vantaggi.scoreboardessential.core.SportRegistry
                        .byId(it.vantaggi.scoreboardessential.core.SportRegistry.PADEL)
                        .capabilities,
                )
            }
            // Le rose vivono nel foglio PARTITA. Non basta isShown: il foglio nascosto resta VISIBLE,
            // solo fuori schermo, quindi isShown sarebbe vero anche a foglio chiuso. Si apre il
            // foglio e si controlla che il pulsante cada davvero dentro lo schermo.
            onView(withId(R.id.match_sheet_button)).perform(click())
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, aspettaStato(scenario, BottomSheetBehavior.STATE_EXPANDED))
            scenario.onActivity { activity ->
                assertTrue(
                    "nel padel la card delle rose deve restare visibile",
                    activity.findViewById<View>(R.id.rosters_card).visibility == View.VISIBLE,
                )
                assertTrue(
                    "e il pulsante per aggiungere un giocatore deve vedersi a foglio aperto",
                    activity.findViewById<View>(R.id.add_team1_player_button).getGlobalVisibleRect(Rect()),
                )
                assertTrue(
                    "le formazioni invece restano solo del calcio",
                    activity.findViewById<View>(R.id.formations_card).visibility == View.GONE,
                )
            }
        }
    }

    /** Porta in fondo il foglio aperto, dove stanno gli ultimi pulsanti. */
    private fun scorriInFondo(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.findViewById<NestedScrollView>(R.id.match_sheet).fullScroll(View.FOCUS_DOWN) }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(500)
    }

    private fun yInFinestra(vista: View): Int = IntArray(2).also { vista.getLocationInWindow(it) }[1]

    @Test
    fun ilFoglioAperto_sta_fra_la_barra_di_stato_e_quella_di_navigazione() {
        // Gli inset erano padding di main_root, ma il behavior posiziona il foglio su
        // parent.getHeight() e lo misura togliendo quel padding: il fondo finiva sotto la barra di
        // navigazione (tagliato da clipToPadding) e in alto restava una striscia piu' alta della
        // barra di stato. Qui si misura in coordinate di finestra, dove non si puo' barare.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.match_sheet_button)).perform(click())
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, aspettaStato(scenario, BottomSheetBehavior.STATE_EXPANDED))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(500)

            // In alto il foglio sale fino al bordo, senza strisce dove si vede la colonna di gioco,
            // e il suo contenuto sta sotto le icone di stato.
            scenario.onActivity { activity ->
                val foglio = activity.findViewById<View>(R.id.match_sheet)
                val stato = ViewCompat.getRootWindowInsets(foglio)!!.getInsets(WindowInsetsCompat.Type.statusBars()).top
                assertTrue("l'emulatore non ha una barra di stato: niente da misurare", stato > 0)
                assertEquals("il foglio aperto deve arrivare al bordo dello schermo, senza strisce", 0, yInFinestra(foglio))
                val cimaTitolo = yInFinestra(activity.findViewById<View>(R.id.match_sheet_title))
                assertTrue("il titolo comincia a ${cimaTitolo}px, dentro la barra di stato che arriva a ${stato}px", cimaTitolo >= stato)
            }

            // In basso l'ultimo pulsante sta sopra la barra di navigazione.
            scorriInFondo(scenario)
            scenario.onActivity { activity ->
                val foglio = activity.findViewById<View>(R.id.match_sheet)
                val navigazione = ViewCompat.getRootWindowInsets(foglio)!!.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                assumeTrue("l'emulatore non ha una barra di navigazione: niente da misurare", navigazione > 0)
                val altezzaFinestra = activity.window.decorView.height
                // Il pulsante piu' in basso: AZZERA TEMPO c'e' solo nello sport con l'orologio.
                val ultimo =
                    listOf(R.id.sheet_reset_timer_button, R.id.sheet_settings_button)
                        .map { activity.findViewById<View>(it) }
                        .first { it.visibility == View.VISIBLE }
                val fondo = yInFinestra(ultimo) + ultimo.height
                assertTrue(
                    "l'ultimo pulsante finisce a ${fondo}px, sotto la barra di navigazione che inizia a ${altezzaFinestra - navigazione}px",
                    fondo <= altezzaFinestra - navigazione,
                )
            }
        }
    }

    @Test
    fun ilFoglioAperto_tiene_il_taglio_agli_angoli() {
        // In material 1.13 shouldRemoveExpandedCorners vale true di default: a foglio EXPANDED il
        // taglio da 12dp di DESIGN.md spariva, cioe' proprio quando il foglio si legge.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.match_sheet_button)).perform(click())
            aspettaStato(scenario, BottomSheetBehavior.STATE_EXPANDED)
            scenario.onActivity { activity ->
                val comportamento = BottomSheetBehavior.from(activity.findViewById<View>(R.id.match_sheet))
                assertFalse("il taglio agli angoli non deve sparire a foglio aperto", comportamento.isShouldRemoveExpandedCorners)
            }
        }
    }

    private fun importanzaColonna(scenario: ActivityScenario<MainActivity>): Int {
        var importanza = -1
        scenario.onActivity { importanza = it.findViewById<View>(R.id.scoreboard_live).importantForAccessibility }
        return importanza
    }

    @Test
    fun ilFoglioAperto_scurisce_e_isola_la_colonna_di_gioco() {
        // A foglio aperto la colonna restava toccabile e raggiungibile da TalkBack: un doppio tocco
        // segnava un punto sotto il foglio.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(
                    "a foglio nascosto lo scrim non deve coprire la colonna",
                    activity.findViewById<View>(R.id.match_sheet_scrim).visibility != View.VISIBLE,
                )
            }
            assertTrue(
                "a foglio nascosto la colonna resta per TalkBack",
                importanzaColonna(scenario) != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
            )

            onView(withId(R.id.match_sheet_button)).perform(click())
            aspettaStato(scenario, BottomSheetBehavior.STATE_EXPANDED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val scrim = activity.findViewById<View>(R.id.match_sheet_scrim)
                assertEquals("lo scrim deve essere sopra la colonna", View.VISIBLE, scrim.visibility)
                assertEquals("nero al 60% a foglio aperto", 0.6f, scrim.alpha, 0.01f)
                assertTrue("e deve intercettare i tocchi", scrim.isClickable)
            }
            assertEquals(
                "a foglio aperto la colonna esce da TalkBack",
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                importanzaColonna(scenario),
            )

            scenario.onActivity { it.findViewById<View>(R.id.match_sheet_scrim).performClick() }
            assertEquals(
                "toccare lo scrim deve chiudere il foglio",
                BottomSheetBehavior.STATE_HIDDEN,
                aspettaStato(scenario, BottomSheetBehavior.STATE_HIDDEN),
            )
            scenario.onActivity { activity ->
                assertTrue(
                    "chiuso il foglio lo scrim deve sparire",
                    activity.findViewById<View>(R.id.match_sheet_scrim).visibility != View.VISIBLE,
                )
            }
            assertTrue(
                "chiuso il foglio la colonna torna a TalkBack",
                importanzaColonna(scenario) != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
            )
        }
    }

    @Test
    fun ricreataDuranteL_animazione_il_foglio_non_resta_a_meta() {
        // Ricreata mentre il foglio sale, BottomSheetBehavior salva lo stato come COLLAPSED anche con
        // skipCollapsed: senza rimedio il foglio tornava come una striscia sopra le zone da toccare.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val comportamento = BottomSheetBehavior.from(activity.findViewById<View>(R.id.match_sheet))
                comportamento.state = BottomSheetBehavior.STATE_EXPANDED
                assertEquals(
                    "la prova vale solo se la ricreazione arriva ad animazione in corso",
                    BottomSheetBehavior.STATE_SETTLING,
                    comportamento.state,
                )
                activity.recreate()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(1000)
            assertEquals(
                "dopo la ricreazione il foglio deve essere nascosto, non a meta'",
                BottomSheetBehavior.STATE_HIDDEN,
                statoDelFoglio(scenario),
            )
        }
    }

    @Test
    fun ricreatoAperto_indietro_chiude_il_foglio_e_non_esce() {
        // Lo stato ripristinato non passa dai callback del behavior: senza riallineare il callback di
        // indietro (e la colonna) in onPostCreate, dopo una rotazione a foglio aperto indietro usciva.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.match_sheet_button)).perform(click())
            aspettaStato(scenario, BottomSheetBehavior.STATE_EXPANDED)
            scenario.recreate()
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, aspettaStato(scenario, BottomSheetBehavior.STATE_EXPANDED))
            assertEquals(
                "ricreata a foglio aperto la colonna deve restare fuori da TalkBack",
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                importanzaColonna(scenario),
            )

            pressBack()
            assertEquals(
                "indietro deve chiudere il foglio anche dopo la ricreazione",
                BottomSheetBehavior.STATE_HIDDEN,
                aspettaStato(scenario, BottomSheetBehavior.STATE_HIDDEN),
            )
            assertEquals("e l'app deve restare aperta", Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
