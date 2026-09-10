# Piano di migrazione — ammodernamento, multi-sport, integrazione Padel Elite

Registro vivo del lavoro. Ogni passo è un commit, con il comando che lo prova e
il suo rollback. Aggiornare la colonna **Stato** man mano.

Branch: `claude/project-review-multi-sport-6e998b`
Documento di decisione completo (audit di 91 finding, modello di dominio,
protocollo Wear, UI): <https://claude.ai/code/artifact/ef50b80a-0764-4591-a79e-b2713ca361da>

---

## Come si costruisce e si verifica

```bash
./gradlew clean test ktlintCheck assembleDebug
```

Questo comando è `VERIFY` in tutto il documento.

**Non serve impostare `JAVA_HOME`.** Dal commit "fissa il JDK del daemon a 17" il
daemon prende Java 17 da `gradle/gradle-daemon-jvm.properties`, anche se il
launcher è la JBR di Android Studio (oggi JDK 25, che Gradle 8.x non può
eseguire). Se la toolchain non è presente, `foojay-resolver-convention` la
scarica.

---

## Fase T — toolchain ✅ COMPLETATA

Requisito Play: dal 31 agosto 2026 gli aggiornamenti richiedono `targetSdk 36`.
**Risolto**: `:mobile` è a 36, il bundle release si costruisce. Le app Wear OS
sono esentate, `:wear` resta a `targetSdk 34`.

Restano due verifiche che richiedono un dispositivo, elencate in fondo.

| # | Passo | Stato |
|---|---|---|
| T1 | Rimuovere il pin AGP nascosto in `settings.gradle` | ✅ fatto |
| T2 | Fissare il JDK del daemon a 17 + foojay resolver | ✅ fatto |
| T3 | Version catalog `gradle/libs.versions.toml`, versioni invariate | ✅ fatto |
| T4 | Una coordinata una versione + igiene dipendenze | ✅ fatto |
| T5 | Wrapper Gradle 8.9 → 8.13 | ✅ fatto |
| T6+T7 | AGP 8.13.0 + Kotlin 2.2.21 + KSP 2.2.21-2.0.5 + Room 2.8.4 — **inseparabili**, vedi sotto | ✅ fatto |
| T8 | ktlint 13.1.0 + engine 1.8.0, format in commit separato | ✅ fatto |
| T9 | compileSdk 34 → 36 su tutti e tre i moduli | ✅ fatto |
| T10 | Robolectric 4.16.1, `robolectric.properties`, Mockito 5, **`includeAndroidResources` in `wear/build.gradle`** | ✅ fatto |
| T11 | `exportSchema = true`, `11.json` committato, `fallbackToDestructiveMigrationFrom(1..5)` | ✅ fatto (con due limiti, sotto) |
| T12 | Edge-to-edge reale (`enableEdgeToEdge` + insets) | ✅ codice fatto · ▲ **verifica manuale ancora da fare** |
| T13 | Igiene manifest (FGS, predictive back, `ForegroundServiceStartNotAllowedException`) | ✅ fatto |
| T14 | **`targetSdk 34 → 36` su `:mobile`** — Play sbloccato | ✅ fatto · ▲ **pass manuale su device ancora da fare** |
| T15 | Sweep deprecati (`Stack`→`ArrayDeque`, guardie SDK morte) | ✅ fatto |
| T16 | `@Parcelize` fuori dalle entità Room → **AGP 9 sbloccato** | ✅ fatto |
| T17 | CI: `lint` + baseline, `assembleRelease`, gate schemi, concurrency | ✅ fatto · golden test protocollo → resta a S3 |

**Vincoli d'ordine (violarli produce fallimenti che sembrano casuali):**
T5→T6 · T6→T9 · T7→T11 · **T10→T14** · T12,T13→T14

### Trappole già incontrate, da non riscoprire

- **AGP era dichiarato in due posti** e vinceva `settings.gradle`. Risolto in T1.
- **Material 1.13 non dichiara più `colorPrimary` nel proprio `R.attr`**
  (`colorSecondary` sì). Va preso da `androidx.appcompat.R`. Già corretto in
  `AnimationUtils.kt`.
- **`legacy-support-core-utils` non arriva più** sotto Material 1.13:
  `localbroadcastmanager` è ora dichiarato esplicitamente su `:mobile`, altrimenti
  il modulo avrebbe smesso di compilare.
- **T10 prima di T14**: risolto alla radice — l'SDK emulato non eredita più dal
  `targetSdk`, è fissato in `src/test/resources/robolectric.properties`.
- **T6, T7 e il bump di Room sono un unico commit.** AGP 8.13 porta
  `databinding-ktx` compilato con Kotlin 2.2 → impone Kotlin ≥ 2.2; Kotlin 2.2
  porta KSP2, che con Room 2.6.1 fallisce con `unexpected jvm signature V` →
  impone Room ≥ 2.7. Il vincolo è bidirezionale, il piano lo conosceva solo in
  una direzione.
- **`mockito-android` non va su `testImplementation`**: registra un MockMaker che
  pretende un runtime Android. Era mascherato da `mockito-inline`.
- **Robolectric e SDK 36**: emulare SDK 36 richiede **Java 21**. Finché la
  toolchain è 17, `robolectric.properties` resta a `sdk=34` — è indipendente dal
  `targetSdk` dell'app.
- **Gli schemi Room vanno agli asset della variante `debug`**, non a
  `test.assets`: sotto Robolectric gli asset visibili sono quelli dell'app.
- **`MigrationTestHelper` di Room 2.8 non gira sotto Robolectric** (apre per
  percorso assoluto, driver configurato col nome nudo). È per test strumentati.
  Lo schema *è* esportato e presente in `mergeDebugAssets`: manca il runner, non
  il dato. Il test resta `@Ignore` con la ragione vera scritta dentro.
- **Gli apostrofi nelle stringhe Android vanno sfuggiti**, e le sequenze di escape
  unicode non bastano: aapt rifiuta la risorsa con «Can not extract resource», un
  errore che non nomina ne' il file ne' la riga. La via piu' economica e' **riformulare
  senza apostrofo**. Ci sono inciampato due volte.
- **`--` non e' ammesso dentro un commento XML.** Un trattino doppio usato come
  incidentale in italiano fa fallire `mergeDebugResources` con «La stringa "--" non
  e' consentita nei commenti» — e il messaggio arriva solo a merge delle risorse,
  non alla scrittura. Stessa famiglia dell'apostrofo: si riformula.
- **Esiste solo `11.json`**: l'export è stato acceso a schema già alla v11, le
  migrazioni storiche non sono validabili a posteriori. Da v12 in poi sì.

### AGP 9 — decisione presa: rimandato, non bloccato

Android Studio segnala periodicamente che AGP 8.13 ha un aggiornamento. **Non
accettarlo, e non usare l'Upgrade Assistant**: riscrive i file di build senza
sapere del version catalog né di `gradle-daemon-jvm.properties`, e trascina anche
Gradle a 9.x.

Cosa è cambiato: i due blocchi più duri **sono caduti**. Non esiste più alcun
`@Parcelize` nel repo (rimosso in T16, quindi niente trigger per google/ksp#3053)
e kapt non è mai stato usato. AGP 9 è ora una **scelta**, non un impedimento.

Cosa costerebbe, se si decidesse di farlo: è una catena, non un bump. Gradle 9.x
+ Kotlin 2.3.x + KSP 2.3.x, e la rimozione del Kotlin Gradle Plugin da tutti e
tre i moduli in favore del built-in Kotlin. Stessa forma di vincoli incrociati
che ha reso T6 e T7 inseparabili.

Le due incognite da verificare prima di impegnarsi:
1. il plugin **ktlint 13.x** sotto built-in Kotlin;
2. **DataBinding**, che `:mobile` usa pesantemente — è già il punto che ha fatto
   esplodere il passaggio ad AGP 8.13 (`databinding-ktx` compilato con Kotlin 2.2).

Nessun requisito di Play tocca AGP, e Studio supporta le versioni degli ultimi
3 anni: 8.13 non ha scadenze. Riaprire la questione dopo la Fase S.

---

## Fase B — bug che il refactor cementerebbe ✅ COMPLETATA (B6 → S3)

Da fare **prima** del dominio multi-sport. Non sono i più gravi in assoluto: sono
quelli che diventano irreparabili se il codice nuovo ci si costruisce sopra.

| # | Bug | File | Stato |
|---|---|---|---|
| B1 | `ACTION_REQUEST_SYNC` registrato nel filtro, nessun ramo nel `when` | `MainViewModel.kt` | ✅ fatto |
| B2 | Lista eventi read-modify-`postValue`, si sovrascrive | `MainViewModel.kt` | ✅ fatto |
| B3 | `startNewMatch()` gira prima di `bindService()` | `MainViewModel.kt` | ✅ fatto |
| B4 | Eco del cronometro: `startTimer()`/`pauseTimer()` senza `fromRemote` | `MainViewModel.kt` | ✅ fatto |
| B5 | Gol come `@Update` di riga intera → ora `UPDATE ... goals = goals + 1` | `PlayerDao.kt` | ✅ fatto |
| B6 | Telefono e orologio dichiarano la stessa capability `scoreboard_app` | `*/res/values/wear.xml` | ✅ fatto in S3, additivo |
| B7 | Marcatore per **nome** → id come quarto campo additivo | `PlayerSelectionActivity.kt` | ✅ fatto |
| B8 | `"Goal"` testo + chiave → `MatchEventType` tipizzato | `MatchEvent.kt` | ✅ fatto |

**B6 è stato spostato dentro S3, deliberatamente.** Separare le capability è un
cambiamento del contratto fra due binari versionati in modo indipendente: se il
telefono smette di cercare `scoreboard_app` mentre un orologio non aggiornato
dichiara solo quello, la coppia smette di vedersi. Va fatto in modo additivo e
insieme al lato che interroga, cioè con il protocollo v2 — non a metà adesso.
Nota anche che `FILTER_REACHABLE` esclude il nodo stesso, quindi il difetto oggi
è di ambiguità potenziale, non un guasto osservabile.

Ortogonali, **non** prerequisiti: `teams` mai scritta, FK mancanti su
`MatchPlayerCrossRef`, wake lock non rilasciato, stringhe hardcoded nei layout.
Obbligatorio **prima del basket** (non del padel): `MatchTimerService` è solo
*bound*, quindi muore con l'Activity.

---

## Fase S — multi-sport ✅ COMPLETATA

Modulo `:core`, `java-library` Kotlin puro, zero Android: è l'unico posto del repo
dove un test è garantito che venga eseguito, e non contiene `@Entity`/`@Parcelize`
quindi non allarga il blocco AGP 9.

| # | Passo | Stato |
|---|---|---|
| S1 | `:core` + contratti + FootballRules + RacketRules + MatchEngine + codec + registro, e il punteggio delegato al motore | ✅ **fatto, zero test esistenti toccati** |
| S2 | `MIGRATION_11_12` + partita viva ripristinabile | ✅ fatto ▲ rollback = perdita dati |
| S3 | Protocollo Wear v2 + B6 | ✅ fatto ▲ **matrice manuale 2×2 da fare** |
| S4 | Selettore sport + gating capability | ✅ fatto ▲ verifica visiva da fare |
| S5 | **Padel punto a punto** — punteggio impaginato dalle regole | ✅ fatto ▲ verifica visiva da fare |
| S6 | **Tennis** — una riga di config | ✅ fatto (nel registro, con test) |
| S7 | Pallavolo / basket, solo su richiesta | ⬜ |

**Segnale d'allarme su S1:** è costruito per non rinominare nulla. Se costringe a
riscrivere anche un solo test, l'ordine è sbagliato — fermarsi e iniettare i DAO
attraverso la factory prima.

### Verifica differenziale contro Padel Elite

`RacketRules` è un **port** di `js/livematch.js`, non un progetto nuovo. Per
provare che sia fedele e non solo coerente con sé stesso, la sezione pura di
`window.LiveScoring` viene eseguita sotto Node su 145 sequenze deterministiche e
il suo stato finale è congelato in `core/src/test/resources/livescoring-reference.txt`.
`RacketRulesDifferentialTest` replica le stesse sequenze e confronta nove campi,
servizio compreso.

Rigenerare il fixture: `node scratchpad/genfixture.js <percorso>` (lo script sta
nello scratchpad di sessione; se serve stabilmente, va spostato nel repo).

Le 5 sequenze che toccano il 6-6 sono escluse alla generazione: è l'unico punto
in cui il port diverge apposta, perché il web non modella il tie-break.

### Il modello di dominio, in breve

`ScoreState` è un sealed a **due** casi: `CounterScore` (calcio) e `RacketScore`
(padel, tennis). Il terzo caso arriverà con la pallavolo, che è lo sport che lo
definisce davvero. `headline()` ritorna la coppia che finisce in
`matches.team1Score/team2Score` — per gli sport racchetta sono i **set vinti**, ed
è il motivo per cui `MatchDao.getPlayerWinCounts()` resta corretto senza modifiche.

Persistenza: `matches` guadagna due colonne, `sportId TEXT NOT NULL DEFAULT
'football'` e `eventLog TEXT NOT NULL DEFAULT ''`. Il backfill **è** la clausola
`DEFAULT`: zero righe riscritte.

Undo = **refold**, non inverso: si toglie l'ultimo evento e si rifà il fold. È
l'unico undo corretto attraverso un confine di game o di set.

---

## Fase I — integrazione con Padel Elite (I1–I3 ✅)

Progetto: `C:/Users/cioti/Desktop/PROGETTI/padel` (Vite + Tailwind + Supabase +
Capacitor, `it.padelite.dashboard`). **In uso reale ogni settimana: nessuna
scrittura sul Supabase di produzione senza autorizzazione esplicita.**

### Cosa c'è già di là — non va riscritto

- `js/livematch.js` (634 righe): motore di scoring padel completo e
  unit-testabile. Modalità `games` e `points`, golden point, killer point,
  vantaggi, best-of-N, `setHistory`, `finalScore`.
- `v2_live_matches`: partita dal vivo, realtime, `config` e `state` JSONB.
  **Stato dichiaratamente effimero, non è il sistema di riferimento.**
- `create_match()`: unico percorso di scrittura, `SECURITY DEFINER`, valida
  autorizzazione e integrità. Firma attuale a 12 parametri, include
  `p_set_scores jsonb DEFAULT NULL`.
- `v2_matches` ha già `set_scores`, `court_id`, `match_time`.

### Cosa manca — ed è il valore dell'integrazione

Timeline punto per punto con timestamp, durate reali, e **chi serviva**.

### Decisioni prese

- Modalità **punto a punto**.
- Ordine di servizio impostabile: rotazione a quattro `A1 → B1 → A2 → B2`.
- **A inizio set la rotazione continua**, non si risceglie. Quindi `serveIndex` è
  un unico contatore monotono per tutta la partita, **mai azzerato**: nessun caso
  speciale al confine di set.
- **Il ricevitore non si registra** (richiederebbe di sapere chi sta a destra e
  chi a sinistra in ogni coppia).

### Modello del servizio

```
serveOrder: [idA1, idB1, idA2, idB2]   // config, scelto a inizio partita
serveIndex: 0                          // state, +1 a ogni game concluso
server     = serveIndex % 2            // derivato
```

`serveIndex % 2` riproduce **esattamente** l'attuale `state.server = 1 - server`
del web, quindi il campo nuovo è un raffinamento stretto e la UI web non va
toccata.

### ⚠ Il tiebreak non è modellato nel web

`gameDecided()` non ha alcun ramo per il tiebreak; `setDecided()` si limita ad
accettare il 7-6. Sul 6-6 il tredicesimo game viene contato come un game normale.
Irrilevante in modalità `games`, **sbagliato in `points`**. Il port Kotlin deve
quindi **estendere** l'esistente su tiebreak e servizio, e le due estensioni vanno
valere per entrambi i lati.

| # | Passo | Stato |
|---|---|---|
| I1 | Mappatura `Player.playerId` ↔ `v2_players.id` | ✅ fatto (schema v13) |
| I2 | Ordine di servizio — **derivato dai roster**, non chiesto | ✅ fatto |
| I3 | Export su file, zero rete, con validazione tipizzata | ✅ fatto |
| I4 | Import nella dashboard + destinazione per la timeline | ⬜ ▲ **richiede autorizzazione**: tocca il progetto padel |
| I5 | Upload diretto via `create_match()` | ⬜ ▲ **richiede autorizzazione**: Supabase di produzione |

Per estendere `create_match()`: parametro nuovo **in coda** con `DEFAULT NULL` e
`DROP FUNCTION` della firma esatta precedente — `CREATE OR REPLACE` non può
aggiungere parametri e creerebbe un secondo overload. Precedente già nel repo
padel: `57_match_set_scores.sql`.

---

## Verifiche che richiedono un dispositivo o credenziali

Non eseguibili in automatico. Da fare a mano al passo indicato.

- **T12** — edge-to-edge su emulatore/telefono API 36 con gesture nav, chiaro e
  scuro, portrait e landscape.
- **T13** — dichiarazione Play Console per il foreground service `specialUse`
  (modulo con revisione umana: mettere in conto un giro di andata e ritorno).
- **T14** — pass manuale completo su telefono API 36 con orologio accoppiato.
- **S2** — prova reale APK pubblicato → partita → APK del branch → dump di
  `matches`. Rollout scaglionato: **il rollback del binario cancella la
  cronologia**.
- **S3** — matrice 2×2 {orologio vecchio, nuovo} × {telefono vecchio, nuovo}, con
  la condizione «orologio vecchio + telefono nuovo + calcio = identico a oggi».
- **I5** — sessione Supabase e scritture sul progetto di produzione.

---

## Stato della rete di test (misurato, non stimato)

Il verde della CI copre 11 test che non vengono eseguiti:

| Suite | Test | Eseguiti | Perché |
|---|---|---|---|
| `:wear` `WearViewModelTest` | 8 | **8** ✅ | risolto in T10 |
| `DatabaseMigrationTest` | 1 | **0** | `@Ignore("Schema files are missing")` — si riaccende con T11 |
| `MainActivityLayoutTest` | 2 | **0** | `@Ignore("PackageParserException")` — ancora da verificare |

Stato attuale: **294 test, 288 eseguiti, 0 falliti.**

Inoltre il source set `androidTest` di `:mobile` **non compila**: quattro simboli
inesistenti in `WearCommunicationTest.kt` (`syncScores`, `sendMessageWithRetry`,
`MSG_TIMER_ACTION`, `PATH_TEST_PONG`). La CI non lo compila mai.

---

## Stato al 8 settembre 2026

Fasi **T**, **B** e quasi tutta **S** completate. `main` allineato al branch.
**347 test, 341 eseguiti, 0 falliti.** Il padel e il tennis sono giocabili sul
telefono; l'orologio e' ancora solo calcio.

In tutta la Fase S **nessun file sotto `src/test` e' stato toccato**: la promessa
"il calcio non cambia" e' verificata meccanicamente con `git status`, non a parole.

### Debiti dichiarati, da chiudere

- **`@string/sport_change_blocked` e' inutilizzata.** La schermata impostazioni non
  puo' sapere se una partita e' in corso senza duplicare la guardia che vive in
  `MainViewModel.selectSport()`. Oggi il cambio a partita viva viene scritto nelle
  preferenze e applicato alla partita **successiva**, in silenzio. Il sottotitolo
  del selettore lo dichiara, ma un avviso esplicito sarebbe meglio.
- **`MatchEngine` registra anche gli eventi senza effetto** (un tocco a partita
  finita). Serve a mantenere vera la proprieta' "applica poi annulla = stato di
  prima", ma significa che dopo un tocco inerte servono **due** annullamenti per
  togliere il punto precedente. Scelta consapevole, da ridiscutere se da' fastidio.
- **`MatchLogCodec` non scrive `sportId`**: lo sport e' gia' una colonna della stessa
  riga. Deviazione voluta rispetto alla proposta originale.

### Verifiche manuali accumulate (nessuna eseguibile senza dispositivo)

1. **Edge-to-edge** su emulatore API 36 con navigazione a gesti (T12).
2. **Pass completo** su telefono API 36 con orologio accoppiato (T14).
3. **Avvio dell'app sull'orologio** — e' il punto in cui si e' manifestato il crash
   del costruttore riflessivo, e nessun build verde lo intercetta.
4. **Padel a schermo**: che l'ingranaggio impostazioni sia raggiungibile con lo
   sport senza cronometro, e che il collasso dei vincoli fra `score_section`,
   `match_log_card` e le card nascoste non lasci spazi vuoti (S4/S5).
5. **Dichiarazione Play Console** per il foreground service `specialUse` (T13).

### S3 — fatto. Cosa è emerso in integrazione

Tre difetti trovati mettendo insieme i due lati, **nessuno colto dai build verdi**:

1. Il campo `side` portava tre significati (1/2 punto, −1/−2 correzione, 0
   annullamento) e il telefono scartava i negativi con `isValidTeamNumber`: sul
   **calcio**, con entrambi i lati aggiornati, il tocco di sottrazione sarebbe
   diventato inerte. Risolto separando il tipo di intenzione in un campo suo.
2. La sequenza ripartiva da 1 a ogni riavvio dell'app orologio mentre il telefono
   ricorda l'ultima vista per nodo: i primi tocchi venivano scartati come «già
   visti». Ora è seminata dall'orologio di sistema.
3. Doppio conteggio: l'orologio manda l'intenzione **e poi** l'attribuzione, e
   ciascuna registrava un gol — un punto, due voci nel registro, due annullamenti.

Il primo e il terzo li ha segnalati l'agente del lato orologio pur non potendoli
correggere: erano in file non suoi.

### Scopo originale di S3, per riferimento

E' l'ultimo pezzo della Fase S e assorbe anche **B6**. Scopo preciso:

- si **aggiunge** un path (`/scoreboard/v2/state`), non se ne cambia mai uno. La
  leva di compatibilita' e' verificata: ne' `SimplifiedDataLayerListenerService` ne'
  `WearDataLayerService` hanno un ramo `else` nel loro `when` sul path, quindi un
  path sconosciuto e' un no-op silenzioso su un orologio non aggiornato;
- il telefono diventa **autoritativo** e manda `ScoreDisplay` gia' impaginato --
  esiste gia', lo espone `MainViewModel.scoreDisplay`. Cosi' il padel non richiede
  una riga di regole sull'orologio;
- i tocchi dell'orologio diventano **intenzioni** con numero di sequenza su
  `MessageClient` (non coalescente), e il telefono ignora cio' che ha gia' visto;
- capability separate `scoreboard_phone` / `scoreboard_watch`, **additive**,
  mantenendo `scoreboard_app` per una release (questo e' B6);
- golden test in `:shared` che asserisce il valore letterale di ogni path e chiave.

Ordine consigliato: prima il golden test, poi le aggiunte. E la matrice manuale
2x2 {orologio vecchio, nuovo} x {telefono vecchio, nuovo} con la condizione
**"orologio vecchio + telefono nuovo + calcio = identico a oggi"**.

---

## Audit dei comandi — 10 settembre 2026

Audit statico di layout, gesti e visibilita' su **orologio e telefono**: 40 rilievi,
**12 gravi**. Statico perche' l'app non e' mai stata osservata in funzione: si e'
letto dove stanno i controlli, cosa dicono e quando compaiono, non come si vedono.

**Tutti e 12 i gravi sono chiusi.** Tre erano regressioni mie ed erano i piu'
dannosi (commit `838449c`); gli altri nove sono di questo commit.

| # | Dove | Rilievo | Rimedio |
|---|---|---|---|
| 1 | orologio | Al risveglio dello schermo il punteggio del padel tornava a 0 | i collector v1 scrivono solo finche' il v2 non e' mai arrivato |
| 2 | orologio | Il polso vibrava «punto preso» anche a messaggio non partito | `sendMessage` ritorna se ha raggiunto un nodo; due pattern distinti |
| 3 | orologio | Niente diceva che nel padel il meno **annulla** | riga `gestureHint` + `contentDescription` che cambiano con lo sport |
| 4 | orologio | La meta' bassa di ogni lato toglieva punti, senza dirlo | divisione invisibile rimossa: tocco = +1, tocco lungo = meno |
| 5 | orologio | La riga dei set rimpiccioliva il numero grande | vista propria per il secondario; il numero tiene la sua altezza |
| 6 | telefono | Nel padel i due `−` erano identici ma sembravano di due squadre | spariscono; resta l'annullamento dichiarato |
| 7 | telefono | La riga HISTORY / END MATCH galleggiava staccata dal contenuto | `layout_constraintVertical_bias="0"` |
| 8 | telefono | Il quarto pulsante schiacciava HISTORY e END MATCH | Esporta entra nella scelta di Condividi: tre comandi in ogni sport |
| 9 | telefono | Testo del punteggio cablato scuro su una card di colore libero | colore calcolato dalla **luminanza percepita** della card |
| 10 | telefono | L'ingranaggio impostazioni era 32dp | 48dp con `padding` 12dp: bersaglio a norma, ingombro visivo uguale |
| 11 | telefono | Il telefono non diceva ne' il set ne' chi serve, l'orologio si' | `match_period_textview` nello spazio del cronometro spento |
| 12 | telefono | In orizzontale il `+` finiva sotto la piega | le altezze del punteggio diventano `dimen`, con `values-land` |

Il rilievo 4 e' anche mezzo **D2**: sull'orologio esiste ora un solo modo di
segnare. Sul telefono gesto e pulsante convivono ancora, quindi D2 resta aperto.

Restano 28 rilievi **medi e bassi**, non affrontati: sono di rifinitura
(spaziature, contrasti minori, etichette) e nessuno impedisce di usare l'app.

*Verifica ancora dovuta:* nessuno di questi rimedi e' stato **visto**. In
particolare vanno guardati su dispositivo il `gestureHint` sull'orologio tondo
piccolo e la riga dei set a 14dp, che sono le due misure piu' strette.

---

## Fase D — direzione di interfaccia

**Decisione presa (8 settembre 2026): la via di mezzo.**

L'identità attuale è deliberatamente espressiva — `asphalt_black`, `graffiti_pink`,
`neon_cyan`, texture di asfalto e cemento, forme `StreetCard` — mentre la filosofia
di Jony Ive è riduttiva: l'interfaccia si toglie di mezzo perché conta il contenuto.
Non sono conciliabili al 100%, e non si prova.

Quindi:

- **Si tiene l'identità street** nelle superfici di contorno: icona, onboarding,
  cronologia, statistiche, report.
- **Si applica la riduzione di Ive alla sola schermata in uso durante la partita**,
  dove l'unica cosa che conta è il numero.

Non si ridipinge l'app di bianco e grigio per «sembrare Apple»: sarebbe cargo cult,
si perderebbe un'identità scelta senza guadagnare nessuno dei principi.

### I rilievi che motivano la decisione

Misurati sul layout, non a impressione.

1. **Troppe scelte simultanee.** 47 elementi con id, 15 controlli, 7 regioni che
   scorrono. L'atto primario — segnare un punto, in piedi a bordo campo, con una mano
   — è una delle sette, **e scorre**.
2. **Due orologi, uno senza nome.** `timer_textview` (`00:00`) e
   `keeper_timer_textview` (`05:00`) hanno lo **stesso** `textAppearance`, ma solo il
   primo ha un'etichetta. L'esistenza del tutorial di primo avvio è il sintomo:
   l'oggetto non si spiega da solo.
3. **Affordance duplicate che divergono.** Pulsante e gesto fanno la stessa cosa —
   ed **erano già divergenti** (il gesto chiamava `subtractScore` mentre il pulsante
   era passato a `decrementScore`); corretto l'8 settembre. La duplicazione non è
   gratis: deriva.
4. **Un controllo che mente.** Il `−` in padel diventa *annulla* con lo stesso glifo.
   Se cambia il significato deve cambiare il segno.

Nota tipografica: la scala è `86 → 20 → 18 → 14`, cioè un salto di 4× e poi tre
valori quasi indistinguibili, col 14sp che porta quattro ruoli. La gerarchia è
affermata dalla sola dimensione, senza gradini intermedi.

### Azioni, in ordine

| # | Azione | Costo | Stato |
|---|---|---|---|
| D1 | Etichettare il secondo cronometro | una stringa | ✅ fatto |
| D2 | Scegliere UNA strada per segnare: gesto **o** pulsante | piccolo | ✅ fatto |
| D3 | Far cambiare segno al `−` quando diventa annulla | piccolo | ✅ fatto |
| D4 | Portare l'atto primario fuori dallo scorrimento: punteggio e comandi sempre visibili | medio | ✅ fatto |
| D5 | Valutare la texture dietro i numeri — **solo dopo** i primi quattro | da guardare a occhio | ⬜ |

**I primi tre cambiano l'esperienza più del quarto**, e D1 da solo toglie la ragione
principale per cui esiste il tutorial.

Punti di attrito nel flusso, non solo nella grafica:
- il dialogo del marcatore interrompe nel momento di massima attenzione (hai appena
  visto il gol e stai guardando il campo): attribuire dopo, o dal log, rispetterebbe
  l'attenzione;
- `reset_scores_button` confonde «azzera» e «termina»: un pulsante, due modelli mentali;
- il cambio sport si applica in silenzio alla partita successiva — è il debito già
  registrato sopra, ed è anche una violazione del principio per cui il sistema non
  deve mai fare qualcosa di diverso da ciò che è sembrato fare.

*Questa analisi è stata fatta su layout, tema e flussi nel codice: l'app non è mai
stata osservata in funzione. D4 e D5 richiedono l'occhio.*

### D2 e D4 — come sono stati chiusi

**D2: restano i pulsanti, spariscono i gesti.** C'erano tre modi per dare un punto
(pulsante `+`, swipe verso l'alto, doppio tocco) e due per toglierlo. I gesti erano
invisibili — nessuna scritta, nessun segno — e il tocco *singolo*, cioe' il primo che
chiunque prova sul bersaglio piu' grande dello schermo, non faceva niente. In piu' un
fling verticale partito da una card veniva consumato dal `GestureDetector`: chi provava
a scorrere la pagina col dito su una squadra le regalava un punto. `ScoreGestureListener`
e i suoi 5 test sono spariti con la funzione.

Il tutorial di primo avvio faceva **tre** affermazioni e tutte e tre erano false o lo
sono diventate: «Tap to add points» (un tocco non aggiungeva nulla), «Long-press to
change team colors» (nessun long click e' mai stato registrato su quelle card: i colori
stanno nelle impostazioni), «Swipe to remove» (ora non c'e' piu'). Riscritto.

**D4: il tabellone esce dallo scorrimento, in verticale.** `activity_main.xml` era una
`NestedScrollView` sola con dentro tutto. Ora il contenuto e' diviso in due file
inclusi — `content_scoreboard_live.xml` (cronometro, punteggi, comandi) e
`content_scoreboard_details.xml` (rose, registro, formazioni, azioni) — e a cambiare
e' solo il contenitore:

- **verticale**: il tabellone e' fisso in cima, scorre solo il resto;
- **orizzontale** (`layout-land/`): scorre tutto, come prima. Fissarlo su 360dp di
  altezza utile non lascerebbe spazio a nient'altro e renderebbe irraggiungibili i
  comandi in fondo.

Il contenuto non e' duplicato: i due `activity_main.xml` includono gli stessi due file.

**Nota sulla baseline di lint.** Dividere il layout ha fatto salire gli avvisi da 11 a
39: erano gli **stessi** avvisi di prima (stringhe cablate, `contentDescription`
mancanti, `UselessParent`) che la baseline non riconosceva piu' perche' registrati
contro `activity_main.xml`. Le 27 voci sono state **rimappate sul file nuovo**, non
rigenerate: rigenerare la baseline avrebbe inghiottito anche gli 11 avvisi che oggi
sono visibili di proposito. Tre voci restano spaiate perche' gia' obsolete prima
(testi nel frattempo passati a `@string`).

*Restano da guardare a occhio:* l'equilibrio verticale del tabellone fisso in verticale
(quanta pagina resta al registro) e D5.
