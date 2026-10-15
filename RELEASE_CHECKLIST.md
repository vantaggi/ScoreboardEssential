# Release Checklist - ScoreboardEssential

Use this checklist before every release to ensure quality and compliance.

## 1. Versioning
- [ ] **Mobile Module** (`mobile/build.gradle`): increment `versionCode`; update `versionName`.
- [ ] **Wear Module** (`wear/build.gradle`): increment `versionCode` keeping the `2000+` offset
      (e.g. mobile `2` → wear `2002`) so the two bundles never collide; set `versionName` to match
      mobile.
- [ ] **Database**: if the Room schema changed, bump the version in `AppDatabase`, add a
      `Migration`, and add a migration test.
- [ ] **Gradle sync** succeeds.

## 2. Code & Security
- [ ] **ProGuard**: confirm `mobile/proguard-rules.pro` still keeps Room entities, `shared.**`,
      domain models, Parcelable CREATORs, ViewModels, binding classes and
      `WearableListenerService` subclasses. (Reference: `proguard-rules-recommendation.pro`.)
- [ ] **Lint**: `./gradlew lint` — review and address warnings.
- [ ] **Unit tests**: `./gradlew test` — all green.
- [ ] **Sensitive data**: no API keys/secrets hardcoded; `keystore.properties` is git-ignored and
      not committed; release logging does not leak game/user data.

## 3. Build & Signing
- [ ] **Keystore**: create a git-ignored `keystore.properties` at the repo root with
      `storeFile`, `storePassword`, `keyAlias`, `keyPassword` (signing activates automatically).
- [ ] **Padel Elite built in** (owner's decision, October 15, 2026: the Play build includes the
      feature): `local.properties` at the repo root (git-ignored) has `PADEL_ELITE_SUPABASE_URL`
      (`https://<project>.supabase.co`) and `PADEL_ELITE_SUPABASE_KEY` (the **publishable** key,
      never the service-role key). Without them the bundle builds anyway, with the feature off.
- [ ] **Build bundles**:
  - `./gradlew :mobile:bundleRelease` → `mobile/build/outputs/bundle/release/mobile-release.aab`
  - `./gradlew :wear:bundleRelease` → `wear/build/outputs/bundle/release/wear-release.aab`
- [ ] **Verify R8**: `./gradlew :mobile:assembleRelease` (minify on) completes without
      missing-class or keep-rule errors.

## 4. Manual Verification (Release Build)
- [ ] **Install** the release build on a physical phone and watch.
- [ ] **Permissions**: `POST_NOTIFICATIONS` prompt appears and works; sharing/export works.
- [ ] **Wear sync** (both directions):
  - [ ] Start a match on the phone; the watch reflects it.
  - [ ] Score updates reflect on both devices instantly.
  - [ ] Match timer and goalkeeper timer stay in sync.
  - [ ] Team names **and colors** sync.
  - [ ] Scoring on the watch prompts scorer selection and the goal is attributed on the phone.
- [ ] **Offline mode**: full functionality with no network.
- [ ] **Padel Elite present**: Settings shows the "Padel Elite" card; sign in with a real account
      lists only that account's groups (if the card is missing, the bundle was built without the
      two values: rebuild).

## 5. Play Store Assets & Listing
- [ ] **Privacy Policy**: host `PRIVACY_POLICY.md` at a public URL and keep it current in the
      Play Console.
- [ ] **Screenshots**: phone screenshots; circular Wear OS screenshots (required for Wear
      distribution).
- [ ] **Feature graphic**: 1024×500.
- [ ] **What's New**: update from `CHANGELOG.md`.
- [ ] **Foreground service declaration (Padel Elite live, `dataSync`)**: in the Play Console, App content >
      Foreground service permissions, declare `FOREGROUND_SERVICE_DATA_SYNC` for `DirettaService`.
      Use the description, the impact-if-interrupted text and the required video from `DESIGN.md`
      ("Diretta, i limiti - D-3", "Nota per la Play Console"); the data it names (the match file:
      players' names and point by point) is the same as in the Data safety answers (section 6).
      The video shows: live switch on, the ongoing notification, app sent to the background,
      "Interrompi diretta" tapped.
- [ ] **Release note (D-3)**: whoever chose their Padel Elite group before the live broadcast shipped now sees the
      switch after the first launch (the role is re-read once per launch).

## 6. Play Console: Sicurezza dei dati (Data safety)

Answers for App content > Data safety. They must match `PRIVACY_POLICY.md`; every answer comes from
the code (`mobile/.../padelelite/`, manifest, `MatchExport.kt`). **Draft, to be reviewed by the owner.**

- [ ] **Which build?** The Play build includes Padel Elite (owner's decision, October 15, 2026;
      section 3): the answers below apply. Only a bundle built without `PADEL_ELITE_SUPABASE_URL`
      and `PADEL_ELITE_SUPABASE_KEY` would answer **No** to data collection.
- [ ] **Data collection and security**
  - Does your app collect or share any of the required user data types? **Yes.**
  - Is all of the user data collected by your app encrypted in transit? **Yes.** Only HTTPS to the
    Supabase project (no `usesCleartextTraffic` or `networkSecurityConfig`, `targetSdk` 36: Android
    blocks cleartext). Keep the configured URL `https://`.
  - Do you provide a way for users to request that their data is deleted? **Yes:** by email to
    the contact in the privacy policy (sections 2.7 and 7); on the phone, sign out / uninstall.
  - Account creation: the app **does not create accounts** (the Padel Elite account is created in
    the dashboard; the app only signs in). Confirm the matching option in the form and whether a
    deletion URL is still requested.
- [ ] **Data types** (everything not listed: **not collected**; "collected" here means it leaves
      the phone, local data is not declared)

| Type (Play) | Collected | Shared | Processed ephemerally | Required or optional | Purposes | What it is in the app |
|---|---|---|---|---|---|---|
| Personal info > **Email address** | Yes | No | No | Optional | App functionality, Account management | Sent to Supabase Auth at sign-in; returned with the session |
| Personal info > **User IDs** (proposed) | Yes | No | No | Optional | App functionality, Account management | The account ID of the session, sent as a filter when reading the user's groups |
| App activity > **Other user-generated content** | Yes | No | No | Optional | App functionality | The match file: players' names as typed, sides, linked Padel Elite player IDs, rules, score, sets, winner, start time with offset, point by point; sent on "Send to Padel Elite", "Send again", the end-of-match checkbox, and after every point with "Broadcast live" |

  - **Shared: No.** The match reaches the group because the user sends it there (user-initiated
    transfer the user expects), and Supabase is the service provider that hosts the database.
  - **Not collected:** location, financial info, health, messages, photos/videos, audio, files,
    calendar, contacts, app interactions, search history, installed apps, web browsing, crash logs,
    diagnostics, performance, device or other IDs. The app has no ads, analytics or crash-reporting
    libraries; system-log messages stay on the device.
  - The password goes to Supabase Auth over HTTPS and is not stored; Play has no data type for it.
- [ ] **Coherence:** the hosted privacy policy is the current `PRIVACY_POLICY.md`; the store
      listing (`STORE_LISTING.md`, "Offline First") matches it; the `dataSync` declaration
      (section 5) describes the same data.

## 7. Upload
- [ ] Upload the mobile App Bundle to the Production/Beta track.
- [ ] Upload the wear App Bundle to the Production/Beta track (correct form-factor settings).
- [ ] Submit for review.
