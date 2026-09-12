package it.vantaggi.scoreboardessential.utils

/**
 * Decide se i FAB della schermata principale vanno nascosti perche' coprono i comandi di fine
 * partita (HISTORY, END MATCH, SHARE).
 *
 * I FAB galleggiano sopra il contenuto che scorre, e la riga delle azioni e' l'ultima: il margine
 * in fondo la libera solo a fine scorrimento. In posizione di riposo, soprattutto quando
 * nell'intestazione fissa compare l'annulla e spinge giu' tutto, la riga finiva proprio sotto i
 * FAB. Un margine piu' grande dipenderebbe dall'altezza dello schermo; confrontare i rettangoli
 * veri a schermo no.
 *
 * E' logica pura, senza `android.graphics.Rect`, perche' nei test JVM quella classe e' uno stub.
 */
object FabOverlap {
    /** Un rettangolo a schermo, in pixel, con `right` e `bottom` esclusi come in `Rect`. */
    data class Box(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    // Toccarsi sul bordo non e' coprire: serve un'area in comune.
    fun intersects(
        a: Box,
        b: Box,
    ): Boolean = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    /** Vero se almeno un comando sta, anche in parte, sotto almeno un FAB. */
    fun fabsMustHide(
        actions: List<Box>,
        fabs: List<Box>,
    ): Boolean = actions.any { azione -> fabs.any { fab -> intersects(azione, fab) } }
}
