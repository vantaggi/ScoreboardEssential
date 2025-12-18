## 2024-05-23 - [Privacy] Disable ADB Backup
**Vulnerability:** The application had `android:allowBackup="true"` in its AndroidManifest.xml files.
**Learning:** This setting allows anyone with physical access to the device (and USB debugging enabled) to extract the application's data (including the Room database with match history and players) using `adb backup`. While the data sensitivity is low for a scoreboard app, it's a privacy best practice to disable it unless a cloud backup mechanism or transfer feature is explicitly implemented.
**Prevention:** Always set `android:allowBackup="false"` in the `<application>` tag of the manifest unless there is a specific requirement to support ADB backup.
