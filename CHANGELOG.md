<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# AdbDeck Changelog

## [Unreleased]
### Added
- **App Tab** — Package selector with auto-detection of project `applicationId`, package details display, app lifecycle actions (open, force stop, kill & restart, app info, clear data, uninstall), grant/revoke all permissions, and per-permission management dashboard with filtering
- **All Apps Tab** — Searchable, filterable list of all installed apps with sorting, right-click context menu for quick actions, and filter by user/system/debuggable apps
- **Deep Links & Intents Tab** — Deep link launcher with history and bookmarks, custom intent builder with support for extras, and unified bookmarks panel for both links and intents
- **Device Settings Tab** — Toggle animations, layout bounds, dark mode, don't keep activities, stay awake, show refresh rate, and more. Font size and display density sliders. Screen rotation controls. Simulate process death and trim memory
- **Commands Tab** — Run arbitrary ADB/shell commands, save frequently used commands, console output with timestamps, and command history navigation with arrow keys
- **Find Action integration** — Force Stop, Clear Data, Kill & Restart, Open App, Toggle Animations, and Simulate Process Death available via Cmd+Shift+A
- Device selection via Android Studio's toolbar device dropdown
- Per-project persistent state for bookmarks, saved commands, and preferences
