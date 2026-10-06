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
