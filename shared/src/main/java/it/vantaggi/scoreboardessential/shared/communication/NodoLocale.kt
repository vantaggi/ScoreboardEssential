package it.vantaggi.scoreboardessential.shared.communication

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable

/**
 * L'id del nodo su cui gira questo processo: l'host dell'URI di ogni DataItem che scrive lui.
 *
 * Serve a scartare cio' che il Data Layer riconsegna al suo stesso autore: i servizi che ascoltano
 * gli stessi path che scrivono non devono rileggersi (L6). L'id si chiede senza bloccare nessun
 * thread e si ricorda per tutta la vita del processo. Finche' non e' noto [eLocale] dice di no,
 * cioe' il comportamento di prima: scartare per errore un DataItem del telefono sarebbe peggio
 * che rigiocare uno dei propri.
 */
object NodoLocale {
    private const val TAG = "NodoLocale"

    @Volatile
    private var id: String? = null

    @Volatile
    private var inCorso = false

    /** Il DataItem con questo host e' stato scritto da questo nodo. Falso se l'id non e' ancora noto. */
    fun eLocale(host: String?): Boolean {
        val mio = id ?: return false
        return host == mio
    }

    /** Avvia la richiesta dell'id, se manca e non ce n'e' gia' una in volo: serve ai service, che non aspettano. */
    fun richiedi(context: Context) {
        if (id != null || inCorso) return
        inCorso = true
        leggi(context) { inCorso = false }
    }

    /**
     * Chiama [dopo] con l'id, subito se e' noto, altrimenti a risposta arrivata; con null se il
     * Data Layer non lo da': chi chiama ripiega allora sul comportamento di prima.
     */
    fun leggi(
        context: Context,
        dopo: (String?) -> Unit,
    ) {
        id?.let {
            dopo(it)
            return
        }
        try {
            Wearable
                .getNodeClient(context)
                .localNode
                .addOnSuccessListener { nodo ->
                    id = nodo.id
                    dopo(nodo.id)
                }.addOnFailureListener { e ->
                    Log.w(TAG, "Nodo locale non disponibile", e)
                    dopo(null)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Nodo locale non disponibile", e)
            dopo(null)
        }
    }

    /** Solo per i test: il processo di prova e' uno solo e ricorda l'id da un test all'altro. */
    fun azzera() {
        id = null
        inCorso = false
    }
}
