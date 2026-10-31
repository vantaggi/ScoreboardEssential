# ScoreboardEssential: istruzioni per gli agenti

Regole del progetto: i commit sono in italiano e finiscono con la riga `Co-Authored-By: <modello> <noreply@anthropic.com>`. Padel Elite e' **produzione** (nessuna scrittura sul suo Supabase senza autorizzazione), le trappole di build (`--` nei commenti XML, apostrofi nelle stringhe, `./gradlew --stop`) e il comando di verifica sono in `MIGRATION_PLAN.md`: leggerlo prima di toccare codice o dati.

## Interfaccia

Il lavoro sull'interfaccia segue la **UI Constitution**. Le regole per gli agenti, il link al sistema, i file dei token e l'identita' del progetto sono in `AGENTS.md`, letto anche dagli agenti che non usano questo file:

@AGENTS.md

## Regole per unire le PR (dal 15 ottobre 2026)

**Finche' l'app non e' pubblicata** ogni PR va verso `main` e si unisce direttamente su `main`: per l'app non c'e' un ramo `development` (la dashboard ce l'ha, l'app no). Alla pubblicazione queste regole si rivedono.

Una PR si unisce solo quando valgono **tutte**:

1. **CI verde sull'ultimo commit**: "Android CI/CD" (`build_and_test`: test, ktlintCheck, lint, assembleDebug e release). Rossa, annullata o ancora in corso vuol dire che non si unisce. Mai saltare, disattivare o mettere in quarantena un test per avere il verde, e niente commit vuoti per rilanciarla.
2. **Nessun conflitto con `main`.** Se c'e', si unisce `main` nel branch (merge, mai rebase o force-push su un branch di altri) e si aspetta di nuovo la CI.
3. **La PR dice com'e' stata verificata**: cosa e' stato eseguito e dove, cosa no ("non visto a schermo", "senza SDK nel container"), e per ogni test nuovo che cosa lo rende rosso.
4. **Nessun commento di revisione senza risposta**; i rilievi bloccanti sono corretti.
5. **Documenti aggiornati**: l'Avanzamento in `PIANO_PADEL_ELITE.md` e, per interfaccia o prodotto, la sezione di `DESIGN.md` (piano UI, stati, scelte, conflitti, debiti, controllo dei pattern vietati).
6. **Padel Elite e' produzione.** Una PR che scrive sul suo Supabase o che chiede una migrazione non si unisce senza il si' esplicito del proprietario; le letture stanno dentro le policy che esistono.
7. **Database Room e protocollo con l'orologio.** Una migrazione Room o un cambio del protocollo (il golden test di `:shared`) si unisce dopo gli strumentati in locale, oppure la PR lo dichiara e decide il proprietario.

Chi unisce e come:

- La sessione che apre la PR **non la unisce da sola**: segue la CI fino al verde (o fino a un errore che non puo' capire, e allora lo descrive) e riferisce lo stato alla sessione che l'ha avviata, o al proprietario. Unisce la sessione che coordina, o il proprietario, quando i punti 1-7 sono veri. Fa eccezione la PR che il proprietario ha detto per scritto di unire.
- Le PR che cambiano l'aspetto dell'app le unisce il proprietario, dopo averle provate sul telefono e sull'orologio (31 ottobre 2026).
- Si unisce con un **merge commit** (non squash, non rebase): restano i commit in italiano con la loro riga `Co-Authored-By`, come gli `Merge branch 'wf...'` della storia.
- Dopo l'unione: nell'Avanzamento la riga passa da "in PR" a "unito su `main`" con l'hash, le verifiche a schermo elencate nella PR restano da fare in locale, e il branch si cancella.
