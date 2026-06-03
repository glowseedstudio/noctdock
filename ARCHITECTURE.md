# NoctDock architecture

NoctDock mirrors a trusted Android handheld to a local receiver over Wi‑Fi. There is no cloud control plane, account system, or internet dependency for play.

## Module overview

```
┌─────────────────────┐     UDP + NSD (LAN)      ┌─────────────────────┐
│   noctdock-sender   │ ◄──────────────────────► │  noctdock-receiver  │
│  (handheld / RP6)   │   pairing, video, audio  │  (TV / phone / tab) │
└──────────┬──────────┘                          └──────────┬──────────┘
           │                                                │
           └────────────────────┬───────────────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │     noctdock-core     │
                    │ protocol, profiles,   │
                    │ discovery models, UI  │
                    │ tokens, policies      │
                    └───────────────────────┘
```

| Module | Role |
|--------|------|
| `:noctdock-core` | Shared Kotlin library: UDP packet codec, stream profiles, discovery/advertisement models, pairing trust helpers, device capability detection, Screen Cloak policy, audio jitter buffer, Compose design system, Azahar intent contract |
| `:noctdock-sender` | Handheld app: Game Hub UI, Console Mode service, MediaProjection capture, MediaCodec encode, Screen Cloak overlay, library, settings, diagnostics |
| `:noctdock-receiver` | Receiver app: NSD advertisement, pairing UI, MediaCodec decode, fullscreen Console View, feedback packets |

## Package map (logical)

Paths are under `src/main/java/com/glowseed/noctdock/`:

| Area | Core | Sender | Receiver |
|------|------|--------|----------|
| Protocol / packets | `core/VideoPackets.kt`, `PacketCodec` | `VideoPipeline.kt` | `VideoReceiverPipeline.kt` |
| Discovery | `DiscoveryProtocol.kt`, `DiscoveryLifecycle.kt` | `SenderRuntime.kt` | `ReceiverRuntime.kt` |
| Profiles / negotiation | `StreamProfile.kt`, `StreamOptimization.kt` | settings + Console Modes UI | decode caps feedback |
| Device capabilities | `DeviceCapabilities.kt` | profile recommendations | — |
| Audio | `AudioSync.kt` | capture in `VideoPipeline` / runtime | jitter buffer in receiver pipeline |
| Screen Cloak | `ProductModels.kt` (policy types) | `ScreenCloak.kt` controller | — |
| UI | `NoctDesignSystem.kt` | `GameHub*.kt`, `MainActivity.kt` | `MainActivity.kt` |
| Azahar contract | `NoctDockAzaharContract.kt` | launch + diagnostics | — |
| Diagnostics | `ProductModels.kt` (`DiagnosticsSnapshot`) | System Status screen | minimal status |

Large file moves are intentionally avoided; names above are navigation anchors for contributors.

## Discovery and pairing

1. Receiver advertises `_noctdock._udp` via Android NSD with TXT records (identity, protocol version, codecs, resolution caps).
2. Sender browses the LAN, sorts results, and shows them under **Screens**.
3. First connect uses a 4-digit code; `PairingTrust` in core validates and stores trusted receiver identity in DataStore.
4. Trusted receivers skip pairing on later sessions; sender Home shows launcher grid instead of portal.

`DiscoveryLifecycleReducer` maps low-level states (advertising, pairing, streaming) to a single lifecycle enum for UI and diagnostics.

Manual host/port connect exists only under advanced settings when mDNS is blocked.

## Streaming path (Console Mode)

1. User grants MediaProjection; sender starts a foreground service (Android 14+ `FOREGROUND_SERVICE_MEDIA_PROJECTION`).
2. Virtual display feeds a hardware MediaCodec encoder (AVC default; HEVC when negotiated).
3. `PacketCodec` fragments encoded frames under MTU limits; receiver reassembles with bounded windows.
4. Receiver submits Annex-B style access units to MediaCodec decoder and renders to a Surface.
5. Heartbeats and `RECEIVER_FEEDBACK` packets drive adaptive bitrate, health grading, and reconnect policy in core optimizers.
6. Optional PCM audio (Android 10+ playback capture) uses separate audio packet types and `AudioJitterBuffer` on the receiver.

Stop/teardown must release projection, encoder, UDP sockets, and Screen Cloak overlays in service `onDestroy` paths.

## Stream profiles

`StreamProfiles` in core defines Performance → Cinema (and hidden test modes). Each profile carries resolution, FPS, bitrate targets, latency priority, and adaptive bitrate floors.

`StreamNegotiator` picks AVC vs HEVC from sender encoder caps and receiver decoder advertisement. UI copy explains HEVC→AVC fallback without exposing MIME strings to users.

## Game Hub UI (sender)

Single-activity Compose shell with inline panels in the main area and chrome at the top and bottom:

- **Bottom mode dock** (`GameHubLauncherModeDockBar`): Home, Library, Screens, Console Modes, Settings — primary navigation (replaces the old top tab row).
- **Top status bar** (`SenderStatusBar`): clock and connectivity indicators.
- `GameHubHomeScreen` owns D-pad routing (`GameHubFocusZone`, `GameHubInput`). The focus zone is still named `TopBar` in code but maps to the **mode dock**.
- Portal (pair/connect orb) vs launcher grid is mode-driven via `GameHubHomeMapper`
- When a screen is trusted, the connected-screen **status pill** lives on the **Screens** panel (not over the Home launcher grid).
- Settings builds rows in `GameHubSettingsFocus.kt`; full diagnostics remain on a dedicated System Status route
- Trusted receiver labels on the Screens list use a tiled linear gradient (`TileMode.REPEAT`) driven by one shared phase
- **Dock portal** card and glass modal sheets use the same outer rotating gradient ring (`modalGlow` on `gameHubFocusRing`)
- **Library** filter chips show the gradient ring on the active filter (not only D-pad focus)
- **Accent primary buttons** (`NoctPrimaryButton`, `NoctPrimaryConsoleButton`) use a shared glow/fill chrome in `NoctDesignSystem.kt`

**Gradient performance:** `GameHubGradientPhaseProvider` exposes `LocalGameHubGradientPhase` from a single `rememberInfiniteTransition`. Only composables that read the local (screen pill, favourite stars, focused rings, open modals) recompose each animation frame—not the full launcher/library grid.

Focus defaults to **Home** in the mode dock on cold start so horizontal navigation works without pressing Up first. **Back** from deeper focus returns to the mode-dock Home anchor before exiting the app.

## Screen Cloak

Sender-only OLED burn-in mitigation. `ScreenCloakController` applies overlay or system-brightness strategies per `ScreenCloakPolicy` in core. Restore paths must run on Console Mode stop; TV picture issues can disable overlay mode via persisted flag.

## NoctDock Azahar (external fork)

3DS top-screen export is implemented in the **NoctDock-Azahar** GPLv2 fork, not in this repo. Sender launches the fork via `NoctDockAzaharContract` intent extras. **Normal Launch** uses `launchAzahar()` → `launchInConsoleMode()` (same screen-capture path as Library **Launch on Screen**). **3DS Mode** uses `launchAzahar3dsMode()` with preflight and the fork’s `THREE_DS_MODE` intent. **Stream Watch** (LAN port `45456`, Python script in the fork) is for export metrics only — see [`STREAM_WATCH.md`](STREAM_WATCH.md) and [`NOCTDOCK_AZAHAR_TESTING.md`](NOCTDOCK_AZAHAR_TESTING.md).

## Logging and diagnostics

- App code logs through `NoctLog`; debug metrics lines are gated by `BuildConfig.NOCT_DEBUG_LOGS`.
- `NoctLog` keeps an in-memory ring buffer (warnings always; debug/info when logged) for **Copy support report** on sender and receiver.
- Release/perf builds disable verbose stream metric spam; counters remain in UI diagnostics.
- `DiagnosticsSnapshot.exportText()` redacts installed app package names by default.
- `formatSupportReport()` in core combines metadata, diagnostics text, and buffered logs for clipboard export (`NoctSupportReport.kt`).

## Testing layout

| Location | Focus |
|----------|--------|
| `noctdock-core/src/test` | Protocol, profiles, pairing, device caps, Screen Cloak policy, Azahar contract |
| `noctdock-sender/src/test` | Game Hub mapper, console modes navigation math |
| `noctdock-receiver/src/test` | Settings persistence, manifest expectations |

Instrumented tests are not required for every change; follow `DEVICE_TESTING.md` for hardware validation.

## Known extension points

- Protocol version bumps: update `ProtocolVersion.Current`, both apps, and tests together.
- New packet types: extend `PacketType` and `PacketCodec` with backward-compatible receivers or version gate.
- New stream profile: add to `StreamProfiles`, capability checks, and Console Modes UI labels.

## Related documents

- [`README.md`](README.md) — user-facing features and build commands
- [`DEVICE_TESTING.md`](DEVICE_TESTING.md) — hardware checklist
- [`NOCTDOCK_AZAHAR_INTEGRATION.md`](NOCTDOCK_AZAHAR_INTEGRATION.md) — fork bridge and export paths
- [`GITHUB_PAGE_DRAFT.md`](GITHUB_PAGE_DRAFT.md) — public project page copy
