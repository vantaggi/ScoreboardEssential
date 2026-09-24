# Export verso Padel Elite - il contratto

**La fonte di verita' del formato e' [`docs/dashboard/SCOREBOARD_FORMAT.md`](docs/dashboard/SCOREBOARD_FORMAT.md)**,
copia del documento che sta nel repository della dashboard: se il formato cambia, cambia prima
li'. Questo file dice **cosa l'app produce davvero oggi** e dove sta nel codice; se un giorno i due
non coincidono, e' questo a essere indietro.

Codice: `core/src/main/kotlin/it/vantaggi/scoreboardessential/core/MatchExport.kt` (costruzione,
validazione e serializzazione, con i test in `core/src/test`).

## Come arriva il file

Nessun invio diretto. L'app scrive un file JSON UTF-8, nome `<data>_<partita>.json`, MIME
`application/json`, e lo condivide con il foglio di condivisione di Android
(`mobile/.../utils/MatchExportUtils.kt`). Si esporta da due posti, con lo stesso file:

- **dalla partita in corso**: Condividi, poi "Esporta partita" (solo padel);
- **dallo storico**, a partita chiusa: il comando "Esporta partita" sulla card di ogni partita di
  padel con un registro. Il motore si rifa' dal registro salvato con lo sport e l'ordine di
  servizio della riga; nel nome del file va la data della partita, non quella dell'export.

Il caricamento diretto con la RPC (I5) **non esiste**: il Supabase di Padel Elite e' produzione, e
dal lato app aspetta un'autorizzazione esplicita del proprietario.

## Formato, versione 2 (dal 24 settembre 2026)

```json
{
  "formatVersion": 2,
  "sportId": "padel",
  "matchId": "3f2a9c1e-5b7d-4e8a-9c01-2d4f6a8b0c1e",
  "startedAt": "2026-09-23T21:04:00+02:00",
  "appVersion": "1.0",
  "config": {"mode": "...", "deuce": "...", "sets": 1, "tieBreak": true,
             "gamesPerSet": 6, "tieBreakTo": 7, "serveOrder": [3, 7, 4, 8]},
  "players": [{"localId": 3, "name": "Marco", "side": 1}],
  "scoreTeam1": 1,
  "scoreTeam2": 0,
  "winnerTeam": 1,
  "setScores": [[6, 4]],
  "timeline": [{"side": 1, "servingPlayerId": 3, "atMillis": 0}]
}
```

Un file v2 e' un file v1 con in piu' `matchId`, `startedAt` e `appVersion`, e **senza**
`padelPlayerId`. Gli altri campi non cambiano ne' nome ne' significato.

| Campo | Significato |
|---|---|
| `formatVersion` | Da controllare in import: un cambio di formato lo incrementa. Oggi 2. |
| `matchId` | UUID generato al **primo punto** e salvato con la partita (`matches.matchUuid`): due export della stessa partita, dal vivo o dallo storico, hanno lo stesso id. **Manca** per le partite salvate prima della versione 14 del database. |
| `startedAt` | L'ora del primo punto, ISO 8601 con i secondi e l'offset sempre in cifre (`+00:00`, mai `Z`), nel fuso del telefono e con l'ora legale di quel giorno. Per una partita segnata dal solo orologio e' il primo tocco, non l'ora della consegna. **Manca** per le partite di prima della 14. |
| `appVersion` | `versionName` dell'app che ha scritto il file. C'e' sempre. |
| `config` | Le regole con cui rigiocare la timeline. `serveOrder` e' una lista di `localId`, nell'ordine A1, B1, A2, B2, vuota se l'ordine di servizio non e' stato impostato. |
| `players` | Esattamente 4, 2 per lato, nell'ordine dei roster (prima il lato 1): `localId` (id nell'app), `name` e `side`. Nessun id esterno: i giocatori del gruppo li sceglie chi importa, seguendo `serveOrder`. |
| `scoreTeam1/2` | Il punteggio di testata dell'app. La dashboard non lo legge: lo ricalcola dai punti. |
| `winnerTeam` | 1, 2, oppure `null` se la partita non e' arrivata in fondo. |
| `setScores` | Un `[gameTeam1, gameTeam2]` per ogni set **chiuso**, in ordine. |
| `timeline` | Un elemento per punto valido: correzioni e tocchi a partita finita sono esclusi. `side` = chi ha vinto il punto. `servingPlayerId` = `localId` di chi serviva, `null` senza ordine di servizio: si traduce con `players`. `atMillis` = millisecondi dal primo punto, `null` se lo sport non traccia i tempi. |

Set, game e punteggio corrente **non** sono nel file: si ricavano rigiocando la timeline con la
config. E' voluto: una copia che puo' divergere dalla fonte e' peggio di un calcolo in piu'.

**Regole di validita'** (l'app non esporta se una manca): 4 giocatori distinti, 2 per lato,
almeno un punto.

**Il numero Padel Elite non c'e' piu'**, per scelta del proprietario: l'app e' a se', e chi la
scarica non deve vedere niente del gruppo. Via il campo dalla scheda del giocatore, via il
controllo che lo pretendeva, via dal file. La colonna `players.padelPlayerId` resta nel database
(toglierla vorrebbe dire ricostruire la tabella), ma nessuno la legge ne' la scrive.

## Il fixture condiviso

`core/src/test/resources/export-v2-sample.json` lo scrive `MatchExportTest.ilFixtureV2CondivisoConLaDashboard`,
ed e' pensato per essere copiato nei test della dashboard. E' la partita a tre set di
`core/src/test/resources/scoreboard/v1-tre-set.json` (189 punti, 7-6 3-6 7-5, i valori attesi del
brief della Cronaca) rigiocata dal nostro motore: servitori e set tornano identici a quelli del
file generato dal copione della dashboard, e tolti i tre campi nuovi il file e' byte per byte il
v1 senza `padelPlayerId`. Id, inizio e versione sono fissi, quindi il file non cambia da un giro
all'altro; se il codice lo fa cambiare, il test lo riscrive e fallisce, e la differenza va guardata
e committata.

## Cose che la dashboard deve sapere

1. **Partita di padel interrotta** (il set unico non si e' chiuso): `scoreTeam` 0-0,
   `winnerTeam` null, `setScores` vuoto. I game del set in corso esistono solo nella timeline.
   Va trattata come "non conclusa", non come uno 0-0.
2. **Tiebreak.** Con `tieBreak: true` un set puo' finire 7-6, e la timeline contiene i punti del
   tiebreak. Nel modello web il tiebreak non era modellato (vedi MIGRATION_PLAN.md, Fase I).
3. **Servizio dopo un tiebreak** (tennis): corretto il 24 settembre 2026 (L9 in VALIDAZIONE.md).
   File esportati prima di quella data possono avere `servingPlayerId` spostato di un giocatore
   dopo il primo tiebreak. Il padel a set unico non ne e' toccato.
4. **Partite salvate prima della versione 14** esportate dallo storico: niente `matchId` ne'
   `startedAt`, e `serveOrder` vuoto con `servingPlayerId` null, perche' l'ordine non era salvato.
   L'import ripiega sull'impronta della v1 per i doppioni e fa scegliere i giocatori per coppia.

## Stato dell'app, per la dashboard

- **24 settembre 2026, formato 2:** export anche dallo storico (migrazione Room 13 -> 14, additiva
  su `matches`: `serveOrder`, `startedAt`, `matchUuid`; il lato dei giocatori c'era gia' in
  `MatchPlayerCrossRef.teamNumber`), `matchId`/`startedAt`/`appVersion` nel file, numero Padel Elite
  tolto da app e file, fixture condiviso.
- **24 settembre 2026, corretto:** nel padel la card delle rose, con "aggiungi giocatore", era
  nascosta insieme alle formazioni. Senza quella card i 4 giocatori non si potevano assegnare ai
  lati e l'export risultava **sempre incompleto**: fino a questa correzione non esisteva nessun
  file reale di padel. Ora le rose restano in ogni sport (le formazioni solo nel calcio); prova
  strumentata `nelPadel_le_rose_restano_per_assegnare_i_giocatori`, verificata per falsificazione.
- **Provato su emulatore il 24 settembre 2026**, col formato 1, dall'interfaccia fino al file
  condiviso: quattro giocatori, padel, due per lato, 16 punti. Il file reale e' in
  `esempi/export_padel_partita_interrotta.json`: rigiocando la timeline si ottengono game 2-1 e
  15-0 nel game in corso, come sullo schermo; `scoreTeam` 0-0, `winnerTeam` null e `setScores`
  vuoto, perche' il set unico non si e' chiuso. E' il caso 1 qui sopra, visto dal vero. Il
  formato 2 non e' ancora stato provato su dispositivo.

## Esempi

| File | Cosa mostra |
|---|---|
| `esempi/export_padel_partita_interrotta.json` | Formato 1, reale. Padel interrotto sul 2-1 nei game: timeline con tempi e servitori, `deuce` = `GOLDEN_POINT` (punto secco sul 40-40), `serveOrder` impostato da solo alternando i lati. |
| `core/src/test/resources/export-v2-sample.json` | Formato 2, generato dal test: padel al meglio di tre set, 7-6 3-6 7-5, 189 punti. |

Per rigiocare un game: 4 punti con 2 di scarto; con `GOLDEN_POINT`, sul 40-40 vince il punto
successivo. Il tiebreak parte sul 6-6 se `tieBreak` e' vero e arriva a `tieBreakTo` con 2 di
scarto. La logica di riferimento e' `RacketRules.kt`.

## Richieste della dashboard

Stanno nella sezione 4 di `docs/dashboard/SCOREBOARD_FORMAT.md`. Fatte il 24 settembre 2026: 0
(via il numero Padel Elite), 1 (export dallo storico), 2 (formato 2), 3 (fixture condiviso).
Fuori da questo lavoro: 4 (la Cronaca nell'app, in un lavoro parallelo) e 5 (upload diretto, I5).
