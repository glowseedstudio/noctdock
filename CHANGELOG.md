# Changelog

All notable changes to NOCTDOCK are documented here.

## [0.2.1] — 2026-06-03

### Documentation

- **User guide** and **architecture** updated to match the Game Hub layout shipped in **0.2.0**: **bottom mode dock** navigation (replacing outdated “top bar” wording) and the **connected-screen status** pill on the **Screens** tab.
- **Contributing** guide: list of user-facing docs to keep in sync when behaviour changes.

### Notes

- Documentation-only release — no APK or protocol changes.

## [0.2.0] — 2026-05-21

### Game Hub layout

Redesigned the sender home screen around your games and apps.

- Mode dock (Home, Library, Screens, Console Modes, Settings) moved to the **bottom centre**
- Controller hints sit **bottom-left** as plain coloured A/B/X/Y labels — no wrapper pill
- Main content uses the full height above the dock
- When a screen is paired, the connected-screen status pill moves to the **Screens** tab instead of sitting over Home

### Controller navigation

- Switching tabs lands focus on the **top of that panel** (first tile, first app, filters, or list item)
- Up from the mode dock enters content; Down from the last row returns to the dock
- Fixed grid paging: moving to page 2 with the D-pad no longer snaps back to page 1

### First-run & settings

- Controller layout picker on first launch (Xbox / Nintendo face-button diagram)
- Launcher layout setting: Grid or Cover shelf

### Visual polish

- Richer game card glass and softer, tighter focus glow
- Premium library filter chips
- Larger controller hint text

### Technical

- Extracted `NoctLauncherModeDock` with unit tests
- Pager focus sync only follows user swipes, not programmatic page changes

## [0.1.0]

Initial release — sender and receiver apps with Game Hub, library, screen pairing, and console mode streaming.
