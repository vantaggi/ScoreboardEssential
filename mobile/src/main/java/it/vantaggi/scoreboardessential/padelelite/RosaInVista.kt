package it.vantaggi.scoreboardessential.padelelite

import it.vantaggi.scoreboardessential.utils.testoPiano

/**
 * Come si mostra la rosa del gruppo (revisione dei menu, passo 4): prima i collegati, che sono quelli
 * che contano, poi gli altri dietro una ricerca. Una rosa della dashboard puo' avere cinquanta nomi e
 * quasi tutti "Non collegato": la lista lunga si apre solo per chi cerca qualcuno.
 *
 * [daCollegare] tiene sempre i nomi uguali da confermare (sono pochi e chiedono una risposta), poi chi
 * corrisponde a [cerca]. Con [cerca] vuoto non c'e' nient'altro. Il confronto ignora maiuscole e accenti.
 */
data class RosaInVista(
    val collegati: List<RosaRow>,
    val daCollegare: List<RosaRow>,
    val totaleDaCollegare: Int,
) {
    companion object {
        fun di(
            righe: List<RosaRow>,
            cerca: String,
        ): RosaInVista {
            val perNome = compareBy<RosaRow> { piano(it.remote.name) }
            val (collegati, altri) = righe.partition { it.linkedLocalName != null }
            val chiave = piano(cerca.trim())
            val proposti = altri.filter { it.proposedLocalName != null }.sortedWith(perNome)
            val trovati =
                if (chiave.isEmpty()) {
                    emptyList()
                } else {
                    altri.filter { it.proposedLocalName == null && piano(it.remote.name).contains(chiave) }.sortedWith(perNome)
                }
            return RosaInVista(collegati.sortedWith(perNome), proposti + trovati, altri.size)
        }

        private fun piano(testo: String): String = testoPiano(testo)
    }
}
