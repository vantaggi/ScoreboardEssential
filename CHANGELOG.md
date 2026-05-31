# Changelog

All notable changes to ScoreboardEssential are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0] - 2026-05-31

First public release.

### Added
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

### Changed
- Win rate is now computed from real match results: `MatchPlayerCrossRef` records each player's
  team affiliation (database schema v11), replacing the previous placeholder value of 0.
- Consolidated the data-access layer to a lean MVVM pattern (ViewModel → Repository), removing the
  unused use-case wrappers.

### Fixed
- Team-color updates now use a shared `KEY_TEAM_COLOR` constant on both the sending and receiving
  sides instead of an ad-hoc magic string.
- The watch's scorer selection now targets the correct message path and is handled on the phone
  (previously it was sent to an unhandled path and silently dropped).
- The Wear Data Layer listeners no longer crash on a single malformed data item; parsing is
  wrapped in error handling.
- Out-of-range scores are validated before being sent to the watch.
- ViewModels release their Wearable sync resources in `onCleared()`.

### Removed
- Dead code: the orphaned `DataSyncObject`, unused domain use cases, a duplicate
  `GetPlayerStatsUseCase` test, placeholder example tests, and unused protocol constants.
