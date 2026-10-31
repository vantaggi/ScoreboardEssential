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
| Colori scritti a mano | 2 | 0 | un valore esadecimale fuori dai file dei token, o un colore costruito nel codice |
| Corpi del testo scritti a mano | 27 | 0 | un `textSize` in un layout invece di uno stile di testo |
| Raggi scritti a mano | 0 | 0 | un raggio in un layout o in un disegno senza `@dimen` |
| Ombre scritte a mano | 0 | 0 | un'elevazione diversa da 0 senza `@dimen` |
| Spaziature scritte a mano | 40 | 0 | un margine o un padding diverso da 0 senza `@dimen` |
| Durate scritte a mano | 0 | 0 | una durata senza `@integer` |
| Durate scritte a mano nel codice | 0 | 0 | una durata data a un'animazione senza `R.integer` |
| Nomi diversi per lo stesso colore | 5 | 0 | lo stesso ruolo con lo stesso valore nello stesso tema sotto due nomi |
| Nomi diversi fra telefono e orologio | 0 | 0 | un colore dell'orologio che sul telefono ha un altro nome o un altro valore |
| Valori di un tema citati al posto del ruolo | 0 | 0 | un `_standard`, `_chiaro` o `_alto_contrasto` citato fuori dai file dei colori e dei temi |
| Stringhe senza traduzione italiana | 0 | 0 | una stringa in `values/` che `values-it/` non ha |
| Testi scritti nel layout o nel codice | 1 | 0 | un testo a schermo che non passa dalle risorse |
| Identificativi che possono finire a schermo | 14 | 0 | "Team 1" come ripiego di un nome, o "Team" in una stringa italiana |
| Geometria del quadrante | | 0 | elementi sotto 3dp dall'anello del portiere o sotto 8dp dalla cornice |

## Che cosa c'e' dietro i numeri

- **Colori (2).** I due veli della barra di navigazione in `utils/Aspetto.kt`, copiati da `enableEdgeToEdge()` della piattaforma: diventano token o restano come eccezione dichiarata.
- **Corpi del testo (27 + 0).** Sul telefono 12 sono le cifre e le etichette della schermata di gioco (`content_scoreboard_live.xml`, in dp: la Constitution ammette misure fisse sui tabelloni, ma vanno nominate come token), gli altri sono righe dello storico, statistiche, formazione e PDF. Sull'orologio erano i titoli e le righe dei menu: dalla PR 4 sono cinque stili di testo (`TextAppearance.Lista.*`).
- **Spaziature (40 + 0).** 26 sono nella schermata di gioco; il resto in righe dello storico e del registro. Sull'orologio erano 7: la PR 2 ha dato un token all'aria della capsula CHI?, la PR 4 agli altri.
- **Nomi doppi (5).** La carta del PDF (`*_print`) ha gli stessi valori del tema chiaro (`*_chiaro`) per cinque ruoli: fondo, gruppo, linea, testo primario e secondario. Un nome solo, oppure la carta dichiarata come ruolo a se'.
- **Fra telefono e orologio (0).** Da questa PR ogni colore ha il nome del suo ruolo, uguale sui due moduli (scelta B del proprietario, 31 ottobre 2026); prima l'orologio citava `ink_black`, `ink_white` ed `elite_lime` dove il telefono citava il ruolo.
- **Testi scritti a mano (1 + 0).** "Event description" in `match_event_item.xml` (testo di prova del layout, sostituito dal codice); sull'orologio la "K" del portiere, che dalla PR 4 passa da `wear_keeper_idle`.
- **Identificativi (14 + 0).** "Team 1" e "Team 2" come ripiego quando manca il nome della squadra: telefono `MainActivity` (10), `MatchHistoryActivity` (2), `MatchSettingsRepository` (2, il predefinito salvato); sull'orologio erano 4 in `MainActivity` e `WearDataLayerService`, dalla PR 4 "Squadra 1" e "Squadra 2" (`cd_team_fallback`). `EtichettaSquadra` li traduce dove passa; dove non passa possono comparire.
- **Stringhe senza traduzione (0).** Ogni stringa traducibile di `values/` ha la sua in `values-it/`.

## Geometria del quadrante

Dalla PR 2 (31 ottobre 2026) e' un test che fallisce: `GeometriaDelPolsoTest` (Robolectric, grafica nativa) misura l'inchiostro vero dei testi e i segni, in calcio col portiere che corre (anche con CHI?) e in padel con due pallini per lato, attivo e ambient, e prova la riga di stato con ogni frase che puo' dire. Regole di 08-wearable: almeno 3dp dall'anello quando si disegna, almeno 8dp dalla cornice (4dp di burn-in piu' 4 d'aria).

Forme: tondo da 192 e 227dp in italiano e in inglese, quadrato da 180, 192 e 227dp. Oggi 0 fuori misura su 87 misure per forma.

La prima misura (PR 1) aveva 4 fuori misura sul tondo da 192dp, tutti la riga di stato "Scollegato · ora" nel padel. Provando ogni frase ne sono uscite altre sul 192 e sul 227 e la capsula "Who?": l'elenco e la scelta stanno in `DESIGN.md`, sezione "PR 2: geometria dell'orologio".

## Dove si chiude ogni conteggio

| Conteggio | PR del piano |
| --- | --- |
| Geometria del quadrante | 2 (geometria dell'orologio): a zero e bloccante |
| Corpi del testo e spaziature dell'orologio | 4 (menu dell'orologio): a zero |
| Corpi del testo e spaziature del telefono, testi a mano, identificativi | 5 (telefono: comandi, righe, foglio) |
| Colori a mano, nomi doppi | 5 o 8, secondo la schermata che li tocca |
| Ogni conteggio a zero | 10 (controlli bloccanti) |
