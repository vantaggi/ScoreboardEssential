# Release Checklist - ScoreboardEssential

Use this checklist before every release to ensure quality and compliance.

## 1. Versioning
- [ ] **Mobile Module**: Open `mobile/build.gradle`.
  - [ ] Increment `versionCode` (e.g., 1 -> 2).
  - [ ] Update `versionName` (e.g., "1.0" -> "1.1").
- [ ] **Wear Module**: Open `wear/build.gradle`.
  - [ ] Increment `versionCode` (must be unique, consider using a scheme like 2000+versionCode to avoid conflicts with mobile).
  - [ ] Update `versionName` to match mobile.
- [ ] **Sync Gradle**: Ensure project syncs successfully.

## 2. Code & Security
- [ ] **ProGuard Rules**: Verify `proguard-rules.pro` includes rules from `proguard-rules-recommendation.pro`.
- [ ] **Lint Check**: Run `./gradlew lint` to catch potential issues.
- [ ] **Unit Tests**: Run `./gradlew test` to ensure no regressions.
- [ ] **Sensitive Data**: Verify no API keys or secrets are hardcoded in the codebase.

## 3. Build & Signing
- [ ] **Keystore**: Ensure you have the release Keystore file (`*.jks` or `*.keystore`) and passwords.
- [ ] **Build Bundle**:
  - Run `./gradlew :mobile:bundleRelease` (generates `.aab` for phone).
  - Run `./gradlew :wear:bundleRelease` (generates `.aab` for watch).
  - *Note: If distributing as a multi-APK or single bundle, ensure the configuration matches Play Store requirements.*

## 4. Manual Verification (Release Build)
- [ ] **Install Release Build**: Install the release APK/Bundle on a physical phone and watch.
  - `adb install -r mobile/release/app-release.apk`
- [ ] **Permissions**:
  - [ ] Verify `POST_NOTIFICATIONS` prompt appears and works.
  - [ ] Verify `WRITE_EXTERNAL_STORAGE` works for exports (if applicable).
- [ ] **Wear Sync**:
  - [ ] Start a match on Mobile.
  - [ ] Verify Watch automatically opens/updates.
  - [ ] Verify Score updates reflect on both devices instantly.
  - [ ] Verify Timer syncs correctly.
  - [ ] Verify Player names sync.
- [ ] **Offline Mode**: Test app functionality without internet (should work 100%).

## 5. Play Store Assets & Listing
- [ ] **Privacy Policy**: Update the hosted Privacy Policy URL if `PRIVACY_POLICY.md` has changed.
- [ ] **Screenshots**:
  - [ ] Mobile: Phone screenshots (16:9 or similar).
  - [ ] Wear OS: Circular screenshots (required for Wear OS distribution).
- [ ] **Feature Graphic**: 1024x500 promo image.
- [ ] **Description**: Update "What's New" text.

## 6. Upload
- [ ] Upload Mobile App Bundle to Production/Beta track.
- [ ] Upload Wear App Bundle to Production/Beta track (using the correct form factor settings in Play Console).
- [ ] Submit for Review.
