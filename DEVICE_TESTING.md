# NoctDock Device Testing

Use this checklist for release validation on real hardware. Keep both devices on the same local Wi-Fi network unless a step explicitly tests interruption.

**Related:** [`CONTRIBUTING.md`](CONTRIBUTING.md) (PR checklist) · [`ARCHITECTURE.md`](ARCHITECTURE.md) (system overview) · [`NOCTDOCK_AZAHAR_TESTING.md`](NOCTDOCK_AZAHAR_TESTING.md) (3DS / Azahar fork) · [`STREAM_WATCH.md`](STREAM_WATCH.md) (Azahar LAN metrics)

## Test Matrix

- Sender: Android 13 handheld or phone.
- Sender: Android 14 handheld or phone.
- Sender: Android 15 handheld or phone.
- Sender: Retroid Pocket 6, recommended Balanced or Quality 720p60 first; Sharp/Cinema only after connection testing.
- Sender: Retroid Pocket 5, recommended Performance 720p60.
- Sender: Unknown Android handheld, start with Performance Mode before increasing quality.
- Receiver: Google TV or Android TV device.
- Receiver: NVIDIA Shield 2019 for Shield-optimised HEVC and Full HD testing.
- Network: 5 GHz or 6 GHz Wi-Fi on the same access point.

Retroid Pocket 5 has Wi-Fi 6 and should support NoctDock, but it has less thermal and encoder headroom than Retroid Pocket 6. Use Performance Mode for demanding emulators or games. Retroid Pocket 6 can test Sharp and Cinema after the local Connection Optimizer reports a strong result.

## Build Variant Rules

- Debug APKs are for development only. They are debuggable and keep detailed stream logs enabled.
- Release APKs are production-like internal test builds. They are non-debuggable, minified, resource-shrunk, locally signed with the debug signing key, and suitable for release-readiness checks.
- Perf APKs are non-debuggable performance-test builds with minification disabled. Use perf builds first when judging latency and smoothness so R8 is not part of the test variable.
- Diagnostics must remain accessible in all variants, but normal release/perf logs should not include recurring stream metric spam.

When comparing debug vs release/perf, keep the same Console Mode, sound mode, TV Game Mode setting, Wi-Fi location, and game scene. Release/perf can reduce debug overhead and logging noise; it cannot fix poor Wi-Fi, overloaded routers, thermal throttling, or slow TV post-processing.

## Privacy And Permissions

- Install both APKs fresh.
- Confirm neither app asks for account sign-in.
- Confirm neither app asks for location, contacts, storage, camera, phone, or nearby-device permissions.
- Confirm Console Mode asks for Android screen capture consent when starting play.
- On Android 10+ sender devices, confirm the sender requests audio permission for local playback capture only; deny it once and confirm video Console Mode still starts.
- Confirm local Wi-Fi works with internet disconnected at the router or WAN blocked.
- Confirm app data is not restored after uninstall/reinstall.

## Pairing And Discovery

- Open NoctDock TV.
- Confirm the TV shows a name, local IP address, port `45454`, and a pairing code.
- Open NoctDock sender.
- Confirm the TV appears automatically.
- Pair with the 4-digit code shown on TV.
- Fully close and reopen the sender.
- Confirm the trusted TV is remembered locally and can be selected without pairing again.
- Use `Advanced & Experimental` manual connection only on a network where discovery is blocked.

## Android Phone And Tablet Receiver

- Install `noctdock-receiver-debug.apk` on an Android phone.
- Install `noctdock-receiver-debug.apk` on an Android tablet.
- Open the receiver app on each device and confirm it launches from the normal Android app drawer.
- Confirm the waiting screen stays simple: NoctDock Receiver, waiting state, pairing code when needed, and the local privacy line.
- Confirm the sender discovers both devices and still discovers Google TV / Android TV receivers.
- Pair from the sender and confirm the receiver name shown on the sender matches the local receiver name setting.
- Open receiver Settings and confirm:
  - Start fullscreen persists.
  - Keep screen awake persists.
  - Prefer landscape while playing persists.
  - Fit Screen / Fill Screen persists.
  - Receiver name persists after app restart.

## Console Mode

- Start Console Mode and grant screen capture permission.
- Confirm the sender enters Dock Mode and the TV shows the live screen.
- Confirm the foreground Console Mode notification appears or Android shows the active foreground service.
- Press Stop from Dock Mode.
- Start Console Mode again.
- Repeat stop/start at least 5 times.
- Confirm the TV returns to waiting state after each stop and resumes after each start.
- Deny screen capture permission.
- Confirm the sender shows a clean permission message and does not start streaming.
- Start Console Mode after denial and grant permission.
- Confirm streaming starts normally.
- Play a local game or media app that allows playback capture.
- Confirm TV audio is heard on NoctDock TV.
- Launch an app that blocks playback capture.
- Confirm Console Mode video continues even if that app has no TV audio.

### Screen Cloak

- Start Console Mode.
- Open `Settings > Experience > Screen Cloak`.
- Select `Dim`.
- If Android asks for `Display over other apps`, allow it.
- Confirm the handheld darkens but the TV picture stays clear.
- Select `Dark`.
- Confirm the handheld darkens further and the TV picture still stays clear.
- Select `Maximum Dark`.
- Confirm the handheld reaches the lowest brightness without a visible black overlay being captured on the TV.
- Press `Test Screen Cloak`.
- If the TV picture stayed clear, confirm transparent overlay mode remains enabled.
- If the TV picture went dark, confirm transparent overlay mode is disabled for this handheld and the app offers backup brightness control.
- Stop Console Mode.
- Confirm handheld brightness restores immediately.
- Repeat the start/stop cycle five times.
- Disconnect or stop the receiver during Console Mode.
- Confirm the handheld brightness restores after the session ends.

## High Quality 1080p Testing

1. 720p Quality AVC:
- Select Quality.
- Start Console Mode.
- Confirm Diagnostics show 1280x720 requested output and AVC codec.
- Confirm the picture is clean before trying higher modes.

2. Sharp 900p HEVC:
- Open Console Modes and run Test My Connection.
- Select Sharp if it is available.
- Start Console Mode.
- Confirm Diagnostics show 1600x900 requested output.
- Confirm Diagnostics show HEVC when both devices support it, or AVC fallback when HEVC is unavailable.

3. Cinema 1080p HEVC:
- Use the Shield on Ethernet if possible.
- Keep the Retroid near the same main router or Wi-Fi node.
- Run Test My Connection and continue only if it reports Ready for Full HD.
- Select Cinema.
- Confirm Diagnostics show 1920x1080 requested output, 1920x1080 virtual display, and HEVC where supported.

4. 1080 Boost AVC:
- Enable only from Advanced/Experimental if exposed for testing.
- Confirm Diagnostics show AVC and the high configured bitrate.
- Watch for heat, packet loss, and decoder errors.

Also test:
- Shield on Ethernet.
- Shield on Wi-Fi.
- Retroid near the main router.
- Mesh node vs same-node results.
- TV Game Mode enabled.
- TV Game Mode disabled only for comparison.
- Actual encoder resolution in Diagnostics.
- Actual receiver decoder codec in Diagnostics.
- Receiver surface size in Diagnostics.
- Standard 1080p mode with 3DS/crop features disabled.

## Handheld Support Matrix

- Retroid:
  - Pocket 6 / Pocket G2: Balanced recommended. Sharp and Cinema only after connection test.
  - Pocket 5 / Pocket 4 Pro / Pocket Mini V2 / Flip 2 SD865: Performance or Balanced recommended depending on stability.
  - Pocket 3+: Performance only. Treat as receiver-or-light-play handheld.

- AYN:
  - Odin 2 / Odin 2 Portal / Odin 3 / Thor Snapdragon 8 Gen 2: full support, higher modes after testing.
  - Thor Lite SD865: conservative defaults, use Performance first.

- AYANEO / KONKR:
  - Pocket S / S2 / EVO / DS / KONKR Pocket FIT: full support, higher modes after testing.
  - Pocket Air: conservative defaults, use Performance first.

- Anbernic:
  - RG557 / RG477M / RG477V: full support, higher modes after testing.
  - RG556: conservative defaults, use Performance first.

- Razer:
  - Razer Edge: conservative defaults, Balanced only after stable testing.

- Logitech / Abxylute / MANGMI:
  - Logitech G Cloud / Abxylute One / One Pro / MANGMI Air X: Performance only, better suited as receiver or for lighter games.

## Audio Modes

1. Retroid Sound:
- Select Retroid Sound in Settings.
- Start Console Mode.
- Confirm video appears on TV.
- Confirm sound remains on the Retroid or connected headphones.
- Confirm no TV sound is expected.

2. TV Sound:
- Select TV Sound in Settings.
- Keep `Lower handheld sound in TV Sound` enabled.
- Start Console Mode and grant audio permission if prompted.
- Confirm video appears on TV.
- Confirm sound plays on TV.
- Confirm handheld media volume is lowered while docked.
- Stop Console Mode.
- Confirm handheld media volume restores.

3. Both:
- Select Both in Settings.
- Start Console Mode.
- Confirm sound plays from the Retroid and TV.
- Confirm any echo is understandable as TV audio delay.

4. Quiet Mode:
- Select Quiet Mode in Settings.
- Start Console Mode.
- Confirm video appears on TV.
- Confirm no TV sound is expected.
- Confirm handheld sound is lowered while docked.
- Stop Console Mode.
- Confirm handheld media volume restores.

Also test emulator audio, Android game audio, headphones connected to Retroid, Bluetooth audio connected to Retroid, and one app whose audio cannot be captured.

## Reconnect and discovery rebroadcast

- Connect sender to receiver and start Console Mode.
- Stop Console Mode from the sender.
- Confirm the receiver returns to **Waiting for handheld** within a few seconds.
- Confirm receiver System Status shows **Broadcasting: yes** and a recent **Last broadcast restart**.
- Reconnect without closing either app.
- Repeat connect → stop → reconnect five times without force-closing either app.
- Confirm sender System Status shows **Discovery state** returning to scanning after stop.
- Background the receiver, return to foreground, and confirm it is discoverable again.
- Start Console Mode.
- Close the sender app or stop Console Mode.
- Confirm the TV exits the active stream and remains open.
- Reopen the sender.
- Start Console Mode again.
- Confirm the TV reconnects without restarting the TV app.
- Confirm System Status on the sender shows TV feedback for received FPS, reassembly drops, decoder errors, audio packets, audio underruns, audio drops, audio buffer, and estimated A/V offset after the TV has been active for a few seconds.
- Confirm System Status shows requested output resolution, codec, configured bitrate, receiver decoder MIME, and receiver surface size.
- Turn Wi-Fi off on the sender during Console Mode.
- Turn Wi-Fi back on and return both devices to the same network.
- Confirm the sender can find or manually reconnect to the TV.

Android-to-Android checks:

- Retroid Pocket 6 sender -> Android phone receiver:
  - Pair successfully.
  - Confirm live video playback.
  - Confirm audio playback when TV Sound or Both is enabled.
  - Confirm Fit Screen preserves aspect ratio.
  - Confirm Fill Screen crops without distortion.
- Retroid Pocket 6 sender -> Android tablet receiver:
  - Repeat the same checks.
- Rotate the phone/tablet while waiting:
  - Confirm the waiting screen follows normal device orientation.
- Start Console Mode with `Prefer landscape while playing` enabled:
  - Confirm the receiver prefers landscape while active.
- Lock and unlock the phone/tablet during or after Console Mode:
  - Confirm the app returns cleanly without a stuck stream surface.
- Stop and restart Console Mode five times:
  - Confirm video/audio recover each time.
- Reopen the receiver app after it was backgrounded:
  - Confirm it returns to the waiting state and can be discovered again.

## Smooth 60 Hz Helper (sender)

- Open **Console Modes** and set **Smooth 60 Hz Helper** to Off, Ask when Console Mode starts, and Always request 60 Hz where supported.
- Start Console Mode with each setting on a high-refresh handheld.
- Confirm the app does not crash when 60 Hz modes are unsupported.
- Confirm sender System Status shows **60 Hz requested**, **Active refresh rate**, and **60 Hz helper** result lines.
- Compare stream smoothness with the helper off vs on; manual Display settings may still be required on some devices.

## TV Remote

- Navigate NoctDock TV using only a D-pad and Select.
- Confirm pairing and waiting screens remain readable from couch distance.
- During Console View, press D-pad or Select.
- Confirm the overlay toggles and does not trap focus.
- Press Back.
- Confirm the app remains stable and does not expose technical diagnostics in the normal TV path.

## UI Review

- Confirm normal sender screens use living-room language, not codec or packet terms.
- Confirm Home top bar opens **Library**, **Screens**, **Console Modes**, and **Settings** inline; **Home** returns to portal or launcher grid.
- Confirm **Back** from launcher/library/screens lists moves focus to the top bar first; a second **Back** from that anchor exits the app.
- Confirm only one focus highlight at a time (top bar **or** grid/list, not both).
- Confirm trusted **screen pill** gradient scrolls smoothly with no colour snap at the loop (skip if Reduced Motion is on).
- Confirm glass tile menus and the **dock portal** card show a soft outer gradient ring without jank when open.
- Confirm portal **Looking…** / **Pair** button labels are not clipped at the bottom on small handheld layouts.
- Confirm **Library** filter chips (All, Favourites, etc.) keep a visible gradient ring on the selected filter.
- Confirm accent **primary** buttons show a pill-shaped glow (not square corners on the sides).
- Confirm sender portal and receiver waiting screen use the same **lens-style dock orb** (sonar rings on the receiver).
- From the Azahar tile picker, **Launch** (not 3DS Mode) prompts for screen capture and starts Console Mode like a normal app launch.
- Confirm System Status is reachable through **Settings** (or an error action), not the primary launcher path.
- Confirm System Status **Copy support report** on sender and receiver copies to the clipboard and pastes readable text (device role, System Status fields, recent in-app logs; no installed app names).
- After a forced error or interrupted stream, reproduce once, copy again, and confirm **Last error** or stream error lines appear in the report.
- Confirm empty states are real states, not mock content.
- Confirm Library contains only installed apps from the device.

## APK Build Commands

Windows:

```powershell
.\gradlew.bat clean
.\gradlew.bat :noctdock-core:test
.\gradlew.bat :noctdock-sender:assembleDebug
.\gradlew.bat :noctdock-receiver:assembleDebug
.\gradlew.bat :noctdock-sender:assembleRelease
.\gradlew.bat :noctdock-receiver:assembleRelease
.\gradlew.bat test
```

Linux:

```sh
./gradlew clean
./gradlew :noctdock-core:test
./gradlew :noctdock-sender:assembleDebug
./gradlew :noctdock-receiver:assembleDebug
./gradlew :noctdock-sender:assembleRelease
./gradlew :noctdock-receiver:assembleRelease
./gradlew test
```

Optional perf builds for latency and smoothness testing:

```sh
./gradlew :noctdock-sender:assemblePerf
./gradlew :noctdock-receiver:assemblePerf
```

APK output paths:

- Sender debug: `noctdock-sender/build/outputs/apk/debug/noctdock-sender-debug.apk`
- Receiver debug: `noctdock-receiver/build/outputs/apk/debug/noctdock-receiver-debug.apk`
- Sender release: `noctdock-sender/build/outputs/apk/release/noctdock-sender-release.apk`
- Receiver release: `noctdock-receiver/build/outputs/apk/release/noctdock-receiver-release.apk`
- Sender perf: `noctdock-sender/build/outputs/apk/perf/noctdock-sender-perf.apk`
- Receiver perf: `noctdock-receiver/build/outputs/apk/perf/noctdock-receiver-perf.apk`

Install examples:

```sh
adb -s <sender-device-id> install -r noctdock-sender/build/outputs/apk/perf/noctdock-sender-perf.apk
adb -s <tv-device-id> install -r noctdock-receiver/build/outputs/apk/perf/noctdock-receiver-perf.apk
```
