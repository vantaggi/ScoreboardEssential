package it.vantaggi.scoreboardessential.utils

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.util.concurrent.atomic.AtomicBoolean

class SingleLiveEvent<T> : MutableLiveData<T>() {
    private val pending = AtomicBoolean(false)

    override fun observe(
        owner: LifecycleOwner,
        observer: Observer<in T>,
    ) {
        super.observe(
            owner,
            Observer { t ->
                if (pending.compareAndSet(true, false)) {
                    observer.onChanged(t)
                }
            },
        )
    }

    override fun setValue(t: T?) {
        pending.set(true)
        super.setValue(t)
    }

    // Qui c'era `fun call() { value = null }`, pensato per gli eventi senza dato. Ma quegli eventi
    // sono SingleLiveEvent<Unit>, e un osservatore Kotlin ha il parametro Unit non nullo: il null
    // lo faceva crashare (visto nelle impostazioni, cambiando sport a partita cominciata). Tolto
    // invece che corretto, perche' non puo' sapere quale valore pubblicare: chi lancia un evento
    // Unit scrive `value = Unit`, e nessuno puo' piu' pubblicare null per sbaglio.
}
