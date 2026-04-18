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
| **Device Selector** | A `ComboBoxAction` in the tool window toolbar to pick the target device. |
| **Package Selector** | A `ComboBoxAction` in the toolbar, auto-detected from the project's `applicationId`, editable. |
| **Tab** | A focused panel within the tool window (App, Apps on Device, Deep Links, Settings, Testing, Commands). |
| **Action** | Every operation is registered as an `AnAction`, discoverable via `Find Action` (Cmd+Shift+A). |

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

AdbDeck does **not** render its own device selector. It reads the device from **Android Studio's toolbar device dropdown** via `ExecutionTargetManager`. This avoids duplication and keeps the UI clean.

### 4.2 App Tab

Core app lifecycle and permission management. The **package selector** is embedded in this tab only — other tabs don't need it.

#### Package Selector

Dropdown at the top of the App tab showing all installed packages, grouped:
1. **Project packages** (bold) — auto-detected from `applicationId`
2. **User-installed apps** — alphabetical
3. **── separator ──**
4. **System apps** — alphabetical

Editable. Remembers last selection per project. Changing the package auto-refreshes the permission dashboard.

#### App Lifecycle Actions

Displayed as a **2-column grid** that resizes properly with the tool window width.

| Action | Description | Notes |
|---|---|---|
| **Open App** | Launch the default/launcher activity | Single click |
| **Force Stop** | `am force-stop {pkg}` | Single click |
| **Kill & Restart** | Force stop + relaunch default activity | Single click |
| **App Info** | Open system Settings → App Info on device | Single click |
| **Clear Data** | `pm clear {pkg}` | Destructive — confirmation dialog |
| **Uninstall** | `pm uninstall {pkg}` | Destructive — confirmation dialog |
| **Grant All Permissions** | Grant all declared dangerous permissions | Single click |
| **Revoke All Permissions** | Revoke all dangerous permissions | Single click |

#### Permission Dashboard

Below the action grid. A `JBTable` with **2 columns only** — keeps the UI clean:

| Column | Content |
|---|---|
| Permission Name | e.g., `android.permission.CAMERA` |
| Action | Grant/Revoke button (only enabled for dangerous permissions) |

- Filterable by: All, Dangerous, Granted, Denied (radio buttons).
- Auto-refreshes when the selected package changes or the tab gains focus.

### 4.3 All Apps Tab

A searchable, filterable list of **all** installed apps on the selected device. No package selector — this tab is for browsing/managing any app. Defaults to showing only user-installed (non-system) apps.

#### App List

Displayed as a `JBTable` (or `JBList` with a custom cell renderer showing app details).

| Column | Content |
|---|---|
| App Name | Human-readable label (derived from package name) |
| Package Name | e.g., `com.spotify.music` |
| Version | Version name |
| Type | User / System (badge/icon) |

#### Filters & Search

- **Search bar** at the top — fuzzy-matches against both app name and package name.
- **Filter chips** in a toolbar below search (toggle on/off):

| Filter | Default | Description |
|---|---|---|
| **User Apps** | ✅ ON | Apps installed by the user (non-system) |
| **System Apps** | ❌ OFF | Pre-installed system apps |
| **Enabled Only** | ✅ ON | Hide disabled apps |
| **Disabled Only** | ❌ OFF | Show only disabled apps |
| **Debuggable** | ❌ OFF | Show only debuggable apps (useful for dev) |

Default view: **User apps, enabled only** — a clean, manageable list. One click to toggle system apps in when needed.

- **Refresh** button in toolbar to re-fetch the list.
- Loading state: indeterminate progress bar while fetching (can take a moment on devices with many apps).

#### Right-Click Context Menu

Right-clicking any app in the list shows a context menu with operations:

| Action | Description | Notes |
|---|---|---|
| **Set as Target Package** | Sets this app as the active package in the toolbar Package Selector — all other tabs now target this app | — |
| **Open App** | Launch the app's default activity | — |
| **Open App Info** | Open system Settings → App Info on device | — |
| **Force Stop** | `am force-stop {pkg}` | — |
| **Clear App Data** | `pm clear {pkg}` | Destructive — confirmation |
| **Clear Cache** | `pm clear --cache-only {pkg}` | API 24+ |
| **Uninstall** | `pm uninstall {pkg}` | Destructive — confirmation. Disabled for system apps unless "Uninstall updates" variant is used. |
| **Disable / Enable** | `pm disable-user {pkg}` / `pm enable {pkg}` | Toggle based on current state |
| **Grant All Permissions** | Grant all dangerous permissions | — |
| **Revoke All Permissions** | Revoke all dangerous permissions | Confirmation |
| **Copy Package Name** | Copy to clipboard | — |

#### Interaction Details

- **Double-click** a row → Sets the app as target package (same as "Set as Target Package" context action).
- **Column sorting** — Click column headers to sort by name, package, type, etc.
- List is fetched via `pm list packages -f` + `dumpsys package` for metadata. Cached and refreshed on tab focus or manual refresh.

### 4.4 Deep Links & Intents Tab

No package selector needed — deep links are launched via `am start` which doesn't require a target package.

| Feature | Description |
|---|---|
| **Deep Link Input** | `TextFieldWithHistory` — enter a URI, press Enter or click ▶ to launch via `am start -a android.intent.action.VIEW -d {uri}`. History auto-saved per project. |
| **Bookmarks** | `JBList` sidebar of saved deep links (label + URI). Add/edit/delete. Clicking a bookmark populates the input field. |
| **Custom Intent Builder** | Expandable form below the deep link input. Fields: Action, Data URI, Component, Flags, Extras (key-value table with type selector: String, Int, Boolean, Long, Float). "Send as Activity / Broadcast / Service" radio buttons. |

### 4.5 Device Settings Tab

No package selector needed — these are device-level settings.

A scrollable list of toggle rows using standard IntelliJ components. Each row shows the setting name and a toggle/control. Current state is read from the device when the tab is focused.

#### Toggles (On/Off)

| Setting | ADB Mechanism | Min API |
|---|---|---|
| Animations (all 3 scales) | `settings put global window_animation_scale` etc. | — |
| Show Layout Bounds | `setprop debug.layout` + restart `systemui` | — |
| Show GPU Overdraw | `setprop debug.hwui.overdraw` | — |
| GPU Profiling Bars | `setprop debug.hwui.profile` | — |
| Don't Keep Activities | `settings put global always_finish_activities` | — |
| Dark Mode | `cmd uimode night yes/no` | API 29+ |
| Stay Awake (while charging) | `settings put global stay_on_while_plugged_in` | — |
| Demo Mode | `settings put global sysui_demo_allowed` + broadcast | API 23+ |

#### Value Controls

| Setting | Control | ADB Mechanism | Min API |
|---|---|---|---|
| Font Scale | Segmented button: S / Default / L / XL | `settings put system font_scale` | — |
| Display Density | Combo box with presets + custom DPI input | `wm density` | — |
| Locale | Dropdown of common locales | `persist.sys.locale` | — |

### 4.6 Testing Utilities Tab

New features specifically for testing edge cases — things developers constantly need but Android Studio doesn't provide.

| Feature | Description | ADB Mechanism |
|---|---|---|
| **Simulate Process Death** | Kill the app process without force-stop, to test `onSaveInstanceState` / restoration | `am kill {pkg}` (requires app to be in background) |
| **Trigger Trim Memory** | Send memory pressure signal at selected level (RUNNING_LOW, RUNNING_CRITICAL, etc.) | `am send-trim-memory {pkg} {level}` |
| **Change Configuration** | Quick buttons to rotate screen, toggle night mode — for testing config change handling | `wm rotation` / `cmd uimode` |

### 4.7 Custom Commands Tab

| Feature | Description |
|---|---|
| **Command Input** | `EditorTextField` with ADB shell syntax highlighting. Auto-prepends `adb -s {device}` (or `adb -s {device} shell` with a toggle). |
| **Saved Commands** | `JBList` sidebar. Save, name, and organize commands. Right-click to edit/delete. Double-click to load into input. |
| **Console Output** | IntelliJ `ConsoleView` at the bottom — shows executed command + stdout/stderr. Supports copy, scroll, clear. Command history navigable with ↑/↓. |
| **Export/Import** | Saved commands and deep link bookmarks exportable as JSON for team sharing via VCS. |

---

## 5. UI / UX Design

### 5.1 Tool Window Layout

AdbDeck registers as a **right-side tool window** (matching Android Studio's convention for auxiliary tools). Uses `ContentManager` with tabs.

```
┌──────────────────────────────────────────────────────────────┐
│  AdbDeck                                             [⚙️]   │
├──────┬──────────┬────────┬──────────┬─────────┬──────────────┤
│ App  │ All Apps │ Links  │ Settings │ Testing │ Commands     │
├──────┴──────────┴────────┴──────────┴─────────┴──────────────┤
│                                                              │
│  (Tab content here — see below)                              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

Tabs overflow into a `>>` dropdown when the window is narrow — this is IntelliJ's default `ContentManager` behavior.

### 5.2 App Tab

```
┌──────────────────────────────────────────────────────────────┐
│ Package: [com.example.myapp                    ▼] [🔄]       │
├────────────────────────┬─────────────────────────────────────┤
│ [▶ Open App          ] │ [⏹ Force Stop                    ] │
│ [🔄 Kill & Restart   ] │ [ℹ App Info                      ] │
│ [🗑 Clear Data       ] │ [📦 Uninstall                    ] │
│ [🔓 Grant All Perms  ] │ [🔒 Revoke All Perms             ] │
├────────────────────────┴─────────────────────────────────────┤
│ Permissions          (All) (Dangerous) (Granted) (Denied) [🔄]│
│ ┌─────────────────────────────────────────────────┬────────┐ │
│ │ android.permission.CAMERA                       │[Revoke]│ │
│ │ android.permission.INTERNET                     │   —    │ │
│ │ android.permission.ACCESS_FINE_LOCATION          │[Grant] │ │
│ └─────────────────────────────────────────────────┴────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### 5.3 All Apps Tab

```
┌──────────────────────────────────────────────────────────────┐
│ 🔍 [Search apps...                                        ] │
│ [✅ User] [System] [✅ Enabled] [Disabled] [Debuggable] [🔄] │
├──────────────────────────────────────────────────────────────┤
│ ┌──────────────┬──────────────────────┬─────────┬──────────┐ │
│ │ App Name     │ Package Name         │ Version │ Type     │ │
│ ├──────────────┼──────────────────────┼─────────┼──────────┤ │
│ │ Spotify      │ com.spotify.music    │ 8.9.0   │ 👤 User  │ │
│ │ Chrome       │ com.android.chrome   │ 124.0   │ 🔄 Updated│ │
│ │ My App       │ com.example.myapp    │ 1.2.3   │ 👤 User  │ │
│ │              │                      │         │          │ │
│ │  Right-click → [Set as Target] [Open] [Stop] [Clear]    │ │
│ │               [Uninstall] [Disable] [Copy Package Name]  │ │
│ └──────────────┴──────────────────────┴─────────┴──────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### 5.4 Deep Links Tab

```
┌──────────────────────────────────────────────────────────────┐
│ ┌──────────────────┐  URI: [______________________] [▶ Open] │
│ │ ⭐ Bookmarks      │                                        │
│ │                  │  ▶ Custom Intent Builder                │
│ │ Home Screen      │  ┌──────────────────────────────────┐   │
│ │ Product Detail   │  │ Action:  [android.intent.action.V│   │
│ │ Login Flow       │  │ Data:    [                       ]│   │
│ │ Settings         │  │ Component: [                     ]│   │
│ │                  │  │ Flags:   [                       ]│   │
│ │                  │  │ Extras:                           │   │
│ │                  │  │  [key    ] [String ▼] [value    ] │   │
│ │                  │  │  [+ Add Extra]                    │   │
│ │                  │  │ Send as: (●) Activity (○) Broad.. │   │
│ │                  │  │              [▶ Send Intent]      │   │
│ │                  │  └──────────────────────────────────┘   │
│ └──────────────────┘                                        │
└──────────────────────────────────────────────────────────────┘
```

### 5.5 Device Settings Tab

```
┌──────────────────────────────────────────────────────────────┐
│ Developer Options                                            │
│  Animations                     [ON ◉ / ○ OFF]              │
│  Show Layout Bounds             [ON ○ / ◉ OFF]              │
│  Show GPU Overdraw              [ON ○ / ◉ OFF]              │
│  GPU Profiling Bars             [ON ○ / ◉ OFF]              │
│  Don't Keep Activities          [ON ○ / ◉ OFF]              │
│  Stay Awake                     [ON ○ / ◉ OFF]              │
│                                                              │
│ Display & Appearance                                         │
│  Dark Mode                      [ON ◉ / ○ OFF]   (API 29+) │
│  Font Scale                     [S] [M] [L] [XL]            │
│  Display Density                [360] [400] [▼ Custom]       │
│  Locale                         [en-US            ▼]         │
│  Demo Mode                      [ON ○ / ◉ OFF]   (API 23+) │
└──────────────────────────────────────────────────────────────┘
```

### 5.6 Testing Utilities Tab

```
┌──────────────────────────────────────────────────────────────┐
│ Process & Memory                                             │
│  [💀 Simulate Process Death]  App must be in background      │
│  [📉 Trim Memory ▼]  RUNNING_LOW | RUNNING_CRITICAL | ...   │
│                                                              │
│ Configuration Changes                                        │
│  [🔄 Rotate →] [↺ Auto-Rotate] [🌙 Toggle Night Mode]       │
└──────────────────────────────────────────────────────────────┘
```

### 5.7 Commands Tab

```
┌──────────────────────────────────────────────────────────────┐
│ ┌──────────────────┐  $ [shell ▼] [_____________________] ▶  │
│ │ 📁 Saved Commands│  ┌──────────────────────────────────┐   │
│ │                  │  │ $ pm list packages | grep myapp  │   │
│ │ List packages    │  │ package:com.example.myapp        │   │
│ │ Dump activity    │  │                                  │   │
│ │ Check battery    │  │ $ getprop ro.build.version.sdk   │   │
│ │                  │  │ 34                               │   │
│ │ [+ Save Current] │  │                                  │   │
│ │ [📤 Export]      │  │                          [Clear] │   │
│ └──────────────────┘  └──────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

### 5.8 Interaction Patterns

| Pattern | Behavior |
|---|---|
| **Single-click action** | Executes immediately. Balloon notification on success/failure. |
| **Toggle** | Reads current state on tab focus, then toggles. Visual state updates immediately (optimistic), reverts if command fails. |
| **Destructive action** | Confirmation dialog. "Don't ask again" option (configurable in Settings). |
| **Form action** | Enter key submits. |
| **Long-running** | Progress indicator in toolbar. Cancel button. |
| **Device disconnected** | Warning banner: "No device selected" with device selector link. Actions disabled. |
| **Unsupported API** | Action grayed out with tooltip: "Requires API {level}+". |

### 5.9 Notifications & Feedback

- **Success:** Non-intrusive balloon — e.g., `✓ App data cleared`.
- **Error:** Error balloon with stderr summary + "Show in Console" link (switches to Commands tab console).
- **Progress:** Indeterminate progress bar in tool window header for ongoing operations.

---

## 6. Keyboard Shortcuts & Actions

All operations are registered as `AnAction` under the `AdbDeck` action group. Discoverable via **Find Action** (`Cmd+Shift+A`) by searching "AdbDeck:".

| Action ID | Description |
|---|---|
| `AdbDeck.OpenToolWindow` | Open/focus the AdbDeck tool window |
| `AdbDeck.ForceStop` | Force stop current app |
| `AdbDeck.ClearData` | Clear current app's data |
| `AdbDeck.KillAndRestart` | Kill & restart current app |
| `AdbDeck.OpenApp` | Launch current app |
| `AdbDeck.OpenDeepLink` | Focus the deep link input field |
| `AdbDeck.ToggleAnimations` | Toggle device animations |
| `AdbDeck.SimulateProcessDeath` | Simulate process death |
| `AdbDeck.CustomCommand` | Focus the custom command input |

No default keybindings — users bind their own via `Settings → Keymap → AdbDeck`.

---

## 7. Settings Page

`Settings → Tools → AdbDeck`:

| Setting | Description | Default |
|---|---|---|
| **ADB Path** | Auto-detected from Android SDK, with manual override | Auto |
| **Confirm Destructive Actions** | Show confirmation for Clear Data, Uninstall, Revoke All | On |
| **Console History Size** | Number of commands to keep | 50 |
| **Export/Import Data** | Export or import deep link bookmarks + saved commands as JSON | — |

Deep link bookmarks and saved commands are managed inline in their respective tabs, not in settings.

---

## 8. Architecture Notes

| Aspect | Approach |
|---|---|
| **ADB Communication** | Use `ddmlib` (bundled with AS) for structured APIs (device list, install). Shell out to `adb` binary for niche commands. |
| **Threading** | All ADB operations via coroutines on `Dispatchers.IO`. UI updates on EDT via `invokeLater`. |
| **State Management** | Per-project `PersistentStateComponent`: selected device serial, package name, deep link history & bookmarks, saved commands, tab collapse states. |
| **Device Monitoring** | `AndroidDebugBridge.IDeviceChangeListener` for real-time connect/disconnect events. |
| **Package Detection** | Parse Gradle build files or use `AndroidFacet` API to extract `applicationId`. Fallback to manual input. |
| **API Level Gating** | Each action declares its minimum API level. UI disables actions when the selected device doesn't meet the requirement. |
| **Tool Window** | Single `ToolWindowFactory` creating tabs via `ContentFactory`. Each tab is a separate panel class. |
| **Action Registration** | All actions registered in `plugin.xml` under an `AdbDeck` group. Each action delegates to the appropriate ADB command, using the current device/package from the state service. |
