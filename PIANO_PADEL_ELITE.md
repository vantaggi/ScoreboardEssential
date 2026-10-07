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

---

## Avanzamento

**5 ottobre 2026, sera.**

| Passo | Stato |
|---|---|
| G-0 token | fatto (`wf31/elite-token`, unito `741b541`): token `elite_*` fonte unica, tema M3 sui token, `elite_outline` #6E6E7E per i contorni di campi e pulsanti (il bordo forte della dashboard fa 1,36:1), etichette di squadra piene con TeamInk; pista "Coerenza con Padel Elite" in DESIGN.md |
| G-1 caratteri | fatto (`wf32/elite-caratteri`, unito `2c54773`): Inter e JetBrains Mono in `shared/res/font` (517 KB, OFL), contorno in Inter, punteggi in mono 800; cifre del gioco in mono a 144,6dp (in mono "88" e "AV" a 150 non entrano); cifre del quadrante restano condensate (in mono a 58sp sul 192dp "AV" fa 69,6dp contro 68) |
| UI Constitution (6 ottobre) | adottata: sezione "Adattamento alla UI Constitution" di `DESIGN.md` (identita', ruoli, conflitti), G-1b (numeri in Inter con `tnum`, JetBrains Mono esce) e G-2..G-9 rifatti, piu' G-10 alto contrasto; `CLAUDE.md` alla radice |
| G-2 componenti | fatto (`wf34/g2-componenti`, unito `28e5f3e`; commit `b8c5dac`, `673256e`, `d2395b1`, `9bef0b5`, `8150178`, `9b6f00c`): ruoli mancanti, gruppi tonali, bottoni a raggio 8, campi, chip e badge, pressione condivisa (scala 0,97, opacita' 0,85), `TokenEliteTest` esteso |
| G-3 icone | fatto (`wf36/g3-icone`, unito `16bf41b`; commit `9441d70`, `b5a0b7e`, `b61350e`, `d80a9de`, `990ce4f`): Material Symbols outlined al posto degli `ic_*` a mano, tabella concetto-icona, via le emoji, niente tinta di elevazione nei dialoghi |
| Invio sui componenti | fatto (`wf37/integrazione-invio`, unito `a0b99ec`; commit `dbae3a6`): le schermate dell'invio usano i componenti di G-2 e le icone di G-3 |
| G-4 contorno | fatto per il contorno il 7 ottobre 2026 (`wf38/g4-contorno`; commit `76c8526`, `86bf57d`, `6c71ae9`, `4981152` e il seguito in `git log`): impostazioni, onboarding, storico, statistiche, giocatori, aggiungi o modifica e dialoghi su gruppi tonali con righe, `EmptyStateView`, `ProgressButton`, schede con indicatore lime, niente maiuscolo, rosa o colori Street; il foglio PARTITA mentre si gioca e' G-6. Non visto sugli emulatori |
| G-6 gioco e foglio | fatto il 7 ottobre 2026 (`wf40/g6-gioco`, commit in `git log`): predefiniti delle squadre lime e ciano (chi non ha mai scelto un colore passa ai nuovi, chi l'ha scelto lo tiene), pallini del servizio lime, NumberRoll al posto di `animateScoreNumber` (su e giu', `duration_standard`, movimento ridotto = cambio immediato, scatola fissa, regione live), pressione condivisa sulle zone al posto di `animateZoneTap`, cifre di gioco in Inter 600 tabulare con `letterSpacing` -0,02 em («AV» 128,5dp a 411dp), niente maiuscolo su nomi e comandi, foglio PARTITA a gruppi con righe rientrate e barretta di squadra al posto dei blocchi pieni, un solo primario (Fine partita), via gli alias Street e i colori Street del gioco e del foglio; resta in maiuscolo il testo composto di striscia, barra e registro dei game; nessuno screenshot |
| G-7 orologio | fatto il 7 ottobre 2026 (`wf42/g7-orologio`, commit in `git log`): colori e tema del quadrante sui token del telefono (via i nove colori Street, le forme `cut` e `StreetCard`), menu e scelta dello sport come gruppi tonali con righe a raggio 8 e 14, senza ombra e senza maiuscolo, pressione condivisa col movimento ridotto, pallino del servizio lime (in ambient solo contorno bianco), predefiniti lime e ciano, NumberRoll sulle cifre condensate (corsa piu' breve, mai in ambient, al risveglio o col movimento ridotto); resta in maiuscolo la riga di stato E e le frasi in gioco; lime solo per chi serve (conto del portiere e CHI? in testo primario), da confermare; nessuno screenshot |
| G-10 alto contrasto | fatto il 7 ottobre 2026 sul telefono (`wf44/g10-alto-contrasto`, commit in `git log`): tema aggiunto, scuro, che cambia solo i colori (testo a 7:1, bordi e icone a 3:1, fuoco bianco) con un `ThemeOverlay` applicato a ogni activity prima di `onCreate`; i token `elite_*` che cambiano sono ora file in `res/color` che seguono il tema, quindi layout e codice non cambiano; interruttore nel nuovo gruppo Accessibilita' delle impostazioni e, se l'utente non ha scelto, contrasto di sistema da Android 14 (`getContrast() >= 0,5`, ricreazione al cambio). `AltoContrastoTest` (24) con falsificazioni. **Orologio non toccato** (nessuna impostazione sull'orologio; da decidere). Non visto sugli emulatori, ne' al 200% |
| G-5, G-8..G-10 | da fare: Cronaca (G-5), PDF (G-8), verifica finale (G-9), alto contrasto (G-10); **G-0 e G-1 sono nel branch di lavoro, non ancora su main** |
| G-5 Cronaca | fatto il 7 ottobre 2026 (`wf41/g5-cronaca`; commit `5f48979` e il seguito in `git log`): sei gruppi tonali con righe e linee rientrate, testi su `elite_text_*`, Inter `tnum` su ogni numero, niente maiuscolo, nome di squadra con barretta, riquadri dei game con la barretta a sinistra (lato 1) o a destra (lato 2); grafico dell'andamento con una domanda sola, lato 1 lime sopra lo zero e lato 2 ciano sotto, etichette dirette col vantaggio massimo, tratteggio a fine set, via i riempimenti; stati vuoti con titolo e motivo; fuori `concrete_gray`, `graffiti_dark_gray`, `outline_gray` e `bg_asphalt_main` (restano `asphalt_*`, `stencil_white`, `sidewalk_gray` per il PDF). Non visto sugli emulatori; nuovi `CronacaG5Test` e `MomentumViewTest` (bitmap vera) |
| G-8 PDF | fatto il 7 ottobre 2026 (`wf43/g8-pdf`, commit in `git log`): il report PDF sta su **carta chiara** (token `print_*`, stessi ruoli in valori chiari; conflitto 14: il fondo scuro non regge la stampa), gruppi tonali con righe sottili, Inter 400 e 600 con `tnum`, niente maiuscolo, colore di squadra solo come barretta accanto al nome (lime e ciano coi predefiniti, scurite a 3:1 da `TeamInk.graphicOnLight`), testo sempre `print_text_primary` (4,5:1 con qualunque colore); via `asphalt_black`, `stencil_white` e `sidewalk_gray` (resta `asphalt_dark` per l'icona, G-9); la pagina si disegna a densita' 160 (prima 16sp diventavano 42 punti su un A4); nessun PDF aperto a mano |
| G-7..G-10 | da fare: orologio (G-7), PDF (G-8), verifica finale (G-9), alto contrasto (G-10) |
| E-2/E-3 casella d'arrivo | PR https://github.com/vantaggi/padel-dashboard/pull/213 verso `development`: migrazione 64, RPC, UI, test (CI contract-tests 54/54, Playwright 295/295). Revisione Opus: unire si', applicare in produzione no finche' non si chiudono 3 medie (lunghezze del payload, spazio occupato, doppioni senza matchId): correzione in corso |
| Falla in produzione (fuori piano) | la revisione ha trovato in `03_fix_rls_recursion.sql` la policy "Insert group_members" con solo `user_id = auth.uid()`: se attiva, chiunque abbia un account puo' inserirsi owner in qualunque gruppo. Da verificare dal proprietario con `select policyname, cmd, with_check from pg_policies where tablename = 'group_members';`. Migrazione 65 di correzione in preparazione su un branch separato |
| Docker | Docker Desktop non parte su questo PC (socket vecchi in `%LOCALAPPDATA%\Docker\run\`: `dockerInference`, `userAnalyticsOtlpHttp.sock`); serve per provare l'app contro un Supabase locale (A-5). Il proprietario decide se cancellarli |
| A-1..A-4 app | fatti il 7 ottobre 2026 (`wf35/invio-padel-elite`): configurazione da `local.properties`, accesso email e password con sessione cifrata col Keystore, gruppi, comando "Invia a Padel Elite" (card dello storico e dialogo di fine partita) con coda WorkManager e stati per partita; client REST su `HttpURLConnection`, nessuna libreria Supabase; provati solo con un server finto (nessuna chiamata al Supabase vero). Dettagli e debiti in `DESIGN.md`, sezione "Invio a Padel Elite" |
| A-5 integrazione | da fare: serve Supabase locale (Docker) e la migrazione 64 corretta; i test JVM col server finto ci sono gia' |
| A-6 privacy e Play | da fare: `PRIVACY_POLICY.md`, scheda "Sicurezza dei dati", `RELEASE_CHECKLIST.md` (il README e la privacy dicono ancora che non si carica niente) |
| A-7 prova sul campo | da fare, del proprietario: in `local.properties` `PADEL_ELITE_SUPABASE_URL` e `PADEL_ELITE_SUPABASE_KEY` (chiave publishable), mai in produzione prima di E-4 |

**7 ottobre 2026 - pubblicato su main** il blocco coerenza grafica G-0..G-4 e G-6 (G-5 Cronaca, G-7 orologio, G-8 PDF, G-9 verifica e maiuscolo dei messaggi composti, G-10 alto contrasto restano da fare), l'adattamento alla UI Constitution (`CLAUDE.md`), l'invio a Padel Elite dall'app (A-1..A-4, spento finche' `local.properties` non ha URL e chiave) e la correzione della durata nello storico (tempo di gioco del registro, non tempo fra inizio e chiusura). Suite verde, **1199 test JVM distinti**, strumentati 38 su 38 su `Pixel_9a_Test`. Schermate in `docs/coerenza/`. Dashboard: PR #213 (casella d'arrivo, migrazione 64) e #214 (falla di `group_members`, migrazione 65) unite in `development`; in produzione nessuna migrazione applicata (prima la 65, poi la 64, dal proprietario).
