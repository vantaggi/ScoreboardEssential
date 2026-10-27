package it.vantaggi.scoreboardessential

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Il runner dei test strumentati: l'applicazione e' [ProvaApplicationStrumentata], che non parla con nessun server vero. */
class ProvaRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, ProvaApplicationStrumentata::class.java.name, context)
}
