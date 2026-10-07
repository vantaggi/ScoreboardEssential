package it.vantaggi.scoreboardessential

import android.Manifest
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.runBlocking
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

    private fun dp(
        activity: MainActivity,
        valore: Int,
    ): Float = valore * activity.resources.displayMetrics.density

    @Test
    fun laZonaPiu_e_un_bersaglio_grande_e_l_altezza_e_quella_del_design() {
        // Il bersaglio primario: almeno 48dp in ogni verso (in realta' 112 di altezza, DESIGN.md).
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (id in listOf(R.id.team1_add_button_card, R.id.team2_add_button_card)) {
                    val zona = activity.findViewById<View>(id)
                    assertTrue("la zona + deve essere cliccabile", zona.isClickable)
                    assertTrue("la zona e' larga ${zona.width}px, sotto i 48dp", zona.width >= dp(activity, 48))
                    assertTrue("la zona e' alta ${zona.height}px, sotto i 48dp", zona.height >= dp(activity, 48))
                    assertEquals("la zona e' alta 112dp", dp(activity, 112), zona.height.toFloat(), 1f)
                }
            }
        }
    }

    @Test
    fun gliSlotDellaColonna_hanno_le_altezze_fisse_del_design() {
        // Barra 56, nomi 48, striscia 56, zone 112: sono misure fisse, non wrap_content. Se una
        // torna a dipendere dal contenuto, durante la partita qualcosa si sposta.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val attese = mapOf(R.id.game_bar to 56, R.id.names_row to 48, R.id.last_action_strip to 56, R.id.zones_row to 112)
                for ((id, altezzaDp) in attese) {
                    assertEquals(
                        "l'altezza di ${activity.resources.getResourceEntryName(id)}",
                        dp(activity, altezzaDp),
                        activity.findViewById<View>(id).height.toFloat(),
                        1f,
                    )
                }
            }
        }
    }

    @Test
    fun nelCalcio_tempo_portiere_e_comandi_stanno_tutti_in_barra() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val barra = activity.findViewById<View>(R.id.game_bar)
                for (id in listOf(R.id.timer_start_button, R.id.keeper_slot, R.id.wear_status_icon, R.id.match_sheet_button)) {
                    val vista = activity.findViewById<View>(id)
                    val nome = activity.resources.getResourceEntryName(id)
                    assertEquals("$nome deve essere visibile nel calcio", View.VISIBLE, vista.visibility)
                    assertTrue("$nome finisce a ${vista.right}px, oltre la barra di ${barra.width}px", vista.right <= barra.width)
                    assertTrue("$nome comincia fuori dalla barra", vista.left >= 0)
                }
                val tempo = activity.findViewById<View>(R.id.timer_start_button)
                assertTrue("il tempo e' alto ${tempo.height}px, sotto i 48dp", tempo.height >= dp(activity, 48))
            }
        }
    }

    @Test
    fun ilNumero_ha_la_stessa_dimensione_con_0_e_con_15_e_il_token_piu_largo_ci_sta() {
        // Niente autoSize: la dimensione si calcola una volta e non segue il testo. Se tornasse
        // un ridimensionamento automatico, fra "0" e "15" il numero cambierebbe grandezza.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(500)
            scenario.onActivity { activity ->
                val uno = activity.findViewById<TextView>(R.id.team1_score_textview)
                val due = activity.findViewById<TextView>(R.id.team2_score_textview)
                uno.text = "0"
                val con0 = uno.textSize
                uno.text = "15"
                assertEquals("il numero cambia dimensione fra 0 e 15", con0, uno.textSize, 0f)
                assertEquals("le due squadre hanno la stessa dimensione", uno.textSize, due.textSize, 0f)
                assertTrue("il numero e' sotto il minimo di 72dp", uno.textSize >= dp(activity, 72) - 1f)
                assertTrue("il numero e' sopra il tetto di 150dp", uno.textSize <= dp(activity, 150) + 1f)
                // Il controllo e' stretto: "88" entra nella meta' di colonna meno i 16dp di aria, e il
                // numero e' il piu' grande possibile, cioe' o sta al tetto o al pavimento oppure un
                // pixel in piu' farebbe uscire "88" dalla larghezza o dall'altezza della riga.
                val riga = activity.findViewById<View>(R.id.score_row)
                val disponibile = riga.width / 2f - dp(activity, 16)
                val larghezza88 = uno.paint.measureText("88")
                assertTrue("\"88\" e' largo ${larghezza88}px ma ne ha ${disponibile}px", larghezza88 <= disponibile + 1f)
                val metriche = uno.paint.fontMetrics
                val scalaConUnPixelInPiu = (uno.textSize + 1f) / uno.textSize
                val alTetto = uno.textSize >= dp(activity, 150) - 1f
                val alPavimento = uno.textSize <= dp(activity, 72) + 1f
                val sforaLaLarghezza = larghezza88 * scalaConUnPixelInPiu > disponibile
                val sforaLAltezza = (metriche.descent - metriche.ascent) * scalaConUnPixelInPiu > riga.height
                assertTrue(
                    "il numero (${uno.textSize}px) non e' il piu' grande possibile: 88 largo $larghezza88 su $disponibile, riga alta ${riga.height}",
                    alTetto || alPavimento || sforaLaLarghezza || sforaLAltezza,
                )
            }
        }
    }

    /** Dove sta una vista sullo schermo e quanto e' grande: e' tutto cio' che non deve muoversi. */
    private data class Posto(
        val x: Int,
        val y: Int,
        val larghezza: Int,
        val altezza: Int,
    )

    private fun postiFissi(scenario: ActivityScenario<MainActivity>): List<Posto> {
        // Aspetta che finisca il riscontro del tocco (scala 0,96 in 100ms): getLocationOnScreen
        // tiene conto della trasformazione, e a meta' animazione darebbe una posizione diversa.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(500)
        val posti = mutableListOf<Posto>()
        scenario.onActivity { activity ->
            for (id in listOf(R.id.team1_add_button_card, R.id.team2_add_button_card, R.id.last_action_strip)) {
                val vista = activity.findViewById<View>(id)
                val posizione = IntArray(2).also { vista.getLocationOnScreen(it) }
                posti += Posto(posizione[0], posizione[1], vista.width, vista.height)
            }
        }
        return posti
    }

    private fun assertNonSiSposta(
        dopo: String,
        prima: List<Posto>,
        adesso: List<Posto>,
    ) {
        val nomi = listOf("la zona + della squadra 1", "la zona + della squadra 2", "la striscia")
        prima.indices.forEach { i ->
            assertEquals("${nomi[i]} si e' spostata o ridimensionata $dopo", prima[i], adesso[i])
        }
    }

    private fun aspettaCheAttivi(
        scenario: ActivityScenario<MainActivity>,
        condizione: (MainActivity) -> Boolean,
    ): Boolean {
        var tentativi = 50
        var vera = false
        while (!vera && tentativi-- > 0) {
            scenario.onActivity { vera = condizione(it) }
            if (!vera) Thread.sleep(100)
        }
        return vera
    }

    @Test
    fun laZonaPiuNonSiSposta() {
        // Il difetto che la colonna a slot fissi toglie: ANNULLA che compare, il tempo che cambia
        // parola, la riga del portiere che sparisce spostavano il + sotto il dito. Qui si misura la
        // posizione sullo schermo delle due zone e della striscia e si pretende che dopo ogni
        // cosa che nel gioco cambia stato la differenza sia zero pixel.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val iniziale = postiFissi(scenario)

            // 1. Un tocco su + accende ANNULLA: prima era un pulsante che compariva.
            onView(withId(R.id.team1_add_button_card)).perform(click())
            assertTrue(
                "il + non ha acceso ANNULLA: la prova non misurerebbe niente",
                aspettaCheAttivi(scenario) { it.findViewById<View>(R.id.undo_goal_button).isEnabled },
            )
            assertNonSiSposta("dopo un + con ANNULLA che si accende", iniziale, postiFissi(scenario))

            // 2. Il tempo: START diventa PAUSA (icona e colore, non una parola piu' lunga).
            assertTrue(
                "il servizio del cronometro non si e' collegato",
                aspettaCheAttivi(scenario) { it.findViewById<View>(R.id.timer_start_button).isEnabled },
            )
            var prima: CharSequence? = null
            scenario.onActivity { prima = ViewCompat.getStateDescription(it.findViewById(R.id.timer_start_button)) }
            onView(withId(R.id.timer_start_button)).perform(click())
            assertTrue(
                "il tocco sul tempo non ha cambiato lo stato: la prova non misurerebbe niente",
                aspettaCheAttivi(scenario) { ViewCompat.getStateDescription(it.findViewById(R.id.timer_start_button)) != prima },
            )
            assertNonSiSposta("dopo START che diventa PAUSA", iniziale, postiFissi(scenario))
            // Lo si rimette fermo: il servizio del cronometro sopravvive al test.
            onView(withId(R.id.timer_start_button)).perform(click())

            // 3. Conferma di ANNULLA: si spegne e non sparisce.
            onView(withId(R.id.undo_goal_button)).perform(click())
            onView(withText(R.string.undo)).inRoot(isDialog()).perform(click())
            assertTrue(
                "ANNULLA non si e' spento dopo la conferma",
                aspettaCheAttivi(scenario) { !it.findViewById<View>(R.id.undo_goal_button).isEnabled },
            )
            scenario.onActivity {
                assertEquals("ANNULLA spento resta al suo posto", View.VISIBLE, it.findViewById<View>(R.id.undo_goal_button).visibility)
            }
            assertNonSiSposta("dopo la conferma di ANNULLA", iniziale, postiFissi(scenario))
        }
    }

    @Test
    fun nelPadel_la_zonaPiu_non_si_sposta_nemmeno_per_24_tocchi_fino_al_6a0() {
        // Ventiquattro punti di fila alla stessa squadra sono sei game da zero: la partita finisce.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var modello: MainViewModel? = null
            scenario.onActivity { activity ->
                modello = ViewModelProvider(activity)[MainViewModel::class.java]
                assertTrue("il cambio sport e' stato rifiutato: la partita non e' vuota", modello!!.selectSport(SportRegistry.PADEL))
            }
            try {
                assertTrue(
                    "la colonna non si e' messa in modalita' padel",
                    aspettaCheAttivi(scenario) { it.findViewById<View>(R.id.score_detail_container).visibility == View.VISIBLE },
                )
                val iniziale = postiFissi(scenario)
                repeat(24) {
                    scenario.onActivity { it.findViewById<View>(R.id.team1_add_button_card).performClick() }
                }
                assertTrue(
                    "dopo 24 punti la partita non e' finita",
                    aspettaCheAttivi(scenario) { modello!!.scoreDisplay.value?.matchOver == true },
                )
                assertNonSiSposta("dopo 24 tocchi fino al 6-0", iniziale, postiFissi(scenario))
                scenario.onActivity {
                    assertEquals(
                        "ANNULLA resta acceso a partita finita: e' il modo di riaprirla",
                        true,
                        it.findViewById<View>(R.id.undo_goal_button).isEnabled,
                    )
                }
            } finally {
                // Lo sport attivo e' una scelta del dispositivo: si rimette il calcio, che le altre prove danno per scontato.
                scenario.onActivity {
                    modello!!.discardMatch()
                    modello!!.selectSport(SportRegistry.FOOTBALL)
                }
            }
        }
    }

    /** [punti] tocchi veri della zona + di [squadra], uno dopo l'altro. */
    private fun tocca(
        scenario: ActivityScenario<MainActivity>,
        squadra: Int,
        punti: Int,
    ) {
        val zona = if (squadra == 1) R.id.team1_add_button_card else R.id.team2_add_button_card
        repeat(punti) { scenario.onActivity { it.findViewById<View>(zona).performClick() } }
    }

    /** Il padel da zero, con i nomi di squadra predefiniti; lo sport si rimette al calcio in fondo. */
    private fun conPadel(prova: (ActivityScenario<MainActivity>, MainViewModel) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var modello: MainViewModel? = null
            scenario.onActivity { activity ->
                modello = ViewModelProvider(activity)[MainViewModel::class.java]
                assertTrue("il cambio sport e' stato rifiutato: la partita non e' vuota", modello!!.selectSport(SportRegistry.PADEL))
            }
            try {
                assertTrue(
                    "la colonna non si e' messa in modalita' padel",
                    aspettaCheAttivi(scenario) { it.findViewById<View>(R.id.score_detail_container).visibility == View.VISIBLE },
                )
                prova(scenario, modello!!)
            } finally {
                scenario.onActivity {
                    modello!!.discardMatch()
                    modello!!.selectSport(SportRegistry.FOOTBALL)
                }
            }
        }
    }

    private fun testoDi(
        scenario: ActivityScenario<MainActivity>,
        id: Int,
    ): String {
        var testo = ""
        scenario.onActivity { testo = it.findViewById<TextView>(id).text.toString() }
        return testo
    }

    @Test
    fun nelPadel_la_barra_dice_chi_serve_e_il_pallino_passa_di_lato_a_ogni_game() {
        conPadel { scenario, modello ->
            lateinit var nome1: String
            scenario.onActivity {
                nome1 =
                    modello.team1Name.value
                        .orEmpty()
            }
            assertTrue(
                "la barra non dice Padel e il servente: \"${testoDi(scenario, R.id.match_period_textview)}\"",
                aspettaCheAttivi(scenario) {
                    it
                        .findViewById<TextView>(R.id.match_period_textview)
                        .text
                        .toString()
                        .startsWith("Padel") &&
                        it
                            .findViewById<TextView>(R.id.match_period_textview)
                            .text
                            .toString()
                            .endsWith(nome1)
                },
            )
            scenario.onActivity {
                assertEquals("il pallino e' dalla parte di chi serve", View.VISIBLE, it.findViewById<View>(R.id.team1_serve_dot).visibility)
                assertEquals(
                    "l'altro pallino e' INVISIBLE e non GONE",
                    View.INVISIBLE,
                    it.findViewById<View>(R.id.team2_serve_dot).visibility,
                )
            }
            val iniziale = postiFissi(scenario)
            tocca(scenario, 1, 4)
            assertTrue(
                "dopo il game il pallino non e' passato dall'altra parte",
                aspettaCheAttivi(scenario) { it.findViewById<View>(R.id.team2_serve_dot).visibility == View.VISIBLE },
            )
            scenario.onActivity {
                assertEquals(View.INVISIBLE, it.findViewById<View>(R.id.team1_serve_dot).visibility)
                val nome2 =
                    modello.team2Name.value
                        .orEmpty()
                assertTrue(
                    "la barra non nomina il nuovo servente ($nome2)",
                    it
                        .findViewById<TextView>(R.id.match_period_textview)
                        .text
                        .toString()
                        .endsWith(nome2),
                )
            }
            assertNonSiSposta("dopo il cambio di servizio", iniziale, postiFissi(scenario))
        }
    }

    @Test
    fun nelPadel_al_6a3_la_barra_dice_VINCE_e_un_tocco_sulla_zona_spenta_non_cambia_il_punteggio() {
        conPadel { scenario, modello ->
            val iniziale = postiFissi(scenario)
            // 3-3 a game alternati, poi tre game di fila a Rossi: 6-3.
            repeat(3) {
                tocca(scenario, 1, 4)
                tocca(scenario, 2, 4)
            }
            tocca(scenario, 1, 12)
            assertTrue("al 6-3 la partita non e' finita", aspettaCheAttivi(scenario) { modello.scoreDisplay.value?.matchOver == true })
            lateinit var atteso: String
            scenario.onActivity {
                val nome1 =
                    modello.team1Name.value
                        .orEmpty()
                atteso = it.getString(R.string.bar_winner, nome1, "6-3")
            }
            assertTrue(
                "la barra non dice \"$atteso\": \"${testoDi(scenario, R.id.match_period_textview)}\"",
                aspettaCheAttivi(scenario) { it.findViewById<TextView>(R.id.match_period_textview).text.toString() == atteso },
            )
            scenario.onActivity {
                assertEquals(
                    "il vincitore resta bianco",
                    it.getColor(R.color.ink_white),
                    it.findViewById<TextView>(R.id.team1_score_textview).currentTextColor,
                )
                assertEquals(
                    "lo sconfitto e' grigio",
                    it.getColor(R.color.elite_text_secondary),
                    it.findViewById<TextView>(R.id.team2_score_textview).currentTextColor,
                )
                assertEquals("la barra della zona spenta si vede", 1f, it.findViewById<View>(R.id.team1_zone_bar).alpha, 0f)
                assertTrue("la zona spenta deve restare toccabile", it.findViewById<View>(R.id.team1_add_button_card).isClickable)
            }
            assertNonSiSposta("a partita finita", iniziale, postiFissi(scenario))

            // Un tocco vero sulla zona spenta: nessun punto, la striscia dichiara PARTITA FINITA.
            lateinit var prima: String
            var righe = 0
            scenario.onActivity {
                prima =
                    "${it.findViewById<TextView>(
                        R.id.team1_score_textview,
                    ).text}-${it.findViewById<TextView>(R.id.team2_score_textview).text}"
                righe =
                    modello.matchEvents.value
                        .orEmpty()
                        .size
            }
            onView(withId(R.id.team2_add_button_card)).perform(click())
            lateinit var dichiarata: String
            scenario.onActivity { dichiarata = it.getString(R.string.strip_msg_match_over) }
            assertTrue(
                "la striscia non dice \"$dichiarata\": \"${testoDi(scenario, R.id.last_action_text)}\"",
                aspettaCheAttivi(scenario) { it.findViewById<TextView>(R.id.last_action_text).text.toString() == dichiarata },
            )
            scenario.onActivity {
                val dopo = "${it.findViewById<TextView>(
                    R.id.team1_score_textview,
                ).text}-${it.findViewById<TextView>(R.id.team2_score_textview).text}"
                assertEquals("il tocco sulla zona spenta ha cambiato il punteggio", prima, dopo)
                assertEquals(
                    "il tocco sulla zona spenta ha scritto nel registro",
                    righe,
                    modello.matchEvents.value
                        .orEmpty()
                        .size,
                )
            }
            assertNonSiSposta("dopo il tocco sulla zona spenta", iniziale, postiFissi(scenario))

            // ANNULLA riapre la partita: la barra torna a dire chi serve e le zone si riaccendono.
            // Nel padel e' un tocco solo: nessun dialogo da confermare.
            onView(withId(R.id.undo_goal_button)).perform(click())
            assertTrue("ANNULLA non ha riaperto la partita", aspettaCheAttivi(scenario) { modello.scoreDisplay.value?.matchOver == false })
            assertTrue(
                "la barra non e' tornata a PADEL · SERVE",
                aspettaCheAttivi(scenario) {
                    it
                        .findViewById<TextView>(R.id.match_period_textview)
                        .text
                        .toString()
                        .startsWith("Padel")
                },
            )
            scenario.onActivity {
                assertEquals("riaperta, la barra della zona si spegne", 0f, it.findViewById<View>(R.id.team1_zone_bar).alpha, 0f)
                assertEquals(
                    "riaperto, il numero torna bianco",
                    it.getColor(R.color.ink_white),
                    it.findViewById<TextView>(R.id.team2_score_textview).currentTextColor,
                )
            }
            assertNonSiSposta("dopo aver riaperto la partita", iniziale, postiFissi(scenario))
        }
    }

    /** Il testo che la striscia deve mostrare dopo ANNULLA nel padel, nel locale del test. */
    private fun annullatoDiRossi(scenario: ActivityScenario<MainActivity>): String {
        lateinit var atteso: String
        scenario.onActivity {
            val nome1 = ViewModelProvider(it)[MainViewModel::class.java].team1Name.value.orEmpty()
            atteso = it.getString(R.string.strip_msg_undone_point, nome1)
        }
        return atteso
    }

    @Test
    fun nelPadel_ANNULLA_e_un_tocco_solo_senza_dialogo_e_la_striscia_dice_ANNULLATO() {
        conPadel { scenario, modello ->
            val iniziale = postiFissi(scenario)
            lateinit var prima: String
            scenario.onActivity {
                prima =
                    modello.scoreDisplay.value
                        ?.let { d -> "${d.side1Primary}-${d.side2Primary}" }
                        .orEmpty()
            }
            onView(withId(R.id.team1_add_button_card)).perform(click())
            assertTrue(
                "il + non ha acceso ANNULLA: la prova non misurerebbe niente",
                aspettaCheAttivi(scenario) { it.findViewById<View>(R.id.undo_goal_button).isEnabled },
            )
            onView(withId(R.id.undo_goal_button)).perform(click())

            // Niente dialogo: e' il centro della decisione. Un dialogo aperto toglie il fuoco alla
            // finestra della schermata di gioco.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { assertTrue("nel padel ANNULLA non deve aprire un dialogo", it.hasWindowFocus()) }
            val atteso = annullatoDiRossi(scenario)
            assertTrue(
                "la striscia non dice \"$atteso\": \"${testoDi(scenario, R.id.last_action_text)}\"",
                aspettaCheAttivi(scenario) { it.findViewById<TextView>(R.id.last_action_text).text.toString() == atteso },
            )
            scenario.onActivity {
                val dopo =
                    modello.scoreDisplay.value
                        ?.let { d -> "${d.side1Primary}-${d.side2Primary}" }
                        .orEmpty()
                assertEquals("ANNULLA non ha riportato il punteggio di prima", prima, dopo)
                assertEquals(
                    "ANNULLA si spegne: non c'e' piu' niente da annullare",
                    false,
                    it.findViewById<View>(R.id.undo_goal_button).isEnabled,
                )
            }
            assertNonSiSposta("dopo ANNULLA con un tocco", iniziale, postiFissi(scenario))
        }
    }

    /**
     * Nel padel ANNULLA e' un tocco solo e senza dialogo: due tocchi rapidi toglievano due punti.
     * I tocchi sono due performClick nello stesso passo sul thread principale, quindi entro i 500 ms
     * qualunque sia la velocita' dell'emulatore.
     */
    @Test
    fun nelPadel_due_tocchi_rapidi_su_ANNULLA_tolgono_un_punto_solo_e_uno_piu_tardi_ne_toglie_un_altro() {
        conPadel { scenario, modello ->
            fun punteggio(): String {
                var testo = ""
                scenario.onActivity {
                    testo =
                        modello.scoreDisplay.value
                            ?.let { d -> "${d.side1Primary}-${d.side2Primary}" }
                            .orEmpty()
                }
                return testo
            }
            val zero = punteggio()
            scenario.onActivity { modello.addScore(1) }
            val unPunto = punteggio()
            scenario.onActivity { modello.addScore(1) }
            assertTrue("i due punti non cambiano il punteggio: la prova non misurerebbe niente", punteggio() != unPunto)

            scenario.onActivity {
                val annulla = it.findViewById<View>(R.id.undo_goal_button)
                annulla.performClick()
                annulla.performClick()
            }
            assertEquals("due tocchi rapidi devono togliere un punto solo", unPunto, punteggio())

            Thread.sleep(700)
            scenario.onActivity { it.findViewById<View>(R.id.undo_goal_button).performClick() }
            assertEquals("un tocco dopo il rimbalzo e' una scelta e toglie l'altro punto", zero, punteggio())
        }
    }

    @Test
    fun nelCalcio_ANNULLA_apre_ancora_il_dialogo_e_non_toglie_il_gol_finche_non_si_conferma() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.team1_add_button_card)).perform(click())
            assertTrue(
                "il + non ha acceso ANNULLA: la prova non misurerebbe niente",
                aspettaCheAttivi(scenario) { it.findViewById<View>(R.id.undo_goal_button).isEnabled },
            )
            onView(withId(R.id.undo_goal_button)).perform(click())

            onView(withText(R.string.undo_goal_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            scenario.onActivity {
                assertEquals(
                    "il gol non si toglie prima della conferma",
                    true,
                    it.findViewById<View>(R.id.undo_goal_button).isEnabled,
                )
            }
            onView(withText(R.string.undo)).inRoot(isDialog()).perform(click())
            assertTrue(
                "ANNULLA non si e' spento dopo la conferma",
                aspettaCheAttivi(scenario) { !it.findViewById<View>(R.id.undo_goal_button).isEnabled },
            )
        }
    }

    @Test
    fun ilPortiere_toccato_scade_e_lo_slot_diventa_rosso_con_CAMBIO_senza_spostare_niente() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var modello: MainViewModel
            scenario.onActivity { modello = ViewModelProvider(it)[MainViewModel::class.java] }
            assertTrue(
                "il servizio del cronometro non si e' collegato",
                aspettaCheAttivi(scenario) { it.findViewById<View>(R.id.timer_start_button).isEnabled },
            )
            try {
                // Due secondi: il service conta a passi di un secondo, scade al terzo controllo.
                scenario.onActivity { modello.setKeeperTimer(2) }
                val iniziale = postiFissi(scenario)
                var slotPrima = Posto(0, 0, 0, 0)
                scenario.onActivity {
                    val slot = it.findViewById<View>(R.id.keeper_slot)
                    slotPrima = Posto(0, 0, slot.width, slot.height)
                    assertTrue("lo slot del portiere deve essere toccabile", slot.isClickable)
                    assertTrue("lo slot e' alto ${slot.height}px, sotto i 48dp", slot.height >= dp(it, 48))
                }
                onView(withId(R.id.keeper_slot)).perform(click())
                assertTrue(
                    "il tocco non ha avviato il conto",
                    aspettaCheAttivi(scenario) {
                        it.findViewById<TextView>(R.id.keeper_timer_textview).currentTextColor ==
                            it.getColor(R.color.elite_text_primary)
                    },
                )
                // Il conto scade: dopo la scadenza lo slot e' pieno elite_error con CAMBIO in elite_background.
                assertTrue(
                    "dopo la scadenza lo slot non e' diventato rosso",
                    aspettaCheAttivi(scenario) { modello.isKeeperTimerExpired.value == true },
                )
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity {
                    val slot = it.findViewById<com.google.android.material.card.MaterialCardView>(R.id.keeper_slot)
                    val valore = it.findViewById<TextView>(R.id.keeper_timer_textview)
                    assertEquals(
                        "lo slot scaduto e' pieno elite_error",
                        it.getColor(R.color.elite_error),
                        slot.cardBackgroundColor.defaultColor,
                    )
                    assertEquals("CAMBIO e' in elite_background", it.getColor(R.color.elite_background), valore.currentTextColor)
                    assertEquals("lo slot scaduto dice CAMBIO", it.getString(R.string.label_keeper_change), valore.text.toString())
                    assertEquals(
                        "TalkBack dice lo stato e l'azione",
                        it.getString(R.string.cd_keeper_expired),
                        slot.contentDescription?.toString(),
                    )
                    assertEquals("lo slot scaduto non cambia larghezza", slotPrima.larghezza, slot.width)
                    assertEquals("lo slot scaduto non cambia altezza", slotPrima.altezza, slot.height)
                }
                assertNonSiSposta("quando il portiere scade", iniziale, postiFissi(scenario))

                // Un tocco lo toglie: riparte dalla durata piena e torna il conto. performClick e non un
                // tocco vero: alla scadenza la notifica del service compare in alto (heads-up) proprio
                // sopra la barra, e il tocco di Espresso cadrebbe su di lei.
                scenario.onActivity { it.findViewById<View>(R.id.keeper_slot).performClick() }
                assertTrue(
                    "il tocco non ha tolto SCADUTO",
                    aspettaCheAttivi(scenario) { modello.isKeeperTimerExpired.value == false },
                )
                scenario.onActivity {
                    val valore = it.findViewById<TextView>(R.id.keeper_timer_textview)
                    assertTrue("il conto non e' tornato: \"${valore.text}\"", valore.text.toString().matches(Regex("\\d\\d:\\d\\d")))
                }
                assertNonSiSposta("dopo il tocco che toglie SCADUTO", iniziale, postiFissi(scenario))
            } finally {
                // Il service del cronometro sopravvive al test: lo si lascia fermo e con la durata di prima.
                scenario.onActivity {
                    modello.resetKeeperTimer()
                    modello.setKeeperTimer(300)
                }
            }
        }
    }

    @Test
    fun nelPadel_al_6a3_la_striscia_dice_TERMINA_e_toccandola_si_apre_il_dialogo_di_fine_partita() {
        conPadel { scenario, modello ->
            repeat(3) {
                tocca(scenario, 1, 4)
                tocca(scenario, 2, 4)
            }
            tocca(scenario, 1, 12)
            assertTrue("al 6-3 la partita non e' finita", aspettaCheAttivi(scenario) { modello.scoreDisplay.value?.matchOver == true })
            lateinit var atteso: String
            scenario.onActivity { atteso = it.getString(R.string.strip_match_over_end) }
            assertTrue(
                "la striscia non dice \"$atteso\": \"${testoDi(scenario, R.id.last_action_text)}\"",
                aspettaCheAttivi(scenario) { it.findViewById<TextView>(R.id.last_action_text).text.toString() == atteso },
            )
            onView(withId(R.id.last_action_strip)).perform(click())
            // Il dialogo e' quello di fine partita: titolo PARTITA FINITA e chi ha vinto, non il rifiuto.
            onView(withText(R.string.end_match_over_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.btn_save_match)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.continue_action)).inRoot(isDialog()).perform(click())
        }
    }

    @Test
    fun nelPadel_al_6a3_con_un_nome_lungo_la_barra_accorcia_il_nome_e_il_punteggio_resta_visibile() {
        conPadel { scenario, modello ->
            var nomeOriginale = ""
            scenario.onActivity {
                nomeOriginale = modello.team1Name.value.orEmpty()
                modello.setTeam1Name("Maria Antonietta Della")
            }
            try {
                // La barra stretta come su uno schermo da 360dp con i caratteri ingranditi.
                scenario.onActivity {
                    val barra = it.findViewById<TextView>(R.id.match_period_textview)
                    barra.layoutParams =
                        (barra.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                            weight = 0f
                            width = dp(it, 150).toInt()
                        }
                }
                repeat(3) {
                    tocca(scenario, 1, 4)
                    tocca(scenario, 2, 4)
                }
                tocca(scenario, 1, 12)
                assertTrue("al 6-3 la partita non e' finita", aspettaCheAttivi(scenario) { modello.scoreDisplay.value?.matchOver == true })
                assertTrue(
                    "il punteggio deve restare, senza ellissi in coda: \"${testoDi(scenario, R.id.match_period_textview)}\"",
                    aspettaCheAttivi(scenario) {
                        val barra = it.findViewById<TextView>(R.id.match_period_textview)
                        val riga = barra.layout
                        barra.text.endsWith("6-3") && riga != null && riga.getEllipsisCount(0) == 0
                    },
                )
                assertTrue("il nome e' accorciato", testoDi(scenario, R.id.match_period_textview).contains("…"))
            } finally {
                scenario.onActivity { modello.setTeam1Name(nomeOriginale) }
            }
        }
    }

    /** Il colore del testo di una voce del dialogo aperto, letto con un'azione Espresso. */
    private fun coloreDelBottoneDelDialogo(testo: Int): Int {
        var colore = 0
        onView(withText(testo)).inRoot(isDialog()).perform(
            object : ViewAction {
                override fun getConstraints() = isAssignableFrom(TextView::class.java)

                override fun getDescription() = "legge il colore del testo"

                override fun perform(
                    uiController: UiController,
                    view: View,
                ) {
                    colore = (view as TextView).currentTextColor
                }
            },
        )
        return colore
    }

    @Test
    fun nelPadel_sul_5a3_e_30a15_TERMINA_mostra_il_punteggio_del_display_e_SCARTA_e_rosso() {
        conPadel { scenario, modello ->
            // 3-3 a game alternati, poi due game a Rossi (5-3) e nel game che segue 30-15.
            repeat(3) {
                tocca(scenario, 1, 4)
                tocca(scenario, 2, 4)
            }
            tocca(scenario, 1, 8)
            tocca(scenario, 1, 2)
            tocca(scenario, 2, 1)
            lateinit var atteso: String
            scenario.onActivity {
                val nome1 =
                    modello.team1Name.value
                        .orEmpty()
                val nome2 =
                    modello.team2Name.value
                        .orEmpty()
                atteso =
                    listOf(
                        it.getString(R.string.end_summary_racket, nome1, nome2),
                        it.getString(R.string.end_part_game, "5-3"),
                        it.getString(R.string.end_part_point, "30-15"),
                    ).joinToString(" · ")
                it.findViewById<View>(R.id.reset_scores_button).performClick()
            }
            // Il dialogo dice il punteggio vero, non 0-0 e non il rifiuto "non iniziata".
            onView(withText(atteso)).inRoot(isDialog()).check(matches(isDisplayed()))
            // G-6: SCARTA e' testo primario (il rosso sul fondo rialzato del dialogo fa 4,35:1); solo SALVA e' lime.
            assertEquals(
                "SCARTA deve essere il testo primario",
                ContextCompat.getColor(ApplicationProvider.getApplicationContext(), R.color.elite_text_primary),
                coloreDelBottoneDelDialogo(R.string.btn_discard_match),
            )
            onView(withText(R.string.continue_action)).inRoot(isDialog()).perform(click())
        }
    }

    @Test
    fun nelCalcio_la_striscia_chiede_il_marcatore_e_toccandola_apre_la_scelta() {
        // La scorciatoia del passo 7: dopo un + senza marcatore la striscia dice di chi e' il gol e
        // "chi ha segnato"; toccarla apre lo stesso dialogo della riga del registro. Se il listener
        // sparisce, o la striscia smette di offrirsi, il dialogo non compare e la prova diventa rossa.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var modello: MainViewModel? = null
            lateinit var nome: String
            val giocatori =
                AppDatabase
                    .getDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
                    .playerDao()
            var giocatore: Player? = null
            try {
                // Il giocatore di prova: il dialogo del marcatore rilegge la rosa dal database, quindi
                // va scritto davvero. Sta dentro il try e ha l'id che gli da' il database: se la prova
                // fallisce prima di finire, il finally lo toglie, e non forza il contatore degli id.
                val nuovo = Player(playerName = "Marco B.", appearances = 0, goals = 0)
                giocatore = nuovo.copy(playerId = runBlocking { giocatori.insert(nuovo) }.toInt())
                scenario.onActivity { activity ->
                    modello = ViewModelProvider(activity)[MainViewModel::class.java]
                    // Senza una rosa la scorciatoia mostrerebbe la Snackbar "nessun giocatore".
                    modello!!.addPlayerToTeam(PlayerWithRoles(giocatore!!, emptyList()), 1)
                    nome =
                        modello!!
                            .team1Name.value
                            .orEmpty()
                }
                val iniziale = postiFissi(scenario)
                onView(withId(R.id.team1_add_button_card)).perform(click())

                lateinit var atteso: String
                scenario.onActivity { atteso = it.getString(R.string.strip_goal_unattributed, nome) }
                assertTrue(
                    "la striscia non dice il gol senza marcatore: \"$atteso\"",
                    aspettaCheAttivi(scenario) { it.findViewById<TextView>(R.id.last_action_text).text.toString() == atteso },
                )
                assertTrue("il testo deve contenere il nome della squadra ($nome): $atteso", atteso.contains(nome))
                assertNonSiSposta("dopo il testo 'chi ha segnato'", iniziale, postiFissi(scenario))

                onView(withId(R.id.last_action_strip)).perform(click())
                assertTrue(
                    "toccare la striscia deve aprire la scelta del marcatore",
                    aspettaCheAttivi(scenario) {
                        it.supportFragmentManager.executePendingTransactions()
                        it.supportFragmentManager.findFragmentByTag(SelectScorerDialogFragment.TAG) != null
                    },
                )

                // Scelto il marcatore: per 3 secondi il messaggio, poi il testo base col nome.
                onView(withText("Marco B.")).inRoot(isDialog()).perform(click())
                val messaggio = { attivita: MainActivity -> attivita.getString(R.string.strip_msg_goal_by, "Marco B.") }
                assertTrue(
                    "dopo la scelta la striscia deve dire chi ha segnato",
                    aspettaCheAttivi(scenario) { it.findViewById<TextView>(R.id.last_action_text).text.toString() == messaggio(it) },
                )
                assertNonSiSposta("durante il messaggio del marcatore", iniziale, postiFissi(scenario))
                assertTrue(
                    "passati 3 secondi deve tornare il testo base col marcatore",
                    aspettaCheAttivi(scenario) {
                        it.findViewById<TextView>(R.id.last_action_text).text.toString() ==
                            it.getString(R.string.strip_goal_by, nome, "Marco B.")
                    },
                )
                scenario.onActivity {
                    assertFalse(
                        "col marcatore la striscia non e' piu' un bersaglio",
                        it.findViewById<View>(R.id.last_action_strip).isClickable,
                    )
                }
            } finally {
                scenario.onActivity { modello?.discardMatch() }
                giocatore?.let { runBlocking { giocatori.delete(it) } }
            }
        }
    }

    @Test
    fun unaSnackbarMostrata_sta_sopra_la_striscia() {
        // Le Snackbar comparivano in fondo, dove ora stanno le zone +: coprivano il bersaglio primario.
        // Ancorate alla striscia, il loro bordo inferiore non scende sotto il suo bordo superiore.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MainViewModel::class.java].sportChangeRejected.value = Unit
            }
            var snackbar: View? = null
            assertTrue(
                "la Snackbar non e' comparsa",
                aspettaCheAttivi(scenario) { attivita ->
                    val testo = attivita.findViewById<View>(com.google.android.material.R.id.snackbar_text)
                    var vista = testo
                    while (vista != null && vista !is Snackbar.SnackbarLayout) vista = vista.parent as? View
                    snackbar = vista
                    snackbar != null
                },
            )
            // L'animazione d'ingresso dura circa 250ms: si misura a posizione finale.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(800)
            scenario.onActivity { activity ->
                val fondoSnackbar = IntArray(2).also { snackbar!!.getLocationOnScreen(it) }[1] + snackbar!!.height
                val striscia = activity.findViewById<View>(R.id.last_action_strip)
                val cimaStriscia = IntArray(2).also { striscia.getLocationOnScreen(it) }[1]
                assertTrue(
                    "la Snackbar arriva a y=$fondoSnackbar e copre la striscia, che comincia a y=$cimaStriscia",
                    fondoSnackbar <= cimaStriscia,
                )
            }
        }
    }

    @Test
    fun unaSnackbarMostrata_a_foglio_aperto_non_e_ancorata_alla_striscia() {
        // A foglio PARTITA aperto la striscia sta sotto lo scrim: una Snackbar ancorata a lei
        // galleggerebbe sulle righe della rosa. Senza ancora sta in fondo, sotto il bordo della striscia.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.match_sheet_button)).perform(click())
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, aspettaStato(scenario, BottomSheetBehavior.STATE_EXPANDED))
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MainViewModel::class.java].sportChangeRejected.value = Unit
            }
            var snackbar: View? = null
            assertTrue(
                "la Snackbar non e' comparsa",
                aspettaCheAttivi(scenario) { attivita ->
                    var vista = attivita.findViewById<View>(com.google.android.material.R.id.snackbar_text)
                    while (vista != null && vista !is Snackbar.SnackbarLayout) vista = vista.parent as? View
                    snackbar = vista
                    snackbar != null
                },
            )
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(800)
            scenario.onActivity { activity ->
                val fondoSnackbar = IntArray(2).also { snackbar!!.getLocationOnScreen(it) }[1] + snackbar!!.height
                val striscia = activity.findViewById<View>(R.id.last_action_strip)
                val cimaStriscia = IntArray(2).also { striscia.getLocationOnScreen(it) }[1]
                assertTrue(
                    "la Snackbar arriva a y=$fondoSnackbar: e' ancorata alla striscia, che comincia a y=$cimaStriscia",
                    fondoSnackbar > cimaStriscia,
                )
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

    /**
     * Passo 14: nel padel il foglio ha la card COPPIE con due posti numerati per lato. Toccare
     * SCAMBIA li inverte (e con loro chi serve per primo); dopo il primo punto il comando si
     * spegne. Senza il collegamento fra il comando e il ViewModel i posti non si muovono.
     */
    @Test
    fun nelPadel_lo_scambio_delle_coppie_inverte_i_posti_e_dopo_il_primo_punto_si_spegne() {
        val giocatori =
            AppDatabase
                .getDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
                .playerDao()
        val inseriti = mutableListOf<Player>()
        try {
            // Scritti davvero nel database: la riga viva del primo punto ne riscrive le formazioni.
            val nomi = listOf("Marco C.", "Anna C.", "Luca C.", "Sara C.")
            nomi.forEach { nome ->
                val nuovo = Player(playerName = nome, appearances = 0, goals = 0)
                inseriti.add(nuovo.copy(playerId = runBlocking { giocatori.insert(nuovo) }.toInt()))
            }
            conPadel { scenario, modello ->
                scenario.onActivity {
                    modello.addPlayerToTeam(PlayerWithRoles(inseriti[0], emptyList()), 1)
                    modello.addPlayerToTeam(PlayerWithRoles(inseriti[1], emptyList()), 2)
                    modello.addPlayerToTeam(PlayerWithRoles(inseriti[2], emptyList()), 1)
                    modello.addPlayerToTeam(PlayerWithRoles(inseriti[3], emptyList()), 2)
                }
                onView(withId(R.id.match_sheet_button)).perform(click())
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, aspettaStato(scenario, BottomSheetBehavior.STATE_EXPANDED))
                assertTrue(
                    "la card COPPIE deve comparire nel padel",
                    aspettaCheAttivi(scenario) { it.findViewById<View>(R.id.pairs_card).visibility == View.VISIBLE },
                )
                assertEquals("Marco C.", testoDi(scenario, R.id.team1_slot1_name))
                assertEquals("Luca C.", testoDi(scenario, R.id.team1_slot2_name))

                // Un tocco sul comando di scambio della prima squadra.
                scenario.onActivity { it.findViewById<View>(R.id.team1_swap_button).performClick() }
                assertTrue(
                    "dopo lo scambio il primo posto e' di Luca",
                    aspettaCheAttivi(scenario) {
                        it.findViewById<TextView>(R.id.team1_slot1_name).text.toString() == "Luca C."
                    },
                )
                assertEquals("Marco C.", testoDi(scenario, R.id.team1_slot2_name))
                assertEquals("l'altra squadra non si muove", "Anna C.", testoDi(scenario, R.id.team2_slot1_name))

                // Dal primo punto lo scambio e' spento, e un tocco non muove niente.
                tocca(scenario, 1, 1)
                assertTrue(
                    "dopo il primo punto lo scambio deve spegnersi",
                    aspettaCheAttivi(scenario) { !it.findViewById<View>(R.id.team1_swap_button).isEnabled },
                )
                scenario.onActivity { it.findViewById<View>(R.id.team1_swap_button).performClick() }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertEquals("Luca C.", testoDi(scenario, R.id.team1_slot1_name))
            }
        } finally {
            inseriti.forEach { runBlocking { giocatori.delete(it) } }
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

    /** Il badge e' il foreground dell'icona: c'e' solo con un arretrato rifiutato. */
    private fun badgeDellOrologio(activity: MainActivity): Boolean =
        activity.findViewById<ImageView>(R.id.wear_status_icon).foreground != null

    private fun cardDellOrologio(activity: MainActivity): Int = activity.findViewById<View>(R.id.watch_notice_card).visibility

    /**
     * Passo 9. Un arretrato rifiutato accende il badge sull'icona e la card in cima al foglio, e li
     * tiene: dopo una ricreazione dell'Activity il ViewModel e' lo stesso e la notizia e' ancora
     * li' (era un SingleLiveEvent, una Snackbar che passava). Toccare l'icona apre il foglio. Solo una
     * partita nuova li spegne.
     */
    @Test
    fun unArretratoRifiutato_tiene_badge_e_card_dopo_la_ricreazione_e_la_partita_nuova_li_spegne() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var modello: MainViewModel? = null
            scenario.onActivity { activity ->
                modello = ViewModelProvider(activity)[MainViewModel::class.java]
                assertFalse("senza notizie l'icona non ha il badge", badgeDellOrologio(activity))
                assertEquals("senza notizie la card non c'e'", View.GONE, cardDellOrologio(activity))
                // Una partita gia' cominciata sul telefono: l'arretrato dell'orologio non puo' entrare.
                modello!!.addScore(1)
                val applica =
                    MainViewModel::class.java.getDeclaredMethod(
                        "applyWatchBatch",
                        String::class.java,
                        Long::class.java,
                        Long::class.java,
                        String::class.java,
                        String::class.java,
                        String::class.java,
                        String::class.java,
                    )
                applica.isAccessible = true
                val voce = listOf(WearConstants.INTENT_POINT, 1, 1000L).joinToString(WearConstants.BATCH_FIELD_SEPARATOR)
                applica.invoke(modello, voce, 7L, 0L, null, null, null, null)
            }
            try {
                assertTrue(
                    "il badge non si e' acceso",
                    aspettaCheAttivi(scenario) { badgeDellOrologio(it) && cardDellOrologio(it) == View.VISIBLE },
                )

                scenario.recreate()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertTrue(
                    "dopo la ricreazione badge e card devono esserci ancora",
                    aspettaCheAttivi(scenario) { badgeDellOrologio(it) && cardDellOrologio(it) == View.VISIBLE },
                )

                // Toccare l'icona apre il foglio, e la card vi e' in cima, a vista.
                onView(withId(R.id.wear_status_icon)).perform(click())
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, aspettaStato(scenario, BottomSheetBehavior.STATE_EXPANDED))
                onView(withId(R.id.watch_notice_card)).check(matches(isDisplayed()))
                pressBack()
                assertEquals(BottomSheetBehavior.STATE_HIDDEN, aspettaStato(scenario, BottomSheetBehavior.STATE_HIDDEN))

                // Partita nuova: il badge e la card vanno via. discardMatch scarta anche la riga viva.
                scenario.onActivity { activity ->
                    assertTrue(
                        "niente da scartare: la partita era vuota",
                        ViewModelProvider(activity)[MainViewModel::class.java].discardMatch(),
                    )
                }
                assertTrue(
                    "la partita nuova non ha spento badge e card",
                    aspettaCheAttivi(scenario) { !badgeDellOrologio(it) && cardDellOrologio(it) == View.GONE },
                )
            } finally {
                // Se la prova e' caduta a meta' la partita resterebbe viva sull'emulatore.
                scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].discardMatch() }
            }
        }
    }

    /**
     * Il foglio e' una NestedScrollView e tiene lo scrollY anche da nascosto: chiuso in fondo e riaperto
     * dall'icona, la card in cima restava fuori vista. Il tocco sull'icona lo riporta in cima.
     */
    @Test
    fun toccareL_icona_con_il_foglio_scorso_in_fondo_porta_la_card_in_vista() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.match_sheet_button)).perform(click())
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, aspettaStato(scenario, BottomSheetBehavior.STATE_EXPANDED))
            scorriInFondo(scenario)
            scenario.onActivity { activity ->
                assertTrue(
                    "la prova vale solo se il foglio e' davvero scorso",
                    activity.findViewById<NestedScrollView>(R.id.match_sheet).scrollY > 0,
                )
            }
            // Scorso in fondo il pulsante CHIUDI e' fuori vista: si chiude con indietro.
            pressBack()
            assertEquals(BottomSheetBehavior.STATE_HIDDEN, aspettaStato(scenario, BottomSheetBehavior.STATE_HIDDEN))

            try {
                scenario.onActivity { activity ->
                    val modello = ViewModelProvider(activity)[MainViewModel::class.java]
                    modello.addScore(1)
                    val applica =
                        MainViewModel::class.java.getDeclaredMethod(
                            "applyWatchBatch",
                            String::class.java,
                            Long::class.java,
                            Long::class.java,
                            String::class.java,
                            String::class.java,
                            String::class.java,
                            String::class.java,
                        )
                    applica.isAccessible = true
                    val voce = listOf(WearConstants.INTENT_POINT, 1, 1000L).joinToString(WearConstants.BATCH_FIELD_SEPARATOR)
                    applica.invoke(modello, voce, 7L, 0L, null, null, null, null)
                }
                assertTrue("la card non si e' accesa", aspettaCheAttivi(scenario) { cardDellOrologio(it) == View.VISIBLE })

                onView(withId(R.id.wear_status_icon)).perform(click())
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, aspettaStato(scenario, BottomSheetBehavior.STATE_EXPANDED))
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                Thread.sleep(500)
                scenario.onActivity { activity ->
                    val card = activity.findViewById<View>(R.id.watch_notice_card)
                    val visibile = Rect()
                    assertTrue("la card e' fuori dalla finestra", card.getGlobalVisibleRect(visibile))
                    assertEquals("la card deve essere visibile per intero", card.height, visibile.height())
                }
            } finally {
                scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].discardMatch() }
            }
        }
    }

    /** L'icona ha il testo primario se l'orologio e' collegato, il secondario (col glifo barrato) se no. */
    @Test
    fun l_icona_dell_orologio_e_chiara_se_collegato_e_grigia_se_no() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val contesto = ApplicationProvider.getApplicationContext<android.content.Context>()
            val stencil = ContextCompat.getColor(contesto, R.color.elite_text_primary)
            val grigio = ContextCompat.getColor(contesto, R.color.elite_text_secondary)

            fun tinta(activity: MainActivity) = activity.findViewById<ImageView>(R.id.wear_status_icon).imageTintList?.defaultColor

            fun imposta(collegato: Boolean) =
                scenario.onActivity { activity ->
                    val campo = MainViewModel::class.java.getDeclaredField("_isWearConnected")
                    campo.isAccessible = true

                    @Suppress("UNCHECKED_CAST")
                    val stato = campo.get(ViewModelProvider(activity)[MainViewModel::class.java]) as MutableLiveData<Boolean>
                    stato.value = collegato
                }
            imposta(true)
            assertTrue("collegato l'icona deve avere il testo primario", aspettaCheAttivi(scenario) { tinta(it) == stencil })
            imposta(false)
            assertTrue("scollegato l'icona deve avere il testo secondario", aspettaCheAttivi(scenario) { tinta(it) == grigio })
        }
    }

    /** Le righe di game del registro a schermo, lette dall'adapter: non dipendono dal foglio aperto o chiuso. */
    private fun righeDeiGame(activity: MainActivity): List<MatchEvent> =
        (activity.findViewById<RecyclerView>(R.id.match_log_recyclerview).adapter as MatchLogAdapter)
            .currentList
            .filter { it.type == MatchEventType.GAME }

    /**
     * Passo 15: nel padel il registro mostra una riga per game, e ANNULLA sul punto che lo chiude
     * toglie quella riga (il game si riapre). Quattro tocchi della zona + chiudono un game.
     */
    @Test
    fun nelPadel_il_registro_ha_una_riga_per_game_e_ANNULLA_la_toglie() {
        conPadel { scenario, _ ->
            tocca(scenario, 1, 3)
            var righe = -1
            scenario.onActivity { righe = righeDeiGame(it).size }
            assertEquals("un game ancora aperto non ha riga", 0, righe)

            tocca(scenario, 1, 1)
            assertTrue(
                "il quarto punto non ha chiuso il game nel registro",
                aspettaCheAttivi(scenario) { righeDeiGame(it).size == 1 },
            )
            scenario.onActivity {
                val riga = righeDeiGame(it).single()
                assertEquals(1, riga.team)
                assertEquals(listOf(1, 0), riga.game!!.gamesAfter)
            }

            onView(withId(R.id.undo_goal_button)).perform(click())
            assertTrue(
                "ANNULLA ha riaperto il game ma la sua riga e' rimasta",
                aspettaCheAttivi(scenario) { righeDeiGame(it).isEmpty() },
            )
        }
    }
}
