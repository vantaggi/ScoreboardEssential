package it.vantaggi.scoreboardessential.wear

import android.app.Application
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * MainActivity ottiene il ViewModel con `by viewModels()` senza factory esplicita, quindi
 * AndroidViewModelFactory lo istanzia PER RIFLESSIONE cercando il costruttore (Application).
 *
 * Quel contratto non e' visibile nel codice e non lo esercita nessun altro test: gli altri
 * costruiscono il ViewModel direttamente passando tutti gli argomenti. Aggiungere un secondo
 * parametro con un valore di default lo ha gia' rotto una volta, facendo crashare l'orologio
 * all'avvio mentre l'intera suite restava verde.
 */
class WearViewModelConstructorTest {
    @Test
    fun `esiste il costruttore (Application) che AndroidViewModelFactory cerca per riflessione`() {
        assertNotNull(
            "WearViewModel deve esporre un costruttore pubblico che prende solo Application: " +
                "AndroidViewModelFactory lo risolve per riflessione e senza di esso MainActivity " +
                "crasha con NoSuchMethodException.",
            WearViewModel::class.java.getConstructor(Application::class.java),
        )
    }
}
