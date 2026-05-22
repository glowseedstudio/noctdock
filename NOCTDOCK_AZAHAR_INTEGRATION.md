# NoctDock Azahar Integration

This document tracks the NoctDock integration layer for a custom Azahar Android fork.

The fork lives outside the NoctDock app tree. NoctDock remains the sender/receiver dock system. Azahar remains a separate Android app/APK with its original emulator behavior, attribution, and GPL notices intact.

**Fork changelog and license compliance:** see [`NoctDock-Azahar/NOTICE`](../NoctDock-Azahar/NOTICE) (short release notice) and [`NoctDock-Azahar/NOCTDOCK_FORK_CHANGELOG.md`](../NoctDock-Azahar/NOCTDOCK_FORK_CHANGELOG.md) (full change list and GPLv2 notes).

**Device testing:** see [`NOCTDOCK_AZAHAR_TESTING.md`](NOCTDOCK_AZAHAR_TESTING.md).

**NoctDock app repo:** sender contract lives in `noctdock-core/.../NoctDockAzaharContract.kt`; contributor workflow in [`CONTRIBUTING.md`](CONTRIBUTING.md) and [`ARCHITECTURE.md`](ARCHITECTURE.md).

## NoctDock Sender vs Azahar Fork

The handheld **NoctDock Sender** and the **NoctDock Azahar** APK are separate apps. Sender code lives in this repo (`NoctDockAzaharContract`, `SenderViewModel.launchAzahar`, `launchAzahar3dsMode`). Export hooks and UDP from the top screen live in the Azahar fork (paths below).

### Normal Launch (`Launch`)

Use this when you want Azahar on the handheld while the **TV shows the full Android display** (same idea as **Launch on Screen** for any other library app).

1. User chooses **Launch** from the Game Hub Azahar picker, Library row, or legacy Library card.
2. `launchAzahar()` resolves the installed package (`com.glowseed.noctdock.azahar` or `.debug`) and calls `launchInConsoleMode()` for that library entry.
3. If streaming is not already active, sender shows the **screen-capture (MediaProjection)** prompt, starts the Console Mode foreground service, then opens Azahar.
4. Sender mirrors the **whole Android UI** to the trusted receiver. Azahar does **not** receive `ACTION_THREE_DS_MODE` or NoctDock receiver extras on this path.
5. User can still enable **NoctDock 3DS Mode** inside Azahar later for top-screen-only export.

### Launch in 3DS Mode (`Launch in 3DS Mode`)

Use this when you want **only the emulated top screen** on the TV; touch and bottom screen stay on the handheld.

1. User chooses **Launch in 3DS Mode** (Game Hub picker, Library, or legacy card).
2. `launchAzahar3dsMode()` runs `NoctDockAzaharContract.preflight()` (Azahar installed, receiver selected, online, **trusted**).
3. Sender **stops** any active Console Mode session (Azahar owns encode/UDP for this path).
4. Sender starts Azahar with `ACTION_THREE_DS_MODE` → `org.citra.citra_emu.ui.main.MainActivity` and intent extras: `noctdock_mode`, receiver name/address/port, negotiated `avc`/`hevc`, sound mode, `noctdock_prompt_user`.
5. Azahar’s in-app **NoctDock 3DS Mode** flow and native hooks (below) export the top screen. Sender does **not** run MediaProjection for this path.

### Sender UI entry points

| Location | Normal **Launch** | **Launch in 3DS Mode** |
|----------|-------------------|-------------------------|
| Game Hub launcher tile (Azahar) | Mode picker → **Launch** | Mode picker → **Launch in 3DS Mode** |
| Game Hub **Library** panel | **Launch** | **Launch in 3DS Mode** |
| Legacy sender Library Azahar card | **Launch** | **Launch in 3DS Mode** |

### Integrator checklist

- Do not assume **Launch** starts 3DS export; it starts **sender Console Mode + mirror**.
- Do not assume **Launch in 3DS Mode** skips pairing; preflight requires a **trusted** receiver.
- Fork changes must keep normal Azahar launch working when opened from the launcher without NoctDock extras.

## License And Attribution (Summary)

- Upstream **Azahar / Citra** remains **GPLv2** (`license.txt` in the Azahar tree). NoctDock changes do not relicense the emulator.
- New/modified `.kt`, `.cpp`, and `.h` files in the fork use the standard header: *Copyright Citra Emulator Project / Azahar Emulator Project; Licensed under GPLv2 or any later version*.
- **NoctDock Sender** and **NoctDock Receiver** are separate APKs (NOCTDOCK repo). This fork only implements the **exporter** side of the protocol.
- Distributing **NoctDock Azahar** builds still requires GPLv2 compliance for this combined work (source, license text, preserve notices).
- `externals/` third-party licenses are unchanged. NoctDock does not add new vendored codecs beyond Android system **MediaCodec**.

## Rules For This Integration

- Azahar source stays separate from NoctDock.
- Normal Azahar emulation must keep working.
- Existing Azahar secondary-display support must stay intact.
- No cloud services, accounts, analytics, ads, or internet services are added.
- NoctDock use is local-network only.
- No fake video frames or dummy stream state should be added.
- Top-screen export should not claim to work until the renderer hook is confirmed and implemented.

## Phase 1 Status

Initial Kotlin-side integration has been added in the Azahar fork under:

`/home/glowseed/Documents/coding projects/NoctDock-Azahar/src/android/app/src/main/java/org/citra/citra_emu/noctdock/`

Added classes (see fork changelog for full list):

- `NoctDockBridge`
- `NoctDockBridgeService`
- `NoctDockAvailabilityChecker`
- `NoctDockIntentContract`
- `NoctDockScreenRoute`
- `NoctDockBridgeSettings`
- `NoctDockTopScreenSource`
- `NoctDockTopScreenExporter` / `NoctDockPacketTransport`
- `NoctDockExportCodecPolicy`
- `NoctDockStreamWatch`
- `NoctDockBottomScreenAutoDim`
- `NoctDockRefreshRateHelper`

The Azahar fork package/application ID was changed to:

`com.glowseed.noctdock.azahar`

The app label was changed to:

`NoctDock Azahar`

The original Kotlin package namespace remains `org.citra.citra_emu`. That keeps the source tree stable and avoids rewriting Azahar internals just to change the installed APK identity.

## User-Facing Flow Added

The Azahar Options screen now exposes:

**NoctDock 3DS Mode**

Description:

> Send the top screen to another screen while keeping touch on this device.

The setting is available only when the NoctDock sender package is installed:

`com.glowseed.noctdock.sender`

When enabled, game launch can prompt:

**Send Top Screen to NoctDock?**

Buttons:

- **Send to Screen**
- **Play Normally**

If export cannot start, the app shows:

> NoctDock could not start 3DS Mode. Playing normally.

OpenGL export is the stable path. Vulkan export is now available as an experimental path and should fall back cleanly if it cannot start.

## Launch Contract

The Azahar fork now accepts and stores NoctDock launch extras:

- `noctdock_mode = THREE_DS_TOP_SCREEN`
- `noctdock_receiver_name`
- `noctdock_receiver_address`
- `noctdock_receiver_port`
- `noctdock_preferred_codec`
- `noctdock_sound_mode`
- `noctdock_prompt_user`

If launched with these extras through the main Azahar activity, Azahar opens normally. The pending NoctDock request is kept for repeat game launches until Azahar is opened normally from the launcher.

If launched directly into `EmulationActivity` with the same extras, the game-start prompt path can also read them.

## Azahar Android Structure

Android lives under:

`/home/glowseed/Documents/coding projects/NoctDock-Azahar/src/android/`

Important files:

- `src/android/app/build.gradle.kts`
- `src/android/app/src/main/AndroidManifest.xml`
- `src/android/app/src/main/java/org/citra/citra_emu/ui/main/MainActivity.kt`
- `src/android/app/src/main/java/org/citra/citra_emu/activities/EmulationActivity.kt`
- `src/android/app/src/main/java/org/citra/citra_emu/fragments/EmulationFragment.kt`
- `src/android/app/src/main/java/org/citra/citra_emu/display/SecondaryDisplay.kt`
- `src/android/app/src/main/java/org/citra/citra_emu/NativeLibrary.kt`
- `src/android/app/src/main/jni/native.cpp`

The Android app uses Kotlin for the UI and JNI bridge, with the emulator/video core in native C++.

## Game Launch Flow

Confirmed flow:

1. `GameAdapter` navigates to `EmulationActivity`.
2. `EmulationActivity` loads settings, creates `SecondaryDisplay`, and installs the emulation nav graph.
3. `EmulationFragment` resolves the `Game`.
4. `EmulationFragment` waits for storage setup and a valid `Surface`.
5. `EmulationState.run()` starts the native emulation thread.
6. Native launch eventually calls `NativeLibrary.run(gamePath)`.

NoctDock prompt wiring was added before `EmulationState.run()` starts native emulation.

## Top And Bottom Screen Rendering Findings

Azahar keeps the normal 3DS screen split in the native renderer.

Confirmed screen IDs:

- top screen: `screen_id = 0`
- bottom screen: `screen_id = 1`

Relevant native files:

- `src/video_core/gpu.cpp`
- `src/video_core/pica/pica_core.cpp`
- `src/video_core/renderer_vulkan/renderer_vulkan.cpp`
- `src/video_core/renderer_opengl/renderer_opengl.cpp`

Renderer screen slots:

- `screen_infos[0]`: top-left eye
- `screen_infos[1]`: top-right eye
- `screen_infos[2]`: bottom screen

Both Vulkan and OpenGL prepare those screen textures before compositing.

## Secondary Display Findings

Azahar already has Android secondary-display support. This must not be removed.

Key files:

- `src/android/app/src/main/java/org/citra/citra_emu/display/SecondaryDisplay.kt`
- `src/android/app/src/main/jni/native.cpp`
- `src/core/frontend/emu_window.cpp`
- `src/core/frontend/framebuffer_layout.cpp`
- `src/video_core/renderer_vulkan/renderer_vulkan.cpp`
- `src/video_core/renderer_opengl/renderer_opengl.cpp`

Current behavior:

- Android creates a primary emulation `SurfaceView`.
- `SecondaryDisplay` creates a second `SurfaceView` inside a `Presentation` when an external display exists.
- If no external display is present, Azahar can fall back to a hidden `VirtualDisplay`.
- Native code creates both a primary `EmuWindow_Android` and a secondary `EmuWindow_Android`.
- Android secondary layout is handled by `AndroidSecondaryLayout()`.

Existing secondary display layout modes include:

- none
- top screen only
- bottom screen only
- side by side

Do not replace this path. NoctDock should be an additional export path, not a substitute for existing external display support.

## Vulkan/OpenGL Render Path Findings

Frame presentation starts from:

`GPU::VBlankCallback()` in `src/video_core/gpu.cpp`

That calls:

`renderer->SwapBuffers()`

Vulkan path:

- `RendererVulkan::SwapBuffers()`
- `RendererVulkan::PrepareRendertarget()`
- `RendererVulkan::DrawScreens()`
- `RendererVulkan::RenderToWindow()`
- `PresentWindow`
- Vulkan swapchain presentation

OpenGL path:

- `RendererOpenGL::SwapBuffers()`
- `RendererOpenGL::PrepareRendertarget()`
- `RendererOpenGL::DrawScreens()`
- `RendererOpenGL::RenderToMailbox()`
- Android `Choreographer`
- `NativeLibrary.doFrame()`
- `EmuWindow_Android_OpenGL::TryPresenting()`
- `eglSwapBuffers()`

The two backends do not share one final Android frame hook. Vulkan presents inside the renderer path. OpenGL uses the mailbox/present path through `doFrame()`.

## Safest Hook Point For Real Top-Screen Export

The safest confirmed hook point is after each backend has prepared top-screen render targets but before final platform presentation:

- Vulkan: near `RendererVulkan::DrawTopScreen()` / `RendererVulkan::RenderToWindow()`
- OpenGL: near `RendererOpenGL::DrawTopScreen()` / `RendererOpenGL::RenderToMailbox()`

The export must preserve:

- normal primary rendering
- normal bottom-screen rendering
- normal touch input
- current secondary-display behavior
- screen swap behavior
- stereo/top-left/top-right handling

The first real implementation should probably start with top-screen-only export from the same screen texture source used by `DrawTopScreen()`, then add backend-specific copying/encoding once the exact GPU readback or shared-image strategy is chosen.

## Risks

- Vulkan and OpenGL need different export plumbing.
- Reading back GPU images every frame can hurt emulation performance.
- Export must not block the emulation/render thread.
- The top screen has stereo variants; most NoctDock use should start with the primary/top-left output unless 3D mode is explicitly handled later.
- Screen swap changes display order but should not change touch behavior.
- Existing Android secondary display paths already use the second window; NoctDock must not hijack that window.
- A network sender inside Azahar must eventually match NoctDock packet/protocol expectations without adding cloud or internet services.

## Current OpenGL Export Implementation

The bridge is now connected for the OpenGL backend. The existing Kotlin interface is still the public entry point:

```kotlin
interface NoctDockTopScreenSource {
    fun startTopScreenExport(config: NoctDockExportConfig)
    fun stopTopScreenExport()
    fun isExporting(): Boolean
}
```

`NoctDockBridge.createTopScreenSource()` now returns the native-backed source instead of the deliberately unavailable placeholder.

### Azahar files changed

- `src/android/app/src/main/java/org/citra/citra_emu/noctdock/NoctDockTopScreenExporter.kt`
- `src/android/app/src/main/java/org/citra/citra_emu/noctdock/NoctDockPacketTransport.kt`
- `src/android/app/src/main/java/org/citra/citra_emu/noctdock/NoctDockBridge.kt`
- `src/android/app/src/main/java/org/citra/citra_emu/NativeLibrary.kt`
- `src/android/app/src/main/java/org/citra/citra_emu/fragments/EmulationFragment.kt`
- `src/android/app/src/main/jni/native.cpp`
- `src/video_core/renderer_opengl/renderer_opengl.cpp`
- `src/video_core/renderer_opengl/renderer_opengl.h`

### JNI bridge

The new native lifecycle methods are:

- `startNoctDockTopScreenExport(...)`
- `stopNoctDockTopScreenExport()`
- `isNoctDockTopScreenExporting()`

The renderer submits frames back through:

- `NativeLibrary.onNoctDockTopScreenFrame(frame, width, height, presentationTimeUs)`

The native bridge checks the active renderer before enabling export. OpenGL and Vulkan are allowed; other renderers fail cleanly. The active backend is exposed to Stream Watch as `rendererBackend`.

OpenGL is treated as stable. Vulkan is treated as experimental.

Normal gameplay continues either way.

### OpenGL hook point

The hook is in `RendererOpenGL::SwapBuffers()` after the normal main/secondary `RenderToMailbox()` calls and before video dumping. It calls:

```cpp
ExportNoctDockTopScreen();
```

`ExportNoctDockTopScreen()` renders `screen_infos[0]`, the same primary top-screen source used by `DrawTopScreen()`, into an OpenGL export framebuffer and reads the result back as RGBA.

Current export defaults to the Balanced performance profile: `800x480` at `30fps`. Advanced settings expose:

- Battery / Safe: `400x240 @ 30fps`
- Balanced: `800x480 @ 30fps`
- Sharp: `800x480 @ 60fps`
- TV: `1280x720 @ 30fps`
- Experimental: `1280x720 @ 60fps`

The default intentionally avoids `720p/60`.

This is intentionally top-left / primary-eye only. It does not handle stereoscopic 3D yet.

Existing secondary display support remains untouched. NoctDock export does not use or replace `secondary_window`.

### Encoding and packet output

This pass uses a real-frame readback path:

OpenGL top-screen texture -> export framebuffer -> `glReadPixels()` RGBA -> Kotlin queue -> MediaCodec byte-buffer encode -> NoctDock UDP packets

It does not fake frames and does not send dummy video.

The packet sender matches the NoctDock receiver protocol:

- `VIDEO_CONFIG`
- `VIDEO_FRAGMENT`
- `HEARTBEAT`
- `STOP`
- `CONNECTION_TEST` for startup receiver reachability

Packet compatibility was checked against `PacketCodec` in NoctDock core:

- magic header: `0x4E44564F`
- protocol version: `1`
- header size: `42` bytes
- `VIDEO_CONFIG` type id: `1`
- `VIDEO_FRAGMENT` type id: `2`
- `HEARTBEAT` type id: `3`
- `STOP` type id: `4`
- `CONNECTION_TEST` type id: `10`
- keyframe flag: `1`
- codec config flag: `2`
- max datagram size: `1400`

Frames are fragmented below the NoctDock MTU, frame ids are monotonic, presentation timestamps use microseconds, config is sent before frames and again around keyframes, and the sender uses only local UDP to the configured receiver address/port.

### Encoder Surface Export Path

OpenGL now tries a lower-overhead encoder-surface path before falling back to readback.

The path is:

OpenGL top-screen texture -> NoctDock encoder EGL surface -> MediaCodec Surface input -> encoded output callback -> existing NoctDock UDP packets

This is intentionally separate from Azahar's existing Android secondary display singleton. The existing `secondary_window` and physical/hidden `SecondaryDisplay` path remain untouched, so HDMI/external display support is not consumed by NoctDock 3DS Mode.

`secondary_window` is not used because Azahar only has one native secondary output. Reusing it for NoctDock would steal the user's physical/hidden secondary display surface and would change existing secondary display behavior. NoctDock instead registers its own encoder input `Surface` and creates a dedicated EGL window surface for export.

Files changed for this path:

- `src/android/app/src/main/java/org/citra/citra_emu/noctdock/NoctDockTopScreenExporter.kt`
- `src/android/app/src/main/java/org/citra/citra_emu/NativeLibrary.kt`
- `src/android/app/src/main/jni/native.cpp`
- `src/video_core/renderer_opengl/renderer_opengl.cpp`

Startup order:

1. Kotlin creates a Surface-input `MediaCodec` encoder.
2. Kotlin registers `codec.createInputSurface()` with native code.
3. Native enables NoctDock export and reports the active renderer/path.
4. `RendererOpenGL::ExportNoctDockTopScreen()` draws `screen_infos[0]` directly into the encoder EGL surface and swaps it.
5. MediaCodec emits H.264 output through the existing packet sender.

If Surface input setup fails before native export starts, Azahar shows:

> Using compatibility export mode.

It then uses the existing byte-buffer/readback fallback path. Vulkan remains experimental and currently uses its existing staging readback path.

Stream Watch reports the active path with:

- `exportPath = SECONDARY_ENCODER_SURFACE` for the OpenGL encoder-surface path
- `exportPath = GL_READBACK_FALLBACK` for compatibility readback
- `encoderSurfaceActive`
- `secondaryWindowActive`
- `readbackFallbackActive`

### Threading behavior

The native renderer callback is kept small. In the encoder-surface path there is no `glReadPixels`, no JNI RGBA frame copy, and no CPU RGBA-to-YUV conversion. The render path does an extra top-screen draw into the MediaCodec surface, then MediaCodec output callbacks and UDP packet sending happen off the render thread.

The readback fallback still performs a GPU readback on the render thread. It is throttled before readback using the configured export fps. Kotlin immediately places frames into a bounded queue. If encoding or UDP output falls behind, stale frames are dropped and gameplay is prioritized.

The MediaCodec input path and UDP packet sender run off the render thread. Stop/start releases encoder state, UDP socket, queues, worker threads, heartbeat scheduling, and backend export resources.

### Performance risks

The preferred OpenGL path is now the encoder-surface path. The readback path remains as a compatibility fallback because it is known to produce real video.

`glReadPixels()` can still stall the GPU/render thread when the fallback is active, especially at `60fps`, `1280x720`, or on slower Android devices.

This pass did not add PBO readback. Instead it reduces damage by:

- defaulting to `30fps`
- keeping export resolution at `800x480` by default
- reusing encoder-side YUV scratch buffers instead of allocating a new YUV buffer per input frame
- throttling native capture before readback/copy
- dropping stale export frames when encoder or packet queues back up
- measuring average full export time and `glReadPixels()` time every 2 seconds
- tracking input queue delay, dropped frames, queue-full events, packets sent, and network send errors

Gameplay-first safety rules now apply to both OpenGL and Vulkan:

- if readback/copy average is above `10ms`, cap export to `30fps`
- if readback/copy average is above `16ms`, step down to the next safer export profile and restart the shared encoder
- if encoder or packet queues fill, drop export frames and prefer local gameplay
- show: `NoctDock 3DS Mode is using a safer setting to keep gameplay smooth.`

Measured device numbers are still pending. The runtime will log aggregate `avgExport` and `avgReadback` values during RP6 testing.

The longer-term goal is still:

GPU renderer output -> encoder surface/hardware buffer -> MediaCodec -> NoctDock packets

For OpenGL, the first version of that path is implemented. Vulkan now also attempts a backend-native encoder surface path using a NoctDock-only Vulkan presentation target before falling back to readback.

## Experimental Vulkan Export Implementation

Vulkan export remains an experimental backend. It does not replace OpenGL; OpenGL remains the stable path.

### Vulkan files changed

- `src/video_core/renderer_vulkan/renderer_vulkan.cpp`
- `src/video_core/renderer_vulkan/renderer_vulkan.h`
- `src/video_core/renderer_vulkan/vk_present_window.h`
- `src/android/app/src/main/jni/native.cpp`
- `src/android/app/src/main/java/org/citra/citra_emu/NativeLibrary.kt`
- `src/android/app/src/main/java/org/citra/citra_emu/noctdock/NoctDockTopScreenExporter.kt`
- `src/android/app/src/main/java/org/citra/citra_emu/noctdock/NoctDockStreamWatch.kt`
- `src/android/app/src/main/java/org/citra/citra_emu/fragments/EmulationFragment.kt`

### Vulkan hook point

The hook is in `RendererVulkan::SwapBuffers()` immediately after:

```cpp
PrepareRendertarget();
RenderScreenshot();
```

It then calls:

```cpp
ExportNoctDockTopScreen();
```

This placement uses the freshly prepared `screen_infos[0]` top-left / primary-eye source before the normal window and secondary-display presentation paths run. It does not hook inside `DrawScreens()` or `DrawTopScreen()`, so main and secondary rendering are not duplicated or hijacked.

### Vulkan encoder-surface implementation

Vulkan now genuinely attempts a MediaCodec encoder-surface path. Android `MediaCodec.createInputSurface()` gives native code an `ANativeWindow`; the Vulkan renderer wraps that in a NoctDock-only lightweight `EmuWindow`, creates a dedicated `PresentWindow`, and presents a top-screen-only swapchain to the encoder surface.

This remains separate from Azahar's real `secondary_window`. The physical/hidden secondary display path is still owned by `secondary_present_window_ptr` and is not reused for NoctDock.

Implementation details:

- `RendererVulkan::ExportNoctDockTopScreen()` is still called from `RendererVulkan::SwapBuffers()` after `PrepareRendertarget()` and `RenderScreenshot()`.
- When native reports encoder-surface mode, Vulkan calls `RenderNoctDockTopScreenToEncoderSurface(width, height)`.
- `NoctDockVulkanEncoderWindow` exposes the MediaCodec `ANativeWindow` as an Android `EmuWindow` with a top-screen-only framebuffer layout.
- A dedicated `noctdock_present_window_ptr` creates its own `VkSurfaceKHR`, swapchain, image views, framebuffers, and render pass for the encoder target.
- `PrepareDraw(...)` was generalized to use the target `PresentWindow` render pass instead of always using `main_present_window`.
- The renderer draws `screen_infos[0]` to the NoctDock encoder swapchain and presents it. MediaCodec then emits encoded output through the existing NoctDock sender.

If the Vulkan encoder-surface target fails at runtime, native switches to:

- `exportPath = VULKAN_READBACK_FALLBACK`

Kotlin restarts the compatibility byte-buffer encoder and shows:

> Using compatibility export mode.

If even fallback cannot run, export stops only and gameplay continues.

### Vulkan extraction method

The first Vulkan pass is a real staging-copy readback:

Vulkan `screen_infos[0]` image view -> offscreen present frame -> `vkCmdCopyImageToBuffer` staging buffer -> mapped RGBA bytes -> existing Kotlin MediaCodec/UDP pipeline

This is now the compatibility fallback, not the preferred Vulkan path. The fallback renders only the primary top screen into an offscreen `Frame`, copies the image into a mapped staging buffer, and submits those bytes through the same `NoctDockTopScreenExportSubmitFrame(...)` callback used by OpenGL readback.

This path is throttled before readback using the active export FPS, so Vulkan does not run the staging copy every emulation frame. The default remains `800x480` at `30fps`, and the same auto-safety rules can cap FPS or step down resolution if Vulkan copy cost is too high.

The copy currently calls `scheduler.Finish()` before reading mapped data, so it is synchronous for correctness. If that cost is too high on device, the next Vulkan step should replace this with a small ring of staging buffers/fences or an Android hardware-buffer / MediaCodec surface path.

### Vulkan safety and fallback

If Vulkan staging allocation or export setup fails, native code reports:

> NoctDock Vulkan export could not start. Playing normally.

The native bridge disables export, Kotlin stops only the NoctDock exporter, and local Azahar gameplay continues. The bottom screen and touch remain on the handheld. Existing Android secondary-display support remains separate.

### Stream Watch Vulkan metrics

Stream Watch keeps the existing shared encoder/network metrics and adds Vulkan-friendly fields:

- `rendererBackend = Vulkan`
- `exportPath = VULKAN_ENCODER_SURFACE` or `VULKAN_READBACK_FALLBACK`
- `vulkanAvailable`
- `vulkanBlocker`
- `vulkanSurfaceBridgeAttempted`
- `vulkanSurfaceBridgeSuccess`
- `vulkanSwapchainCreated`
- `vulkanAhbBridgeAttempted`
- `vulkanAhbBridgeSuccess`
- `vulkanCopyRenderAvgMs`
- `vulkanAcquireMs`
- `vulkanPresentMs`
- `vulkanExportActive`
- `vulkanCopyAvgMs`
- `vulkanCopyMaxMs`
- `readbackAvgMs`
- `readbackMaxMs`
- `exportQueueDepth`
- `droppedExportFrames`

The older `glReadPixelsAvgMs` / `glReadPixelsMaxMs` keys are still emitted for compatibility with the current watcher script, but Vulkan reports should prefer the generic `readback*` or `vulkanCopy*` fields.

### Vulkan known risks

- The preferred Vulkan path depends on whether Android accepts a `VkSurfaceKHR`/swapchain over the MediaCodec input `ANativeWindow` on the device.
- Some Android Vulkan surface formats may need channel swizzling. The implementation converts common BGRA swapchain formats back to RGBA before handing frames to the encoder.
- Only the primary top screen is exported. Stereoscopic 3D and bottom-screen export remain out of scope.
- If the Vulkan encoder surface target fails, Vulkan falls back to the existing real staging readback path.
- The final goal is still a GPU surface / encoder path that avoids CPU readback for both backends where possible.

### OpenGL and Vulkan comparison

- OpenGL stable path: export framebuffer + `glReadPixels()` + shared Kotlin encoder/UDP.
- Vulkan preferred path: NoctDock-only `PresentWindow` + MediaCodec input surface + shared Kotlin encoder/UDP.
- Vulkan fallback path: offscreen present frame + `vkCmdCopyImageToBuffer` staging readback + shared Kotlin encoder/UDP.
- Both paths use the same NoctDock packets, metadata, receiver validation, heartbeats, STOP packets, bounded encoder queue, and Stream Watch reporting.
- Both paths use the same performance profiles and gameplay-first safety controller.

### Runtime fallback

The exporter now has internal states:

- `IDLE`
- `STARTING`
- `EXPORTING`
- `RECEIVER_UNREACHABLE`
- `ENCODER_ERROR`
- `FRAME_READBACK_TOO_SLOW`
- `STOPPED`

Before export starts, Azahar validates the receiver address/port and sends a NoctDock `CONNECTION_TEST` packet. The NoctDock receiver already echoes this packet, so Azahar can detect a missing receiver before enabling the renderer hook. If setup fails, the game continues normally and the UI shows:

> NoctDock screen is not available. Playing normally.

If export fails after gameplay starts, Azahar stops NoctDock export only, keeps emulation running, and shows:

> NoctDock 3DS Mode stopped. Playing normally.

## Test Checklist

Build test completed:

```text
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk ./gradlew :app:assembleVanillaDebug
BUILD SUCCESSFUL
```

Device testing still needed:

See `NOCTDOCK_AZAHAR_TESTING.md` for the full RP6 checklist.

