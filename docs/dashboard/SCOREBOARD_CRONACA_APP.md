# La Cronaca dentro ScoreboardEssential: brief per la sessione dell'app

Documento da passare alla sessione di lavoro sul repository
`vantaggi/ScoreboardEssential`. Il proprietario vuole le schermate della
**Cronaca** (quelle della dashboard Padel Elite, `js/match-log.js` e
`js/match-log-ui.js`) anche **dentro l'app**, sui dati del telefono, offline.
Contratto del file condiviso: [`SCOREBOARD_FORMAT.md`](./SCOREBOARD_FORMAT.md).

Qui ci sono le definizioni esatte, con i valori attesi su una partita di
esempio, così i due lati contano allo stesso modo.

---

## 0. Prima di tutto: via il numero Padel Elite

Decisione del proprietario. **ScoreboardEssential è un'app a sé**: chi la
scarica non deve vedere niente che riguardi il gruppo di Padel Elite.

- Togliere il campo `padelPlayerId` dalla scheda del giocatore
  (`AddEditPlayerActivity`) e dal modello (`Player.padelPlayerId`, migrazione
  additiva: la colonna può restare nel DB, basta non mostrarla né usarla).
- Togliere `ExportProblem.UnlinkedPlayers` dall'export: il file si esporta con
  i soli nomi del tabellone ("A", "B", "C", "D" o quelli scelti dall'utente).
- Nel file `padelPlayerId` si **omette**. La dashboard fa scegliere i giocatori
  a chi importa, **nell'ordine di servizio** del file.
- Resta essenziale `config.serveOrder`: oggi nasce da `refreshServeOrder()`
  (primo della squadra 1, primo della squadra 2, secondo della 1, secondo della
  2). È quello che permette alla dashboard di dire "1° al servizio".

## 1. Dove mettere il codice

- **`:core`**, Kotlin puro, accanto a `MatchExporter`: un `MatchStats` che
  prende `MatchEngine` (per la stessa ragione di `MatchExporter.build`: il
  motore garantisce `state == log.fold(initial)`) e restituisce un oggetto di
  sole statistiche. Niente Android, niente dipendenze nuove, test JUnit.
- **`:mobile`**: una schermata "Cronaca" raggiungibile dallo storico partite.
  Richiede che la partita chiusa conservi `serveOrder` e l'ora di inizio
  (richiesta 1 di `SCOREBOARD_FORMAT.md` §4): senza, servizio e tempi restano
  vuoti e la schermata lo deve dire.

Il replay esiste già: `MatchExporter.replay` rigioca il log con
`RacketRules` e ricava il servitore dallo stato **prima** del punto. Le
statistiche si calcolano nella stessa passata.

## 2. Cosa calcolare (definizioni della dashboard)

Per ogni punto, dallo stato **prima** del punto, si prova a dare il punto a
ciascuno dei due lati (`rules.apply(state, Point(1))`, `Point(2)`) e si guarda
che cosa cambierebbe. Da lì vengono tutte le definizioni "in palio":

| Voce | Definizione |
|---|---|
| **Punto decisivo** | Modalità punti, fuori dal tie-break, e **entrambi** i lati vincerebbero il game con questo punto (golden point sul 40-40, killer point sul secondo 40-40). Con `ADVANTAGE` non esiste. |
| **Palla break** | Modalità punti, fuori dal tie-break, servitore noto, e il lato che **riceve** vincerebbe il game con questo punto. Convertita se il ricevitore lo vince e il game si chiude. |
| **Set point per X** | X vincendo questo punto chiuderebbe il set. |
| **Match point per X** | X vincendo questo punto chiuderebbe la partita. |
| **Set point annullato da Y** | Y vince un punto che era set point (non match point) per l'avversario. |
| **Match point annullato da Y** | Y vince un punto che era match point per l'avversario. |
| **Game tenuto / break** | Solo game normali (non tie-break) con servitore noto: tenuto se lo vince il lato di chi serviva il **primo** punto del game. |
| **Servizio per giocatore** | Punti giocati e vinti al servizio (tie-break esclusi), game serviti e tenuti. |
| **Striscia** | Il massimo di punti consecutivi vinti da un lato, con il set in cui è successo. |
| **Rimonta nel set** | Per il vincitore del set, il massimo svantaggio in game (almeno 2) dopo un game qualsiasi del set: "da 2-5 a 7-5". |
| **Rimonta nella partita** | Partita finita, più di un set, il vincitore ha perso il primo set. |
| **Andamento** | Per ogni punto, punti vinti lato 1 meno punti vinti lato 2, cumulati. |
| **Tempi** | Solo se tutti i punti hanno `atMillis`. Durata = ultimo meno primo. Set: dalla fine del set precedente alla fine di questo. **Game: dal primo all'ultimo punto del game** (le pause di cambio campo non sono gioco). Punto medio = media degli intervalli fra punti consecutivi. |

Momenti chiave, in quest'ordine, e solo se c'è il dato: match point annullati,
partita ribaltata, rimonte nei set, tie-break ("Tie-break del 1° set a X,
7-5"), strisce di almeno 5 punti, set point annullati, punti decisivi (se
almeno 2 giocati).

## 3. Valori attesi su una partita di esempio

La partita è in `tests/fixtures/scoreboard/v1-tre-set.json` nel repository
della dashboard. È generata da un copione game per game
(`tests/fixtures/scoreboard/genera.mjs`) con il servizio di `RacketRules`.
Vale anche come test differenziale: 189 punti, `serveOrder [1,2,3,4]`, lato 1
= localId 1 e 3.

| Voce | Atteso |
|---|---|
| Set | 7-6 (tie-break 7-5), 3-6, 7-5; vince il lato 1 |
| Game totali | 17-17 |
| Game serviti / tenuti | lato 1: 16 / 10 · lato 2: 17 / 11 (33 game, 21 tenuti) |
| Break convertiti | 6 per parte |
| Punti decisivi | 5 giocati, vinti 3 dal lato 1 e 2 dal lato 2 |
| Match point annullati | lato 1: **2** (sul 2-5 del terzo set: 40-30 e poi 40-40) · lato 2: 0 |
| Set point annullati | 0 e 0 |
| Rimonte | terzo set, lato 1 da 2-5 a 7-5 |
| Partita ribaltata | no |

Anche il file vero dell'app (`esempi/export_padel_partita_interrotta.json`,
qui `app-reale-interrotta.json`) va tenuto nei test. Deve dare 2-1 nei game,
15-0 nel game in corso, e servitori 1, 4, 2 e poi 3 sul game in corso.

## 4. La schermata (come nella dashboard)

In quest'ordine, ciascuna sezione con il suo stato vuoto onesto:

1. **Tabellone**: coppie, set con i punti del tie-break in apice, badge
   "Interrotta" se non è finita; sotto, punti vinti e game totali.
2. **Andamento**: linea dei punti di vantaggio, sopra lo zero lato 1 e sotto
   lato 2, linee tratteggiate a fine set.
3. **Game per game**: per set, una cella per game col punteggio dopo il game,
   colore di chi l'ha vinto, "B" sui break, "TB 7-5" sul tie-break.
4. **Servizio**: tabella per giocatore (punti vinti al servizio e %, game
   tenuti) e palle break convertite per coppia. Senza ordine di servizio:
   "Il tabellone non sapeva chi serviva".
5. **Tempi**: durata, per set, tempo fra un punto e l'altro, game medio, game
   più lungo. Senza tempi: "Il tabellone non ha registrato i tempi".
6. **Momenti chiave**: le frasi della §2.

Sull'orologio non serve niente: la cronaca si guarda dopo, sul telefono.

## 5. Più avanti: invio diretto senza numeri Padel Elite

Con il numero Padel Elite tolto, l'invio diretto (I5) non può scegliere i
giocatori dal telefono, né deve farlo. Il disegno naturale è una **casella
d'arrivo**:
- l'app, con un login opzionale, carica il file in una coda del gruppo;
- un admin lo apre dalla dashboard e sceglie i giocatori nell'ordine di
  servizio, come per l'import da file.

L'app non vede mai i dati del gruppo. Da progettare quando servirà.
