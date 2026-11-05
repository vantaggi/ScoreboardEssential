# Changelog

All notable changes to ScoreboardEssential are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-11-05

Prima release pubblicata, come GitHub Release (non sugli store: il rilascio su Google Play e
Apple e' in pausa, vedi `docs/RILASCIO_STORE.md`). Raccoglie tutto il lavoro dal tag `0.0.1`
(agosto 2025) all'ultimo main del 5 novembre 2026, compresa la voce 1.0 del 31 maggio, preparata
ma mai uscita. Database Room alla versione 16; telefono `targetSdk 36`, orologio `targetSdk 35`.

### Sport e partita
- Tre sport: calcio, padel e tennis, con le regole in un modulo `core` condiviso.
- Padel e tennis: game, set e registro dei game; regola del 40-40 a scelta (punto secco,
  killer point predefinito, vantaggi), anche dall'orologio.
- Chi serve: si sceglie e si corregge dal telefono e dal polso. Le sigle delle due coppie
  restano sempre in vista, quella di chi batte in lime.
- Sigle delle coppie al posto di TEAM 1 / TEAM 2; nome predefinito della squadra nella lingua
  in uso.
- Cronaca della partita sul telefono; annullo del gol con due tasti dalle parole diverse.
- Portiere in minuti o spento, sul telefono e sull'orologio.
- Una partita 0-0 col solo tempo avviato si puo' chiudere o scartare.

### Serata a coppie che ruotano
- Serata con gironi e iscritti presi dalla dashboard, partite del girone come la dashboard,
  spareggio alla quarta e ordine di battuta.
- Annulla di 8 secondi; sull'orologio squadre in campo e prossima partita della serata.

### Padel Elite
- Accesso, anche con Google, dalla schermata Padel Elite.
- Invio della partita all'account Padel Elite, con "Invia di nuovo" e invio dallo storico.
- Rose e giocatori della dashboard nella scelta della partita; tutti i giocatori da collegare
  in vista, la ricerca li stringe.
- Diretta della partita per gli admin, con servizio in primo piano e i suoi limiti
  (pendenti all'avvio, avviso dopo 6 ore, gruppo perso).

### Orologio
- Quadrante a fasce con la coppia di cifre al centro, tondo e quadrato; menu ridisegnato,
  liste a tutta larghezza, aptica.
- Conto del portiere in ambient, voce Schermo acceso, icona della partita in corso sul
  quadrante (Ongoing Activity).
- Con il telefono in tasca l'orologio rivede il punteggio (motore nel processo e servizio in
  primo piano).
- Fine partita dal polso, anche per uno 0-0 col cronometro avviato.

### Telefono e interfaccia
- Nuovo aspetto secondo la UI Constitution: token di colore per ruolo, tre temi (scuro, chiaro,
  alto contrasto), Inter con numeri tabulari, icone Material Symbols, transizioni e NumberRoll
  sui punteggi, tavolozza di squadra.
- Home rivista: nomi lunghi su due righe, striscia del padel che tiene il punteggio, testo
  grande fino al 200%.
- Storico per giorno, col nome della squadra e l'ora d'inizio; Giocatori per sport;
  Statistiche per sport con la scheda del giocatore divisa per sport.
- Impostazioni a righe; foglio Partita (prima la partita, poi l'app).
- Report PDF su carta chiara.

### Affidabilita'
- Sincronizzazione telefono-orologio piu' solida: protocollo con identita' della partita,
  stato pubblicato in ordine e con versione, coda dei tocchi rifiutati, rinvio e invii in serie.
- Molti test nuovi su `core`, `shared`, telefono e orologio; CI con ktlint, lint e build
  release, e controllo dei valori scritti a mano nell'interfaccia.

### Gia' nella voce 1.0 del 31 maggio 2026 (mai pubblicata)

#### Added
- Score tracking for two teams, with one-tap undo of the last goal.
- Custom team names and per-team colors (long-press a team card to choose a color).
- Match timer backed by a foreground service, plus a goalkeeper-rotation countdown timer
  with an expiry alert.
- Player and roster management, including player roles surfaced in the roster and the
  goal-scorer picker.
- Match history and a statistics screen (top scorers, appearances, win rate).
- Match report sharing, including PDF export.
- First-run onboarding tutorial (shown once).
- Two-way phone ↔ watch synchronization of scores, team names, colors, both timers and match
  state over the Wearable Data Layer.
- Watch-side goal scoring with scorer selection sent back to the phone and attributed to the
  player.
- Full roster sync to the watch so the scorer picker is populated.
- English and Italian localization.

#### Changed
- Win rate is now computed from real match results: `MatchPlayerCrossRef` records each player's
  team affiliation (database schema v11), replacing the previous placeholder value of 0.
- Consolidated the data-access layer to a lean MVVM pattern (ViewModel → Repository), removing the
  unused use-case wrappers.

#### Fixed
- Team-color updates now use a shared `KEY_TEAM_COLOR` constant on both the sending and receiving
  sides instead of an ad-hoc magic string.
- The watch's scorer selection now targets the correct message path and is handled on the phone
  (previously it was sent to an unhandled path and silently dropped).
- The Wear Data Layer listeners no longer crash on a single malformed data item; parsing is
  wrapped in error handling.
- Out-of-range scores are validated before being sent to the watch.
- ViewModels release their Wearable sync resources in `onCleared()`.

#### Removed
- Dead code: the orphaned `DataSyncObject`, unused domain use cases, a duplicate
  `GetPlayerStatsUseCase` test, placeholder example tests, and unused protocol constants.
