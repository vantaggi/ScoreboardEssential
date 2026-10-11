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
      ("Diretta, i limiti - D-3", "Nota per la Play Console"). The video shows: live switch on, the ongoing
      notification, app sent to the background, "Interrompi diretta" tapped.
- [ ] **Release note (D-3)**: whoever chose their Padel Elite group before the live broadcast shipped now sees the
      switch after the first launch (the role is re-read once per launch).

## 6. Upload
- [ ] Upload the mobile App Bundle to the Production/Beta track.
- [ ] Upload the wear App Bundle to the Production/Beta track (correct form-factor settings).
- [ ] Submit for review.
