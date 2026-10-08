package it.vantaggi.scoreboardessential.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import it.vantaggi.scoreboardessential.core.Serata
import it.vantaggi.scoreboardessential.core.SerataCodec

/**
 * Dove si ricorda la serata in corso: una sola, finche' l'utente non la chiude.
 *
 * E' un'interfaccia perche' la serata ha due utenti nello stesso processo (la schermata Serata e il
 * [it.vantaggi.scoreboardessential.MainViewModel], che la consegna al motore e la chiude a fine
 * partita) e i test li provano con una memoria al posto delle preferenze.
 *
 * Niente tabella: la serata e' un testo piccolo ([SerataCodec]) e non una relazione con le partite.
 * Le partite giocate restano nello storico, uguali a ogni altra; chiudere la serata toglie solo
 * questo testo.
 */
interface SerataStore {
    /** La serata ricordata, o null se non c'e' (o se il testo non si legge). */
    fun load(): Serata?

    /** Ricorda [serata]; null la chiude. */
    fun save(serata: Serata?)
}

/** La serata nelle preferenze dell'app (`app_prefs`, chiave [CHIAVE]): sopravvive alla morte del processo. */
class SerataPrefsStore(
    private val prefs: SharedPreferences,
) : SerataStore {
    override fun load(): Serata? = SerataCodec.decode(prefs.getString(CHIAVE, null))

    override fun save(serata: Serata?) {
        prefs.edit {
            if (serata == null) remove(CHIAVE) else putString(CHIAVE, SerataCodec.encode(serata))
        }
    }

    companion object {
        const val CHIAVE = "serata"
    }
}
