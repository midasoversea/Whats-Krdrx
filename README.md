# Whats-Krdrx

Android companion utility for WhatsApp. This app does not modify WhatsApp or access its private database.

## V1.1
- WhatsApp-inspired green identity, dark-first theme and persisted one-tap Light/Dark toggle.
- Status Downloader: the user selects the WhatsApp `.Statuses` folder through Android Storage Access Framework, lists image/video files and saves one or all to `Pictures/Whats-Krdrx`.
- Notification Archive: optional Android Notification Listener records text exposed by WhatsApp notifications in a local SQLite database. Includes refresh and clear-all controls.
- Android 11+ (API 30+), package `com.whats.krdrx`.

## Important limitations
- Status access requires the user to grant access to the `.Statuses` folder. Android/WhatsApp storage layout and device file pickers may differ.
- Notification Archive is not direct recovery of deleted WhatsApp chat messages. It cannot recover content not exposed in notifications, messages received before access was enabled, or media hidden from notification previews. Notification dismissal does not prove that a sender deleted a message.
- No WhatsApp APK modification, private database access, message interception, or automated message sending.

## Build
GitHub Actions builds a debug APK on every push to `main` and on manual dispatch. Download the APK from the workflow run's Artifacts section.
