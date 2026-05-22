# NoctDock Azahar RP6 Test Checklist

Use this checklist for the first real Retroid Pocket 6 / NoctDock receiver validation pass.

**Related:** [`NOCTDOCK_AZAHAR_INTEGRATION.md`](NOCTDOCK_AZAHAR_INTEGRATION.md) · [`DEVICE_TESTING.md`](DEVICE_TESTING.md) · [`CONTRIBUTING.md`](CONTRIBUTING.md)

## Build Under Test

- Azahar fork: `NoctDock-Azahar`
- Variant: `vanillaDebug`
- Renderer target for export: OpenGL stable, Vulkan experimental
- Default export: `800x480` at `30fps`
- Test modes available: Battery / Safe `400x240 @ 30fps`, Balanced `800x480 @ 30fps`, Sharp `800x480 @ 60fps`, TV `1280x720 @ 30fps`, Experimental `1280x720 @ 60fps`

Build command:

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk ./gradlew :app:assembleVanillaDebug
```

Offline fallback if GitHub/DNS is unavailable for Vulkan validation layers:

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk ./gradlew :app:assembleVanillaDebug --offline -x :app:downloadVulkanValidationLayers
```

The validation layer download is a debug aid only. It must not hide compile errors or block normal debug APK creation.

## Basic Launch

- [ ] Normal Azahar launch works without NoctDock.
- [ ] A game boots with NoctDock 3DS Mode disabled.
- [ ] Export off baseline has no NoctDock readback/copy, encoder, or packet activity.
- [ ] Export off baseline FPS is recorded before export testing.
- [ ] NoctDock 3DS Mode prompt can be declined.
- [ ] Declining the prompt starts normal local gameplay.
- [ ] Existing Android secondary display support still works.

## NoctDock Sender (Game Hub / Library)

Validate on the **Retroid sender** with NoctDock Receiver running and (for 3DS tests) a **trusted** screen.

### Normal Launch

- [ ] **Launch** from Game Hub Azahar picker (not **Launch in 3DS Mode**) prompts for **screen capture** when Console Mode is not already active.
- [ ] After granting capture, sender enters Dock/Console Mode and Azahar opens on the handheld.
- [ ] TV shows the **full Android display** (launcher/UI inside Azahar counts as mirrored Android UI), not top-screen-only 3DS export yet.
- [ ] **Launch** from Game Hub **Library** Azahar row behaves the same as the picker **Launch** button.
- [ ] Legacy Library card **Launch** behaves the same (screen-capture prompt, then open Azahar).
- [ ] With Console Mode already active, **Launch** opens Azahar without a second capture prompt.

### Launch in 3DS Mode (sender side)

- [ ] **Launch in 3DS Mode** does **not** use sender MediaProjection; Azahar opens with NoctDock extras (see Renderer Coverage below).
- [ ] Without a trusted receiver, sender shows an appropriate status (pair first / screen not ready) and does not start 3DS export.
- [ ] With trusted receiver online, **Launch in 3DS Mode** opens Azahar; top-screen export is validated in **Renderer Coverage** and **Gameplay Behavior**.

### Regression

- [ ] Picker **Launch** after a failed or denied capture prompt does not leave sender in a broken stream state.
- [ ] Switching from a mirrored **Launch** session to in-Azahar **3DS Mode** follows Azahar’s own settings/prompts; sender stop/start rules in `launchAzahar3dsMode()` still apply.

## Renderer Coverage

- [ ] OpenGL renderer allows NoctDock export.
- [ ] OpenGL top screen appears on the NoctDock receiver.
- [ ] OpenGL Stream Watch reports `exportPath = OPENGL_ENCODER_SURFACE` when Surface export works.
- [ ] If Surface export setup fails, OpenGL shows `Using compatibility export mode.`
- [ ] OpenGL readback fallback reports `exportPath = OPENGL_READBACK_FALLBACK`.
- [ ] Vulkan renderer shows `Renderer: Vulkan (Experimental)` in the 3DS Mode prompt.
- [ ] Vulkan export starts.
- [ ] Vulkan top screen appears on the NoctDock receiver.
- [ ] Vulkan export uses the same receiver and selected codec settings.
- [ ] Vulkan reports `exportPath = VULKAN_ENCODER_SURFACE` when the MediaCodec `ANativeWindow` swapchain is accepted.
- [ ] Vulkan reports `exportPath = VULKAN_READBACK_FALLBACK` if the encoder surface target fails and compatibility mode restarts.
- [ ] Vulkan reports `vulkanAvailable = true` when encoder-surface export is active.
- [ ] Vulkan reports a precise `vulkanBlocker` only when the Vulkan surface path cannot be used.
- [ ] If Vulkan export cannot start, Azahar shows: `NoctDock Vulkan export could not start. Playing normally.`
- [ ] Vulkan fallback continues normal gameplay.

## Receiver Availability

- [ ] Receiver unavailable path shows: `NoctDock screen is not available. Playing normally.`
- [ ] Receiver startup probe sends NoctDock `CONNECTION_TEST`.
- [ ] Receiver startup probe receives NoctDock `CONNECTION_TEST` echo.
- [ ] Receiver unavailable path does not crash.
- [ ] Receiver unavailable path does not stop emulation.
- [ ] Receiver available path starts export.
- [ ] Receiver logs/video surface show real top-screen video.

## Gameplay Behavior

- [ ] Top screen appears on the NoctDock receiver.
- [ ] Bottom screen remains on the handheld.
- [ ] Touch still works on the handheld bottom screen.
- [ ] Controls still work.
- [ ] Audio behavior remains unchanged for this pass.
- [ ] Screen swap does not break touch/control flow.
- [ ] Existing secondary display path is not hijacked by NoctDock.
- [ ] A physical secondary display/external monitor still works when NoctDock 3DS Mode is off.
- [ ] NoctDock encoder-surface export does not replace the existing physical secondary display surface.

## Export Stop/Restart

- [ ] Stop export by leaving gameplay.
- [ ] Export cleanup sends STOP packet.
- [ ] Encoder stops cleanly.
- [ ] UDP socket closes cleanly.
- [ ] Heartbeat job stops cleanly.
- [ ] OpenGL export framebuffer/texture is released after stop.
- [ ] Vulkan export offscreen frame/staging buffer is released after stop.
- [ ] Starting export again works without app restart.
- [ ] Vulkan export stop/restart works without app restart.
- [ ] If export fails after gameplay starts, message appears: `NoctDock 3DS Mode stopped. Playing normally.`
- [ ] Runtime export failure does not stop emulation.

## RP6 Export Test Order

Record for every test:

- `exportPath`
- top screen appears: yes/no
- bottom touch works: yes/no
- gameplay slowdown: none/slight/bad
- receiver stability
- Stream Watch report

### A. OpenGL

- [ ] `800x480 @ 30fps`
- [ ] `800x480 @ 60fps`
- [ ] `1280x720 @ 30fps`
- [ ] `1280x720 @ 60fps`
- [ ] OpenGL encoder-surface path reports `OPENGL_ENCODER_SURFACE` when Surface input works.
- [ ] OpenGL compatibility readback fallback reports `OPENGL_READBACK_FALLBACK` if Surface input is unavailable.

### B. Vulkan

- [ ] `800x480 @ 30fps`
- [ ] `800x480 @ 60fps`
- [ ] `1280x720 @ 30fps`
- [ ] `1280x720 @ 60fps`
- [ ] Vulkan encoder-surface path reports `VULKAN_ENCODER_SURFACE` when the MediaCodec `ANativeWindow` swapchain is accepted.
- [ ] Vulkan compatibility readback fallback reports `VULKAN_READBACK_FALLBACK` if the encoder surface target fails.
- [ ] Vulkan encoder-surface mode is preferred, with readback used only as compatibility fallback.
- [ ] Battery / Safe `400x240 @ 30fps` remains available as the lowest-cost fallback.
- [ ] Default remains Balanced `800x480 @ 30fps`.
- [ ] No fake or dummy frames are shown.

### C. Failure Tests

- [ ] Receiver closed before export starts.
- [ ] Receiver disconnect during export.
- [ ] Prompt decline plays normally.
- [ ] Export stop/restart works without app restart.
- [ ] Physical secondary display regression test passes with NoctDock off.
- [ ] Failure paths stop NoctDock export only and do not kill gameplay.

## Gameplay-First Safety

- [ ] If average readback/copy exceeds `10ms`, export caps to `30fps`.
- [ ] If average readback/copy exceeds `16ms`, export steps down one profile.
- [ ] If encoder queue fills, stale export frames are dropped.
- [ ] If packet queue/network send falls behind, export frames are dropped.
- [ ] Safety message appears when needed: `NoctDock 3DS Mode is using a safer setting to keep gameplay smooth.`
- [ ] Local gameplay remains smooth enough to control.

## Performance Logging

Watch logcat for aggregate NoctDock logs. There should be no per-frame spam.

Check every 1-2 seconds for:

- [ ] export state
- [ ] frame count
- [ ] average full export time
- [ ] average readback/copy time
- [ ] Vulkan copy average/max time when renderer is Vulkan
- [ ] export path (`OPENGL_ENCODER_SURFACE`, `OPENGL_READBACK_FALLBACK`, `VULKAN_ENCODER_SURFACE`, `VULKAN_READBACK_FALLBACK`, or `VULKAN_UNAVAILABLE`)
- [ ] encoder surface active yes/no
- [ ] readback fallback active yes/no
- [ ] Vulkan surface bridge attempted/success where available
- [ ] Vulkan swapchain created where available
- [ ] Vulkan blocker text only when fallback/unavailable
- [ ] encoder input queue time
- [ ] raw dropped frames
- [ ] queue full events
- [ ] packets sent
- [ ] packet drops
- [ ] network send errors

Record these during gameplay:

- Game:
- Renderer:
- Export path:
- Export mode:
- Export resolution:
- Export fps:
- Average Azahar FPS:
- Average export time:
- Average readback time:
- Vulkan copy average/max:
- Raw dropped frames:
- Packet send errors:
- Receiver video quality notes:

## NoctDock Stream Watch

Stream Watch is a local debug tool for a laptop on the same LAN. It does not upload telemetry and does not include game path, ROM name, usernames, or personal data.

Enable it on the Retroid:

1. Open Azahar home settings.
2. Open `NoctDock 3DS Mode`.
3. Open `Export Settings`.
4. Open `NoctDock Stream Watch`.
5. Enable it after reading the warning: `Debug stream metrics are visible on your local network while enabled.`

Find the Retroid IP:

1. Open Android Wi-Fi settings.
2. Tap the connected network.
3. Note the device IP address.

Run the watcher from a laptop terminal:

```bash
python3 tools/noctdock_stream_watch.py --host RETROID_IP --port 45456
```

If SSE is blocked by a network/device quirk, poll instead:

```bash
python3 tools/noctdock_stream_watch.py --host RETROID_IP --port 45456 --poll
```

Useful endpoints:

- `GET /health` checks whether the debug server is alive.
- `GET /metrics` returns the current JSON snapshot.
- `GET /watch` streams one JSON snapshot per second as Server-Sent Events.
- `GET /report` returns the last 5 minutes of summary data plus recent events.

Metrics to watch:

- `exportState`: current exporter state.
- `exportPath`: `OPENGL_ENCODER_SURFACE`, `OPENGL_READBACK_FALLBACK`, `VULKAN_ENCODER_SURFACE`, `VULKAN_READBACK_FALLBACK`, or `VULKAN_UNAVAILABLE`.
- `encoderSurfaceActive`: true when MediaCodec Surface input is active.
- `secondaryWindowActive`: false for NoctDock export because it does not hijack Azahar's real `secondary_window`.
- `readbackFallbackActive`: true when compatibility readback is active.
- `vulkanAvailable`: true when Vulkan encoder-surface export is active; false when it is unavailable.
- `vulkanBlocker`: exact reason Vulkan encoder-surface export is unavailable or fell back.
- `exportMode`: Battery / Safe, Balanced, Sharp, TV, Experimental, or auto-safe variant.
- `actualExportFps`: real exported frame rate, not emulator FPS.
- `readbackAvgMs`: average backend readback/copy cost.
- `readbackMaxMs`: worst recent backend readback/copy cost.
- `glReadPixelsAvgMs`: OpenGL-compatible readback key, kept for watcher compatibility.
- `glReadPixelsMaxMs`: OpenGL-compatible max readback key, kept for watcher compatibility.
- `vulkanExportActive`: true when the active backend is Vulkan.
- `vulkanSurfaceBridgeAttempted`: true when Vulkan attempts the MediaCodec `ANativeWindow` surface path.
- `vulkanSurfaceBridgeSuccess`: true when `VULKAN_ENCODER_SURFACE` is active.
- `vulkanSwapchainCreated`: true when the NoctDock Vulkan encoder swapchain is active.
- `vulkanAhbBridgeAttempted`: false for the current implementation; AHardwareBuffer is reserved as a later bridge if surface swapchain fails broadly on target devices.
- `vulkanCopyRenderAvgMs`: average Vulkan export render/copy time.
- `vulkanAcquireMs` / `vulkanPresentMs`: reserved for finer Vulkan WSI timing.
- `vulkanCopyAvgMs`: Vulkan copy/readback average when using Vulkan.
- `vulkanCopyMaxMs`: Vulkan copy/readback max when using Vulkan.
- `exportQueueDepth`: current export queue depth.
- `droppedExportFrames`: dropped export frames.
- `encoderQueueDepth`: raw frame queue waiting for MediaCodec input.
- `encoderQueueDrops`: frames dropped because export fell behind.
- `packetsSent` / `bytesSent`: local NoctDock packet output.
- `sendErrors`: UDP send failures.
- `receiverFps`, `receiverPacketLoss`, `receiverDrops`: currently `null` unless receiver feedback is wired later.
- `gameplayFpsImpact`: currently `null` unless gameplay baseline comparison is wired later.
- `streamHealth`: `EXCELLENT`, `GOOD`, `FAIR`, or `POOR`.
- `recommendation`: simple tuning advice based on real values.

Interpret recommendations:

- If readback is over `12ms`, test `30fps` or a lower resolution.
- If encoder queue grows or drops appear, lower FPS first, then resolution.
- If send errors appear, test lower bitrate/profile or put both devices on the same router.
- If receiver is unreachable, check NoctDock receiver state and Wi-Fi/LAN.
- If metrics are stable, try the next quality step.

The watcher writes:

- `noctdock_stream_report.json`
- `noctdock_stream_report.txt`

The `/report` output includes:

- `exportOffBaseline`
- `openGlExportResult`
- `vulkanExportResult`
- `recommendedExportMode`

Stream Watch cleanup:

- [ ] Disable Stream Watch from Azahar settings when done.
- [ ] Confirm `GET /health` no longer responds after export/app stop.
- [ ] Confirm normal gameplay still works with Stream Watch disabled.

## Receiver Packet Compatibility

Expected NoctDock packet details:

- Magic: `0x4E44564F`
- Version: `1`
- Header size: `42`
- Config type: `1`
- Video fragment type: `2`
- Heartbeat type: `3`
- Stop type: `4`
- Connection test type: `10`
- MTU-safe datagrams: max `1400` bytes
- Config sent before frames and around keyframes
- Frame ids increase monotonically
- Timestamps are microseconds
- Keyframe flag is set on keyframes

Pass criteria:

- [ ] Receiver accepts config.
- [ ] Receiver assembles fragments.
- [ ] Receiver decodes keyframes.
- [ ] Receiver continues receiving heartbeats.
- [ ] Receiver handles STOP cleanly.

## Bottom Screen Auto-Dim

- [ ] Default is **Gentle** under NoctDock 3DS Mode → Export Settings → Bottom Screen Auto-Dim.
- [ ] Start NoctDock 3DS Mode and confirm top screen appears on the receiver.
- [ ] Wait 10 seconds without touching the handheld touchscreen.
- [ ] Confirm the bottom/handheld screen dims (local window brightness only; no overlay).
- [ ] Touch the bottom screen and confirm it brightens immediately.
- [ ] Wait 10 seconds again and confirm it dims again.
- [ ] Stop export and confirm handheld brightness returns to normal.
- [ ] Repeat with **OpenGL** and **Vulkan** renderers.
- [ ] Stream Watch `/metrics` includes `bottomScreenAutoDimEnabled`, `bottomScreenDimMode`, `bottomScreenDimmed`, `idleSeconds`, and `brightnessRestoreState`.
- [ ] **Off** mode never dims during export.
- [ ] Normal Azahar play (no 3DS export) never dims.

## Smooth 60 Hz during 3DS Mode

- [ ] Stream Watch `/metrics` includes `requested60Hz`, `activeRefreshRate`, and `refreshRateHelperResult` while export is active.
- [ ] 60 Hz helper is inactive when export is off (fields show not requested).
- [ ] Starting NoctDock 3DS Mode requests 60 Hz where Android allows it and does not crash when unsupported.
- [ ] Stopping export clears the helper (refresh fields return to not requested).
- [ ] Compare stream smoothness with export on a high-refresh handheld; use manual Display settings if guidance is shown.

## Final Acceptance Notes

- [ ] OpenGL export builds.
- [ ] OpenGL export sends real top-screen video.
- [ ] Vulkan export builds.
- [ ] Vulkan export sends real top-screen video or fails gracefully.
- [ ] Normal play works with export off.
- [ ] Normal play continues if export fails.
- [ ] Bottom screen stays on handheld.
- [ ] Touch and controls remain usable.
- [ ] Stream Watch reports `rendererBackend = Vulkan` during Vulkan export.
- [ ] Stream Watch report compares export off, OpenGL, and Vulkan samples.
- [ ] Compare Vulkan vs OpenGL FPS/readback/copy cost.
- [ ] 30fps safe mode exists and is default.
- [ ] 60fps mode exists for testing.
- [ ] Readback stall risk is capped and logged.
