# Export verso Padel Elite - il contratto

Questo file e' il punto di coordinamento fra l'app (questo repository) e la dashboard di Padel
Elite, che lo legge. Descrive **cosa l'app produce davvero oggi**, non cosa vorremmo. Se la
dashboard ha bisogno di qualcosa di diverso, lo si scrive in fondo, nella sezione "Richieste
della dashboard", e l'app si adegua lasciando traccia qui.

Sorgente di verita': `core/src/main/kotlin/it/vantaggi/scoreboardessential/core/MatchExport.kt`
(costruzione, validazione e serializzazione, con i test in `core/src/test`).

## Come arriva il file

Nessun invio diretto. L'app scrive un file JSON UTF-8, nome `<data>_<partita>.json`, MIME
`application/json`, e lo condivide con il foglio di condivisione di Android
(`mobile/.../utils/MatchExportUtils.kt`). **Non esistono** ne' l'import nella dashboard (I4) ne'
il caricamento diretto con `create_match()` (I5): il Supabase di Padel Elite e' produzione, e dal
lato app entrambi aspettano un'autorizzazione esplicita del proprietario, due permessi distinti.

## Formato, versione 1

```json
{
  "formatVersion": 1,
  "sportId": "padel",
  "config": {"mode": "...", "deuce": "...", "sets": 1, "tieBreak": true,
             "gamesPerSet": 6, "tieBreakTo": 7, "serveOrder": [3, 7, 4, 8]},
  "players": [{"localId": 3, "name": "Marco", "side": 1, "padelPlayerId": 42}],
  "scoreTeam1": 1,
  "scoreTeam2": 0,
  "winnerTeam": 1,
  "setScores": [[6, 4]],
  "timeline": [{"side": 1, "servingPlayerId": 3, "atMillis": 0}]
}
```

| Campo | Significato |
|---|---|
| `formatVersion` | Da controllare in import: un cambio di formato lo incrementa. |
| `config` | Le regole con cui rigiocare la timeline. `serveOrder` e' una lista di `localId`, vuota se l'ordine di servizio non e' stato impostato. |
| `players` | Esattamente 4, 2 per lato, ognuno con `localId` (id nell'app) e `padelPlayerId` (id in Padel Elite). |
| `scoreTeam1/2` | Stessi nomi e forma delle colonne di `v2_matches`: **set vinti**, non game. |
| `winnerTeam` | 1, 2, oppure `null` se la partita non e' arrivata in fondo. |
| `setScores` | Un `[gameTeam1, gameTeam2]` per ogni set **chiuso**, in ordine. |
| `timeline` | Un elemento per punto valido: correzioni e tocchi a partita finita sono esclusi. `side` = chi ha vinto il punto. `servingPlayerId` = **`localId`** (non `padelPlayerId`) di chi serviva, `null` senza ordine di servizio: si traduce con `players`. `atMillis` = millisecondi dal primo punto, `null` se lo sport non traccia i tempi. |

Set, game e punteggio corrente **non** sono nel file: si ricavano rigiocando la timeline con la
config. E' voluto: una copia che puo' divergere dalla fonte e' peggio di un calcolo in piu'.

**Regole di validita'** (l'app non esporta se una manca): 4 giocatori distinti, 2 per lato,
tutti con `padelPlayerId`, almeno un punto.

## Cose che la dashboard deve sapere

1. **Partita di padel interrotta** (il set unico non si e' chiuso): `scoreTeam` 0-0,
   `winnerTeam` null, `setScores` vuoto. I game del set in corso esistono solo nella timeline.
   Va trattata come "non conclusa", non come uno 0-0.
2. **Tiebreak.** Con `tieBreak: true` un set puo' finire 7-6, e la timeline contiene i punti del
   tiebreak. Nel modello web il tiebreak non era modellato (vedi MIGRATION_PLAN.md, Fase I).
3. **Servizio dopo un tiebreak** (tennis): corretto il 24 settembre 2026 (L9 in VALIDAZIONE.md).
   File esportati prima di quella data possono avere `servingPlayerId` spostato di un giocatore
   dopo il primo tiebreak. Il padel a set unico non ne e' toccato.

## Stato dell'app, per la dashboard

- **24 settembre 2026, corretto:** nel padel la card delle rose, con "aggiungi giocatore", era
  nascosta insieme alle formazioni. Senza quella card i 4 giocatori non si potevano assegnare ai
  lati e l'export risultava **sempre incompleto**: fino a questa correzione non esisteva nessun
  file reale di padel. Ora le rose restano in ogni sport (le formazioni solo nel calcio); prova
  strumentata `nelPadel_le_rose_restano_per_assegnare_i_giocatori`, verificata per falsificazione.
- Per esportare serve ancora che i quattro giocatori abbiano il `padelPlayerId`: si imposta nella
  scheda del giocatore (gestione giocatori). L'app lo chiede per nome quando manca.
- **Non ancora provato:** un export completo di padel dall'interfaccia, fino al file condiviso.
  E' il prossimo controllo su emulatore.

## Richieste della dashboard

(Da compilare dal lato dashboard: campi mancanti, forme diverse, casi da gestire.)
