# Contributing to NoctDock

Thank you for helping improve NoctDock. This project is a local-only Android dock for handhelds and TV/phone receivers. Contributions should preserve privacy, stability, and real streaming behaviour.

## Before you start

- Read [`README.md`](README.md) for product scope and build commands.
- Read [`ARCHITECTURE.md`](ARCHITECTURE.md) for module layout and data flow.
- Read [`DEVICE_TESTING.md`](DEVICE_TESTING.md) before marking UI or streaming changes as done.
- For Azahar / 3DS Mode work, read [`NOCTDOCK_AZAHAR_INTEGRATION.md`](NOCTDOCK_AZAHAR_INTEGRATION.md) (sender **Launch** vs **Launch in 3DS Mode**) and [`NOCTDOCK_AZAHAR_TESTING.md`](NOCTDOCK_AZAHAR_TESTING.md).

## What we accept

- Bug fixes with clear reproduction steps.
- UI polish that does not change intended flows without discussion.
- Protocol or discovery fixes that keep wire compatibility or document version bumps.
- Tests for logic in `:noctdock-core` and module unit tests.
- Documentation improvements.

## What to avoid

- Cloud accounts, analytics, ads, or internet-backed features.
- Fake metrics, stub encoders, or placeholder streaming state in production paths.
- Breaking changes to pairing trust, DataStore keys, or packet layouts without a protocol version plan.
- Large drive-by refactors unrelated to the issue.
- Logging per frame, per packet, or per audio buffer in release/perf builds.

## Development setup

Requirements:

- Android SDK with compile/target SDK 36 and minSdk 29 (see `gradle/libs.versions.toml`).
- JDK 17+ for Gradle.
- Linux, macOS, or Windows with Android platform tools for device install.

```sh
./gradlew clean
./gradlew :noctdock-core:test
./gradlew :noctdock-sender:assembleDebug
./gradlew :noctdock-receiver:assembleDebug
./gradlew test
```

Use `./gradlew` on Linux/macOS; on Windows use `.\gradlew.bat` with the same task names.

### Build variants

| Variant | Purpose |
|--------|---------|
| `debug` | Development, debuggable, verbose stream logs via `NoctLog` |
| `release` | Internal production-like APK, R8 minify, reduced log noise |
| `perf` | Latency/smoothness comparison without R8 |

Do not treat debug APKs as the final word on latency. Compare perf/release on real hardware when changing streaming code.

## Code style

- **Kotlin** in `com.glowseed.noctdock.*` packages; match surrounding formatting (4-space indent, no wildcard imports).
- Prefer extending types in `:noctdock-core` when both sender and receiver need the same behaviour.
- Keep sender UI in `noctdock-sender/.../sender/` Game Hub and screen files; avoid duplicating protocol parsing in UI layers.
- Use `NoctLog` instead of raw `android.util.Log` in app code.
- Comments should explain *why* (lifecycle, MediaProjection, UDP fragmentation, trust rules), not restate obvious code.
- Avoid `TODO`, `hack`, `for now`, and `temporary` in production code; open a GitHub issue instead.

### Formatting (Spotless + ktlint)

Formatting is enforced with [Spotless](https://github.com/diffplug/spotless) and ktlint (Compose-aware rules).

```sh
./gradlew spotlessApplyAll   # fix formatting before commit
./gradlew spotlessCheckAll   # CI uses this; must pass on PRs
```

CI runs `spotlessCheckAll` on every push and pull request (see `.github/workflows/ci.yml`).

## Testing expectations

- Run `:noctdock-core:test` for any core change.
- Run module tests when touching sender/receiver logic.
- Manually verify on at least one handheld and one receiver when changing discovery, pairing, streaming, audio, Screen Cloak, or Game Hub navigation.

Add unit tests when you touch:

- Packet encode/decode (`VideoPackets`, `PacketCodec`)
- Profile negotiation and device capability mapping
- Pairing/trust reducers
- Diagnostics export redaction
- Azahar contract constants and preflight

## Pull request checklist

- [ ] Behaviour unchanged unless the PR description explains the intentional UX change.
- [ ] No secrets, keystore files, or personal device IDs committed.
- [ ] Diagnostics exports still omit installed app names by default.
- [ ] `./gradlew :noctdock-core:test` passes.
- [ ] Sender and receiver debug builds assemble.
- [ ] `DEVICE_TESTING.md` updated if manual steps change.

## Azahar fork (separate repo)

NoctDock Azahar lives in a **separate GPLv2 fork** (`NoctDock-Azahar`). Do not merge emulator GPL sources into this repo. Sender/receiver changes that affect 3DS Mode must update:

- `noctdock-core/.../NoctDockAzaharContract.kt`
- `NOCTDOCK_AZAHAR_INTEGRATION.md`
- `NOCTDOCK_AZAHAR_TESTING.md`

Preserve upstream GPL notices in the fork; see the fork `NOTICE` and changelog.

## License

NoctDock Sender and Receiver are **Apache-2.0** (see [`LICENSE`](LICENSE)). Third-party Android libraries remain under their respective licenses. **NoctDock Azahar** is GPLv2 in the separate fork tree only.

## Questions

Open a GitHub issue with:

- Device model and Android version
- Sender vs receiver
- Build variant (debug / release / perf)
- Whether the issue is discovery, pairing, video, audio, or UI

Redact home network details if you paste logs. On sender or receiver, open **Settings → System Status → Copy support report** and paste into the GitHub issue (includes System Status fields and recent in-app logs; no installed app names or ROM paths). For Azahar 3DS export debugging, see [`STREAM_WATCH.md`](STREAM_WATCH.md).
