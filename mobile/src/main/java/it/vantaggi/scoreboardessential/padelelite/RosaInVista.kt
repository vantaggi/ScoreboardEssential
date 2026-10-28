package it.vantaggi.scoreboardessential.padelelite

import it.vantaggi.scoreboardessential.utils.testoPiano

/**
 * Come si mostra la rosa del gruppo (revisione dei menu, passo 4): prima i collegati, che sono quelli
 * che contano, poi gli altri. Gli altri sono tutti in vista, perche' si collegano toccando il nome
 * (scelta di Cioti, 28 ottobre 2026: "se ci sono i nomi gia' scritti, clicco e via"); la ricerca
 * serve solo a stringere una lista lunga.
 *
 * [daCollegare] tiene sempre i nomi uguali da confermare (sono pochi e chiedono una risposta), poi
 * tutti gli altri, oppure, se [cerca] non e' vuoto, chi ci corrisponde. Il confronto ignora maiuscole e accenti.
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
                altri
                    .filter { it.proposedLocalName == null && piano(it.remote.name).contains(chiave) }
                    .sortedWith(perNome)
            return RosaInVista(collegati.sortedWith(perNome), proposti + trovati, altri.size)
        }

        private fun piano(testo: String): String = testoPiano(testo)
    }
}
