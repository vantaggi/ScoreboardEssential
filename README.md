# ScoreboardEssential

ScoreboardEssential is a simple, intuitive scoreboard for amateur soccer matches, available for
Android phones and Wear OS watches. Track scores for two teams, run match and goalkeeper-rotation
timers, manage players and rosters, and keep everything synchronized between phone and watch in
real time. The project's goal is to "Professionalize the Passion" by giving amateur sports a
robust, dependable tool.

## Project Architecture

A multi-module Android project with a clean separation of concerns:

* **`mobile`** — the phone application (Android Views + View/Data Binding, `ViewModel` +
  `LiveData`, Room, a foreground `MatchTimerService`).
* **`wear`** — the Wear OS companion (Views + ViewBinding, `ViewModel` + `StateFlow`).
* **`shared`** — common code used by both apps: the Wearable Data Layer sync engine
  (`OptimizedWearDataSync`), the wire-protocol constants (`WearConstants`), the transport model
  (`PlayerData`), validation (`WearDataValidator`) and haptics (`HapticFeedbackManager`).

## Tech Stack

* **UI:** Android Views with View/Data Binding
* **State:** `ViewModel`, `LiveData` (mobile) and `StateFlow` (wear)
* **Database:** Room
* **Phone ↔ Watch:** Google Play Services Wearable Data Layer (DataItems + Messages)
* **Architecture:** lean MVVM (ViewModel → Repository → DAO)

## Features

* **Score management** — increase/decrease each team's score, with a one-tap **undo** for the
  last goal.
* **Custom team names and colors** — long-press a team card to pick its color.
* **Match timer** — start, pause and reset; runs in a foreground service so it survives
  backgrounding.
* **Goalkeeper timer** — a secondary countdown (e.g. for goalkeeper rotation) that alerts when it
  expires.
* **Player & roster management** — create players, assign roles, and build each team's roster.
* **Player roles** — roles are shown in the roster and the goal-scorer picker, and feed the
  formation view.
* **Match history & statistics** — finished matches are saved; a statistics screen shows top
  scorers, appearances and **win rate** (derived from each player's team affiliation per match).
* **Match report sharing** — export a match report (including a PDF) to share results.
* **Onboarding** — a first-run tutorial, shown once.
* **Two-way phone ↔ watch sync** — scores, team names, colors, both timers and match state stay in
  sync in both directions. Scoring on the watch prompts a **scorer selection** that is sent back to
  the phone and attributed to the player; the full roster is synced to the watch so the picker is
  populated.
* **Offline-first** — everything works without an internet connection; no data leaves the device.
* **Localization** — English (default) and Italian.

## Building

```bash
# Debug builds
./gradlew :mobile:assembleDebug :wear:assembleDebug

# Tests and lint
./gradlew test lint

# Release bundles (see Release signing below)
./gradlew :mobile:bundleRelease :wear:bundleRelease
```

### Release signing

Release builds are signed only if a git-ignored `keystore.properties` exists at the repo root:

```properties
storeFile=path/to/release.jks
storePassword=********
keyAlias=********
keyPassword=********
```

Without it, debug builds work normally and release builds are produced unsigned. The phone and
watch share an `applicationId` (`it.vantaggi.scoreboardessential`); the watch uses a `2000+`
`versionCode` offset so its bundle never collides with the phone's.

## Data & Privacy

All data is stored locally in a Room database; nothing is uploaded. Android auto-backup is
disabled (`allowBackup="false"`). See [PRIVACY_POLICY.md](PRIVACY_POLICY.md).
