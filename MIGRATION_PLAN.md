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

## Fase T — toolchain

Requisito Play: dal 31 agosto 2026 gli aggiornamenti richiedono `targetSdk 36`.
Il progetto è a 34, quindi **oggi non è pubblicabile**. Proroga richiedibile al
1 novembre 2026. Le app Wear OS sono esentate: `:wear` resta a `targetSdk 34`.

| # | Passo | Stato |
|---|---|---|
| T1 | Rimuovere il pin AGP nascosto in `settings.gradle` | ✅ fatto |
| T2 | Fissare il JDK del daemon a 17 + foojay resolver | ✅ fatto |
| T3 | Version catalog `gradle/libs.versions.toml`, versioni invariate | ✅ fatto |
| T4 | Una coordinata una versione + igiene dipendenze | ✅ fatto |
| T5 | Wrapper Gradle 8.9 → 8.13 | ✅ fatto |
| T6 | AGP 8.5.0 → 8.13.0 | ⬜ da fare |
| T7 | Kotlin 2.0.21 → 2.3.x + KSP appaiato + `jvmToolchain(17)` | ⬜ |
| T8 | ktlint 12.1.1 → 13.x (+ `ktlintFormat` in un secondo commit) | ⬜ |
| T9 | compileSdk 34 → 36 su tutti e tre i moduli | ⬜ |
| T10 | Robolectric → 4.16, `robolectric.properties`, Mockito 5 su `:wear`, **`includeAndroidResources` in `wear/build.gradle`** | ⬜ |
| T11 | Room → 2.8.4, `exportSchema = true`, schemi committati, `fallbackToDestructiveMigrationFrom(1..5)` | ⬜ |
| T12 | Edge-to-edge reale (`enableEdgeToEdge` + insets) | ⬜ ▲ richiede verifica manuale |
| T13 | Igiene manifest (FGS, predictive back, `ForegroundServiceStartNotAllowedException`) | ⬜ |
| T14 | **`targetSdk 34 → 36` su `:mobile`** — una riga, sblocca Play | ⬜ ▲ richiede pass manuale su device |
| T15 | Sweep deprecati (`Stack`→`ArrayDeque`, bundle Parcelable, guardie SDK morte) | ⬜ |
| T16 | Togliere `@Parcelize` da `Player`/`Role` (3 call site) → sblocca AGP 9 | ⬜ |
| T17 | CI: `lint`, `assembleRelease`, gate schemi, golden test del protocollo | ⬜ |

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
- **T10 prima di T14**: sette test Robolectric di `:mobile` non hanno `@Config`,
  quindi ereditano il `targetSdk`. Con 36 chiederebbero un jar android-all che
  Robolectric 4.12.2 non ha.

---

## Fase B — bug che il refactor cementerebbe

Da fare **prima** del dominio multi-sport. Non sono i più gravi in assoluto: sono
quelli che diventano irreparabili se il codice nuovo ci si costruisce sopra.

| # | Bug | File | Stato |
|---|---|---|---|
| B1 | `ACTION_REQUEST_SYNC` registrato nel filtro, nessun ramo nel `when` | `MainViewModel.kt:391` | ⬜ |
| B2 | Lista eventi read-modify-`postValue`, si sovrascrive | `MainViewModel.kt:687,758` | ⬜ |
| B3 | `startNewMatch()` gira prima di `bindService()` e pubblica 0-0 | `MainViewModel.kt:360` | ⬜ |
| B4 | Eco del cronometro: `startTimer()`/`pauseTimer()` senza `fromRemote` | `MainViewModel.kt:239` | ⬜ |
| B5 | Gol come `@Update` di riga intera su istanza stantia → serve `UPDATE ... goals = goals + 1` | `MainViewModel.kt:605` | ⬜ |
| B6 | Telefono e orologio dichiarano la stessa capability `scoreboard_app` | `*/res/values/wear.xml` | ⬜ |
| B7 | Marcatore attribuito per **nome**, l'id viene scartato | `PlayerSelectionActivity.kt:71` | ⬜ |
| B8 | `"Goal"` è insieme testo mostrato e chiave semantica | `MainViewModel.kt:609` | ⬜ |

Ortogonali, **non** prerequisiti: `teams` mai scritta, FK mancanti su
`MatchPlayerCrossRef`, wake lock non rilasciato, stringhe hardcoded nei layout.
Obbligatorio **prima del basket** (non del padel): `MatchTimerService` è solo
*bound*, quindi muore con l'Activity.

---

## Fase S — multi-sport

Modulo `:core`, `java-library` Kotlin puro, zero Android: è l'unico posto del repo
dove un test è garantito che venga eseguito, e non contiene `@Entity`/`@Parcelize`
quindi non allarga il blocco AGP 9.

| # | Passo | Stato |
|---|---|---|
| S1 | `:core` + `ScoreState`/`ScoringEvent`/`SportRules` + `FootballRules` + `MatchEngine`. Zero cambi di comportamento | ⬜ |
| S2 | `MIGRATION_11_12` + partita viva ripristinabile | ⬜ ▲ rollback = perdita dati |
| S3 | Protocollo Wear v2 (path nuovo, mai uno cambiato) | ⬜ ▲ matrice manuale 2×2 |
| S4 | Selettore sport + gating capability, solo calcio nel registro | ⬜ |
| S5 | **Padel punto a punto** | ⬜ |
| S6 | **Tennis** — una riga di config | ⬜ |
| S7 | Pallavolo / basket, solo su richiesta | ⬜ |

**Segnale d'allarme su S1:** è costruito per non rinominare nulla. Se costringe a
riscrivere anche un solo test, l'ordine è sbagliato — fermarsi e iniettare i DAO
attraverso la factory prima.

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

## Fase I — integrazione con Padel Elite

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
| I1 | Mappatura `Player.playerId` ↔ `v2_players.id` (chiude anche B7) | ⬜ |
| I2 | Selezione dell'ordine di servizio a inizio partita | ⬜ |
| I3 | Export su file, zero rete, con validazione a monte (4 giocatori distinti e mappati) | ⬜ |
| I4 | Import nella dashboard + destinazione per la timeline | ⬜ ▲ tocca il progetto padel |
| I5 | Upload diretto via `create_match()` | ⬜ ▲ tocca Supabase di produzione |

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
| `:wear` `WearViewModelTest` | 8 | **0** | manca `includeAndroidResources` → Robolectric in legacy resources mode, salta tutto su SDK > 28 |
| `DatabaseMigrationTest` | 1 | **0** | `@Ignore("Schema files are missing")` |
| `MainActivityLayoutTest` | 2 | **0** | `@Ignore("PackageParserException")` |

Inoltre il source set `androidTest` di `:mobile` **non compila**: quattro simboli
inesistenti in `WearCommunicationTest.kt` (`syncScores`, `sendMessageWithRetry`,
`MSG_TIMER_ACTION`, `PATH_TEST_PONG`). La CI non lo compila mai.
