# Il file del tabellone: contratto fra ScoreboardEssential e Padel Elite

ScoreboardEssential (app Android e Wear OS, repository `vantaggi/ScoreboardEssential`)
registra le partite di padel **punto per punto**. Questo documento dice com'è fatto il
file che l'app esporta e che la dashboard importa, e che cosa ne fa la dashboard.

Le due parti vivono in repository diversi. **Questo file è il riferimento per entrambe.**
Se il formato cambia, cambia prima qui.

> **Il tabellone è un di più, non un obbligo.** Chi non usa l'app continua a inserire le
> partite a mano o dalla Diretta come sempre. L'import non tocca `create_match()`,
> non aggiunge colonne a `v2_matches` e le partite senza cronaca si comportano come prima.

---

## 1. Il file, versione 1 (quella che l'app esporta oggi)

Scritto da `MatchExporter.toJson` in `core/.../MatchExport.kt`. JSON compatto in UTF-8,
nome del file `yyyy-MM-dd_HHmm_padel-<squadra1>-vs-<squadra2>.json`.

```json
{
  "formatVersion": 1,
  "sportId": "padel",
  "config": {
    "mode": "POINTS",
    "deuce": "GOLDEN_POINT",
    "sets": 1,
    "tieBreak": true,
    "gamesPerSet": 6,
    "tieBreakTo": 7,
    "serveOrder": [1, 2, 3, 4]
  },
  "players": [
    { "localId": 1, "name": "Marco", "side": 1, "padelPlayerId": 101 },
    { "localId": 2, "name": "Anna",  "side": 2, "padelPlayerId": 102 },
    { "localId": 3, "name": "Luca",  "side": 1, "padelPlayerId": 103 },
    { "localId": 4, "name": "Sara",  "side": 2, "padelPlayerId": 104 }
  ],
  "scoreTeam1": 6,
  "scoreTeam2": 0,
  "winnerTeam": 1,
  "setScores": [[6, 0]],
  "timeline": [
    { "side": 1, "servingPlayerId": 1, "atMillis": 0 },
    { "side": 1, "servingPlayerId": 1, "atMillis": 30000 }
  ]
}
```

| Campo | Significato |
|---|---|
| `formatVersion` | Versione del formato. La dashboard accetta **1 e 2** e rifiuta le altre. |
| `sportId` | Solo `"padel"` viene importato. |
| `config.mode` | `POINTS` (15/30/40) o `GAMES` (un tocco = un game). |
| `config.deuce` | `GOLDEN_POINT`, `KILLER_POINT` o `ADVANTAGE`. |
| `config.sets` | Al meglio di N set: 1, 3 o 5. |
| `config.tieBreak`, `tieBreakTo`, `gamesPerSet` | Tie-break a `tieBreakTo` punti con scarto di due, sul pari a `gamesPerSet`. |
| `config.serveOrder` | Id **locali** nell'ordine di servizio A1, B1, A2, B2. Vuoto se non impostato. |
| `players[]` | I quattro giocatori, **nell'ordine del roster e non del lato**: il lato lo dice `side`. |
| `players[].padelPlayerId` | **Facoltativo, da togliere** (vedi §4): `v2_players.id` della dashboard. Se c'è, precompila soltanto; se manca, chi importa sceglie i giocatori. |
| `scoreTeam1/2` | Nell'app sono i **set vinti** (o i game, a set unico). **La dashboard non li usa** (vedi §3). |
| `winnerTeam` | 1, 2, oppure `null` se la partita non è arrivata in fondo. |
| `setScores` | `[gamesTeam1, gamesTeam2]` per ogni set **chiuso**. Il set in corso non c'è. |
| `timeline[]` | Un elemento per punto giocato, in ordine. |
| `timeline[].side` | Il lato che ha vinto il punto: 1 o 2. |
| `timeline[].servingPlayerId` | Id **locale** di chi serviva; `null` senza ordine di servizio. |
| `timeline[].atMillis` | Millisecondi dal primo punto (il primo vale 0); `null` se non tracciati. |

La timeline **non** contiene il punteggio: set, game e 15/30/40 si ricavano rigiocando i
punti con le stesse regole. È una scelta dell'app: una copia del punteggio che può
divergere dalla fonte è peggio di un calcolo in più.

## 2. Versione 2: aggiunte richieste all'app

Tutte **opzionali e additive**: un file v2 è un file v1 con in più questi campi, e i
campi v1 non cambiano né nome né significato.

| Campo | Tipo | Perché serve |
|---|---|---|
| `matchId` | UUID, generato a inizio partita e salvato | Riconoscere lo stesso file importato due volte, e più avanti l'upload diretto ripetuto. |
| `startedAt` | ISO 8601 **con offset**, es. `2026-09-23T21:04:00+02:00` | Data e ora della partita: in v1 le deve scrivere a mano chi importa. |
| `appVersion` | stringa, es. `"1.4.0"` | Sapere quale versione ha prodotto un file quando qualcosa non torna. |

## 3. Che cosa ne fa la dashboard

L'import (`js/match-log.js` e `js/match-log-ui.js`, RPC `import_scoreboard_match`,
migrazione 63) fa queste cose, in quest'ordine:

1. **Legge e valida** il file: versione 1 o 2, `sportId = padel`, quattro giocatori
   distinti, due per lato, almeno un punto, al massimo 3000 punti.
2. **Rigioca i punti** con `window.LiveScoring` (lo stesso motore della Diretta, col
   tie-break vero attivato solo per l'import) e **controlla che il risultato coincida** con
   `setScores` e `winnerTeam` del file. Se non coincide il file viene rifiutato: vuol dire
   che i due motori non sono d'accordo, e salvare un risultato sbagliato sarebbe peggio
   che non salvarlo.
3. **Fa scegliere i giocatori all'admin, nell'ordine di servizio.** Il tabellone non
   conosce i giocatori della dashboard: nel file hanno i nomi dell'app ("A", "B"… o
   quelli scelti dall'utente). L'anteprima li elenca come in `config.serveOrder`:
   1° al servizio (coppia 1), 2° (coppia 2), 3° (compagno del 1°), 4° (compagno del
   2°), e per ognuno l'admin sceglie il giocatore del gruppo. Così la cronaca sa chi
   serviva ogni punto. Senza ordine di servizio si sceglie per coppia. Se il file porta
   ancora `padelPlayerId`, serve solo a precompilare.
4. **Calcola le colonne di `v2_matches` come l'inserimento manuale:**
   - `player1_id, player2_id` = lato 1, `player3_id, player4_id` = lato 2;
   - `set_scores` = i set chiusi, più il set interrotto se non è in parità (es. `4-3`);
   - `score_team1/2` = come il modulo "Inserisci match" dalla 4.12.0: **a set unico
     i game del set** (6-4), **a più set i set vinti** (2-1). La somma dei game
     faceva di un 7-6 3-6 7-5 un 17-17, cioè un pareggio per l'ELO. I game restano
     in `set_scores`. `scoreTeam1/2` del file non viene letto: si ricalcola dai punti;
   - `winner_team` = quello del file se la partita è finita, altrimenti chi ha vinto più
     set di `set_scores`, `0` in parità;
   - `match_date` e `match_time` da `startedAt` (v2) o scritti dall'admin (v1); il campo
     lo sceglie l'admin.
5. **Salva la partita chiamando `create_match()`** dentro la RPC, nella stessa
   transazione: stessi controlli, stessi trigger (monete, achievement, notifiche).
6. **Salva la cronaca** in `v2_match_logs`, una riga per partita, con gli id già
   tradotti in `v2_players.id`:
   - `serve_order` = ordine di servizio in id padel;
   - `points` = `[[lato, idPadelDiChiServiva|null, atMillis|null], ...]`.

Una partita importata due volte non crea un doppione: la RPC riconosce `external_id`
(il `matchId` in v2, un'impronta di giocatori e punti in v1) e restituisce la partita
esistente con `already_imported = true`.

**Una partita importata è una partita come le altre**: si modifica e si cancella dallo
storico come sempre. Cancellandola, la cronaca se ne va con lei (`ON DELETE CASCADE`).

## 4. Richieste all'app (per la sessione di lavoro su ScoreboardEssential)

0. **Togliere il numero Padel Elite (`padelPlayerId`) dall'app**, per scelta del
   proprietario: ScoreboardEssential è un'app a sé, chi la scarica non deve vedere
   niente che riguardi il gruppo di Padel Elite. Via il campo dalla scheda del
   giocatore, via il controllo `UnlinkedPlayers` dall'export, e nel file il campo
   si omette. La dashboard non ne ha bisogno: chi importa sceglie i giocatori
   nell'ordine di servizio (§3). Resta essenziale che `config.serveOrder` ci sia.

In ordine di valore. Tutte additive, nel rispetto delle regole del `MIGRATION_PLAN.md`
dell'app: il golden test non si aggiorna ma si affianca, `:core` resta senza dipendenze.

1. **Esportare anche dallo storico, a partita chiusa.** Oggi l'export esiste solo nella
   schermata live, prima di chiudere la partita (`endMatch()` azzera il motore). Serve
   salvare su `matches` l'**ordine di servizio** e l'**ora di inizio** in epoch, con una
   migrazione Room 13 → 14 additiva e lo schema `14.json` committato. `finalizeMatch` oggi
   sovrascrive `timestamp` con l'ora di fine: l'inizio va salvato a parte.
2. **`formatVersion 2`** con `matchId`, `startedAt` e `appVersion` (§2). `matchId` va
   generato e salvato a inizio partita, non all'export, altrimenti due export della stessa
   partita avrebbero due id diversi.
3. **Un fixture condiviso.** `MatchExportTest` scrive la partita realistica a tre set in
   `core/src/test/resources/export-v2-sample.json`; la dashboard lo copia in
   `tests/fixtures/scoreboard/` e lo usa come test differenziale fra i due motori.
4. **Le schermate della Cronaca anche nell'app**, offline: definizioni, valori
   attesi e schermate in [`SCOREBOARD_CRONACA_APP.md`](./SCOREBOARD_CRONACA_APP.md).
5. **Più avanti, upload diretto (I5).** Login Supabase **opzionale** nell'app e chiamata
   alla stessa RPC `import_scoreboard_match`, con gli stessi argomenti che oggi prepara la
   dashboard. Richiede il permesso `INTERNET` e l'aggiornamento di `PRIVACY_POLICY.md`,
   `README.md`, `RELEASE_CHECKLIST.md` e della scheda Play: senza login l'app deve restare
   interamente offline.

## 5. La RPC, per chi la chiama (dashboard oggi, app domani)

```
import_scoreboard_match(
  p_group uuid, p_date text,
  p1 int, p2 int, p3 int, p4 int,        -- p1,p2 lato 1; p3,p4 lato 2
  s1 int, s2 int, p_winner int,          -- come create_match
  p_court_id uuid, p_match_time text, p_set_scores jsonb,
  p_external_id text, p_format_version int, p_app_version text,
  p_config jsonb, p_serve_order int[], p_points jsonb,
  p_started_at timestamptz, p_duration_ms int
) RETURNS jsonb   -- { "match": <riga v2_matches>, "already_imported": bool }
```

Errori in più rispetto a `create_match`: `court_not_in_group`, `invalid_points`,
`invalid_config`, `invalid_serve_order`, `invalid_set_scores`, `invalid_external_id`.
