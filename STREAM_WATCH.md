# NoctDock Stream Watch

Stream Watch is a **local LAN debug tool** for Azahar 3DS export. It helps developers and testers inspect encoder/export metrics from a laptop or terminal on the same Wi‑Fi network. It is **not** part of the main NoctDock sender/receiver UDP protocol (port `45454`).

## What it is for

- Watch live export metrics while testing **NoctDock-Azahar** 3DS Mode on a Retroid.
- Compare OpenGL vs Vulkan export paths, readback fallbacks, frame pacing, and network send health.
- Capture a short rolling summary via `/report` for bug reports (still local-only; no cloud upload).

Stream Watch does **not** include game paths, ROM names, usernames, or account data. While enabled, metrics are visible to anything on your **local network** — disable it when finished.

## Where the code lives

| Piece | Location |
| --- | --- |
| HTTP debug server + metrics | **NoctDock-Azahar** fork (not this repo) |
| Python watcher script | `tools/noctdock_stream_watch.py` in the Azahar fork |
| Full checklist + metric glossary | [`NOCTDOCK_AZAHAR_TESTING.md`](NOCTDOCK_AZAHAR_TESTING.md) (section *NoctDock Stream Watch*) |
| Integration design notes | [`NOCTDOCK_AZAHAR_INTEGRATION.md`](NOCTDOCK_AZAHAR_INTEGRATION.md) |

Clone or build the Azahar fork beside this repo if you want to run the script, for example:

```text
../NoctDock-Azahar/tools/noctdock_stream_watch.py
```

Default debug port: **45456** (separate from NoctDock discovery/stream port **45454**).

## Quick start for testers

1. Install the **NoctDock-Azahar** build on the Retroid (see Azahar testing doc).
2. On the Retroid: **Azahar home → NoctDock 3DS Mode → Export Settings → NoctDock Stream Watch** → enable (read the LAN warning).
3. Find the Retroid IP: Android **Wi‑Fi → connected network → IP address**.
4. On a laptop on the **same LAN**, from the Azahar repo:

```bash
python3 tools/noctdock_stream_watch.py --host RETROID_IP --port 45456
```

If Server-Sent Events are blocked on your network, poll instead:

```bash
python3 tools/noctdock_stream_watch.py --host RETROID_IP --port 45456 --poll
```

### HTTP endpoints

| Endpoint | Purpose |
| --- | --- |
| `GET /health` | Server alive |
| `GET /metrics` | Current JSON snapshot |
| `GET /watch` | SSE: one JSON snapshot per second |
| `GET /report` | Last ~5 minutes summary + recent events |

### Metrics to watch first

- `exportState` — exporter state
- `exportPath` — e.g. `OPENGL_ENCODER_SURFACE`, `VULKAN_ENCODER_SURFACE`, readback fallbacks
- `encoderSurfaceActive` — MediaCodec Surface input active
- `rendererBackend` — OpenGL vs Vulkan
- `vulkanAvailable` / `vulkanBlocker` — Vulkan path health

See [`NOCTDOCK_AZAHAR_TESTING.md`](NOCTDOCK_AZAHAR_TESTING.md) for the full glossary and release checklist items.

## Cleanup

- Disable Stream Watch in Azahar export settings when done.
- Confirm normal 3DS gameplay works with Stream Watch off.

## App support reports (sender / receiver)

For **NoctDock sender or receiver** bugs (not Azahar export), use **Settings → System Status → Copy support report** in either app. That copies device info, System Status fields, and a ring buffer of recent in-app logs for pasting into GitHub issues. See [`DEVICE_TESTING.md`](DEVICE_TESTING.md).
