# Prompt per la chat della dashboard: adottare la UI Constitution

Da incollare in una sessione di Claude Code aperta sul repository `vantaggi/padel-dashboard`.

```text
Lavoriamo sul repository della dashboard Padel Elite (vantaggi/padel-dashboard). Obiettivo: allineare
tutta l'interfaccia al mio design system "UI Constitution", come e' gia' stato fatto nell'app
ScoreboardEssential, cosi' che app e dashboard sembrino lo stesso prodotto.

REGOLE (la dashboard e' PRODUZIONE, usata ogni settimana):
- Lavora su branch creati da `development` e apri PR verso `development`; puoi unirle tu in development
  quando i controlli sono verdi. Il merge su `main` lo faccio io alla fine di tutto: mai push o merge su main.
- Nessuna scrittura sul database Supabase di produzione, nessuna migrazione applicata, nessuna chiave o
  login. Questo lavoro e' solo interfaccia: non toccare logica dei dati, RPC, migrazioni, policy.
- Le preview di Vercel puntano al database vero: per provare la UI usa solo `?test=true` (mock).
- Lavora a passi corti (20-30 minuti), ognuno chiuso da un commit; commit in italiano nello stile del
  repo (feat(ui): ..., fix(ui): ...), che finiscono con la riga Co-Authored-By del modello che usi.

FONTI (leggile prima di toccare codice):
1. La UI Constitution: https://claude.ai/artifact/8DhrtZW85wiHTsfw1vmXSn (sola lettura, non pubblicarci
   niente). Leggi il README (regole) e le sezioni adapting, layout, motion, review, integration
   (la parte Web: tokens.css e mappa Tailwind) e la tabella delle icone (assets/Icons/README.md).
2. Come e' stata adattata nell'app: nel repository vantaggi/ScoreboardEssential, ramo main, il file
   DESIGN.md, sezione "Adattamento alla UI Constitution" (tabella ruolo -> valore, conflitti dichiarati,
   decisioni) e CLAUDE.md. Leggili con `gh api repos/vantaggi/ScoreboardEssential/contents/DESIGN.md -q .content | base64 -d`.
3. Il codice della dashboard: css/style.css (:root e i temi: questi sono i valori veri; docs/DESIGN_SYSTEM.md
   e' in parte superato), tailwind.config.js, js/*.js, index.html.

DECISIONI GIA' PRESE (valgono anche qui, per coerenza con l'app):
- I valori di Padel Elite restano e si mappano sui ruoli della Constitution (canvas #0D0D0F, surface #161618,
  elevated #1E1E22, testo #D1D1D8 / #8A8A9A, accento unico lime #C8F135 con testo #0D0D0F sopra,
  ciano #00E5FF solo nei grafici, avviso #E09A35, errore #E05252). Il contorno dei controlli che deve
  arrivare a 3:1 usa #6E6E7E (il bordo forte #2A2A2E fa 1,36:1 sul fondo).
- Carattere del testo: Inter. Numeri e punteggi: Inter con cifre tabulari (font-variant-numeric: tabular-nums),
  NON JetBrains Mono (la Constitution riserva il monospazio al codice). Al massimo tre pesi per schermata:
  400, 500, 600.
- I due lati di una partita: lato 1 lime, lato 2 ciano, ovunque (Cronaca gia' cosi'; la Diretta oggi usa
  lime contro rosso: va allineata). Chi serve si segna con il lime.
- Gruppi tonali (superficie su canvas, bordo del gruppo, righe separate da linee sottili rientrate) al
  posto di card ovunque; card solo per elementi indipendenti; nessuna ombra sulle superfici ferme; un solo
  bottone primario per regione; badge solo bordo; niente glow, gradienti decorativi, emoji
  nell'interfaccia (restano nei contenuti come achievement e Gazzetta); stessa pressione ovunque
  (scala 0,97 e opacita' 0,85 in 100ms); movimento solo fra due stati, durate 100/160/240ms, e
  prefers-reduced-motion rispettato.
- "Togliere prima di rimpicciolire": ogni schermata ha un compito e un valore piu' importante; stati
  vuoti ed errori scritti come chiede la Constitution; niente toni da marketing.
- Temi: la dashboard ha nove temi; tienili, ma ognuno deve definire gli stessi ruoli; aggiungi il tema ad
  alto contrasto della Constitution (testo 7:1, bordi 3:1).
- Icone: la Constitution sul web dice Lucide; la dashboard usa Heroicons. Proponimi una scelta con i
  costi (migrare a Lucide con la tabella concetto -> icona, oppure tenere Heroicons come conflitto
  dichiarato con la stessa tabella dei concetti) prima di cambiare le icone.

COME PROCEDERE (procedura "Adapting" della Constitution):
1. Ispeziona il codice e scrivi in docs/DESIGN_SYSTEM.md (riscrivendolo, perche' e' superato) la sezione
   "Adattamento alla UI Constitution": le sei righe d'identita' della dashboard (e' una dashboard web densa,
   letta con calma, molte tabelle: densita' compatta/bilanciata), la tabella ruolo -> variabile CSS ->
   valore (Padel Elite o predefinito della Constitution), i contrasti in ogni tema, i conflitti dichiarati.
2. Aggiungi alla radice un CLAUDE.md breve dal modello di integration.md ("Instructions for coding agents"),
   compilato per la dashboard.
3. Collega i token: le variabili CSS dei ruoli (in style.css o in un file caricato prima) e la mappa di
   tailwind.config.js sui ruoli; niente colori, raggi, ombre o durate scritti a mano nelle classi nuove.
4. Poi le schermate, una o due per passo: classifica, match e inserimento risultato, casella d'arrivo e
   import da file, Cronaca, Diretta, tornei, profilo, impostazioni, modali.
5. Ogni passo: test Playwright (`npx playwright test --workers=1`, la CI usa un worker), `npm run build:css`
   e `npm run build`, schermate con ?test=true a 375px, ~700px e 1280px, e alla fine il rapporto e il
   controllo dei pattern vietati di review.md.
Prima di cominciare dichiarami il piano dei passi (con le decisioni che ti servono da me) e aspetta il mio ok.
```
