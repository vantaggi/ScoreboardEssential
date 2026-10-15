# ScoreboardEssential: istruzioni per gli agenti

Regole del progetto: i commit sono in italiano e finiscono con la riga `Co-Authored-By: <modello> <noreply@anthropic.com>`. Padel Elite e' **produzione** (nessuna scrittura sul suo Supabase senza autorizzazione), le trappole di build (`--` nei commenti XML, apostrofi nelle stringhe, `./gradlew --stop`) e il comando di verifica sono in `MIGRATION_PLAN.md`: leggerlo prima di toccare codice o dati.

Il lavoro sull'interfaccia segue la **UI Constitution**, il design system del proprietario: <https://claude.ai/artifact/8DhrtZW85wiHTsfw1vmXSn> (sola lettura, non pubblicarci niente).

Prima di ogni modifica all'interfaccia:
1. Leggere nella Constitution le regole (README) e le sezioni Adapting, Layout, Motion, Review; per l'orologio anche Wearable.
2. Leggere in `DESIGN.md` la sezione "Adattamento alla UI Constitution": e' la mappatura dei ruoli sui token Android (`mobile/src/main/res/values/colors.xml`, `themes.xml`), i conflitti dichiarati e il passo G-n in corso. Riusare i token e i componenti che ci sono.
3. Dichiarare il piano: gerarchia, layout, componenti riusati, stati, movimento, accessibilita', comportamento a schermo stretto e al 200%, conflitti.

Sempre:
- Solo token: nessun colore, spaziatura, raggio, ombra, durata o curva scritti a mano. Valori nuovi solo se nominano un ruolo riusabile.
- Icone dalla tabella concetto -> icona di `DESIGN.md` (G-3): Material Symbols outlined, una famiglia, niente emoji; i comandi di sola icona hanno `contentDescription`.
- Ogni stato che vale: normale, premuto, fuoco, selezionato, disattivo, in corso, errore, vuoto, sola lettura, movimento ridotto. Un solo bottone primario per regione; niente ombre sulle superfici ferme; gruppi tonali, card solo per elementi indipendenti; al massimo tre pesi per schermata (400, 500, 600).
- Movimento solo fra due stati, coi token di durata (100, 160, 240ms) e le molle; la stessa pressione ovunque (scala 0,97, opacita' 0,85); mai cicli senza significato.
- Togliere prima di rimpicciolire: ogni schermata ha un compito e un valore piu' importante.
- Numeri in Inter con `tnum`; sul quadrante dell'orologio le cifre restano condensate (Inter non entra).
- Finire con il rapporto e il controllo dei pattern vietati di `review.md`.

Identita' del telefono:
```text
Product character: tabellone da bordo campo, letto di sfuggita e toccato col pollice; contorno sobrio nel linguaggio di Padel Elite
Density: balanced nel contorno; in gioco un numero per lato
Geometry: precise
Accent behavior: restrained
Motion character: precise
Primary domain pattern: tabellone a due meta' con punteggio tabulare, registro e Cronaca
Distinctive visual behavior: nero puro in gioco, cifre bianche giganti, colore di squadra solo in zone + e barretta (TeamInk), pallino lime per il servizio
```

Identita' dell'orologio:
```text
Product character: un numero al polso, letto in mezzo secondo e toccato con un dito
Density: compact
Geometry: precise (tondo prima, quadrato dopo)
Accent behavior: functional (il lime marca solo chi serve; in ambient nessun colore)
Motion character: calm
Primary domain pattern: quadrante a fasce con la coppia di cifre al centro e ricevuta aptica per lato
Distinctive visual behavior: cifre bianche su nero puro, strisce di squadra, pallino lime, ambient con le sole cifre sottili
```

Toni e accento (tema scuro, l'unico): `background-canvas` = `#0D0D0F` (`elite_background`), `background-surface` = `#161618` (`elite_surface`), `accent-default` = lime `#C8F135` (`elite_lime`), `on-accent` = `#0D0D0F`; gioco e quadrante su nero `#000000` con cifre `#FFFFFF`. Il ciano `#00E5FF` solo nei grafici.

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

- La sessione che apre la PR **non la unisce da sola**: segue la CI fino al verde (o fino a un errore che non puo' capire, e allora lo descrive) e riferisce lo stato alla sessione che l'ha avviata, o al proprietario. Unisce la sessione che coordina, o il proprietario, quando i punti 1-7 sono veri.
- Si unisce con un **merge commit** (non squash, non rebase): restano i commit in italiano con la loro riga `Co-Authored-By`, come gli `Merge branch 'wf...'` della storia.
- Dopo l'unione: nell'Avanzamento la riga passa da "in PR" a "unito su `main`" con l'hash, le verifiche a schermo elencate nella PR restano da fare in locale, e il branch si cancella.
