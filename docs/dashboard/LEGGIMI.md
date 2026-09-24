# Documenti della dashboard di Padel Elite, copiati qui

Copie in sola lettura, prese il 24 settembre 2026 dal branch `claude/trusting-maxwell-grovzo` del
repository `vantaggi/padel-dashboard` (commit `234c340`), perche' chi lavora su quest'app li abbia
sotto mano senza accedere all'altro progetto. **La fonte di verita' resta la loro**: il formato
del file cambia prima in `docs/SCOREBOARD_FORMAT.md` di quel repository, poi qui.

| File | Cosa contiene |
|---|---|
| `SCOREBOARD_FORMAT.md` | Il contratto del file esportato (v1 oggi, v2 richiesta) e cosa ne fa la dashboard. La sezione 4 elenca le richieste all'app. |
| `SCOREBOARD_CRONACA_APP.md` | Il brief per portare la Cronaca nell'app: definizioni, valori attesi, schermate. |
| `genera-fixture.mjs` | Il copione che ha generato la partita a tre set di prova. |

Le partite di prova stanno in `core/src/test/resources/scoreboard/`: `v1-tre-set.json` (189 punti,
valori attesi nel brief, sezione 3) e `app-reale-interrotta.json`, identica al nostro
`esempi/export_padel_partita_interrotta.json`.
