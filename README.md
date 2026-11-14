# Whats-Krdrx

Android companion utility for WhatsApp. This app does not modify WhatsApp or access its private database.

## V1.0 foundation
- WhatsApp-inspired green identity.
- Dark mode by default and one-tap Light/Dark toggle.
- Theme preference persisted locally.
- Home dashboard and navigation placeholders for planned modules.
- Android 11+ (API 30+).

## Build
GitHub Actions builds a debug APK on every push to `main` and on manual dispatch. Download the APK from the workflow run's Artifacts section.

Package: `com.whats.krdrx`

## Privacy and limitations
Future notification archive features will require explicit Android notification access and can only record information exposed by notifications. The app will not read WhatsApp's private database, intercept message traffic, or automate message sending.
