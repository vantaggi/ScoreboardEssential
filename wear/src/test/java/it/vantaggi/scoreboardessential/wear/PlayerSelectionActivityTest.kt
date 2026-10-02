package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.os.SystemClock
import android.os.Vibrator
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.time.Duration
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

/**
 * La scelta del marcatore: la lista su nero con SALTA in cima, e il polso che dice se il nome e'
 * arrivato.
 *
 * Prima i nodi si chiedevano a mano e con zero nodi non succedeva niente: la schermata si chiudeva
 * come dopo un invio riuscito, e il gol arrivava poi al telefono senza marcatore.
 *
 * Il tempo e' di due specie e si sposta in due posti, come in [MenuActivityTest]: l'istante che
 * misura la guardia dei 400ms (l'orologio iniettato in [PlayerSelectionActivity.orologio]) e il
 * looper che fa scattare la chiusura dei 15s. [avanza] li muove insieme; niente Thread.sleep per il
 * tempo. L'unico attesa vera e' l'invio, che passa da Dispatchers.IO.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerSelectionActivityTest {
    private var adesso = 1_000_000L

    @Before
    fun setup() {
        PlayerSelectionActivity.orologio = { adesso }
    }

    @After
    fun ripristina() {
        PlayerSelectionActivity.creaSync = { OptimizedWearDataSync(it) }
        PlayerSelectionActivity.orologio = SystemClock::uptimeMillis
    }

    private fun avanza(millisecondi: Long) {
        adesso += millisecondi
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millisecondi))
    }

    /** Il canale vero con i client GMS finti: l'esito lo decide lo stesso criterio del pallino. */
    private fun telefono(collegato: Boolean): (Context) -> OptimizedWearDataSync =
        { contesto ->
            val nodo = Mockito.mock(Node::class.java)
            Mockito.`when`(nodo.id).thenReturn("telefono")
            val info = Mockito.mock(CapabilityInfo::class.java)
            Mockito.`when`(info.nodes).thenReturn(if (collegato) setOf(nodo) else emptySet())
            val capability = Mockito.mock(CapabilityClient::class.java)
            Mockito
                .`when`(capability.getCapability(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(Tasks.forResult(info))
            val nodi = Mockito.mock(NodeClient::class.java)
            Mockito.`when`(nodi.connectedNodes).thenReturn(Tasks.forResult(if (collegato) listOf(nodo) else emptyList()))
            val messaggi = Mockito.mock(MessageClient::class.java)
            Mockito
                .`when`(messaggi.sendMessage(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(Tasks.forResult(1))
            OptimizedWearDataSync(contesto, Mockito.mock(DataClient::class.java), messaggi, capability, nodi)
        }

    private val rossi = PlayerData(id = 7, name = "Rossi", roles = emptyList())
    private val bianchi = PlayerData(id = 8, name = "Bianchi", roles = listOf("ATT"))

    /** Apre la lista come la apre il quadrante, con la sua rosa, il colore e il punteggio del gol. */
    private fun apri(
        giocatori: List<PlayerData> = listOf(rossi),
        colore: Int = 0xFFFFD600.toInt(),
        risultato: String = "3–2",
        collegato: Boolean = true,
        canale: ((Context) -> OptimizedWearDataSync)? = null,
    ): PlayerSelectionActivity {
        PlayerSelectionActivity.creaSync = canale ?: telefono(collegato)
        val intent = PlayerSelectionActivity.intent(RuntimeEnvironment.getApplication(), 1, giocatori, colore, risultato)
        val activity = Robolectric.buildActivity(PlayerSelectionActivity::class.java, intent).setup().get()
        // La lista si misura a mano: dentro il test nessuno la impagina.
        val lista = activity.findViewById<RecyclerView>(R.id.player_list)
        lista.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
        )
        lista.layout(0, 0, 400, 400)
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    private fun riga(
        activity: PlayerSelectionActivity,
        posizione: Int,
    ): View =
        activity
            .findViewById<RecyclerView>(R.id.player_list)
            .findViewHolderForAdapterPosition(posizione)!!
            .itemView

    private fun nome(
        activity: PlayerSelectionActivity,
        posizione: Int,
    ) = riga(activity, posizione).findViewById<TextView>(R.id.player_name).text.toString()

    /**
     * L'invio passa da Dispatchers.IO, un thread vero: si fa girare il looper finche' l'esito non
     * chiude la schermata, con un limite.
     */
    private fun aspettaLaChiusura(activity: PlayerSelectionActivity) {
        val limite = System.currentTimeMillis() + 5_000
        while (!activity.isFinishing && System.currentTimeMillis() < limite) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("La schermata non si e' chiusa dopo l'esito", activity.isFinishing)
    }

    /** Apre la scelta con un solo giocatore, tocca Rossi e aspetta che la schermata si chiuda. */
    private fun scegliRossi(collegato: Boolean = true): PlayerSelectionActivity {
        val activity = apri(collegato = collegato)
        // Passata la guardia dei 400ms: prima un tocco non sceglie nessuno.
        avanza(400L)
        // Posizione 0 e' SALTA: Rossi e' la seconda riga.
        riga(activity, 1).performClick()
        aspettaLaChiusura(activity)
        return activity
    }

    private fun ultimaVibrazione(): LongArray {
        val vibratore = RuntimeEnvironment.getApplication().getSystemService(Vibrator::class.java)
        return shadowOf(vibratore).pattern
    }

    @Test
    fun `senza nodi la scelta vibra da errore e lo dice a schermo`() {
        val activity = scegliRossi(collegato = false)

        assertArrayEquals(WearPatterns.NON_CONFERMATO, ultimaVibrazione())
        assertEquals(activity.getString(R.string.wear_scorer_not_sent), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `col telefono collegato la scelta vibra la conferma e non dice niente`() {
        // Il controllo del test precedente: l'errore non deve comparire quando il nome e' partito.
        scegliRossi()

        assertArrayEquals(HapticFeedbackManager.PATTERN_CONFIRM, ultimaVibrazione())
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    // --- La lista: SALTA in cima, righe da 52dp ---

    @Test
    fun `SALTA e' la prima riga, poi i giocatori nell'ordine della rosa`() {
        val activity = apri(giocatori = listOf(rossi, bianchi))

        assertEquals(activity.getString(R.string.wear_skip), nome(activity, 0))
        assertEquals("Rossi", nome(activity, 1))
        assertEquals("Bianchi", nome(activity, 2))
    }

    @Test
    fun `ogni riga e' alta almeno 52dp`() {
        val activity = apri(giocatori = listOf(rossi, bianchi))
        val densita = activity.resources.displayMetrics.density

        listOf(0, 1, 2).forEach { posizione ->
            val altezza = riga(activity, posizione).height / densita
            assertTrue("la riga $posizione e' alta ${altezza}dp", altezza >= 52f)
        }
    }

    @Test
    fun `la lista e' su nero e porta il focus alla corona`() {
        val activity = apri()

        val radice = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        assertEquals(activity.getColor(R.color.ink_black), (radice.background as ColorDrawable).color)
        assertTrue("senza focus la corona non scorre niente", activity.findViewById<View>(R.id.player_list).hasFocus())
    }

    @Test
    fun `l'intestazione dice quale gol e la barra e' del colore della squadra`() {
        val activity = apri(colore = 0xFFFFD600.toInt(), risultato = "3–2")

        assertEquals(
            activity.getString(R.string.wear_goal_header, "3–2"),
            activity.findViewById<TextView>(R.id.goal_header).text.toString(),
        )
        assertEquals(0xFFFFD600.toInt(), (activity.findViewById<View>(R.id.goal_bar).background as ColorDrawable).color)
        // Anche la riga di un giocatore porta la barra, nello stesso colore; SALTA no.
        assertEquals(View.INVISIBLE, riga(activity, 0).findViewById<View>(R.id.player_bar).visibility)
        assertEquals(
            0xFFFFD600.toInt(),
            (riga(activity, 1).findViewById<View>(R.id.player_bar).background as ColorDrawable).color,
        )
    }

    @Test
    fun `un colore di squadra scuro sul nero viene portato a 3 a 1`() {
        // Il blu notte da solo sul nero fa meno di 3:1 e la barra sparirebbe.
        val scuro = 0xFF1A237E.toInt()
        val activity = apri(colore = scuro)

        val barra = (activity.findViewById<View>(R.id.goal_bar).background as ColorDrawable).color
        assertEquals(TeamInk.graphicOnBlack(scuro) or TeamInk.NERO, barra)
        assertTrue("contrasto ${TeamInk.contrast(barra, TeamInk.NERO)}", TeamInk.contrast(barra, TeamInk.NERO) >= 3.0)
    }

    // --- Guardia di 400ms ---

    @Test
    fun `un tocco entro 400ms dall'apertura non sceglie nessuno, nemmeno SALTA`() {
        val activity = apri()

        avanza(400L - 1)
        riga(activity, 0).performClick()

        assertFalse("SALTA ha chiuso dentro la guardia", activity.isFinishing)
    }

    @Test
    fun `entro 400ms nemmeno un giocatore parte, a 400ms si`() {
        val activity = apri()

        avanza(400L - 1)
        riga(activity, 1).performClick()
        // L'invio si segna in modo sincrono prima di partire: se il tocco fosse passato, sarebbe
        // gia' acceso. L'esito arriva da un thread vero, e' inutile aspettarlo per provare un "no".
        assertFalse("il tocco dentro la guardia ha mandato un marcatore", inInvio(activity))

        avanza(1)
        riga(activity, 1).performClick()
        assertTrue("passata la guardia il tocco deve scegliere", inInvio(activity))
        aspettaLaChiusura(activity)
        assertArrayEquals(HapticFeedbackManager.PATTERN_CONFIRM, ultimaVibrazione())
    }

    private fun inInvio(activity: PlayerSelectionActivity): Boolean {
        val campo = PlayerSelectionActivity::class.java.getDeclaredField("inInvio")
        campo.isAccessible = true
        return campo.getBoolean(activity)
    }

    @Test
    fun `passati 400ms SALTA chiude senza mandare niente`() {
        val activity = apri()

        avanza(400L)
        riga(activity, 0).performClick()

        assertTrue(activity.isFinishing)
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `la guardia dei 400ms parte da quando la lista e' visibile, non da onCreate`() {
        PlayerSelectionActivity.creaSync = telefono(true)
        val intent = PlayerSelectionActivity.intent(RuntimeEnvironment.getApplication(), 1, listOf(rossi), 0xFFFFD600.toInt(), "3–2")
        val controller = Robolectric.buildActivity(PlayerSelectionActivity::class.java, intent).create()
        // Gonfiare i layout su un orologio costa tempo: la lista compare solo dopo.
        adesso += 300L
        val activity = controller.start().postCreate(null).resume().visible().get()
        val lista = activity.findViewById<RecyclerView>(R.id.player_list)
        lista.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
        )
        lista.layout(0, 0, 400, 400)

        // 100ms dopo la comparsa sono 400ms dopo onCreate: la guardia vera non e' ancora passata.
        avanza(100L)
        riga(activity, 1).performClick()
        assertFalse("la guardia e' partita da onCreate e non dal primo frame", inInvio(activity))

        avanza(300L - 1)
        riga(activity, 1).performClick()
        assertFalse("un millisecondo prima dei 400ms dalla comparsa", inInvio(activity))

        avanza(1L)
        riga(activity, 1).performClick()
        assertTrue(inInvio(activity))
        aspettaLaChiusura(activity)
    }

    // --- Rosa vuota ---

    @Test
    fun `con la rosa vuota la lista ha SALTA e la scritta che dice perche'`() {
        val activity = apri(giocatori = emptyList())

        assertEquals(1, activity.findViewById<RecyclerView>(R.id.player_list).adapter!!.itemCount)
        assertEquals(activity.getString(R.string.wear_skip), nome(activity, 0))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.player_list).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.empty_state_text).visibility)
    }

    @Test
    fun `con la rosa vuota SALTA chiude la lista`() {
        val activity = apri(giocatori = emptyList())

        avanza(400L)
        riga(activity, 0).performClick()

        assertTrue("senza giocatori SALTA e' l'unica uscita", activity.isFinishing)
    }

    @Test
    fun `con una rosa la scritta della rosa vuota non c'e'`() {
        val activity = apri(giocatori = listOf(rossi))

        assertEquals(View.GONE, activity.findViewById<View>(R.id.empty_state_text).visibility)
    }

    // --- Invio in corso: i 15s non chiudono a meta' ---

    /** Un telefono che non risponde mai da solo: l'esito lo da' il test con [invioSospeso]. */
    private var invioSospeso: Continuation<Boolean>? = null

    private val telefonoCheNonRisponde: (Context) -> OptimizedWearDataSync =
        {
            Mockito.mock(
                OptimizedWearDataSync::class.java,
                Answer { invocazione ->
                    if (invocazione.method.name == "sendMessage") {
                        @Suppress("UNCHECKED_CAST")
                        invioSospeso = invocazione.rawArguments.last() as Continuation<Boolean>
                        COROUTINE_SUSPENDED
                    } else {
                        Mockito.RETURNS_DEFAULTS.answer(invocazione)
                    }
                },
            )
        }

    @Test
    fun `un invio ancora senza esito non viene chiuso dai 15 secondi`() {
        val activity = apri(canale = telefonoCheNonRisponde)
        avanza(400L)
        riga(activity, 1).performClick()
        assertTrue(inInvio(activity))

        // Il doppio tocco di TalkBack non passa da onUserInteraction: i 15s non si riarmano da soli.
        avanza(15_000L)
        assertFalse("la schermata si e' chiusa prima dell'esito dell'invio", activity.isFinishing)

        // Quando l'esito arriva, si chiude (e vibra) come sempre.
        invioSospeso!!.resume(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.isFinishing)
        assertArrayEquals(HapticFeedbackManager.PATTERN_CONFIRM, ultimaVibrazione())
    }

    @Test
    fun `un input durante un invio non riarma i 15 secondi`() {
        val activity = apri(canale = telefonoCheNonRisponde)
        avanza(400L)
        riga(activity, 1).performClick()

        avanza(10_000)
        activity.onUserInteraction()
        avanza(15_000L)

        assertFalse("l'input durante l'invio ha riarmato la chiusura", activity.isFinishing)
    }

    // --- Chiusura a 15s ---

    @Test
    fun `senza input la lista si chiude dopo 15 secondi`() {
        // L'avvio dell'activity consuma qualche millisecondo del looper di Robolectric: il conto dei
        // 15 secondi parte da onCreate, dentro quella finestra. Si misura da prima e da dopo.
        val prima = SystemClock.uptimeMillis()
        val activity = apri()
        val dopo = SystemClock.uptimeMillis()

        avanza(prima + 15_000L - 1 - dopo)
        assertFalse("chiusa un istante prima dei 15s", activity.isFinishing)

        avanza(dopo - prima + 1)
        assertTrue("ancora aperta dopo i 15s", activity.isFinishing)
    }

    @Test
    fun `un input riparte i 15 secondi`() {
        val activity = apri()
        avanza(10_000)

        // La corona o un tocco: Activity.onUserInteraction.
        activity.onUserInteraction()
        avanza(15_000L - 1)
        assertFalse("i 15s non sono ripartiti dall'input", activity.isFinishing)

        avanza(1)
        assertTrue(activity.isFinishing)
    }
}
