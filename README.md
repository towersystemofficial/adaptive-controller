# Adaptive Remote

Adaptive Remote is an experimental Android controller for compatible JoyHub Bluetooth Low Energy devices. It supports direct output, reusable patterns, and optional AI-assisted Text, Video, and Procedural modes.

> [!WARNING]
> This project is an early public development preview, not a certified medical or safety device. Only the Cafatop Knight advertised as `J-Mars`, using the JoyHub `FFA0`/`FFA1` transport, has been physically validated. Keep a non-software way to stop or remove connected hardware available.

> [!IMPORTANT]
> **Source-visible, not open source.** Copyright © 2026 Tower System. All rights reserved. Public access is provided only so development can be followed and evaluated. No permission is granted to copy, modify, redistribute, publish, sell, sublicense, or create derivative works from this code.

## Public alpha scope

- Android 12 or newer (API 31+)
- Read-only BLE discovery before a device is saved
- Persistent connection with Stop-before-disconnect behavior
- Direct manual control, presets, patterns, repeats, and a timeline editor
- Optional AI modes using a user-supplied xAI API key
- On-device encryption for the API key through Android Keystore
- Local settings and pattern storage with Android cloud backup disabled
- Explicit Android consent for Accessibility, overlays, and screen capture

The app currently targets a narrow, validated protocol. It does not claim support for every JoyHub device.

## Data and network behavior

Manual control, BLE diagnostics, patterns, settings, and on-device motion scoring remain local. AI features connect directly to `https://api.x.ai` using the API key entered by the user:

| Mode | Data sent to xAI |
| --- | --- |
| Text | Captured visible passage and recent generated-batch summaries |
| Video | Five compressed screen frames per analyzed cluster and recent summaries |
| Procedural | Style choices, session state, and recent generated-batch summaries |

Requests set `store: false`, but xAI's own terms and retention rules still apply. See [PRIVACY.md](PRIVACY.md) before enabling AI or capture features. API usage may incur charges on the user's xAI account.

## Build locally

Requirements:

- Android Studio with Android SDK 36
- JDK 17
- Internet access for the initial dependency download

Clone the repository, open its root in Android Studio, allow Gradle sync to complete, and run the `app` configuration on an Android 12+ device. A Gradle wrapper is not committed yet, so command-line and CI builds remain incomplete until one is generated with Gradle 8.13.

## Safety model

- The app begins with a versioned safety and privacy notice.
- Device discovery inspects GATT services before offering the controlled low-output probe.
- Output values are clamped to the protocol range.
- Stop clears all four continuous JoyHub channels.
- Disconnect and service shutdown paths attempt Stop before closing GATT.
- Text and Video AI output is gated when qualifying content is absent.

These are risk reductions, not guarantees. Bluetooth disconnects, Android process termination, device firmware, or physical hardware failure can prevent a command from arriving.

## Feedback and security

The project is not accepting code contributions while ownership and product terms are being established. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Please report vulnerabilities privately using the instructions in [SECURITY.md](SECURITY.md), especially anything involving unintended output, capture disclosure, secret handling, or exported Android components.

## Copyright and use

Copyright © 2026 Tower System. All rights reserved.

No open-source license is offered. Viewing this repository does not grant permission to use its contents beyond what applicable law independently permits. The repository may become private as product development advances; previously downloaded copies cannot be recalled.
