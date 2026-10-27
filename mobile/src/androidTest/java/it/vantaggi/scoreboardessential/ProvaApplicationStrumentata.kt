package it.vantaggi.scoreboardessential

import android.content.Context
import it.vantaggi.scoreboardessential.padelelite.KeystoreSecretBox
import it.vantaggi.scoreboardessential.padelelite.PadelEliteAccount
import it.vantaggi.scoreboardessential.padelelite.PadelEliteApi
import it.vantaggi.scoreboardessential.padelelite.PadelEliteConfig
import it.vantaggi.scoreboardessential.padelelite.PadelEliteGroup
import it.vantaggi.scoreboardessential.padelelite.PadelEliteServices
import it.vantaggi.scoreboardessential.padelelite.PadelEliteSession
import it.vantaggi.scoreboardessential.padelelite.SessionStore

/**
 * L'applicazione dei test strumentati ([ProvaRunner]). Padel Elite punta a un indirizzo dove non risponde nessuno
 * (`127.0.0.1:1`, la rete del telefono stesso): la diretta si accende ma ogni richiesta fallisce e resta in attesa per
 * la rete. **Nessuna richiesta arriva al Supabase vero**, che e' produzione. La sessione e' finta e il gruppo e' admin,
 * cosi' la diretta si puo' accendere senza accesso.
 */
class ProvaApplicationStrumentata : ScoreboardEssentialApplication() {
    override val padelElite: PadelEliteServices by lazy {
        val config = PadelEliteConfig(INDIRIZZO_MUTO, "chiave-finta")
        PadelEliteServices(
            this,
            config,
            matchRepository,
            accountFactory = {
                val sessione = SessionStore(getSharedPreferences("sessione_strumentata", Context.MODE_PRIVATE), KeystoreSecretBox())
                sessione.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "prova@esempio.it"))
                PadelEliteAccount(config, PadelEliteApi(config), sessione).also {
                    it.selectGroup(PadelEliteGroup("g-1", "Padel", "admin"))
                }
            },
        )
    }

    companion object {
        const val INDIRIZZO_MUTO = "https://127.0.0.1:1"
    }
}
