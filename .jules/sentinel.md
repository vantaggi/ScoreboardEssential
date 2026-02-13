## 2024-05-23 - [Privacy] Disable ADB Backup
**Vulnerability:** The application had `android:allowBackup="true"` in its AndroidManifest.xml files.
**Learning:** This setting allows anyone with physical access to the device (and USB debugging enabled) to extract the application's data (including the Room database with match history and players) using `adb backup`. While the data sensitivity is low for a scoreboard app, it's a privacy best practice to disable it unless a cloud backup mechanism or transfer feature is explicitly implemented.
**Prevention:** Always set `android:allowBackup="false"` in the `<application>` tag of the manifest unless there is a specific requirement to support ADB backup.

## 2024-05-25 - [Logging] Sensitive Data Exposure in Logs
**Vulnerability:** The application was logging raw game state values (team scores and timer state) in `SimplifiedDataLayerListenerService.kt` using `Log.d` with `String` interpolation (e.g., `Log.d(TAG, "T1=$team1, T2=$team2")`).
**Learning:** Even though scores are not strictly PII, logging precise game state can leak information in production logs, which violates security best practices regarding data minimization. Logs should be used for tracing flow, not for dumping data values unless absolutely necessary for debugging in a controlled environment.
**Prevention:** Remove variable interpolation from log messages in production code. Use generic messages like `"Broadcasted score update"` instead of `"Broadcasted score update: T1=10, T2=5"`.
