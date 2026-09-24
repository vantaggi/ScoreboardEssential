# Piano di migrazione — ammodernamento, multi-sport, integrazione Padel Elite

Registro vivo del lavoro. Ogni passo è un commit, con il comando che lo prova e
il suo rollback. Aggiornare la colonna **Stato** man mano.

Branch: `claude/project-review-multi-sport-6e998b`
Documento di decisione completo (audit di 91 finding, modello di dominio,
protocollo Wear, UI): <https://claude.ai/code/artifact/ef50b80a-0764-4591-a79e-b2713ca361da>

---

## RIPRENDI DA QUI — stato all'11 settembre 2026

`main` e il branch sono su `origin`, allineati a `a344b15`. **60 commit** oltre il
vecchio `origin/main`.

```bash
./gradlew clean test ktlintCheck lintDebug assembleDebug assembleDebugAndroidTest
```

**399 test JVM, zero saltati, zero falliti.** Non esiste piu' un solo `@Ignore` nel
progetto. Piu' 6 prove strumentate che compilano e aspettano un dispositivo.

### Che cosa e' finito

| Fase | Stato |
|---|---|
| **T** (ammodernamento SDK/JDK/AGP) | completa |
| **B** (fondazioni) | completa |
| **S** (multi-sport: dominio, padel, tennis, protocollo Wear v2) | completa |
| **I** (Padel Elite) | I1-I3 fatti; **I4-I5 fermi, servono autorizzazioni** |
| **D** (interfaccia, via di mezzo di Ive) | D1-D4 fatti; **D5 aperto** |
| **Audit dei comandi** (40 rilievi) | **esaurito**: 12 gravi, 20 medi, 8 bassi |
| Debiti dichiarati | **tutti e 3 chiusi** |

Oltre al piano originale, in questa sessione sono nate tre cose che non c'erano:
il **cambio sport dall'orologio**, la **partita registrata dal solo polso** con coda
su disco e consegna al telefono, e il **punteggio calcolato offline al polso**.

### Le quattro cose aperte, in ordine di peso

**1. LE VERIFICHE SU DISPOSITIVO. Il primo giro e' stato fatto il 12 settembre** (vedi in
fondo): l'app si installa, si avvia e i 6 test strumentati passano su emulatore API 36.
Restano da guardare a occhio le superfici grafiche. Il testo qui sotto e' quello originale.

**LE VERIFICHE SU DISPOSITIVO.**
Niente di quanto scritto in questi giorni e' mai stato **visto** girare. Il codice
compila, i test passano, il lint tace: nessuna di queste tre cose guarda uno schermo.
In ordine di probabilita' di sorpresa:

- il **tabellone fisso** in verticale (D4): quanta pagina resta al registro sotto di esso;
- l'orologio: il **"K" da 48dp** toglie 48dp di larghezza ai due punteggi, e i due comandi
  in fondo (SPORT e AZZERA) sono stimati ~122dp contro i ~136dp del quadrato inscritto di
  un quadrante da 192dp -- entrano sulla carta, non e' stato verificato;
- il passaggio **online -> offline -> online**, unico punto in cui due sorgenti si
  alternano a schermo;
- le **impostazioni**, la cui radice e' passata da `ScrollView` a `LinearLayout` con la
  barra sopra, e la **cronologia**, dove la prima card ora si aggancia alla barra;
- l'**orizzontale**, che ha un `layout-land` nuovo di zecca;
- la matrice 2x2 {orologio vecchio, nuovo} x {telefono vecchio, nuovo}, con la condizione
  "orologio vecchio + telefono nuovo + calcio = identico a oggi".

La prima di queste e' ora un comando invece che una procedura:
`./gradlew :mobile:connectedDebugAndroidTest`.

**2. I4-I5: import e caricamento diretto su Padel Elite.** Fermi da giorni, e non per
mancanza di tempo: il Supabase di quel progetto e' **produzione**, usato ogni settimana
con dati veri. Servono due permessi distinti, ed e' bene tenerli distinti:
lettura/scrittura sullo schema, e l'uso di `create_match()`. Finche' non arrivano, l'export
su file (I3) resta la strada completa e senza rete.

**3. D5 -- la texture dietro i numeri.** Il piano stesso la classifica come "da guardare
a occhio". Senza vedere l'app non ho una base per deciderla: e' una scelta, non un difetto.

**4. ~~L'ultimo debito dichiarato.~~ Chiuso l'11 settembre 2026** -- vedi in fondo.

### ~~Attriti di flusso mai trasformati in attivita'~~ — entrambi chiusi l'11 settembre 2026

Vedi le due sezioni in fondo. Erano: il **dialogo del marcatore** che interrompeva nel
momento di massima attenzione, e **`reset_scores_button`** che confondeva "azzera" e
"termina".

### Cose da sapere prima di toccare qualcosa

- **AGP 9: rimandato, non bloccato.** Non accettare l'Upgrade Assistant di Android Studio:
  riscrive i file di build senza sapere del version catalog ne' di
  `gradle-daemon-jvm.properties`.
- **Le trappole gia' pagate** stanno nella sezione "Trappole gia' incontrate": apostrofi
  nelle stringhe Android, `--` nei commenti XML, i default di Kotlin che non sono default
  SQL, `mockito-android` sul classpath dei test unitari. Costano tutte un ciclo di build.
- **Il golden test in `:shared`** congela i valori letterali del protocollo. Se diventa
  rosso, la risposta quasi certamente **non** e' aggiornare il valore atteso: e' aggiungere
  un path nuovo e lasciare il vecchio in scrittura per una release.
- **`origin/main` ha una branch protection** che richiede le pull request. Il push del
  10 settembre l'ha aggirata con un bypass autorizzato dal proprietario del repo.

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
- ~~**`MigrationTestHelper` di Room 2.8 non gira sotto Robolectric**~~ **Chiuso l'11
  settembre 2026: la classe e' stata spostata in `androidTest`, dove gira.** Il testo
  originale della trappola:
- **`MigrationTestHelper` di Room 2.8 non gira sotto Robolectric** (apre per
  percorso assoluto, driver configurato col nome nudo). È per test strumentati.
  Lo schema *è* esportato e presente in `mergeDebugAssets`: manca il runner, non
  il dato. Il test resta `@Ignore` con la ragione vera scritta dentro.
- **`--` nei commenti XML: pagata TRE volte.** Un trattino doppio usato come incidentale
  in italiano fa fallire `mergeDebugResources`. Vale per ogni file di risorse, non solo per
  i valori: layout, drawable, vettori. Vedi anche la voce piu' avanti.
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

- ~~**`@string/sport_change_blocked` e' inutilizzata.**~~ **Chiuso interamente l'11
  settembre 2026:** prima sull'orologio, poi anche sul telefono. Il selettore delle
  impostazioni non scrive piu' la preferenza a partita cominciata: rifiuta e lo dice,
  e la voce mostrata torna quella vera. La condizione non e' duplicata -- e' la STESSA
  (`il registro eventi non e' vuoto`) letta dalla copia persistita, che e' l'unica a cui
  quella schermata puo' arrivare.
  Il testo originale del debito:
- **`@string/sport_change_blocked` era inutilizzata.** La schermata impostazioni non
  puo' sapere se una partita e' in corso senza duplicare la guardia che vive in
  `MainViewModel.selectSport()`. Oggi il cambio a partita viva viene scritto nelle
  preferenze e applicato alla partita **successiva**, in silenzio. Il sottotitolo
  del selettore lo dichiara, ma un avviso esplicito sarebbe meglio.
- ~~**`MatchEngine` registra anche gli eventi senza effetto.**~~ **Chiuso l'11
  settembre 2026: la decisione e' stata rovesciata.** Un evento che non cambia lo stato
  non entra piu' nella storia. Il testo originale del debito:
- **`MatchEngine` registrava anche gli eventi senza effetto** (un tocco a partita
  finita). Serviva a mantenere vera la proprieta' "applica poi annulla = stato di
  prima", ma significava che dopo un tocco inerte servivano **due** annullamenti per
  togliere il punto precedente.
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

I 28 rilievi **medi e bassi** sono chiusi: 16 sull'orologio e 12 sul telefono
(vedi sotto). L'audit e' esaurito.

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
| D5 | Valutare la texture dietro i numeri — **solo dopo** i primi quattro | da guardare a occhio | ✅ fatto |

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

### La passata sull'orologio - 11 settembre 2026

Sedici rilievi medi e bassi, tutti sul modulo `:wear`. Quattordici chiusi, due
**deliberatamente non fatti**.

| Dove | Rilievo | Rimedio |
|---|---|---|
| layout | RESET aveva un'area di tocco di ~21dp | `minHeight` 48dp; lo stile e' borderless, quindi cresce l'area, non il rettangolo |
| layout | Il "K" stava a cavallo della cucitura fra i due lati | il "K" **diventa** la cucitura: i lati si fermano su di lui, non su una guida che ci passava sotto |
| layout | Contrasto del "K" al limite (14sp rosa su nero) | 16sp in grassetto, da `@dimen` |
| layout | Le due guide invisibili si sovrapponevano ai comandi | `guideline_center` e `guideline_bottom` rimosse: i lati si vincolano a viste vere |
| layout | Lo stato del collegamento era un punto di 8dp distinto solo dal colore | 12dp, `contentDescription` che cambia, e la riga in basso che dice **NIENTE TELEFONO** |
| layout | Nessuna `contentDescription` in tutto il modulo | sui due lati, sul "K" e sull'indicatore |
| layout | Due figli `match_parent` nello stesso `LinearLayout` verticale | peso alla lista, `wrap_content` al testo di stato |
| misure | `values-sw320dp` non si applica a nessun orologio reale | soglia portata a `sw210dp` (i quadranti veri stanno fra 160 e 240dp) |
| misure | Due dei quattro `dimen` non erano usati da nessun layout | `wear_score_text_size` rimosso (il punteggio fa autoSize); `wear_keeper_timer_text_size` **collegato** al "K" |
| stringhe | Testi scritti nei layout e nel codice, nessun `values-it` | tutti in `strings.xml` con la traduzione italiana |
| stringhe | Il commento di `strings.xml` descriveva una sovrascrittura mai esistita | commento e cartella `values-round` vuota rimossi |
| codice | Il dialogo di reset era un `AlertDialog` con testo lungo in inglese nel codice | stringhe da risorsa, messaggio accorciato |
| codice | Nel padel l'anello del portiere poteva ricomparire | i rami `Running` e `Finished` rispettano `auxAvailable`, come gia' faceva il "K" |
| codice | Senza cronometro la stessa riga sembrava ancora un comando | il periodo e' piu' quieto del cronometro e non e' piu' cliccabile |
| codice | La schermata "chi ha segnato" non aveva una via d'uscita visibile | voce **NESSUNO** in coda alla rosa: uscire e' un bersaglio come gli altri |
| codice | `showTeamNameInput` non era raggiungibile da nessun comando | rimossa |

**Non fatti, e perche'.** *Keep-screen-on e supporto ambient* durante la partita:
tenere acceso lo schermo di un orologio costa batteria, e "partita attiva" oggi sul
polso non ha una definizione pulita - `scoreState` resta valorizzato anche a partita
finita. L'ambient vero richiede un `AmbientLifecycleObserver` e un layout dedicato:
e' una funzione, non una correzione. Entrambe vanno decise, non dedotte.

*Da guardare su dispositivo:* il "K" da 48dp toglie 48dp di larghezza ai due
punteggi (che pero' si ridimensionano da soli), e su quadranti piccoli la colonna
centrale potrebbe risultare troppo larga.

### Cambio sport anche dall'orologio - 11 settembre 2026

Chiesto esplicitamente. Implementato **senza spostare l'autorita'**: l'orologio non
conosce gli sport, non ne decide nessuno, e continua a non dipendere da `:core`.

**Come.** Lo stato v2 guadagna quattro chiavi *additive*:

| Chiave | Cosa porta |
|---|---|
| `sport_label` | il nome dello sport corrente, gia' tradotto dal telefono |
| `sport_ids` | gli id scegliibili, separati da una barra verticale |
| `sport_labels` | le rispettive etichette, gia' tradotte |
| `match_in_progress` | vero se la partita ha almeno un evento |

e un path nuovo, `/scoreboard/v2/sport`, su `MessageClient`. Il golden test in
`:shared` li congela come tutti gli altri.

**Perche' le etichette viaggiano.** Se l'elenco degli sport vivesse anche
sull'orologio, aggiungerne uno vorrebbe dire aggiornare **due** APK invece di uno:
esattamente cio' che il v2 e' fatto per evitare. Le traduzioni vivono solo sul
telefono, quindi partono da li' gia' risolte. Su un telefono che parla una bozza
precedente del v2 gli elenchi arrivano vuoti e il comando **non compare**: nessun
ramo speciale, nessun errore.

**Perche' gli elenchi sono stringhe e non array.** `OptimizedWearDataSync.sendData`
serializza solo tipi scalari. Allargare quel `when` per un caso solo sarebbe stato un
cambio piu' rischioso di un separatore.

**Il numero di sequenza resta UNO per nodo.** La schermata di scelta **non spedisce
niente**: restituisce l'id alla schermata principale, che chiede al telefono usando lo
stesso contatore delle intenzioni di punteggio. Un `WearViewModel` creato dentro la
schermata di scelta ne avrebbe avuto uno suo, seminato da un orologio di sistema
diverso: due contatori indipendenti sullo stesso nodo sono il modo in cui un messaggio
nuovo viene scartato come «gia' visto». E' stata la prima stesura, ed e' stata cambiata.

**La guardia resta una sola.** `MainViewModel.selectSport` rifiuta a partita iniziata,
e nessuno la duplica: il servizio non valida l'id contro un elenco (`:core` e' l'unico
posto che sa quali sport esistono; un id sconosciuto viene assorbito da
`SportRegistry.byId`, che ripiega sul calcio), e il ramo del broadcast non ricontrolla
se la partita sia cominciata. L'orologio conosce `match_in_progress` e lo dice **prima**
di chiedere, per non far partire una richiesta di cui sa gia' la risposta -- ma se la
chiedesse lo stesso, a rifiutare sarebbe comunque il telefono, con uno Snackbar.

*Da guardare su dispositivo:* i due comandi in fondo (SPORT e AZZERA) sono una catena
"packed" di larghezze `wrap_content`. Stimati ~122dp in totale, contro i ~136dp del
quadrato inscritto di un quadrante da 192dp: entrano. Su un quadrante da 160dp non
entrerebbero, ma non esistono Wear OS moderni cosi' piccoli. Va **verificato**.

### Partita segnata dal solo orologio - 11 settembre 2026

Chiesto: «qualcuno usa solo l'app dal watch per registrare la partita e poi la vuole
inviare dal telefono». **Prima non funzionava affatto**, e non in modo parziale:
sull'orologio non esisteva NESSUNA persistenza (nessun `SharedPreferences`, nessun
file, nessun database), e un tocco non consegnato produceva una vibrazione di errore e
spariva. Con il telefono in borsa si perdeva la partita intera.

**Il disegno non sposta l'autorita'.** L'orologio registra INTENZIONI con il loro
orario, non punteggi: quando il telefono non risponde le mette in coda su disco, e al
ritorno le spedisce. Il telefono le ripiega nel motore ai tempi giusti. Per un motore
che rifa' il calcolo dall'inizio a ogni annullamento, riapplicare un registro **e'
letteralmente la stessa operazione** di riceverlo dal vivo.

| Pezzo | Dove |
|---|---|
| la coda su disco | `wear/.../PendingIntents.kt` |
| l'arretrato in un messaggio solo | `MSG_INTENT_BATCH` |
| la conferma di applicazione | `MSG_BATCH_ACK` |
| l'orario del tocco | `KEY_AT_MILLIS`, nel singolo e nel blocco |

**Tre decisioni che valgono piu' del codice.**

1. **L'arretrato parte come UN messaggio.** `MessageClient` non garantisce l'ordine:
   due tocchi che arrivassero invertiti farebbero scartare il piu' vecchio dalla
   guardia sulla sequenza, cioe' perdere un punto proprio mentre si recupera una
   partita intera. Un messaggio, una sequenza, applicato in ordine dall'altra parte.
2. **La coda si svuota sulla CONFERMA, non sulla consegna.** "Consegnato al nodo" non
   vuol dire "applicato": il messaggio raggiunge il servizio del telefono anche ad app
   chiusa, e quel servizio non conosce le regole di nessuno sport -- puo' solo inoltrare
   a un ViewModel che potrebbe non esistere. Il telefono conferma **dopo** aver
   applicato, e solo allora l'orologio cancella.
3. **L'orario e' quello del tocco, non della consegna.** Una partita giocata alle 18 e
   consegnata alle 20 resta una partita delle 18: altrimenti il riassunto direbbe che
   e' durata due ore, e i tempi esportati verso Padel Elite sarebbero tutti sbagliati
   nello stesso modo.

**Niente fusioni decise al posto dell'utente.** Se il telefono ha gia' eventi suoi,
l'arretrato **non** viene applicato e **non** viene confermato: resta sull'orologio, che
riprovera' al collegamento successivo, e il telefono lo dice. Nessuna perdita, e la
scelta di quale partita conti resta a chi la sa.

**Il tetto e' 2000 voci.** Un padel lungo sta abbondantemente sotto (3 set da 13 game da
~7 punti fanno meno di 300 tocchi); il tetto serve al caso in cui l'orologio resti a
registrare per giorni senza mai trovare il telefono. Oltre, il tocco non viene
registrato e la vibrazione lo dice.

**Al polso si vede.** La riga in basso diceva «NIENTE TELEFONO», che si legge come «non
sto registrando niente» -- il contrario di quel che succede. Ora dice anche quanti punti
stanno aspettando. La vibrazione ha un terzo pattern: conferma, **tenuto da parte**,
errore.

**Copertura.** `PendingIntentsTest`, 7 test, verificato per falsificazione: sostituendo
`removeFirst(n)` con uno svuotamento totale, il test che protegge dalla perdita dei
punti segnati durante la consegna diventa rosso.

### ~~Quello che ancora NON fa~~ - fatto l'11 settembre 2026

Il testo qui sotto e' rimasto per memoria della decisione. Il seguito e' nella
sezione successiva.

**Il punteggio non si vedeva al polso mentre il telefono non c'era.** L'orologio continua a
mostrare l'ultimo stato ricevuto piu' il conteggio in attesa: i tocchi sono salvi, ma
per un'ora si segna alla cieca. Mostrare il punteggio vero offline richiede che `:wear`
dipenda da `:core` e tenga un motore locale da usare **solo** senza telefono. E'
fattibile e coerente (i due motori folderebbero gli stessi eventi con lo stesso codice,
quindi non possono divergere), ma e' un cambio di dipendenze fra moduli e va deciso, non
dedotto: il costo e' che da quel momento un nuovo sport richiede di aggiornare **anche**
l'APK dell'orologio per funzionare offline.

### La passata sul telefono - 11 settembre 2026

Gli ultimi 12 rilievi dell'audit. Uno era gia' rientrato da solo, gli altri 11 sono chiusi.

| Rilievo | Rimedio |
|---|---|
| La descrizione parlata del `−` veniva aggiornata sulla vista sbagliata | **gia' rientrato**: il codice che la aggiornava e' sparito quando i due `−` sono stati nascosti nel padel. L'`ImageView` interna diventa `importantForAccessibility="no"`, cosi' la descrizione la da' solo il comando |
| Il punteggio cambia da solo quando arriva dall'orologio e nessuno lo annuncia | `accessibilityLiveRegion="polite"` sulle due cifre |
| I nomi squadra sembravano toccabili e non lo erano | **rimesso il listener**: `TeamNameDialogFragment` esisteva gia', completo, e non lo apriva nessuno |
| Ogni schermata tornava indietro in modo diverso, due non tornavano affatto | barra e freccia su cronologia, impostazioni e statistiche, come le due schermate che gia' funzionavano |
| Nelle impostazioni sport e lingua si salvavano subito, il resto no | **via il pulsante SALVA**: i tre campi si salvano alla perdita del fuoco e in `onPause` |
| Il campo del cambio portiere restava visibile giocando a padel | segue `hasAuxCountdown`, come la schermata principale |
| Nome squadra e punteggio potevano crescere senza limite | `maxLines` e `ellipsize` sul nome, `maxLines` sulla cifra |
| `values-night/themes.xml` era una copia identica | cancellato |
| Lo stato dell'orologio si leggeva solo dal colore di un'icona | `contentDescription` aggiornata nei due rami, con le stesse parole del tooltip |
| Testi cablati in inglese nella schermata principale | in `strings.xml` con la traduzione; i segnaposto riscritti a runtime diventano `tools:text` |
| Le misure del tabellone erano cablate nel layout | gia' fatto con i rilievi gravi (`score_section_min_height`, `score_card_min_height`, `score_text_size`) |
| La cifra grande scattava verso l'alto quando compariva il dettaglio | `layout_constraintVertical_bias="0"` sui due contenitori |

**Le statistiche avevano una freccia solo disegnata.** `activity_statistics.xml` portava
`app:navigationIcon="@drawable/ic_back"` e un titolo cablato in inglese, ma
`StatisticsActivity` non chiamava mai `setSupportActionBar` ne' registrava un listener:
quella freccia non era mai stata cliccabile. Ora la mette AppCompat, e il titolo arriva
dall'`android:label` gia' dichiarato nel manifest. `ic_back` e' rimasto senza referenti
ed e' stato rimosso.

**Quattro stringhe e un disegno spariti perche' i rimedi li hanno resi orfani:**
`cd_subtract_point` e `cd_undo_point` (i due `−` del padel non esistono piu'),
`settings_saved` e `save_settings` (non c'e' piu' un pulsante SALVA), `ic_back`.
Lint sul telefono scende da 11 a 8 avvisi; i tre che restano sono di terze parti o
pre-esistenti.

### Punteggio offline al polso - 11 settembre 2026

Deciso e fatto: `:wear` dipende ora da `:core`, e tiene un motore locale usato
**solo** finche' il telefono non ha confermato.

**La regola, in una riga:** il polso mostra il proprio calcolo finche' ha tocchi non
confermati, altrimenti mostra quello che dice il telefono.

**Non e' una seconda autorita'.** E' la STESSA operazione che fara' il telefono, con lo
stesso codice di `:core`, sugli stessi eventi: il registro ricevuto per ultimo piu' la
coda locale. Per costruzione i due risultati non possono divergere, perche' non esiste
un secondo algoritmo da tenere allineato -- c'e' una sola funzione, chiamata due volte.
E' esattamente per questo che il registro (`KEY_EVENT_LOG`) viaggia nello stato v2:
senza, il polso potrebbe calcolare solo i punti che ha segnato lui, e una partita
cominciata col telefono in mano ripartirebbe visivamente da zero.

**Tre decisioni.**

1. **Le capacita' non si salvano su disco.** Sono una funzione dello sport: a freddo si
   ricavano da `SportRegistry`, che e' piu' giusto che conservarne una copia capace di
   invecchiare. Su disco stanno solo lo sport e il registro.
2. **Gli eventi locali si applicano senza tempo.** Il tempo appartiene alla cronaca, non
   alla regola: il punteggio non dipende da quando e' stato dato il tocco, e gli orari
   veri li porta la coda quando parte. Convertirli al polso vorrebbe dire tenere li' un
   secondo orologio di partita per un valore che nessuno legge.
3. **Mentre un arretrato e' in viaggio non si ridisegna.** Lo stato applicato e l'ack
   partono dal telefono quasi insieme: ricalcolare in quella finestra significherebbe
   sommare l'arretrato a un registro che lo contiene gia'.

**Due difetti trovati dai test, non dalla lettura.**

- Con un tocco in coda e un telefono che parla una bozza precedente del v2 (nessuno
  `sportId`), il calcolo locale usciva prima e **lo schermo restava vuoto**. Ora dice
  di non esserci riuscito e chi ha chiesto mostra l'ultimo dato vero.
- `LastKnownMatch` era **a sola scrittura**: nessuno la rileggeva, quindi un orologio
  riavviato a meta' partita col telefono in borsa avrebbe perso comunque il punteggio --
  proprio lo scenario per cui la classe esisteva.

**Il prezzo, dichiarato.** Uno sport nuovo ora richiede di aggiornare **anche** l'APK
dell'orologio per funzionare offline. Online continua a bastarne uno solo: il polso
rende stringhe come prima.

**Copertura:** `OfflineScoreTest`, 6 test, tutti costruiti confrontando il polso con un
motore di riferimento invece che con valori scritti a mano. Verificati per
falsificazione: ignorando la coda locale, due test diventano rossi; togliendo la
rilettura da disco, quello del riavvio diventa rosso.

*Da guardare su dispositivo:* il passaggio online -> offline -> online, che e' l'unico
punto in cui due sorgenti si alternano a schermo.

### I test strumentati adesso compilano - 11 settembre 2026

Fino a oggi `./gradlew assembleDebugAndroidTest` **falliva**: nessuno se ne accorgeva
perche' la CI non lo invocava, ed era l'altra meta' della "CI verde" che non verificava
niente. Ora compila, e dentro c'e' qualcosa che vale la pena eseguire.

**`WearCommunicationTest` e' stato cancellato, non riparato.** Non era un test rotto da
una modifica: era un test che **non poteva passare** in nessuna delle sue quattro prove.
Referenziava `syncScores` e `sendMessageWithRetry`, che non esistono; asseriva su un path
`scoreboard/score_data` mai esistito; una prova non aveva **nessuna asserzione** ("verifica
nei log che il retry sia avvenuto"); l'ultima aspettava dieci secondi un `PATH_TEST_PONG`
che nessuno scrive -- e infatti quel path e' nell'elenco di quelli scritti e mai letti.

**Il test di migrazione e' passato da `@Ignore` a reale**, ed e' cresciuto. Verifica i
DATI e non solo lo schema, perche' una migrazione puo' produrre uno schema formalmente
giusto e righe sbagliate -- ed e' esattamente il difetto che in questo progetto e' costato
di piu' (i default di Kotlin non sono default SQL). Tre prove: 11->12 con il backfill dei
default, 12->13 con la colonna nullable, e la catena 11->13 in un colpo solo, che e' il
percorso vero di un telefono rimasto indietro di due versioni. Verifica anche che
l'indice si chiami **carattere per carattere** come quello che Room genera.

**Le due prove di layout erano peggio che inutili.**

- La prima girava su tre `Configuration` diverse ma le applicava DOPO il launch, con
  `resources.configuration.updateFrom()`: non rifa' il layout, quindi misurava tre volte
  la stessa schermata. Pretendeva anche 280dp di altezza in orizzontale, dove ora
  `values-land` ne prevede 180 di proposito.
- La seconda era racchiusa in un `if (dialogFragment != null)`: se il dialogo NON
  compariva, passava in silenzio. Non poteva fallire -- e nel frattempo il listener che
  lo apriva era stato tolto e la card continuava ad accendersi sotto il dito.

Riscritte in modo che possano diventare rosse, la seconda e' ora la guardia di regressione
del listener rimesso oggi.

**Stato dei test: 399 JVM, ZERO saltati, zero falliti.** Non esiste piu' un solo `@Ignore`
in tutto il progetto. Piu' 6 prove strumentate che compilano e aspettano un dispositivo:

    ./gradlew :mobile:connectedDebugAndroidTest

E' anche la prima delle verifiche manuali accumulate che diventa un comando invece che
una procedura.

### Il selettore dello sport non mente piu' - 11 settembre 2026

Ultimo debito dichiarato ancora aperto, e il piu' sgradevole dei tre: era anche una
violazione del principio per cui **il sistema non deve mai fare qualcosa di diverso da
cio' che e' sembrato fare.**

Cosa succedeva: si sceglieva un altro sport a partita in corso; la preferenza veniva
scritta **sempre**; `MainViewModel.selectSport` rifiutava di applicarla, giustamente,
perche' convertire un punteggio vivo fra due regolamenti non ha una risposta giusta. Ma
nessuno lo diceva alla schermata impostazioni, che intanto mostrava lo sport nuovo nel
menu. Risultato: il tabellone continuava col vecchio, il menu diceva il nuovo, e il
cambio scattava da solo alla partita **successiva**. Tre bugie in fila.

Ora il selettore controlla, rifiuta, lo dice con `@string/sport_change_blocked` e rimette
nel menu la voce vera. **La guardia non e' duplicata:** e' la stessa condizione -- il
registro eventi non e' vuoto -- letta dalla copia persistita invece che dal motore, che
quella schermata non ha. La nota originale del debito diceva che duplicarla avrebbe
creato due verita' capaci di divergere; leggere la stessa verita' da dove e' scritta non
ne crea una seconda.

Resta la finestra di pochi millisecondi fra il primo punto e la sua persistenza, in cui
la schermata potrebbe ancora accettare: in quel caso si ricade nel comportamento di
prima, cioe' `selectSport` rifiuta. Nessuna regressione, e nessuna pretesa di atomicita'
fra due schermate che non condividono un motore.

### L'ultimo debito: un annullamento che sembrava non fare niente - 11 settembre 2026

**Decisione rovesciata.** `MatchEngine.apply` registrava anche gli eventi senza effetto --
un tocco a partita finita, un lato fuori da 1..2, una correzione a zero -- per tenere vera
la proprieta' algebrica "applica poi annulla = esattamente lo stato di prima". Ora un
evento che non cambia lo stato **non entra nella storia**, perche' non e' accaduto.

**Perche'.** La proprieta' era vera e il prezzo lo pagava chi usa l'app: dopo un tocco
inerte servivano DUE annullamenti per togliere il punto precedente, e **il primo sembrava
non fare niente**. Un comando che appare inerte e' il difetto piu' grave che
un'interfaccia possa avere, perche' l'utente non ha modo di distinguerlo da un'app
bloccata. Vale piu' di un'identita' che nessuno all'infuori del motore puo' osservare.

**Un argomento che sembrava decisivo e non lo era.** Avevo ipotizzato che gli eventi
inerti inquinassero anche il riassunto e l'export verso Padel Elite con punti fantasma.
Controllato: **no.** `MatchSummarizer` e `MatchExporter` li saltano gia' entrambi con
`if (next == state) continue`. Quei filtri restano, e non sono ridondanza: sono cio' che
mantiene leggibili i registri **gia' salvati** dalle versioni che scrivevano gli eventi
inerti. Il test dell'export e' stato riscritto per costruire proprio quel caso con
`restoreLog`, invece di fabbricarlo applicando eventi che il motore non registra piu'.

**Una incoerenza trovata mentre si guardava.** `subtractScore` aveva gia' la guardia
giusta -- "gli effetti collaterali scattano solo se il punteggio e' davvero cambiato" --
mentre `addScore` non ce l'aveva: a partita finita apriva comunque il dialogo del
marcatore e scriveva un gol nel registro a schermo. Il tabellone diceva una cosa e la
cronaca un'altra. Ora le due funzioni si comportano allo stesso modo.

**Test.** La proprieta' centrale e' stata riscritta nella forma in cui e' osservabile, e
il ciclo da 200 passi ora verifica **entrambi** i rami (e fallisce se non li esercita tutti
e due). Piu' un test sul caso vero: partita di padel finita, tocco in piu', **un solo**
annullamento e un solo cambiamento visibile. Verificati per falsificazione: togliendo la
riga di guardia, entrambi diventano rossi.

**~~Resta aperto:~~ chiuso subito dopo, su richiesta** -- vedi la sezione seguente.

### I comandi si spengono a partita finita - 11 settembre 2026

Seguito diretto del punto precedente: restavano premibili e non facevano nulla.

**Dove vive l'informazione.** `ScoreDisplay` guadagna `matchOver`, e non una LiveData a
parte. Il motivo e' la classe di difetto inseguita per tutta la sessione: il display e'
costruito da **un solo punto per sport**, a ogni cambiamento di stato, quindi non esiste un
posto in cui qualcuno possa dimenticarsi di aggiornarlo. Una LiveData separata avrebbe
avuto cinque punti di aggiornamento -- `publishEngineState`, `applySport`, `startNewMatch`,
il ripristino dal database, l'arretrato dall'orologio -- e sarebbe bastato saltarne uno.

**Telefono.** I due `+` diventano non cliccabili, si attenuano al 40% e la loro
descrizione parlata dice perche'.

**Orologio.** Li' il `+` e' **tutto il lato**, ed e' anche il posto dove e' piu' facile
continuare a toccare senza guardare. I due lati si spengono allo stesso modo, e la riga in
basso -- quella costruita per il gesto e poi riusata per "niente telefono" -- dice
**PARTITA FINITA**. Spegnere due bersagli senza dire perche' li farebbe sembrare rotti.

**L'annullamento resta acceso da entrambe le parti.** E' esattamente cio' che serve se
l'ultimo punto era sbagliato, e riportandolo indietro lo stato torna "non finita": i
comandi si riaccendono da soli, senza un ramo dedicato.

**Anche offline.** Il polso lo calcola da solo quando il telefono non c'e', con lo stesso
codice: `rebuildLocalState` prende `matchOver` dal proprio `ScoreDisplay`. Coperto da un
test che chiude la partita con un punto in coda, verificato per falsificazione.

### L'icona, rifatta - 11 settembre 2026

Non era una questione di gusto: tre problemi misurabili.

1. **Una "V" e una "S" sovrapposte**, entrambe con bordo nero, sopra un cerchio bianco
   traslucido, piu' due schegge decorative. A 48dp -- la misura a cui un'icona vive -- le
   due lettere sovrapposte diventavano una macchia.
2. **Fuori dalla zona sicura.** Un'icona adattiva puo' contare solo su un cerchio di
   **raggio 33** attorno a (54,54): il cerchio bianco aveva raggio 40 e le schegge stavano
   a x 80-85. La maschera del lanciatore li tagliava.
3. **`monochrome` puntava al disegno a colori.** L'icona a tema usa solo il canale ALFA:
   la silhouette di quel disegno era il cerchio pieno, quindi l'icona a tema era un disco
   con le lettere invisibili dentro.

**Il marchio nuovo:** due colonne di punteggio, allineate in basso, di altezza diversa,
nei due colori delle squadre. E' la schermata di gioco ridotta all'osso -- due lati, due
colori, uno avanti all'altro -- e non c'e' niente da leggere, quindi regge a 24dp in una
notifica quanto a 108dp nelle impostazioni. L'estremo piu' lontano sta a 32,7 dal centro:
dentro la zona sicura con un margine, quindi nessuna maschera taglia niente.

`ic_launcher_monochrome_vs.xml` e' un disegno **dedicato**, non un riuso: stesse due
colonne, tinta unica, silhouette che coincide col marchio. Il tint lo applica il sistema,
quindi nel file non c'e' nessun attributo di tema da risolvere -- un `?attr/` li' dentro e'
un rischio, perche' il drawable viene gonfiato fuori dal contesto di un'activity.

Aggiunta anche la variante tonda su `mipmap-anydpi-v33`, che prima esisteva solo su v26 e
quindi su Android 13+ ricadeva su una versione senza monocromatica.

**I PNG legacy in `mipmap-hdpi` e compagni non sono stati toccati**: con `minSdk 31` le
icone adattive coprono ogni dispositivo supportato e quei bitmap non vengono mai usati come
icona di lancio. Sono peso morto, ma rigenerarli non e' possibile da qui e cancellarli
senza poter verificare dove altro siano referenziati non vale il rischio.

*Da guardare:* l'anteprima e' stata resa e mostrata, ma su un lanciatore vero cambiano
l'ombra dinamica e il ritaglio effettivo. E' comunque la modifica piu' facile da giudicare
a colpo d'occhio fra tutte quelle di questa sessione.

### Il marcatore si sceglie quando si vuole - 11 settembre 2026

Secondo attrito di flusso, e dietro c'era un buco piu' grosso.

**L'attrito.** Il dialogo del marcatore si apriva da solo dopo ogni gol, cioe' nel momento
di massima attenzione: hai appena visto segnare, stai guardando il campo, e l'app ti chiede
di scegliere un nome da un elenco. Chi non sceglieva in fretta perdeva l'azione successiva.
Ora il gol si registra e basta; la riga nel registro dice **"tocca per il marcatore"** e la
domanda aspetta. A farla e' l'utente, quando gli va.

**Il buco trovato mentre si guardava.** `ScoringEvent.Point.playerId` **non veniva mai
valorizzato**: tutti e cinque i punti di costruzione sul telefono passavano solo `side`. Il
marcatore finiva solo in `matchEventLog`, una lista di presentazione tenuta **in memoria**.
Due conseguenze:

- spariva alla morte del processo, mentre il punteggio sopravviveva nel registro salvato;
- non arrivava mai al riassunto, che legge `ScoringEvent.Point.playerId`: la riga
  **"Marcatori" del report WhatsApp era vuota per costruzione, in ogni partita**. Il test
  che la copre passava perche' costruiva gli eventi con i playerId a mano.

E' un buco lasciato da me costruendo `MatchSummarizer`: avevo scritto il consumatore e mai
il produttore.

**`MatchEngine.attribute(index, playerId)`** riscrive una voce gia' scritta, ed e' l'unica
modifica permessa in un registro altrimenti in sola aggiunta. Puo' esserlo per una ragione
precisa: **il playerId e' inerte per le regole** -- nessuna implementazione di
`SportRules.apply` lo legge -- quindi lo stato non puo' cambiare. Un indice fuori intervallo
o un evento che non e' un punto non fanno niente, perche' chi chiama lavora su una lista che
un annullamento puo' aver accorciato nel frattempo.

**Perche' un indice e non "l'ultimo punto".** La scelta non arriva piu' subito dopo il gol:
puo' arrivare dieci minuti e sei gol dopo. `MatchEvent` porta quindi `engineIndex`, deciso
quando il gol viene registrato. Porta anche `playerId`, che e' il segnale affidabile di
"attribuito": `player` non lo e', perche' quando il marcatore manca contiene comunque il
nome della squadra e non e' mai nullo.

**Copertura:** `MatchEngineAttributeTest`, 5 test -- lo stato non cambia, la voce giusta,
la sopravvivenza a salvataggio e ripristino, la catena completa fino ai marcatori del
riassunto, e i casi che non devono fare niente. Verificati per falsificazione: togliendo la
riga che riscrive, tre diventano rossi.

Due test del ViewModel asserivano il vecchio comportamento ("increments score and shows
scorer dialog") e sono stati riscritti su quello nuovo: il gol e' registrato, non
attribuito, e sa a quale punto del motore si riferisce. Altri due ("does not show scorer
dialog") non hanno piu' oggetto, perche' non lo mostra piu' nessuno.

### D5 — la texture che non c'era - 11 settembre 2026

Il piano classificava D5 come "da guardare a occhio". Guardando il codice invece che lo
schermo, si e' scoperto che la domanda era mal posta.

**Le due texture non esistono.** `bg_asphalt_main` e `bg_concrete_card` sono `<shape>` con
un `<solid>` dentro: `#121212` e `#1E1E1E`, tinte piatte. E in tutto il progetto non c'e'
un solo `<bitmap>`. L'analisi di Fase D descriveva "texture di asfalto e cemento": era una
descrizione dei NOMI, non dei file. L'ho scritta io, ed era sbagliata.

**Quello che c'era davvero dietro i numeri era un'ombra**, ed era diventata un difetto
misurabile il giorno prima. Le due cifre avevano
`shadowColor="@color/asphalt_dark"` (#121212) fissa, con `dx=3 dy=3 radius=6`. Ma da ieri
il colore del testo si adatta alla luminanza della card scelta dall'utente:

- **card chiara** (spray yellow, il default): testo `#1E1E1E`, ombra `#121212`. Sono lo
  stesso colore. A 86sp condensed bold, con 3px di scarto e 6 di sfocatura, non e'
  un'ombra: e' il glifo disegnato due volte, sfalsato. Il numero risulta piu' spesso e
  sbavato su un lato.
- **card scura**: l'ombra si vede e non disturba, ma non sta separando niente -- il
  contrasto fra `#E0E0E0` e la card basta gia', e sotto non c'e' nessuna texture da cui
  staccarsi.

**Rimossa.** Su una tinta piatta un'ombra sul testo non ha un mestiere: non separa, non
aggiunge profondita' a un piano che non ne ha, e nel caso di default peggiora la forma
della cifra che e' l'oggetto piu' importante dello schermo. E' la riduzione di Fase D
applicata dove era stata promessa, con un argomento misurabile invece che di gusto.

**Anteprima resa e mostrata** prima di procedere: quattro casi, card chiara e scura, con e
senza. Resta l'unica cosa da confermare su un dispositivo vero, dove il rendering del
testo non e' quello di un SVG.

### L'emulatore non partiva, e cosa e' venuto fuori - 12 settembre 2026

**La causa.** Nascosta in mezzo a 257 righe di log verboso, una sola riga:

    FATAL | Running multiple emulators with the same AVD is an experimental feature.
            Please use -read-only flag to enable this feature.

L'emulatore credeva che un'altra istanza stesse gia' usando l'AVD `Pixel_9a`, a causa di un
**lock orfano**: `hardware-qemu.ini.lock/` (una cartella, con dentro un file `pid`) e
`multiinstance.lock`. Android Studio non mostra quella riga, quindi il sintomo che si vede
e' "non parte" senza altro.

**Come ci si finisce.** Fermando il processo `emulator.exe` senza fermare il `qemu-system`
che ha generato: il figlio resta vivo, tiene il lock, e ogni avvio successivo fallisce. Nel
corso della diagnosi l'ho riprodotto **io stesso** con un `Stop-Process` sul lanciatore.
All'inizio della sessione c'erano gia' 6 `crashpad_handler` orfani senza nessun emulatore
vivo: la stessa cosa era successa prima, piu' volte.

**Il rimedio** (da tenere a portata di mano, perche' ricapitera'):

```bash
adb kill-server
taskkill /F /IM qemu-system-x86_64.exe /IM qemu-system-x86_64-headless.exe /IM crashpad_handler.exe /IM netsimd.exe
rmdir /s /q "%USERPROFILE%\.android\avd\Pixel_9a.avd\hardware-qemu.ini.lock"
del /q "%USERPROFILE%\.android\avd\Pixel_9a.avd\multiinstance.lock"
```

Verificato: dopo la pulizia l'emulatore parte con la finestra e `adb devices` lo vede.
WHPX, l'AVD e le immagini di sistema erano sempre stati a posto, e anche il backend grafico
non c'entrava (falliva identico con `host`, `angle_indirect` e `swiftshader_indirect`).

### Il primo giro su dispositivo vero - 12 settembre 2026

Con l'emulatore in piedi, i 6 test strumentati hanno girato per la prima volta. **Hanno
trovato subito due cose che nessun build verde aveva visto in giorni.**

**1. Un crash che avevo introdotto io.** `dialog_team_name.xml` aveva quattro `Chip` senza
`layout_width`/`layout_height`:

    InflateException: Binary XML file line #79: You must supply a layout_width attribute

`TeamNameDialogFragment` era codice morto da tempo e nessuno poteva aprirlo. **L'11 settembre
ho rimesso il listener sul nome squadra** -- chiudendo il rilievo "i nomi sembrano toccabili
e non lo sono" -- e da quel momento toccare il nome di una squadra **chiudeva l'app**. Il
crash abortiva l'intera esecuzione: giravano 2 test su 6.

**2. Un difetto di robustezza vero.** `DialogFragment.show` usa `commit()`, che dopo
`onSaveInstanceState` lancia `IllegalStateException` e porta giu' l'app. Ora i dialoghi
aperti da un tocco passano tutti da una sola funzione che controlla `isStateSaved`: un tocco
che arriva mentre l'activity sta andando via si perde, invece di chiudere l'app.

**3. Una premessa sbagliata nel mio test.** Con la guardia, il test e' passato da crash ad
asserzione fallita -- il che ha provato che lo stato *era* davvero salvato. Il motivo: su
un'installazione pulita `MainActivity` lancia subito `OnboardingActivity`, che la mette in
pausa. Il test non misurava la schermata di gioco, misurava una schermata gia' coperta da
un'altra. Ora spegne il tutorial prima di montarla.

**Esito: 6 test strumentati su 6 verdi**, comprese le tre migrazioni Room, che fino a ieri
erano `@Ignore` e non erano mai state eseguite in tutta la vita del progetto.

### Guardata davvero, sull'emulatore - 12 settembre 2026

L'app e' stata installata e vista per la prima volta. Confermato a occhio: il tabellone
fisso in cima (D4), i numeri netti senza ombra (D5), il testo scuro sulle card chiare, la
riga di tre comandi (HISTORY / END MATCH / SHARE), la barra con la freccia nelle
impostazioni, il pulsante SALVA sparito, e l'icona nuova che si legge nel dock.

**Un difetto grave trovato subito.** Nelle impostazioni i valori dei campi -- nome squadra,
secondi del portiere, lingua, sport -- erano **testo scuro su card scure**, illeggibili.

Il tema dichiarava `Theme.Material3.DayNight.NoActionBar`, ma l'app dipinge asfalto e
cemento **sempre**: non ha mai seguito il tema di sistema. Su un dispositivo in modalita'
chiara quel parent risolve alla base **Light**, e da li' arriva `android:textColorPrimary`
scuro. Le sovrascritture di Material3 (`colorSurface`, `colorOnSurface`...) c'erano gia' ed
erano giuste: il buco stava nei colori del **framework**, che `TextInputEditText` usa per il
testo digitato. Base cambiata a `Theme.Material3.Dark.NoActionBar`: li sistema tutti in una
volta, e la dichiarazione coincide con il comportamento. **Verificato a occhio dopo il fix.**

### Da guardare la prossima volta (visto, non ancora sistemato)

1. **Due campi verdi vuoti.** Le `FormationView` (200dp ciascuna) senza giocatori mostrano solo lo
   sfondo. *Correzione del 13 settembre:* non e' vero che sono "senza segni" -- `bg_football_field`
   ha bordo, linea di meta' campo e cerchio; a quella misura si leggono poco. Solo calcio: nel
   padel la card e' nascosta.
2. ~~**Le etichette delle rose contraddicono le card.**~~ **Chiuso il 13 settembre**, vedi sotto. Nel riquadro TEAM ROSTERS "Team 1" e'
   rosa e "Team 2" ciano (`colorPrimary`/`colorSecondary`), mentre le card sopra sono gialla
   e verde. Sono le stesse due squadre con due coppie di colori diverse.
3. ~~**I FAB coprono il contenuto.**~~ **Ritirato il 13 settembre:** la riga delle azioni ha
   gia' `marginBottom 80dp`, quindi la fine della lista e' libera. Un FAB che passa sopra il
   contenuto mentre si scorre e' il comportamento standard di un FAB, non un difetto.
4. **Il blocco cronometro occupa circa un quinto dello schermo** nell'intestazione fissa, per
   mostrare "00:00" piu' START e RESET. Con D4 quello spazio e' ora permanente, e al
   contenuto scorrevole resta poco piu' di un quarto della pagina.
5. ~~**L'icona usa giallo e ciano.**~~ **Chiuso il 13 settembre:** la seconda colonna ora e'
   `team_electric_green`, come la squadra 2 di default in `ColorRepository`. Verificato a occhio
   nello splash del telefono.
6. **Il padel non e' stato guardato:** manca il giro sullo sport che si usa davvero.

### Il padel crashava a ogni apertura - 13 settembre 2026

**Il difetto piu' grave trovato finora, e solo perche' l'app e' stata aperta davvero.**

    NullPointerException: MutableLiveData.setValue on a null object reference
      at MainViewModel.applySport(MainViewModel.kt:502)
      at MainViewModel.<init>(MainViewModel.kt:549)

`init` stava alla riga 548, `_scoreDisplay` alla **874**. Il collettore delle impostazioni,
lanciato in `init`, riceve la prima emissione subito e -- quando lo sport salvato non e' il
calcio -- chiama `applySport`, che scriveva `_scoreDisplay` quando era ancora null. Sul
telefono c'era `active_sport = padel`. **Chiunque avesse scelto padel crashava a ogni avvio**,
cioe' esattamente l'uso reale. E' la stessa famiglia del crash di `matchEventLog` dichiarato
dopo `init`, gia' pagato una volta.

**Rimedio:** `_scoreDisplay` spostato sopra `init`, con un commento che dice perche' deve
restare li'. Era l'unico campo toccato da quel percorso dichiarato dopo `init`.

**Perche' nessun test l'aveva visto, per due ragioni.** `MainViewModelTest` simulava le
impostazioni con `emptyFlow()`, quindi quel percorso non veniva mai percorso. E usava
`StandardTestDispatcher`, che fa partire la coroutine di `init` solo DOPO il costruttore,
quando tutti i campi esistono gia'. In produzione `Main.immediate` la esegue DENTRO il
costruttore. Il test nuovo usa un `UnconfinedTestDispatcher` apposta: con il dispatcher del
setup passerebbe anche col difetto.

**La prima stesura del test non proteggeva niente, e lo ha detto la falsificazione.** Rimesso
`_scoreDisplay` dopo `init`, il test restava VERDE per due motivi: l'NPE nasceva in una
coroutine di `viewModelScope` e, fuori da `runTest`, finiva nel gestore delle eccezioni non
catturate senza toccare il thread del test; e l'asserzione su `activeSport` passava comunque,
perche' `applySport` lo scrive prima della riga che crashava. Rimedio: il corpo sta dentro
`runTest(UnconfinedTestDispatcher())`, che raccoglie le eccezioni non gestite e fa fallire il
test. **Verificato per falsificazione:** col difetto rimesso il test e' ROSSO con esattamente
l'NPE visto su emulatore (`applySport` <- `emit` del collettore di `init`), 18 test / 1
fallito; ripristinato, 18 / 0.

Verificato su emulatore: con padel salvato l'app ora parte, nessun crash nel buffer, schermata
del padel corretta (punteggio, riga dei set, solo i `+`, cronometro nascosto, ingranaggio
raggiungibile).

**Anche le etichette delle rose** ora prendono il colore della squadra nello stesso observer
che colora la card, invece di `colorPrimary`/`colorSecondary` del tema. Compilato e verificato
dalla suite, **non ancora guardato a schermo** (serve il calcio con le rose aperte).

### L'orologio, visto - 13 settembre 2026

Avviato l'emulatore `Wear_OS_Small_Round`, installata e aperta l'app: **gira in padel senza
crash**. Punteggio, riga dei set, `HOLD: UNDO`, RESET, il "K" nascosto, nulla tagliato sul
quadrante tondo piccolo. Il "K" da 48dp e i due comandi in fondo, che erano i punti a rischio,
non si sovrappongono.

**Quello che NON e' stato possibile verificare:** i due emulatori non sono accoppiati. Sul
telefono c'e' la companion app e una configurazione verso l'orologio, ma da entrambi i lati
`IsConnected=false`. Lo stato a schermo sull'orologio ("15 15", lime e rosa) e' quindi vecchio,
e sincronizzazione e colori restano da provare. L'accoppiamento passa dalla companion app, che
chiede l'accesso a un account: va fatto a mano.

**Il pallino verde mente: indagato.** Prova controllata: `+` sul telefono, il telefono salva
`/scoreboard/v2/state` con 15-0, sull'orologio in 36 secondi non arriva niente e resta 15-15.
Il dump di `WearableService` su entrambi dice `0 connected out of 1`; l'ultima connessione
vera dell'orologio si e' chiusa il 7 settembre dopo 1'32". Eppure l'app, da tutti e due i lati,
logga "Connected to 1 nodes".

Causa: `OptimizedWearDataSync.updateConnectedNodes` deduce il collegamento da
`getCapability(..., FILTER_REACHABLE)`, che restituisce il nodo anche a connessione chiusa, e lo
ricalcola solo quando la capability CAMBIA -- mai a intervalli. Il 15-15 a schermo e' il
DataItem rimasto in cache, riletto da `restoreStateFromDataItems` all'avvio. Quindi il pallino
dice "telefono raggiungibile" e il numero sotto e' vecchio, senza nessun segnale che lo sia.
**Correzione a una lettura precedente:** "Syncing all data" sul telefono subito dopo il
`request_sync` dell'orologio NON prova che il messaggio sia arrivato: puo' averlo innescato il
listener della capability del telefono stesso.

**Rimedio proposto, non applicato:** incrociare i nodi della capability con
`nodeClient.connectedNodes` (il `NodeClient` e' gia' iniettato e inutilizzato), e rinfrescare lo
stato anche al ritorno in primo piano. Non applicato perche' non verificabile finche' i due
emulatori non sono davvero accoppiati: un indicatore di connessione corretto "a occhio" e'
esattamente il difetto da togliere. `adb forward tcp:5601 tcp:5601` non ha riaperto la
connessione in 25 secondi; l'accoppiamento va rifatto dalla companion app, a mano.

**Visto di passaggio sul telefono, in padel:** il pulsante dice "UNDO LAST GOAL" / "ANNULLA
ULTIMO GOL" e la riga del registro "GOAL! Team 1 - tap to add the scorer" (`label_undo_last_goal`,
`log_goal_unattributed` in `MatchLogAdapter`). Parole del calcio in uno sport senza gol e senza
marcatori.

**Sistemato, e visto su emulatore in padel.** Il criterio e' `attributesScorer` (vero solo nel
calcio), nessun `when` sullo sport. Pulsante "UNDO LAST POINT", dialogo "Undo Last Point?", riga
del registro "Point - Team 1", riga dopo l'annullamento "Undo: Point removed"; l'errore di fine
partita ora e' neutro per tutti gli sport ("score before ending it"). Toccare la riga di un punto
non apre piu' niente.

**Un difetto gia' presente, trovato dal test:** `setOnClickListener` rende la view cliccabile
anche quando riceve `null`, e nell'adapter stava DOPO `isClickable = daAttribuire`: ogni riga del
registro restava un bersaglio, anche nel calcio le righe gia' attribuite. Invertito l'ordine.
`MatchLogAdapterTest`, 2 test, verificato per falsificazione: togliendo il rimedio e' rosso con
"expected Point - Team 1 but was GOAL! Team 1 - tap to add the scorer".

**Visti nello stesso giro, NON sistemati:**
- Dopo "Partita ripresa" i punti ripristinati non hanno righe nel registro e non si possono
  annullare: l'annullamento passa da `actionStack`, che al ripristino non viene ricostruito. Col
  punteggio a 15 ripreso, il pulsante di annullamento non c'e'.
- Quando compare il pulsante di annullamento la schermata scende di una riga e i due FAB in basso
  coprono HISTORY e SHARE. L'esame precedente dei FAB era stato fatto senza quel pulsante.
- Il telefono mostra "Wear OS Connected" con gli emulatori scollegati: stessa causa del pallino.
- In `MatchLogAdapter` l'evidenziazione `event.event.contains("GOAL!")` non scatta mai: il testo
  salvato e' "Goal". Codice morto da prima.

### Una lezione di metodo

Dopo il riavvio dell'emulatore ho mandato due tocchi a coordinate fisse senza guardare: sono
finiti su Google Calendar. Da allora ogni tocco e' preceduto da uno screenshot.

### Cinque voci chiuse con un workflow di agenti - 13 settembre 2026

Chiesto dal proprietario: "implementa il piano con subagent e dynamic workflow". Cinque voci,
ognuna implementata da un agente in un worktree isolato (branch `wf/<voce>`), poi un revisore
indipendente in sola lettura con il compito di smontarla, e un giro di correzione solo dove il
revisore ha trovato problemi seri. Uniti a mano senza conflitti; verifica completa verde,
**445 test JVM, zero falliti**, lint senza avvisi nuovi.

| Voce | Rimedio | Revisione |
|---|---|---|
| FAB sopra HISTORY/END MATCH/SHARE | `FabOverlap.fabsMustHide` confronta i rettangoli veri a schermo; i FAB si nascondono solo mentre la riga delle azioni e' sotto di loro, ricalcolato a ogni scorrimento e cambio di layout | approvato (3 note basse) |
| Partita ripresa senza righe ne' annullamento | `rebuildEventsAndUndo()` ripercorre il registro del motore e ricostruisce righe SCORE, pila degli annullamenti e `canUndo`, senza ricontare i gol nel database | approvato |
| Collegamento Wear dichiarato falsamente | `nodiCollegati()`: un nodo conta solo se sta sia nella capability sia in `nodeClient.connectedNodes`; `refreshConnection()` al ritorno in primo piano su telefono e orologio; stesso criterio per `sendMessage` e `testConnection` | approvato |
| Evidenziazione dei punti mai attiva | chiave `event.type == SCORE` invece del testo "GOAL!" | approvato |
| Intestazione fissa troppo alta | righe orizzontali: annulla, ingranaggio e orologio sulla prima; tempo e START/RESET su una riga con `Flow`, che manda i pulsanti sotto invece di tagliare il tempo | **corretto**: la prima versione tagliava "100:00" con PAUSA gia' a scala 1.0 |

Tutti con falsificazione dichiarata dei test JVM; i test strumentati nuovi (FAB e intestazione)
sono stati solo compilati dagli agenti, per regola.

**Visto su emulatore, in padel, sull'app unita:** intestazione su una riga sola; partita ripresa
a 30-0 con le sue due righe "Point - Team 1" e l'annullamento presente; le righe dei punti nel
giallo della squadra; a riposo i FAB nascosti con HISTORY e SHARE liberi, a fine scorrimento i FAB
tornano e la riga resta libera; annullare dopo la ripresa porta a 15-0, toglie una riga e lascia
il secondo punto annullabile. Il messaggio del telefono dice "Wear OS Not Connected", coerente
con l'orologio spento, ma non prova il caso sbagliato (connessione chiusa con capability ancora
raggiungibile): quello resta da vedere con i due emulatori accoppiati.

**`connectedDebugAndroidTest`: 9 test, zero falliti**, compresi i tre nuovi
(`inNessunaPosizioneDiScorrimento_unFabCopreIComandiDiFinePartita`,
`senzaCronometro_l_intestazione_e_una_riga_sola`,
`conCronometro_tempo_e_comandi_stanno_compatti_ma_restano_bersagli`). Al primo tentativo non era
partito nessun test: la partizione dati dell'emulatore era al 95% e l'installazione falliva con
`INSTALL_FAILED_INSUFFICIENT_STORAGE`. Svuotare le cache non liberava niente; su indicazione del
proprietario e' stata disinstallata `LiveMatchFinder` dall'emulatore (da 328 a 472 MB liberi).
Resta poco margine: una prossima installazione di qualcos'altro puo' riportare il problema.

**Visto anche nel calcio**, con l'app installata da sola su un'installazione pulita: ingranaggio e
orologio in alto, "MATCH TIME 00:00" con START e RESET sulla stessa riga. Nessun crash, tutorial
e richiesta dei permessi di notifica come previsto (la richiesta non e' stata toccata).

**Visto di passaggio:** il tutorial dice "Use the + and - buttons on each team", ma in padel e
tennis il "-" dentro le card non c'e' piu'. Da riscrivere senza nominare il "-".

### Il crash delle impostazioni e la sincronizzazione che non arrivava - 13 settembre 2026

**Il crash, segnalato dal proprietario con il logcat.** Cambiare sport a partita cominciata
chiudeva le impostazioni: `NullPointerException ... MatchSettingsActivity.observeViewModel$lambda$5,
parameter it`. `SingleLiveEvent.call()` pubblicava null anche sugli eventi `Unit`, e l'osservatore
Kotlin ha il parametro non nullo. `call()` e' stato tolto: i tre eventi `Unit` (`sportChangeBlocked`,
`sportChangeRejected`, `watchBatchRejected`) pubblicano `Unit`, e pubblicare null per sbaglio non
compila piu'. Commit `9f17103`, visto su emulatore: al posto del crash compare "Finish the current
match to change sport" e il selettore resta sul calcio.

**Lezione sul test.** La prima stesura contava gli avvisi ed era verde anche col difetto. Il
bytecode mostra perche': la lambda dell'Activity ha `checkNotNullParameter`, quella scritta dentro
`runTest` no. Il test ora verifica il contratto (l'evento pubblica un valore non nullo): rosso
prima del rimedio, verde dopo.

**Correzione a "Il pallino verde mente".** Quella diagnosi era sbagliata. Con i due emulatori
accesi il telefono diceva "Wear OS Connected", `nodiCollegati()` trovava `Wear_OS_Small_Round` e il
ping partiva, mentre `dumpsys` diceva ancora `0 connected out of 1`. Il log dell'orologio ha
chiuso la questione: **i `DATA_CHANGED` dal telefono arrivavano, un secondo dopo l'invio.**
`dumpsys` contava solo il canale di rete dell'emulatore, non l'unica strada del Data Layer. Il
collegamento c'era; il rimedio `wf/connessione` resta innocuo, ma non curava la causa.

**La causa vera: un permesso che Play Services non ha.** Subito dopo, sull'orologio:
`Permission Denial: Accessing service ...wear.WearDataLayerService ... requires
android.permission.BIND_WEARABLE_LISTENER_SERVICE`. Entrambi i servizi di ascolto
(`WearDataLayerService` e `SimplifiedDataLayerListenerService`) dichiaravano quel permesso, aggiunto
l'11 marzo 2026 dal commit `3aa5132` (google-labs-jules) per restringere chi puo' collegarsi. Ma
Google Play Services non lo possiede: nessun `onDataChanged` e nessun `onMessageReceived` ha mai
raggiunto l'app. E' la spiegazione del quadrante fermo su 15-15: si aggiornava solo rileggendo i
DataItem al risveglio (`restoreStateFromDataItems`), mai in tempo reale. Il permesso e' stato
tolto da entrambi i manifest; il controllo su chi chiama lo fa gia' `WearableListenerService`.
`ListenerServiceManifestTest` (uno in `:mobile`, uno in `:wear`) e' rosso prima del rimedio e
verde dopo. Suite completa verde: 451 test JVM, lint senza avvisi nuovi.

**Non ancora verificato su dispositivo:** che dopo il rimedio un punto segnato sul telefono arrivi
all'orologio in tempo reale, e che un tocco sull'orologio arrivi al telefono. Durante
l'installazione degli APK nuovi i due emulatori sono stati chiusi. E' la prima cosa da fare alla
riaccensione: segnare un punto sul telefono senza toccare l'orologio e cercare nel suo log
"Broadcasted v2 state" invece di "Permission Denial".

### Validazione completa con un workflow di agenti - 23 settembre 2026

**Il rapporto intero sta in `VALIDAZIONE.md`**: 70 difetti (16 alti, 25 medi, 29 bassi) in 12
lotti, ognuno con file, scenario e rimedio. Dieci aree cercate da un agente e smontate da un
revisore indipendente: 98 rilievi confermati, 8 respinti. Tutto in sola lettura, niente eseguito.

Il giudizio in breve: il dominio in `:core` regge; il problema sta nei bordi. Usata dal solo
telefono e senza rose l'app e' affidabile. Tre difetti alti colpiscono anche chi non usa
l'orologio: la fine partita riporta indietro i gol dei giocatori (L1), il padel non finito non si
puo' salvare (L1), e nel calcio ANNULLA dopo una correzione toglie l'evento sbagliato (L3). Tutto
il percorso con l'orologio (L4-L7) ha modi dimostrabili di perdere o raddoppiare punti, e non ha
mai girato su un dispositivo: fino a `cc142d7` i servizi di ascolto non ricevevano niente.

**Correzione a note precedenti di questo piano:** B5 (gol riportati indietro) e' chiuso a meta';
la Fase T lascia `:wear` a targetSdk 34, che Play non accetta piu' dal 31 agosto 2026; la
decisione "niente fusioni" sull'arretrato dell'orologio contraddice il calcolo offline costruito
sopra lo stesso registro; il totale "451 test JVM" somma debug e release.

**Ordine consigliato:** prima le prove su dispositivo elencate in fondo a `VALIDAZIONE.md`
(decidono la gravita' reale di L4-L6), poi L1, L3 e L9, che non ne hanno bisogno.

### Design per telefono e orologio - 24 settembre 2026

**Il progetto intero sta in `DESIGN.md`, le bozze in `design/`.** Un workflow di agenti in sola
lettura: per ciascuna piattaforma tre proposte indipendenti, un giudice che le ha confrontate
ricalcolando i contrasti WCAG e aprendo i file citati, una sintesi dalla vincente.

- **Telefono, "Bordo campo" (8,5/10):** il numero in alto, il pollice in basso, niente che si
  sposta sotto il dito. Slot fissi al posto dell'intestazione che cambia altezza (e' la causa
  che `FabOverlap` tampona), numero bianco su nero con il colore di squadra sulle zone del `+`,
  rose, registro e formazioni in un foglio PARTITA nella stessa Activity. Lo street resta nel
  contorno, come da Fase D.
- **Orologio, "Mezzo secondo" (8/10):** due cifre bianche a dimensione fissa, un secondo numero
  sempre nello stesso posto, una riga di stato che parla solo quando qualcosa non va (scollegato,
  punti in coda, partita finita). Il colore di squadra diventa una striscia e non e' mai testo.
- **Una sola regola per il colore squadra, `TeamInk` in `:core`,** cosi' da dare lo stesso
  risultato sui due lati: oggi un colore scuro scelto dall'utente fa sparire il punteggio.

Il piano di ciascuna pista mette per primi i passi piccoli e sicuri (testi che oggi fanno danni,
contrasti, schermo acceso). La schermata di gioco nuova aspetta L1-L3 della validazione. Le
decisioni di gusto, identita' e priorita' sono elencate in fondo a ogni pista di `DESIGN.md`:
nessun passo di design e' stato avviato.

### L9 Dominio racchetta - 24 settembre 2026

I due difetti di L9 in `VALIDAZIONE.md` sono chiusi, solo in `:core` e con test JVM puri.

- **Servizio dopo il tie-break.** `GamePoints.TieBreak` ricorda il `serveIndex` del primo
  punto (`openedAt`) e alla chiusura il contatore riparte da li' piu' uno: il set dopo lo apre
  chi ha ricevuto il primo punto, qualunque sia il punteggio del tie-break. Conseguenza da
  sapere: `serveIndex` non e' piu' monotono, alla chiusura del tie-break scende. Nessuno ne
  dipendeva; il KDoc di `RacketScore` lo dice.
- **Riassunto di una partita non finita.** `MatchSummary.currentSet` porta i game del set in
  corso. Il testo condiviso li mette in testa finche' nessun set e' chiuso, poi nella riga dei
  set. `score` resta `headline()`.

Nessun cambio al protocollo Wear ne' al database: lo stato del motore non si persiste, si
ricava rifacendo il fold del registro, quindi anche le partite salvate prima si rileggono giuste.
