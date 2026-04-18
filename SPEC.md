# AdbDeck — Product Specification

> An Android Studio plugin that provides quick access to ADB operations developers actually need — the ones Android Studio doesn't already surface well.

---

## 1. Vision & Goals

**Problem:** Android developers repeatedly context-switch to a terminal for ADB commands that Android Studio doesn't expose through its UI — toggling developer settings, managing permissions, testing deep links, simulating edge cases, and clearing app data.

**Solution:** AdbDeck is a focused tool window with tabbed panels for the ADB operations Android Studio lacks. It follows IntelliJ's native UI patterns and complements (never duplicates) built-in tools.

**Target Users:** Android developers using Android Studio (or IntelliJ with Android plugin).

**Design Principles:**
- **Complementary** — Don't reimplement what Android Studio already does well (Device File Explorer, screenshots, screen mirroring, logcat, layout inspector, database inspector). Instead, fill the gaps.
- **Native feel** — Use IntelliJ platform components (`ActionToolbar`, `JBTable`, `ComboBoxAction`, Kotlin UI DSL). No custom widgets that look out of place.
- **One-click operations** — Most actions should be a single click or toggle.
- **Non-blocking** — All ADB operations run on background threads; UI stays responsive.
- **Context-aware** — Disable or hide actions unsupported by the selected device's API level.
- **Safe** — Destructive actions require confirmation (configurable).

---

## 2. Core Concepts

| Concept | Description |
|---|---|
| **Device Selection** | Reads the active device from Android Studio's toolbar device dropdown via `ExecutionTargetManager`. No custom device selector. |
| **Package Selector** | A `ComboBox` in the App tab, auto-detected from the project's `applicationId`, with device-aware package listing. |
| **Tab** | A focused panel within the tool window (App, All Apps, Deep Links, Settings, Commands). |
| **Action** | Key operations are registered as `AnAction`, discoverable via `Find Action` (Cmd+Shift+A). |
| **ActionContext** | Interface providing access to selected device, package, console, and project — shared by all tabs. |

---

## 3. What We DON'T Build (Android Studio Already Handles These)

These features exist natively in Android Studio. AdbDeck should **not** duplicate them:

| Feature | Android Studio Tool |
|---|---|
| Device list & management | Device Manager, toolbar device selector |
| Wireless debugging pairing | Device Manager → Pair via Wi-Fi |
| Screenshot & screen recording | Logcat toolbar, Running Devices window |
| Screen mirroring | Running Devices window |
| File browsing, pull, push | Device File Explorer |
| Database inspection | App Inspection → Database Inspector |
| Network inspection | App Inspection → Network Inspector |
| Layout inspection | Layout Inspector |
| APK install (project build) | Run/Debug configurations |
| Port forwarding | Handled via App Inspection / terminal |

---

## 4. Feature Breakdown

### 4.1 Device Selection

AdbDeck does **not** render its own device selector. It reads the device from **Android Studio's toolbar device dropdown** via `ExecutionTargetManager` through the `DeviceService` project service. When no device is connected, the tool window shows a "No device connected" empty state and polls for device availability.

### 4.2 App Tab

Core app lifecycle and permission management. The **package selector** is embedded in this tab only — other tabs don't need it.

#### Package Selector

`ComboBox` at the top of the App tab showing all installed packages, grouped:
1. **Project packages** (bold) — auto-detected from `applicationId` via `AndroidFacet` / `GradleAndroidModel`
2. **User-installed apps** — alphabetical
3. **── separator ──**
4. **System apps** — alphabetical

Remembers last selection per device. Changing the package auto-refreshes the permission dashboard and package details.

#### Package Details

Below the package selector, a key-value grid showing:
- Version, Version Code, Build Type (Debug/Release), Min SDK, Target SDK, Install Date, Update Date, Splits

Auto-fetched from `dumpsys package` when the selected package changes.

#### App Lifecycle Actions

Displayed as **clickable rows** with hover highlighting. Each row shows an icon and label.

| Action | Description | Notes |
|---|---|---|
| **Open App** | Launch the default/launcher activity via `monkey` | Single click |
| **Force Stop** | `am force-stop {pkg}` | Single click |
| **Kill & Restart** | Force stop + relaunch default activity | Single click |
| **App Info** | Open system Settings → App Info on device | Single click |
| **Clear App Data** | `pm clear {pkg}` | Destructive — confirmation dialog |
| **Uninstall** | `adb uninstall {pkg}` | Destructive — confirmation dialog |
| **Grant All Permissions** | Grant all declared dangerous permissions | Single click |
| **Revoke All Permissions** | Revoke all dangerous permissions | Single click |

#### Permission Dashboard

Below the action rows. A `JBTable` with **2 columns**:

| Column | Content |
|---|---|
| Permission Name | e.g., `android.permission.CAMERA` |
| Action | Grant/Revoke button (only enabled for dangerous/runtime permissions) |

- Filterable by: All, Dangerous, Granted, Denied (radio buttons).
- Auto-refreshes when the selected package changes or the tab gains focus.

### 4.3 All Apps Tab

A searchable, filterable list of **all** installed apps on the selected device. No package selector — this tab is for browsing/managing any app.

#### App List

Displayed as a `JBTable`.

| Column | Content |
|---|---|
| App Name | Derived from package name (last segment, capitalized) |
| Package Name | e.g., `com.spotify.music` |
| Version | Version name (or version code if name unavailable) |
| Type | User / System |

#### Filters

Exclusive radio-style selection:

| Filter | Default | Description |
|---|---|---|
| **All** | ❌ OFF | Show all apps |
| **User** | ✅ ON | User-installed (non-system) apps |
| **System** | ❌ OFF | System apps only |
| **Debuggable** | ❌ OFF | Debuggable apps only |

Search bar for fuzzy matching against app name and package name.

- **Refresh** button to re-fetch the list.
- Column sorting via clickable headers.

#### Right-Click Context Menu

| Action | Description | Notes |
|---|---|---|
| **Set as Target Package** | Sets this app as the active package in the package selector | — |
| **Open App** | Launch the app | — |
| **App Info** | Open system Settings → App Info | — |
| **Force Stop** | `am force-stop {pkg}` | — |
| **Clear App Data** | `pm clear {pkg}` | Destructive — confirmation |
| **Uninstall** | `adb uninstall {pkg}` | Destructive — confirmation |
| **Disable / Enable** | `pm disable-user {pkg}` / `pm enable {pkg}` | Toggle based on current state |
| **Grant All Permissions** | Grant all dangerous permissions | — |
| **Revoke All Permissions** | Revoke all dangerous permissions | — |
| **Copy Package Name** | Copy to clipboard | — |

#### Interaction Details

- **Double-click** a row → Sets the app as target package.

### 4.4 Deep Links & Intents Tab

No package selector needed.

#### Deep Link Launcher

- **URI combo box** (editable, with dropdown showing recent URIs). Press Enter or click "Open" to launch via `am start -a android.intent.action.VIEW -d {uri}`.
- History auto-saved per project (last 20 entries).
- Bookmark button to save the current URI with a label.

#### Custom Intent Builder

Scrollable form below the deep link input:

| Field | Description |
|---|---|
| Type | Radio buttons: Activity, Broadcast, Service |
| Action | Text field (e.g., `android.intent.action.VIEW`) |
| Data | URI text field |
| Component | Text field (e.g., `com.example/.MainActivity`) |
| Category | Text field |
| Flags | Multi-select dropdown with common intent flags (NEW_TASK, CLEAR_TOP, etc.) |
| Extras | Editable table with columns: Enabled (checkbox), Key, Type (String/Int/Boolean/Float/Long), Value |

"Clear" button resets the form. "Send Intent" executes the built command.

#### Bookmarks Panel

Sticky panel at the bottom showing a unified table of saved deep link and intent bookmarks:

| Column | Content |
|---|---|
| Type | "Link" or "Intent" |
| Label | User-given bookmark name |
| Value | URI (for links) or intent type (for intents) |

- Single-click populates the form. Double-click a link bookmark opens it immediately.
- Right-click context menu: Open/Send, Copy URI, Copy Label, Edit Label, Remove.
- Intent bookmarks can be saved with full intent state (type, action, data, component, category, flags, extras).

### 4.5 Device Settings Tab

No package selector needed — combines device-level settings with testing utilities.

A scrollable panel with IntelliJ-style `TitledSeparator` sections and `JBCheckBox` toggle rows.

#### Developer Options

| Setting | ADB Mechanism |
|---|---|
| Animations (all 3 scales) | `settings put global window_animation_scale` etc. |
| Layout Bounds | `setprop debug.layout` + `service call activity` |
| Show Overdraw | `setprop debug.hwui.overdraw` + restart |
| Don't Keep Activities | `settings put global always_finish_activities` |
| Stay Awake (while charging) | `settings put global stay_on_while_plugged_in` |
| Show Refresh Rate | `settings put system show_refresh_rate_overlay` |
| Show Surface Updates | `setprop debug.hwui.show_dirty_regions` |
| Profile HWUI Rendering | `setprop debug.hwui.profile` |

#### Display & Appearance

| Setting | Control | ADB Mechanism |
|---|---|---|
| Dark Mode | Checkbox toggle | `cmd uimode night yes/no` |
| Font Size | Slider (4 stops: 0.85, 1.0, 1.15, 1.3) | `settings put system font_scale` |
| Display Size | Slider (5 stops with ±20% range) | `wm density` |

#### Configuration Changes

| Control | Description |
|---|---|
| Rotate → | Rotates screen 90° clockwise |
| Auto-Rotate | Enables auto-rotate |

#### Process & Memory

| Control | Description |
|---|---|
| Simulate Process Death | `am kill {pkg}` — app must be in background |
| Trim Memory | Dropdown of levels (Running Moderate through Complete) + Send button |

All toggles refresh their state from the device when the tab gains focus or the refresh button is clicked.

### 4.6 Custom Commands Tab

| Feature | Description |
|---|---|
| **Shell Toggle** | Checkbox: "Shell command" — when checked, prepends `adb shell`; when unchecked, sends raw ADB command. |
| **Command Input** | `JBTextField` with `$` prefix. Enter key or "Run" button executes. |
| **Save Button** | Bookmark icon to save current command with a name. |
| **Saved Commands** | `JBTable` with Name and Command columns. Single-click loads into input, double-click runs. Right-click: Run, Copy Command, Copy Name, Remove. |
| **Console Output** | `JTextArea` with IDE editor font and colors. Timestamped entries. Copy and Clear buttons. Auto-trims at 50 entries. |
| **History Navigation** | ↑/↓ arrow keys navigate command history in the input field. |

---

## 5. UI / UX Design

### 5.1 Tool Window Layout

AdbDeck registers as a **right-side tool window**. Uses `ContentManager` with tabs. Shows "No device connected" empty state when no device is available, automatically transitioning to full UI when a device connects.

```
┌──────────────────────────────────────────────────────────────┐
│  AdbDeck                                             [⚙️]   │
├──────┬──────────┬────────┬──────────┬──────────────────────────┤
│ App  │ All Apps │ Links  │ Settings │ Commands                 │
├──────┴──────────┴────────┴──────────┴──────────────────────────┤
│                                                              │
│  (Tab content here)                                          │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

Tab selection triggers data refresh for the active tab (permissions for App, app list for All Apps, toggle states for Settings).

### 5.2 Interaction Patterns

| Pattern | Behavior |
|---|---|
| **Single-click action** | Executes immediately. Balloon notification on success/failure. |
| **Toggle** | Reads current state on tab focus, then toggles. |
| **Destructive action** | Confirmation dialog (configurable via settings). |
| **Form action** | Enter key submits. |
| **Device disconnected** | Empty state: "No device connected". Actions disabled. Auto-reconnects when device appears. |
| **Console logging** | All ADB commands and their output are logged to the Commands tab console. |

### 5.3 Notifications & Feedback

- **Success:** Non-intrusive balloon — e.g., `✓ Done` or first 100 chars of output.
- **Error:** Error balloon with stderr summary (first 100 chars).
- **Console:** All commands logged with timestamps to the Commands tab console panel.

---

## 6. Keyboard Shortcuts & Actions

All operations are registered as `AnAction` under the `AdbDeck.ActionGroup`. Discoverable via **Find Action** (`Cmd+Shift+A`) by searching "AdbDeck:".

| Action ID | Description |
|---|---|
| `AdbDeck.ForceStop` | Force stop current app |
| `AdbDeck.ClearData` | Clear current app's data (with confirmation) |
| `AdbDeck.KillAndRestart` | Kill & restart current app |
| `AdbDeck.OpenApp` | Launch current app |
| `AdbDeck.ToggleAnimations` | Toggle device animations on/off |
| `AdbDeck.SimulateProcessDeath` | Kill the app process to test state restoration |

No default keybindings — users bind their own via `Settings → Keymap → AdbDeck`.

---

## 7. Persistent State

Per-project state stored in `.idea/adbdeck.xml` via `PersistentStateComponent`:

| Setting | Description | Default |
|---|---|---|
| **Last Package Name** | Last selected package | — |
| **Last Package Per Device** | Per-device package memory | — |
| **Auto-Detect Package** | Whether to auto-detect from project | `true` |
| **Confirm Destructive Actions** | Show confirmation for Clear Data, Uninstall | `true` |
| **Console History Size** | Number of commands to keep | 50 |
| **ADB Path Override** | Manual ADB path (empty = auto-detect) | — |
| **Deep Link History** | Recent URIs (last 20) | — |
| **Deep Link Bookmarks** | Saved label + URI pairs | — |
| **Intent Bookmarks** | Saved custom intents with full state | — |
| **Saved Commands** | Named ADB commands | — |

---

## 8. Architecture

| Aspect | Approach |
|---|---|
| **ADB Communication** | `AdbController` project service: resolves ADB binary from Android SDK, executes commands via `ProcessBuilder` with 30s timeout. Uses `ddmlib` for device listing. |
| **Threading** | All ADB operations on pooled threads via `ApplicationManager.executeOnPooledThread`. UI updates via `invokeLater`. |
| **State Management** | `AdbDeckStateService` — per-project `PersistentStateComponent` in `.idea/adbdeck.xml`. |
| **Device Selection** | `DeviceService` reads `ExecutionTargetManager.getActiveTarget()` for the selected `AndroidExecutionTarget`. |
| **Package Detection** | `PackageDetectionService` inspects `AndroidFacet` / `GradleAndroidModel` on app-type modules to extract `applicationId`. |
| **Command Layer** | Static `object` command classes: `AppCommands`, `PermissionCommands`, `DeviceSettingsCommands`, `TestingCommands`, `IntentCommands`, `AppListCommands`. All blocking, called from background threads. |
| **Tool Window** | `AdbDeckToolWindowFactory` creates tabs via `ContentFactory`. `AdbDeckToolWindow` implements `ActionContext` and `Disposable`. Each tab is a separate `JPanel` subclass. |
| **Action Registration** | Actions registered in `plugin.xml` under `AdbDeck.ActionGroup`. Each extends `BaseAdbAction` which handles device/package resolution and background execution. |
| **Shared Utilities** | `AdbActionRunner` extension functions on `ActionContext` for the common execute → log → notify pattern. `Notifications.kt` for balloon notifications via the registered notification group. |
| **Reusable Components** | `SettingToggleRow` (checkbox with async read/write), `iconButton` (square icon-only button with hover effect), `ConsolePanel` (timestamped log output), `PackageSelectorPanel` (grouped combo with separator). |

---

## 9. Plugin Dependencies

| Dependency | Purpose |
|---|---|
| `com.intellij.modules.platform` | Core IntelliJ platform |
| `org.jetbrains.android` | Android plugin — provides `AndroidFacet`, `GradleAndroidModel`, `AndroidSdkUtils`, `AndroidExecutionTarget`, `ddmlib` |
