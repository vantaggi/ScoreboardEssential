# Piano: prova sul campo, coerenza grafica con Padel Elite, invio diretto della partita

Scritto il 5 ottobre 2026, dopo la chiusura dei lotti L1-L12 e del design di telefono e orologio
(vedi `MIGRATION_PLAN.md`, sezione "RIPRESA"). Le informazioni sulla dashboard vengono da una
lettura in sola lettura di `vantaggi/padel-dashboard` (via `gh api`, nessuna scrittura, nessuna
chiamata a Supabase). Padel Elite resta **produzione**: ogni passo che la tocca e' segnato con
**[AUTORIZZAZIONE]** e non parte senza un si' esplicito del proprietario.

**Decisioni del proprietario (5 ottobre 2026, sera).** Accettate tutte le proposte del piano: G1
via lo street dal contorno, G2 lati lime/ciano, G3 Inter e JetBrains Mono (cifre dell'orologio da
misurare), G4 solo il tema Navy scuro, G5 nome e icona da proporre; E-a casella d'arrivo per tutti i
membri con conferma di un admin, E-c proposta dei giocatori. Autorizzato il lavoro nel repository
`padel-dashboard` sul ramo `development` (branch e PR verso `development`; il merge su `main` lo fa
il proprietario alla fine, dopo le prove). **Non** autorizzati: scritture sul database di produzione
(la migrazione 64 si prova solo su Supabase locale con `npx supabase start`), creazione di progetti o
client esterni (Supabase di prova, OAuth Google): per ora l'accesso e' solo email/password. Lavoro
della dashboard nel clone `PROGETTI/padel-dashboard-claude` (la copia del proprietario
`PROGETTI/padel` non si tocca).

Tre filoni, in quest'ordine:

1. **Prova sul campo** con Galaxy Watch 8 e Galaxy S26 Ultra: subito, non dipende da niente.
2. **Coerenza grafica** con la dashboard: solo nell'app, nessuna autorizzazione.
3. **Invio diretto** della partita all'account Padel Elite: tocca la dashboard e il suo database.

---

## 1. Prova sul campo (questa settimana)

**Si puo' fare.** Telefono e orologio hanno minSdk 30 (Android 11 / Wear OS 3): l'S26 Ultra e il
Galaxy Watch 8 (Wear OS 6) sono coperti. L'app dell'orologio non e' autonoma (`standalone=false`):
lavora col telefono a cui l'orologio e' gia' abbinato con Galaxy Wearable. Le due app devono avere
lo stesso package (`it.vantaggi.scoreboardessential`, gia' cosi') e **la stessa firma**: si
installano tutte e due le build di debug compilate su questo PC, che usano la stessa chiave di debug.

### Installazione (la fa il proprietario, una volta)

Telefono:
1. Impostazioni > Info sul telefono > Informazioni software > tocca 7 volte "Numero build".
2. Opzioni sviluppatore > Debug USB attivo; collega il cavo e accetta l'impronta del PC.
3. `adb install -r mobile/build/outputs/apk/debug/mobile-debug.apk`

Orologio (senza cavo, debug via Wi-Fi; orologio e PC sulla stessa rete):
1. Impostazioni > Info sull'orologio > Software > tocca 7 volte "Versione software".
2. Opzioni sviluppatore > Debug ADB attivo, poi "Debug wireless" > "Associa nuovo dispositivo":
   l'orologio mostra indirizzo, porta e codice.
3. `adb pair <ip>:<porta-associazione>` col codice, poi `adb connect <ip>:<porta-debug>`.
4. `adb -s <ip>:<porta> install -r wear/build/outputs/apk/debug/wear-debug.apk`
5. Finita l'installazione si puo' spegnere il debug wireless (consuma batteria).

Prima di uscire:
- sul telefono, Batteria > Limiti di utilizzo in background: ScoreboardEssential **mai in
  sospensione** (Samsung chiude i servizi in background, e il servizio dei messaggi dell'orologio
  e' proprio quello);
- aprire l'app del telefono una volta, scegliere lo sport (padel) e le rose;
- aprire l'app dell'orologio e controllare che dica il gesto (TIENI: -1), non SCOLLEGATO.

### Cosa provare in partita (le cose che gli emulatori non sanno fare)

| # | Prova | Atteso |
|---|---|---|
| 1 | Segnare dal polso per un set intero | ogni tocco vibra (1 impulso sinistra, 2 destra), punteggio uguale su telefono e orologio |
| 2 | Pallini del servizio (padel) | 1 o 2 pallini dalla parte di chi serve, cambiano a ogni game |
| 3 | Telefono in borsa lontano (Bluetooth giu') | riga E: SCOLLEGATO, poi «n IN CODA»; il punteggio continua al polso |
| 4 | Ritorno del telefono | la coda parte da sola, il telefono mostra il riepilogo dei punti arrivati, la riga torna al gesto |
| 5 | App del telefono chiusa (swipe) e tocco dal polso | IN ATTESA TELEFONO; alla riapertura il punto c'e' una volta sola |
| 6 | Polso abbassato | ambient: cifre sottili, minuti, niente strisce; al rialzo nessun tocco perso |
| 7 | Corona nel menu | scorre le voci; FINE PARTITA col doppio tocco chiude e salva sul telefono |
| 8 | Partita intera | nello storico del telefono: una partita, punteggio giusto, Cronaca leggibile |
| 9 | Export | Condividi > Esporta partita: file JSON da tenere per l'import in Padel Elite (filone 3) |

Come raccogliere i problemi: una nota con l'ora; poi, col telefono collegato al PC,
`adb logcat -d > logcat-telefono.txt` (e lo stesso per l'orologio). I file si danno a Claude.
Cose gia' viste sugli emulatori: dopo un'installazione pulita il telefono chiede il permesso delle
notifiche all'avvio; l'app companion di Wear OS degli emulatori va in crash spesso (non e' la
nostra, e su dispositivi veri Samsung usa Galaxy Wearable). Il file esportato si potra' importare
nella dashboard solo dopo il rilascio della 4.12.0 (passo E-0 del filone 3).

---

## 2. Coerenza grafica con la dashboard

### Dove siamo

L'app ha un'identita' propria, "street": asfalto `#121212`, cemento, card ad angoli tagliati
(StreetCard), `sans-serif-condensed` bold, rosa e ciano neon, colori di squadra predefiniti giallo
`#FFD600` e verde `#76FF03`. La schermata di gioco e il quadrante sono gia' "ridotti" (nero puro,
cifre bianche enormi, colore di squadra solo in strisce e zone +, regola di contrasto TeamInk in
:core). Il problema percepito ("a volte sembra disegnata male") sta soprattutto nel contorno: foglio
PARTITA, storico, statistiche, giocatori, impostazioni, dialoghi, PDF, menu dell'orologio.

La dashboard (valori veri da `css/style.css`, non da `docs/DESIGN_SYSTEM.md` che e' indietro):

| Ruolo | Padel Elite | App oggi |
|---|---|---|
| fondo | `#0D0D0F` | `#000000` (gioco), `#121212` (contorno) |
| superficie / rialzata / hover | `#161618` / `#1E1E22` / `#2A2A2E` | `#1E1E1E`, `#2C2C2C` |
| bordo / bordo forte | `#1E1E22` / `#2A2A2E` | `#6E6E6E` (outline) |
| testo 1 / 2 / 3 | `#D1D1D8` / `#8A8A9A` / `#7F7F93` | `#E0E0E0` / `#9E9E9E` |
| marchio | lime `#C8F135` | rosa |
| accento (solo grafici) | ciano `#00E5FF` | ciano neon |
| errore / avviso | `#E05252` / `#E09A35` | `#FF1744` / ambra |
| caratteri | Inter; numeri JetBrains Mono 700-800 tabulari | sans-serif-condensed bold |
| angoli | card 14, tabellone 12, bottoni/input 8, badge 6 | angoli tagliati 12 |
| icone | Heroicons outline, tratto 2 | Material |
| regole | un solo bottone primario per vista (lime, testo scuro), badge solo bordo, niente glow, animazioni <= 240ms, bersagli >= 44px | bersagli >= 48dp |

Colori dei due lati nella dashboard: nella Diretta lime contro rosso, nella Cronaca lime contro
ciano. Non sono token: due convenzioni diverse.

### Principio proposto

**La dashboard da' il contorno, l'app tiene la schermata di gioco.** Cioe': tutto cio' che si
guarda con calma (foglio, storico, Cronaca, statistiche, giocatori, impostazioni, dialoghi, PDF,
menu dell'orologio) prende token, caratteri, forme e regole di Padel Elite, cosi' che passare
dall'app alla dashboard sembri lo stesso prodotto. La schermata di gioco e il quadrante restano
come sono (nero, cifre giganti, colore di squadra con TeamInk), cambiando solo caratteri delle
cifre e accenti, perche' sono stati progettati per la leggibilita' al sole e a distanza, non per
somigliare a una pagina web.

### Decisioni del proprietario (prima di cominciare)

- **G1. Identita' street**: si toglie del tutto dal contorno (proposta), o si tiene qualcosa (per
  esempio gli angoli tagliati sulle sole card di partita)?
- **G2. Colori predefiniti dei lati**: lime/ciano come la Cronaca (proposta: e' dove l'app e la
  dashboard mostrano la stessa partita), lime/rosso come la Diretta, o restano giallo/verde? Restano
  comunque personalizzabili, e TeamInk garantisce il contrasto.
- **G3. Caratteri**: Inter e JetBrains Mono nell'app (proposta: come font scaricabili di Google, o
  inclusi in `res/font`, circa 600 KB); sull'orologio, JetBrains Mono per le cifre se a 58sp nel
  192dp "AV" e "40" ci stanno (va misurato: il condensato e' piu' stretto), altrimenti si tiene il
  condensato sulle cifre e Inter sul resto.
- **G4. Temi**: la dashboard ha 9 temi (Navy predefinito, chiaro, dracula, neon, stagionali). Si
  parte dal solo Navy scuro (proposta); un tema chiaro dell'app e' un lavoro a parte.
- **G5. Icona e nome**: l'app resta "ScoreboardEssential" con un'icona sua, o diventa parte di
  Padel Elite (nome, icona lime)? Cambia anche la scheda Play.

### Passi (corti, uno per sessione, ognuno con test e prova sugli AVD)

| Passo | Cosa | Costo |
|---|---|---|
| G-0 | Token: `colors.xml` e tema M3 riscritti sui valori di Padel Elite (fondo, superfici, bordi, testi, lime, ciano solo nei grafici, errore, avviso), in un solo file come oggi; test di contrasto WCAG aggiornati | piccolo |
| G-1 | Caratteri Inter e JetBrains Mono (secondo G3), numeri tabulari ovunque ci sono punteggi | piccolo |
| G-2 | Componenti: card (raggio 14, bordo 1px, niente ombra), bottone primario lime unico per vista, secondario, distruttivo solo testo, badge solo bordo, input; via StreetCard dal contorno (secondo G1) | medio |
| G-3 | Icone: set outline a tratto 2 (Heroicons come vettoriali, o Material Symbols outlined, che ci assomigliano) | piccolo |
| G-4 | Foglio PARTITA, storico, statistiche, giocatori, impostazioni, onboarding sui nuovi componenti | medio |
| G-5 | Cronaca dell'app allineata alla Cronaca della dashboard: stesso ordine, stessi colori dei lati (G2), apice del tie-break, "B" sui break, grafico con lo stesso stile | medio |
| G-6 | Schermata di gioco: solo cifre (G3), accento lime al posto del rosa, colori predefiniti dei lati (G2) | piccolo |
| G-7 | Orologio: menu e selezione sport sui token (superfici, lime per l'azione, raggi), cifre secondo G3; quadrante invariato nel resto | piccolo |
| G-8 | PDF del report con gli stessi token e caratteri | piccolo |
| G-9 | Verifica: schermate app e pagine della dashboard affiancate (storico, Cronaca, tabellone), tre AVD dell'orologio, carattere al 200%, contrasti | medio |

Il dettaglio di ogni passo va in `DESIGN.md` come per le piste di telefono e orologio. Nessun passo
tocca la dashboard; se il proprietario vuole anche il contrario (la dashboard che prende qualcosa
dall'app), e' un lavoro su Padel Elite **[AUTORIZZAZIONE]**.

---

## 3. Invio diretto della partita all'account Padel Elite

### Si puo' fare? Si'. Cosa c'e' gia'

- **Import da file finito** in `vantaggi/padel-dashboard`, branch `development` (PR #211, #212,
  versione 4.12.0): admin > Inserisci match > Importa file, rigioca i punti col motore della
  dashboard, l'admin sceglie i giocatori nell'ordine di servizio, Cronaca per tutti i membri, test
  Playwright, di contratto su Postgres e differenziale col motore Kotlin. **Non e' ancora su main**:
  in produzione (Vercel) non c'e'.
- **Nel database di produzione c'e' gia'** la migrazione 63: tabella `v2_match_logs` e RPC
  `import_scoreboard_match(...)`, che chiama `create_match()` nella stessa transazione (monete,
  achievement, push come l'inserimento manuale) ed e' idempotente su `(group_id, external_id)`
  (il `matchId` del file).
- **Limiti per l'invio dall'app**: la RPC e' riservata a owner/admin del gruppo e vuole gli id dei
  quattro giocatori della dashboard, che l'app non conosce (per scelta: dalla v2 il file non porta
  piu' `padelPlayerId`). Per questo la dashboard aveva gia' disegnato una **casella d'arrivo**
  (`docs/SCOREBOARD_CRONACA_APP.md` §5): l'app carica la partita in una coda del gruppo, un admin la
  apre e sceglie i giocatori. **Non esiste ancora**: ne' tabella, ne' RPC, ne' schermata.
- Accesso: Supabase Auth (email/password e Google), gruppi con ruoli owner/admin/member,
  `v2_players.linked_user_id` lega un giocatore dell'archivio a un account.

### Il flusso proposto

1. Nell'app, una volta: **Accedi a Padel Elite** (stesso account della dashboard, email/password o
   Google), scelta del gruppo fra quelli di cui si e' membri.
2. A partita chiusa (dialogo di fine partita e storico, solo padel): **Invia a Padel Elite**. La
   partita parte subito se c'e' rete, altrimenti resta in coda e parte da sola appena c'e'.
3. Nella dashboard, gli admin vedono **Casella d'arrivo (n)**; aprono la partita nella schermata di
   import che esiste gia', con i nomi del file e, dove possibile, i giocatori gia' proposti (chi ha
   inviato e' `linked_user_id`; gli altri per nome); scelgono, confermano: la partita entra come
   oggi, con Cronaca.
4. Se chi invia e' admin, puo' fare il passo 3 subito dalla dashboard sul telefono: nessun file da
   girare a nessuno.

L'app resta usabile senza account e senza rete: l'invio e' un'aggiunta.

### Passi

Padel Elite (repository e database di produzione):

| Passo | Cosa | Chi |
|---|---|---|
| E-0 | Rilascio della 4.12.0: `development` -> `main` (l'import da file va in produzione) | proprietario |
| E-1 | Ambiente di prova: un progetto Supabase separato (gratuito) o `supabase start` in locale, con tutte le migrazioni; la preview Vercel di oggi scrive sul database vero, quindi **non** si prova li' | **[AUTORIZZAZIONE]** per crearlo |
| E-2 | Migrazione 64 (prima solo sull'ambiente di prova): tabella `v2_scoreboard_inbox` (gruppo, inviata da, `external_id` unico per gruppo, file v2 in jsonb, stato in attesa/importata/scartata, partita creata, date); RLS: chi invia vede le sue, owner/admin vedono quelle del gruppo, nessuna scrittura diretta; RPC `submit_scoreboard_match(p_group, p_payload)` per i membri (validazione di forma e dimensione come la 63, solo padel, idempotente); RPC per elencare e scartare; `import_scoreboard_match` con un parametro facoltativo per chiudere la voce della casella nella stessa transazione; push agli admin all'arrivo | branch e PR su `development` **[AUTORIZZAZIONE]** |
| E-3 | Dashboard: voce Casella d'arrivo per owner/admin, che apre l'import esistente precompilato; test Playwright e di contratto come per la 63 | stessa PR |
| E-4 | Migrazione 64 in produzione | proprietario **[AUTORIZZAZIONE]** |

App:

| Passo | Cosa | Costo |
|---|---|---|
| A-1 | Configurazione: URL di Supabase e chiave pubblica ("publishable") da `local.properties` / segreti della CI in `BuildConfig`, mai nel repository; permesso `INTERNET` | piccolo |
| A-2 | Accesso: libreria `supabase-kt` (Auth) con email/password e Google (Credential Manager; serve un client OAuth Android con lo SHA-1 della chiave di firma, registrato in Google Cloud e in Supabase) **[AUTORIZZAZIONE]** per la configurazione; sessione cifrata (DataStore + Keystore); esci | medio |
| A-3 | Gruppi: elenco dei gruppi dell'utente (lettura permessa dalle policy esistenti), scelta salvata | piccolo |
| A-4 | Invio: "Invia a Padel Elite" su partite di padel chiuse; WorkManager con lavoro unico per `matchId` e ritentativi; stato per partita nello storico (in coda, inviata, gia' presente); il file e' lo stesso dell'export v2, il contratto resta `docs/SCOREBOARD_FORMAT.md` della dashboard | medio |
| A-5 | Test: unitari su coda e stati, di contratto sul fixture `app-v2-tre-set.json`, integrazione contro l'ambiente di prova (mai produzione) | medio |
| A-6 | Privacy e Play: `PRIVACY_POLICY.md`, scheda "Sicurezza dei dati" (account e dati partita inviati a Padel Elite solo su richiesta), `RELEASE_CHECKLIST.md` | piccolo |
| A-7 | Prova sul campo completa: partita registrata col Galaxy Watch, inviata dall'S26, importata da un admin, Cronaca visibile nella dashboard | proprietario |

Ordine: E-0 quando vuole il proprietario; poi E-1, E-2/E-3 e A-1..A-5 in parallelo (A-5 ha bisogno
di E-2 sull'ambiente di prova); E-4 e il rilascio dell'app per ultimi. Claude non inserisce
credenziali e non fa accessi: le prove con un account vero le fa il proprietario.

### Decisioni del proprietario

- **E-a. Chi puo' inviare**: ogni membro del gruppo (proposta: casella d'arrivo, un admin conferma)
  o solo owner/admin (allora l'app potrebbe importare direttamente, ma dovrebbe conoscere i
  giocatori del gruppo, cosa che finora si e' scelto di evitare)?
- **E-b. Accesso**: email/password, Google, o tutti e due?
- **E-c. Proposta dei giocatori**: solo chi invia (`linked_user_id`), o anche gli altri per nome?
- **E-d. Ambiente di prova**: progetto Supabase separato o Supabase in locale?
- **E-e. Tennis e calcio**: fuori (la dashboard e' solo padel), confermato?

### Rischi

- Il database di produzione si tocca due volte (63 c'e' gia', 64 nuova): migrazioni additive,
  prima sull'ambiente di prova, poi dal proprietario.
- La chiave pubblica di Supabase nell'APK e' prevista (e' "publishable"), ma tutta la sicurezza sta
  nelle RLS e nelle RPC: vanno provate da utente membro, admin e anonimo.
- Due versioni dell'app in giro: la casella d'arrivo accetta formatVersion 1 e 2 come l'import.

---

## Riepilogo delle autorizzazioni

| Cosa | Perche' |
|---|---|
| Lavorare nel repository `padel-dashboard` (branch e PR verso `development`) | E-2, E-3 |
| Creare un progetto Supabase di prova | E-1 |
| Migrazione 64 in produzione | E-4 |
| Client OAuth Google per Android | A-2, se si sceglie Google |

Il filone 1 (prova sul campo) e il filone 2 (coerenza grafica) non ne chiedono nessuna.
