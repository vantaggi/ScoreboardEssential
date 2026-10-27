package it.vantaggi.scoreboardessential.ui.statistics

/**
 * Le regole della classifica che valgono per gol e vittorie (revisione dei menu, passo 6).
 *
 * Il lime marca il primo solo quando c'e' davvero un primo: un valore sopra zero e piu' alto del
 * secondo. Con tutti a zero, o due in testa a pari merito, nessuno e' lime. E una classifica di
 * soli zeri non e' una classifica: e' lo stato vuoto.
 */
object Classifica {
    fun haUnPrimo(valori: List<Int>): Boolean {
        val primo = valori.firstOrNull() ?: return false
        return primo > 0 && (valori.size == 1 || primo > valori[1])
    }

    fun eVuota(valori: List<Int>): Boolean = valori.none { it > 0 }
}
