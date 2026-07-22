# Privacy notes

Last updated: 2026-07-22

Adaptive Remote does not operate a project-owned server and does not include analytics or advertising SDKs. The current alpha stores settings, saved patterns, the selected Bluetooth device, and an encrypted user-supplied xAI API key locally on the Android device. Android backup is disabled for application data.

## Permissions

- **Nearby devices / Bluetooth:** scans for and connects to compatible BLE hardware.
- **Notifications and foreground services:** keeps an active device or capture session visible and provides Stop actions.
- **Display over other apps:** shows movable status and Stop controls during continuous modes.
- **Accessibility:** when the user explicitly enables the service, reads visible text so Text mode can capture it.
- **Screen capture:** when the user approves Android's media-projection dialog, samples screen frames for Video mode.
- **Internet:** sends optional AI requests directly to xAI.

## Data sent off-device

Text mode sends captured visible text. Video mode sends compressed sampled screen frames. Procedural mode sends generation instructions and session summaries. These requests go directly from the app to xAI with the user's API key and request `store: false`. Users should review xAI's current privacy terms before use; this project cannot control provider-side logging, processing, or retention.

Do not capture private messages, credentials, financial data, health information, another person's content, or any screen you are not authorized to process. Stop a capture session before switching to sensitive content.

## Deleting local data

Clearing the app's storage or uninstalling the app deletes its local preferences and invalidates/removes access to its Android Keystore-protected API-key material. The app does not provide a way to delete data already processed by xAI; use the provider's account and privacy controls for that.

## Scope

This file describes the repository's current alpha behavior and is not a substitute for a store-ready legal privacy policy. Review it whenever permissions, network providers, storage, analytics, crash reporting, or distribution change.
