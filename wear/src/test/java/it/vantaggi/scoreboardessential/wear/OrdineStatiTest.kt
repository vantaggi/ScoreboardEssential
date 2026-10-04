package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.os.Vibrator
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * L5, ordine degli stati: il Data Layer puo' consegnare due stati ravvicinati invertiti, e quello
 * superato arrivato per ultimo riportava il polso indietro (telefono 3-1, orologio fermo a 2-0).
 * Uno stato con versione minore dell'ultima vista si ignora, sia dal ViewModel sia dal servizio che
 * lavora ad app chiusa; uno senza versione (telefono non aggiornato) si applica come prima.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OrdineStatiTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var vibrator: Vibrator

    @Mock
    private lateinit var packageManager: android.content.pm.PackageManager

    private val contesto: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockitoAnnotations.openMocks(this)

        Mockito.`when`(application.getSystemService(Context.VIBRATOR_SERVICE)).thenReturn(vibrator)
        Mockito.`when`(application.packageManager).thenReturn(packageManager)
        Mockito.`when`(packageManager.hasSystemFeature(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(application.applicationContext).thenReturn(application)
        Mockito
            .`when`(application.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
            .thenAnswer { inv -> contesto.getSharedPreferences(inv.getArgument(0), inv.getArgument(1)) }
        listOf("wear_pending_intents", "wear_last_known_match").forEach { nome ->
            contesto
                .getSharedPreferences(nome, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun nuovoViewModel(): WearViewModel {
        val telefono = Mockito.mock(OptimizedWearDataSync::class.java)
        Mockito.`when`(telefono.connectionState).thenReturn(MutableStateFlow(ConnectionState.Disconnected))
        return WearViewModel(application, telefono)
    }

    /** Lo stato dei due lati a [punti]; [versione] nulla e' un telefono che non la manda. */
    private fun mappa(
        punti: String,
        versione: Long?,
    ): DataMap =
        DataMap().apply {
            putString(WearConstants.KEY_SPORT_ID, "padel")
            putString(WearConstants.KEY_SIDE1_PRIMARY, punti)
            putString(WearConstants.KEY_SIDE2_PRIMARY, "0")
            if (versione != null) putLong(WearConstants.KEY_STATE_VERSION, versione)
        }

    private fun stato(
        punti: String,
        versione: Long?,
    ) = WearScoreState.fromDataMap(mappa(punti, versione))

    private fun punteggioMostrato() = viewModel.scoreState.value?.side1Primary

    private lateinit var viewModel: WearViewModel

    @Test
    fun `il ViewModel ignora uno stato con versione minore dell'ultima vista`() {
        viewModel = nuovoViewModel()

        viewModel.applyStateV2(stato("40", 10L))
        viewModel.applyStateV2(stato("15", 9L))

        assertEquals("il 9 e' superato dal 10", "40", punteggioMostrato())
    }

    @Test
    fun `il ViewModel applica uno stato con versione uguale o maggiore`() {
        viewModel = nuovoViewModel()

        viewModel.applyStateV2(stato("15", 10L))
        viewModel.applyStateV2(stato("15", 10L))
        viewModel.applyStateV2(stato("30", 11L))

        assertEquals("30", punteggioMostrato())
    }

    @Test
    fun `il ViewModel applica uno stato senza versione anche dopo uno con versione`() {
        viewModel = nuovoViewModel()

        viewModel.applyStateV2(stato("40", 10L))
        viewModel.applyStateV2(stato("15", null))

        assertEquals("un telefono vecchio non manda la versione: si applica come prima", "15", punteggioMostrato())
        assertEquals("e non cancella quella ricordata", 10L, LastKnownMatch(contesto).versioneStato)
    }

    @Test
    fun `la versione vista sopravvive a un nuovo ViewModel`() {
        nuovoViewModel().applyStateV2(stato("40", 10L))
        viewModel = nuovoViewModel()

        viewModel.applyStateV2(stato("15", 9L))

        assertEquals("dopo il riavvio il 9 e' ancora superato: l'ultimo visto sta su disco", null, punteggioMostrato())
    }

    /** Un item come lo consegna il Data Layer: i byte del DataMap, il path del v2. */
    private fun itemV2(mappa: DataMap): DataItem {
        val item = Mockito.mock(DataItem::class.java)
        Mockito.`when`(item.uri).thenReturn(Uri.parse("wear://nodo" + WearConstants.PATH_STATE_V2))
        Mockito.`when`(item.data).thenReturn(mappa.toByteArray())
        Mockito.`when`(item.freeze()).thenReturn(item)
        return item
    }

    /** Quanti v2 il servizio ha inoltrato alla schermata, con la loro versione. */
    private fun ascoltaInoltri(): MutableList<Long> {
        val inoltrati = mutableListOf<Long>()
        val ricevitore =
            object : android.content.BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    val payload = intent.getByteArrayExtra(WearDataLayerService.EXTRA_V2_PAYLOAD) ?: return
                    inoltrati += WearScoreState.fromDataMap(DataMap.fromByteArray(payload)).stateVersion
                }
            }
        LocalBroadcastManager
            .getInstance(contesto)
            .registerReceiver(ricevitore, android.content.IntentFilter(WearDataLayerService.ACTION_STATE_V2_UPDATE))
        return inoltrati
    }

    private fun consegna(
        punti: String,
        versione: Long?,
    ) {
        WearDataLayerService.dispatchDataItem(contesto, itemV2(mappa(punti, versione)))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `il servizio senza Activity non inoltra uno stato con versione minore dell'ultima vista`() {
        val inoltrati = ascoltaInoltri()

        consegna("40", 10L)
        consegna("15", 9L)

        assertEquals("solo il 10: il 9 e' superato", listOf(10L), inoltrati)
        assertEquals(10L, LastKnownMatch(contesto).versioneStato)
    }

    @Test
    fun `il servizio senza Activity inoltra uno stato senza versione`() {
        val inoltrati = ascoltaInoltri()

        consegna("40", 10L)
        consegna("15", null)

        assertEquals("il senza versione passa, come con un telefono non aggiornato", listOf(10L, 0L), inoltrati)
    }
}
