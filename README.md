# Adaptive Remote

Private Android controller for the Cafatop Knight (`J-Mars`).

The project is intentionally being built in hardware-gated milestones. The
current checkpoint adds the pattern workspace after successful physical
validation of manual J-Mars control.

## Current state

- Native Kotlin and Jetpack Compose project
- Package id: `com.towersys.adaptiveremote`
- Minimum Android version: Android 12 (API 31)
- Target/compile SDK: Android 16 (API 36)
- Private-data backup disabled
- Adaptive multiplier model implemented with a `0.5×` default
- Navigable mode cards with Manual controls housed in Manual mode
- Unit tests cover scaling and clamping semantics
- Read-only BLE scan and GATT service inspection for `J-Mars`
- Copyable diagnostic report that checks for JoyHub `FFA0`/`FFA1`
- Persistent foreground connection with notification Stop action
- Live full-range manual slider and level presets
- Explicit stop-before-disconnect behavior
- Pattern playback through the validated foreground BLE service
- Built-in wave, pulse, and staircase patterns
- Custom timeline editor with intensity and duration per step
- Saved patterns, favorites, recent history, repeats, and procedural generation
- Repeat-until-Stop playback without disconnecting the Knight
- Text-mode visible-passage capture through an opt-in Accessibility service
- Locally encrypted xAI key and explicit Grok 4.5 whole-passage analysis
- AI-generated text timelines with the default 0.50× adaptive scale
- Text timelines sized from passage length and AI-interpreted narrative pacing
- Continuous visible-text monitoring with a movable floating Stop control
- Live replacement timelines that crossfade from the current output and hold until new text arrives
- Text bubble with current excerpt, step progress, and a caught-up scroll cue
- Per-step motion gating: non-action, teasing/denial, pauses, and aftercare are forced to zero
- Video mode with Android screen-share consent and foreground capture
- Five ordered frames sampled evenly over one second per video cluster
- Conflated video analysis that drops stale queued clusters instead of accumulating lag
- Video motion gating, crossfaded live replacement, floating status, and Stop controls
- Latest-wins AI timelines with smooth per-step transitions and faster video sampling
- On-device video motion scoring with an explicit-action baseline and motion-sensitive amplification
- Video-specific visual explicit-content gating with visible gate and motion status in the bubble
- Persistent home-page AI multiplier shared by Text, Video, and Procedural modes
- Bounded current-plus-pending Grok batch pipeline with blending and three-loop fallback
- Grok-driven Procedural mode with Tease, Standard, Denial, Edge, Finish, and Close behavior
- Automatic BLE reconnect after unexpected Knight connection loss
- Scan-gated persistent connection; Stop preserves it and unexpected BLE loss triggers reconnect

## Build prerequisites

- Android Studio with Android SDK 36
- JDK 17
- Internet access for the first Gradle dependency download

Open this directory in Android Studio and allow it to perform the initial Gradle
sync. The GitHub Actions workflow installs Gradle 8.13 and Android SDK 36,
runs unit tests, builds the debug APK, and uploads it as a private workflow
artifact. A Gradle wrapper will be generated and committed once a Gradle runtime
is available; the current coding environment does not contain Gradle or an
Android SDK.

## Safety boundary

The physical unit has been confirmed as `J-Mars`, its `FFA0`/`FFA1` transport
was discovered read-only, and a brief low-output start/stop command was
successfully tested. All output still passes through the validated JoyHub frame
encoder, and disconnect paths attempt Stop before closing GATT.
