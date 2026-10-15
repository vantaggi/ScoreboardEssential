# Prompt per ripartire in una sessione nuova

Da incollare come primo messaggio di una sessione nuova di Claude Code aperta sul repository
ScoreboardEssential (cartella `C:\Users\cioti\Desktop\PROGETTI\ScoreboardEssential`).

```text
Riprendiamo il lavoro su ScoreboardEssential (app Android, telefono e orologio) e sulla dashboard Padel Elite
(repo vantaggi/padel-dashboard, clone in C:\Users\cioti\Desktop\PROGETTI\padel-dashboard-claude). Lavora a
oltranza a passi corti, prendendo tu le decisioni tecniche; chiedimi solo le decisioni di prodotto.

PRIMA DI TUTTO leggi, senza riassumermeli:
1. Nel repo dell'app: CLAUDE.md, MIGRATION_PLAN.md (trappole di build e verifica), PIANO_PADEL_ELITE.md
   (soprattutto la sezione 4 e l'ultima nota "15 ottobre 2026 - punto di ripartenza"), DESIGN.md solo dove serve.
   Il branch di lavoro e' claude/scoreboard-essential-review-c3c805: le ultime note di registro sono li' e non
   ancora su main (portale su main alla prima pubblicazione dell'app).
2. Nella dashboard (ramo development): docs/ANALISI_UI_CONSTITUTION.md (analisi, decisioni D1-D16, piano a 60
   passi in 7 fasi), docs/TELEMETRIA.md, docs/SCOREBOARD_FORMAT.md solo se tocchi casella o diretta.
3. La UI Constitution: https://claude.ai/artifact/8DhrtZW85wiHTsfw1vmXSn (sola lettura; README, adapting,
   layout, motion, review, integration, tabella icone).

REGOLE FERME
- App: si pubblica direttamente su main dopo verifica completa (./gradlew clean test ktlintCheck lintDebug
  assembleDebug assembleDebugAndroidTest; se il clean e' bloccato da un daemon usa cleanTest, mai --stop ne'
  kill di processi) e, se si tocca l'interfaccia, strumentati su Pixel_9a_Test.
- Dashboard: e' PRODUZIONE. Solo branch da development e PR verso development, merge con CI verde. MAI push o
  merge su main: main lo unisce solo il proprietario (la PR di rilascio e' la #219). Le patch urgenti per main
  si preparano come PR verso main e le unisco io.
- Database Supabase di produzione (progetto PadelElite): letture permesse; OGNI scrittura o migrazione solo
  dopo il mio via libera esplicito per quella specifica operazione. Le migrazioni 64, 65, 66, 67 sono gia'
  applicate (13 ottobre).
- Commit in italiano; nella dashboard nello stile del repo (feat/fix/docs(...): ...); ogni commit chiuso dalla
  riga Co-Authored-By del modello che lo scrive. Corpo delle PR chiuso dalla riga di Claude Code.
- Non toccare C:\Users\cioti\Desktop\PROGETTI\padel (copia del proprietario).

COSTI (importante)
- Massimo 1-2 agenti alla volta, passi da 20-40 minuti, ognuno chiuso da un commit o una PR.
- Implementazione meccanica: Haiku o Sonnet; Sonnet per il resto; Opus solo per le revisioni avversarie su
  sicurezza, dati e migrazioni, e una revisione Opus a fine di ogni fase del piano della dashboard.
- Niente attese a vuoto: la CI della dashboard ora gira su 4 shard in ~4-5 minuti con un ripetersi automatico.
- Rispetta le soglie: non avviare passi nuovi oltre il 50% del limite delle 5 ore o l'85% del settimanale.

DOVE SIAMO
- App: diretta solo admin (D-2) e limiti della diretta (D-3, servizio dataSync) su main; da provare su un
  telefono vero con Android 15 e da dichiarare dataSync nella Play Console (testo in RELEASE_CHECKLIST.md).
- Dashboard development: fase 0 in parte fatta (XSS #224, telemetria fine #228/#230, gironi salvati #229, CI
  veloce #231). Decisioni prese: D1 temi stagionali come sono; D4 icone Material Symbols outlined in SVG con la
  tabella dell'app; D10 navigazione Classifica, Serata, Tornei, Statistiche, Altro + Gestione per gli admin +
  striscia In diretta; D16 telemetria fine; per le altre si segue la raccomandazione del documento.

COSA FARE ORA
1. Fase 0 rimanente della dashboard, un passo per PR: 0.2 (classi non compilate e variabili CSS mancanti), 0.3
   (Chart.js a versione fissa, modale legacy e doppioni di funzione), 0.4 (CTA "Inserisci match" solo ad admin
   e owner, FAB tolti, fuoco senza raggio, nomi accessibili ai comandi di sola icona, viewport-fit=cover e zoom
   sbloccato, safe area, colonna dei nomi della classifica visibile a 1024px). Lo 0.7 (Capacitor) aspetta la
   decisione D13: chiedimela quando ci arrivi.
2. Revisione Opus della fase 0, poi fase 1 (token) secondo il piano.
Alla fine di ogni passo: una riga nell'avanzamento del piano e un riassunto breve per me.
```
