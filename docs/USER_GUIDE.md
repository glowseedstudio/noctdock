# NoctDock User Guide

This guide explains what NoctDock is, how the apps fit together, and how to use them day to day. For downloads, see [**GitHub Releases**](https://github.com/glowseedstudio/noctdock/releases/latest). For 3DS top-screen play, also install [**NoctDock Azahar**](https://github.com/glowseedstudio/noctdock-azahar/releases/latest).

---

## What you need

| App | Install on | Package |
| --- | --- | --- |
| **NoctDock Receiver** | TV, Shield, Android TV, tablet, or phone used as the big screen | `com.glowseed.noctdock.receiver` |
| **NoctDock Sender** | Android handheld or phone you play on | `com.glowseed.noctdock.sender` |
| **NoctDock Azahar** *(optional)* | Same handheld, for 3DS top-screen export | `com.glowseed.noctdock.azahar` |

Both devices must be on the **same local Wi‑Fi** (Ethernet on the receiver helps for 1080p). No account, cloud, or internet relay is required.

---

## Quick start

1. Open **Receiver** on the TV or display. Leave it on the waiting screen.
2. Open **Sender** on the handheld. Open **Screens** in the top bar and pick your receiver.
3. On first connect, enter the **4-digit pairing code** shown on the TV. Turn on **Remember this screen** if you want.
4. Open **Console Modes**, pick a profile (start with **Balanced**), and run **Test My Connection** if you plan to use Sharp or Cinema.
5. On **Home**, tap **Enter Console Mode** and approve **screen capture** when Android asks.
6. Play. The TV shows your handheld screen; controls stay on the handheld. Tap **Stop Console Mode** when finished.

**3DS with Azahar:** pair the TV first → **Library** → pick Azahar → **Launch in 3DS Mode** (top screen on TV, bottom screen and touch on handheld).

---

## Sender (handheld) — main areas

The sender is built like a **console launcher**, not a technical dashboard. Use the top bar to switch areas; the main panel below swaps content.

| Tab | What it is for |
| --- | --- |
| **Home** | Pairing portal before a trusted screen; game shelves after trust (Favourites, Recent, emulators, Android games, Azahar tile). **Enter Console Mode** lives here. |
| **Library** | Search, filters, favourites, add apps, launch on handheld or on TV. |
| **Screens** | Find receivers, connect, pair, trust, search again. |
| **Console Modes** | Stream quality presets, sound-related streaming toggles, **Test My Connection**. |
| **Settings** | Look and feel, sound defaults, Screen Cloak, advanced streaming options, **System Status**. |

**D-pad / controller:** one clear focus ring on the active tab and tiles. **Back** once returns focus to the top bar; **Back** again from the Home tab exits the app.

Deep technical detail (codec, packet loss, encoder caps) stays under **Settings → System Status** so the launcher stays calm.

---

## Receiver (TV / display) — what you see

| Phase | Screen |
| --- | --- |
| **Waiting** | “Waiting for handheld” — receiver is advertising on the LAN. |
| **Pairing** | Large **4-digit code** — enter this on the sender. |
| **Active** | Fullscreen **Console View** — your stream. |
| **Interrupted** | Connection dropped — reconnect from the sender. |

**While playing:** tap or D-pad to show a short overlay (quality, sound, settings on phones/tablets). **Fit Screen** vs **Fill Screen** is in receiver settings. Brief Wi‑Fi hiccups show “Reconnecting…” before giving up.

**TV remote:** D-pad works; **Back twice** within about 2 seconds exits the receiver app.

---

## Console Mode and Dock Mode

**Console Mode** captures the handheld screen, encodes it on the device, and sends video over your LAN to the receiver.

1. You tap **Enter Console Mode** (or launch an app to TV from Library).
2. Android asks for **screen recording / MediaProjection** permission — required for capture.
3. A background service keeps the stream alive while you play.
4. The UI switches to **Dock Mode**: black OLED-friendly layout, connected screen name, sound mode, optional play overlay (FPS / battery), **Stop Console Mode**.

Console Mode ends when you stop it, the receiver disconnects, the heartbeat times out, or you revoke screen capture in Android settings.

---

## Pairing and trusted screens

- Receivers on the same Wi‑Fi appear automatically in **Screens**.
- **First time:** TV shows a code; sender enters it.
- **After success:** a **pairing token** is saved locally on both sides so reconnects can skip the code when you use the same trusted screen.
- **Remember this screen** stores name, address, port, and token — nothing is sent to the cloud.
- **Manual connection** (IP + port, default `45454`) is in Settings for unusual networks.

Wrong codes are rate-limited on the receiver to reduce guessing.

---

## Console profiles (quality presets)

Profiles are presets — you do not tune encoders by hand. Higher is not always better; smooth play matters more than peak resolution.

| Profile | Resolution | FPS | When to use |
| --- | --- | ---: | --- |
| **Performance** | 720p | 60 | Weakest networks or underpowered handhelds. |
| **Balanced** | 720p | 60 | **Default** — good starting point. |
| **Quality** | 720p | 60 | Cleaner 720p when the link is solid. |
| **Sharp** | 900p | 60 | Strong Wi‑Fi; needs a good connection test. |
| **Cinema** | 1080p | 60 | Full HD when TV and network can handle it. |

**Sharp** and **Cinema** only appear when your handheld, receiver, and **Test My Connection** support them. The app may use **HEVC** when both sides support it and fall back to **AVC** automatically if needed.

**Console Modes** also includes:

- **Fast response** — lower latency bias.
- **Adaptive picture** — bitrate adapts within the profile range.
- **Battery Saver** — caps bitrate and UI work.
- **Play overlay** — optional FPS/battery chip in Dock Mode.
- **Smooth 60 Hz Helper** — asks for 60 Hz on supported panels during Console Mode.
- **Test My Connection** — LAN probe to the selected receiver before you push resolution.

---

## Sound modes

| Mode | Behaviour |
| --- | --- |
| **Retroid Sound** | Audio stays on the handheld (safest when games block capture). |
| **TV Sound** | Sends app playback audio to the TV when Android allows. |
| **Both** | TV and handheld — useful for testing; echo is possible. |
| **Quiet Mode** | No TV audio; handheld media volume lowered for the session, restored on stop. |

TV Sound and Both need the **audio capture** permission Android shows for playback capture — not your microphone.

---

## Screen Cloak

Dims the **handheld** screen while the **TV** picture stays bright.

| Mode | Effect |
| --- | --- |
| Off | Normal brightness |
| Dim / Dark / Maximum Dark | Progressive dim |

Configure in **Settings**. **Test Screen Cloak** during an active stream checks that the TV did not go dark with the overlay.

---

## App Library

Open from the **Library** tab. Everything comes from **apps installed on your sender** — no cloud library, accounts, or cover-art servers.

- Search, **favourites**, **recently played**
- Emulator detection (RetroArch, Azahar, Dolphin, PPSSPP, etc.) plus manual add
- **Launch** on handheld, **Launch on Screen** (Console Mode), or **Launch in 3DS Mode** for Azahar

---

## NoctDock Azahar (3DS Mode)

Separate emulator app — see the [Azahar repo](https://github.com/glowseedstudio/noctdock-azahar) for install.

- **Launch** — normal Console Mode (whole Android UI stream), then Azahar opens.
- **Launch in 3DS Mode** — checks Azahar is installed, receiver is online and **trusted**, then sends top-screen export only (bottom screen and touch stay on handheld).

Export quality (resolution/FPS) is set **inside Azahar**; codec follows sender negotiation. Optional **bottom-screen dim** while exporting is an Azahar-only setting.

---

## System Status and getting help

**Settings → System Status** on sender and receiver shows discovery, connection, picture, sound, and device capability in plain groups.

- **Copy support report** — redacted diagnostics + recent logs for GitHub issues (no app library names in the report).
- **Latency Test** — on-screen flash pattern for slow-motion camera timing.

When opening an issue, include device model, Android version, receiver type, Wi‑Fi vs Ethernet, and the Console profile you used. See [CONTRIBUTING.md](https://github.com/glowseedstudio/noctdock/blob/main/CONTRIBUTING.md).

**Maintainer note:** NoctDock is a side project maintained in spare time alongside a full-time job. Issues are handled as quickly as possible, but please allow patience — thanks.

---

## Tips

- Start with **Balanced** and only move up after **Test My Connection** passes.
- **Ethernet on the receiver** helps for 1080p and busy Wi‑Fi homes.
- If HEVC struggles, the app should fall back to AVC — check System Status for the active codec.
- Revoking screen capture in Android settings will stop Console Mode immediately.
- For Azahar LAN debug metrics only, see [STREAM_WATCH.md](https://github.com/glowseedstudio/noctdock/blob/main/STREAM_WATCH.md) (off by default; local network only).

---

## More documentation

| Document | Audience |
| --- | --- |
| [README](https://github.com/glowseedstudio/noctdock/blob/main/README.md) | Overview and features |
| [DEVICE_TESTING.md](https://github.com/glowseedstudio/noctdock/blob/main/DEVICE_TESTING.md) | Device QA checklist |
| [NOCTDOCK_AZAHAR_INTEGRATION.md](https://github.com/glowseedstudio/noctdock/blob/main/NOCTDOCK_AZAHAR_INTEGRATION.md) | Integrators / developers |
| [ARCHITECTURE.md](https://github.com/glowseedstudio/noctdock/blob/main/ARCHITECTURE.md) | System design |
