# Misura dell'adozione della UI Constitution

Prima misura: 31 ottobre 2026, sul branch `claude/project-thread-d9rjng` (da `main` 6a86049) dopo l'installazione (PR 1). E' il passo 1 di "Adopting in an existing project" (02-integration) e i controlli di 09-checks: i numeri sono la barra di avanzamento dell'adozione. Nessuna PR deve farne salire uno; quando uno arriva a zero il suo controllo in CI diventa bloccante (passo g del piano).

Come si rifa':

```bash
./gradlew :wear:testDebugUnitTest --tests '*GeometriaDelPolsoTest'   # geometria del quadrante
python3 scripts/misura-ui.py               # la tabella
python3 scripts/misura-ui.py --dettaglio   # con ogni riga trovata
```

In CI lo script gira dopo i test unitari (passo 6b di "Android CI/CD") e scrive la tabella nel riepilogo della run, come avviso: esce sempre con 0.

## Conteggi

| Controllo | Telefono | Orologio | Che cosa conta |
| --- | ---: | ---: | --- |
| Colori scritti a mano | 0 | 0 | un valore esadecimale fuori dai file dei token, o un colore costruito nel codice |
| Corpi del testo scritti a mano | 0 | 0 | un `textSize` in un layout invece di uno stile di testo |
| Raggi scritti a mano | 0 | 0 | un raggio in un layout o in un disegno senza `@dimen` |
| Ombre scritte a mano | 0 | 0 | un'elevazione diversa da 0 senza `@dimen` |
| Spaziature scritte a mano | 0 | 0 | un margine o un padding diverso da 0 senza `@dimen` |
| Durate scritte a mano | 0 | 0 | una durata senza `@integer` |
| Durate scritte a mano nel codice | 0 | 0 | una durata data a un'animazione senza `R.integer` |
| Nomi diversi per lo stesso colore | 0 | 0 | lo stesso ruolo con lo stesso valore nello stesso tema sotto due nomi |
| Nomi diversi fra telefono e orologio | 0 | 0 | un colore dell'orologio che sul telefono ha un altro nome o un altro valore |
| Valori di un tema citati al posto del ruolo | 0 | 0 | un `_standard`, `_chiaro` o `_alto_contrasto` citato fuori dai file dei colori e dei temi |
| Stringhe senza traduzione italiana | 0 | 0 | una stringa in `values/` che `values-it/` non ha |
| Testi scritti nel layout o nel codice | 0 | 0 | un testo a schermo che non passa dalle risorse |
| Identificativi che possono finire a schermo | 0 | 0 | "Team 1" come ripiego di un nome, o "Team" in una stringa italiana |
| Geometria del quadrante | | 0 | elementi sotto 3dp dall'anello del portiere o sotto 8dp dalla cornice |

## Che cosa c'e' dietro i numeri

Dal 31 ottobre 2026 (PR 4 per l'orologio, PR 5 per il telefono) ogni conteggio e' a zero. Che cosa c'era e dove e' andato:

- **Colori (2).** I due velini della barra di navigazione in `utils/Aspetto.kt`: ora `scrim_barre_luminose` e `scrim_barre_scure` (non `_chiaro` e `_scuro`: la misura legge quei suffissi come valori di un tema).
- **Corpi del testo (27 + 13).** Sul telefono le cifre e le etichette del tabellone (in dp, negli stili `TextAppearance.App.Game.*.Fissa`), poi storico, statistiche, formazione e PDF; sull'orologio i titoli e le righe dei menu (`TextAppearance.Lista.*`).
- **Spaziature (40 + 7).** `space_*` dove il valore e' sulla scala; 2dp e 6dp del telefono hanno un nome (`aria_fine`, `aria_tabellone`) e restano un debito dichiarato in `DESIGN.md` (PR 5).
- **Nomi doppi (5).** La carta del PDF (`*_print`) ora e' un alias dei valori del tema chiaro.
- **Testi scritti a mano (1 + 2).** "Event description" (testo di prova, ora `tools:text`) e la "K" del portiere (`wear_keeper_idle`).
- **Identificativi (14 + 4).** I ripieghi "Team 1" e "Team 2" a schermo passano da `label_team_1/2` sul telefono e da `cd_team_fallback` sull'orologio. Il predefinito salvato in `MatchSettingsRepository` resta "Team 1" nei dati (il segnale "nessun nome scelto") e lo script non lo conta: a schermo passa da `EtichettaSquadra`.

## Geometria del quadrante

Dalla PR 2 (31 ottobre 2026) e' un test che fallisce: `GeometriaDelPolsoTest` (Robolectric, grafica nativa) misura l'inchiostro vero dei testi e i segni, in calcio col portiere che corre (anche con CHI?) e in padel con due pallini per lato, attivo e ambient, e prova la riga di stato con ogni frase che puo' dire. Regole di 08-wearable: almeno 3dp dall'anello quando si disegna, almeno 8dp dalla cornice (4dp di burn-in piu' 4 d'aria).

Forme: tondo da 192 e 227dp in italiano e in inglese, quadrato da 180, 192 e 227dp. Oggi 0 fuori misura su 87 misure per forma.

La prima misura (PR 1) aveva 4 fuori misura sul tondo da 192dp, tutti la riga di stato "Scollegato · ora" nel padel. Provando ogni frase ne sono uscite altre sul 192 e sul 227 e la capsula "Who?": l'elenco e la scelta stanno in `DESIGN.md`, sezione "PR 2: geometria dell'orologio".

## Dove si chiude ogni conteggio

| Conteggio | PR del piano |
| --- | --- |
| Geometria del quadrante | 2 (geometria dell'orologio): a zero e bloccante |
| Corpi del testo e spaziature dell'orologio | 4 (menu dell'orologio): a zero |
| Corpi del testo e spaziature del telefono, testi a mano, identificativi | 5 (telefono): a zero |
| Colori a mano, nomi doppi | 5: a zero |
| Ogni conteggio a zero | 10 (controlli bloccanti) |
