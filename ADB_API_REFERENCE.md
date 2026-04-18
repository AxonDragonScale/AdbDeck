# ADB API Reference — Android Studio SDK (2025.3.x)

> Quick reference of ddmlib and Android Studio SDK APIs available to AdbDeck.
> Based on Android Studio Panda 2025.3.2.6 (`org.jetbrains.android` bundled plugin).
>
> **Jar locations:**
> - ddmlib: `plugins/android/lib/sdk-tools.jar`
> - Android Studio APIs: `plugins/android/lib/android.jar`

---

## 1. Bridge & Device Discovery

### `AndroidDebugBridge` — `com.android.ddmlib.AndroidDebugBridge`

The singleton bridge manages ADB server connections and device tracking.

```
// Get the cached bridge instance (thread-safe, returns null if not initialized)
static getBridge(): AndroidDebugBridge?

// Device list (call only when isConnected && hasInitialDeviceList)
getDevices(): IDevice[]
hasInitialDeviceList(): Boolean
isConnected(): Boolean

// Restart the ADB server
restart(): Boolean
restart(timeout: Long, unit: TimeUnit): Boolean
```

#### Listeners (all static, callbacks come on background threads)

```
static addDeviceChangeListener(listener: IDeviceChangeListener)
static removeDeviceChangeListener(listener: IDeviceChangeListener)

static addDebugBridgeChangeListener(listener: IDebugBridgeChangeListener)
static removeDebugBridgeChangeListener(listener: IDebugBridgeChangeListener)
```

#### `IDeviceChangeListener`
```kotlin
interface IDeviceChangeListener {
    fun deviceConnected(device: IDevice)
    fun deviceDisconnected(device: IDevice)
    fun deviceChanged(device: IDevice, changeMask: Int)
}
```

**Change masks:**
| Constant | Value | Meaning |
|---|---|---|
| `IDevice.CHANGE_STATE` | 0x0001 | Online/offline/unauthorized state changed |
| `IDevice.CHANGE_CLIENT_LIST` | 0x0002 | Process list changed |
| `IDevice.CHANGE_BUILD_INFO` | 0x0004 | Build properties now available |
| `IDevice.CHANGE_PROFILEABLE_CLIENT_LIST` | — | Profileable client list changed |

### `AndroidSdkUtils` — `org.jetbrains.android.sdk.AndroidSdkUtils`

> ⚠️ Most methods require **EDT**.

```
// Get the debug bridge (EDT only! — initializes bridge if needed)
static getDebugBridge(project: Project): AndroidDebugBridge?

// Find ADB binary (safe from any thread)
static findAdb(project: Project): AdbSearchResult   // .adbPath: File?
static getAdb(project: Project): File?               // DEPRECATED, use findAdb

// SDK paths
static findValidAndroidSdkPath(): File?
static getAndroidSdkPathOrDefault(): File?
static isAndroidSdkAvailable(): Boolean
```

---

## 2. IDevice — `com.android.ddmlib.IDevice`

The core device handle. Implements `IShellEnabledDevice`.

### Device Identity & Properties

```
getSerialNumber(): String
getName(): String
isEmulator(): Boolean
isOnline(): Boolean
isOffline(): Boolean
getState(): DeviceState

// Properties (cached, may be null early in connection)
getProperty(name: String): String?
getPropertySync(name: String): String       // Blocking, throws
getPropertyCacheOrSync(name: String): String // Cache first, sync fallback
arePropertiesSet(): Boolean
getProperties(): Map<String, String>

// Convenience
getVersion(): AndroidVersion
getAbis(): List<String>
getDensity(): Int
getLanguage(): String
getRegion(): String
getBattery(): Future<Integer>
getBatteryLevel(): Integer?                 // Blocking
isRoot(): Boolean                           // Blocking
root(): Boolean                             // Blocking

// Emulator AVD
getAvdName(): String                        // DEPRECATED
getAvdData(): ListenableFuture<AvdData>     // Replacement
```

**Property constants:**
| Constant | System Property | Example Value |
|---|---|---|
| `PROP_DEVICE_MODEL` | `ro.product.model` | `Pixel 7` |
| `PROP_DEVICE_MANUFACTURER` | `ro.product.manufacturer` | `Google` |
| `PROP_BUILD_API_LEVEL` | `ro.build.version.sdk` | `34` |
| `PROP_BUILD_VERSION` | `ro.build.version.release` | `14` |
| `PROP_BUILD_CODENAME` | `ro.build.version.codename` | `REL` |
| `PROP_DEVICE_CPU_ABI` | `ro.product.cpu.abi` | `arm64-v8a` |
| `PROP_DEVICE_DENSITY` | `ro.sf.lcd_density` | `420` |
| `PROP_DEBUGGABLE` | `ro.debuggable` | `1` |
| `PROP_DEVICE_BOOT_QEMU_DISPLAY_NAME` | `ro.boot.qemu.avd_name` | `Pixel_7_API_34` |

**DeviceState enum:**
`ONLINE`, `OFFLINE`, `UNAUTHORIZED`, `BOOTLOADER`, `RECOVERY`, `SIDELOAD`, `AUTHORIZING`, `DISCONNECTED`, `FASTBOOTD`

**Feature enum:**
`SCREEN_RECORD`, `SHELL_V2`, `ABB_EXEC`, `REAL_PKG_NAME`, `SKIP_VERIFICATION`, `PROCSTATS`

**Mount points:**
`MNT_EXTERNAL_STORAGE`, `MNT_ROOT`, `MNT_DATA`

### Shell Commands

```
// Fire-and-forget with output collection
executeShellCommand(command: String, receiver: IShellOutputReceiver)
executeShellCommand(command: String, receiver: IShellOutputReceiver, timeout: Int)
executeShellCommand(command: String, receiver: IShellOutputReceiver,
                    maxTimeout: Long, unit: TimeUnit, stdin: InputStream?)

// Force stop / kill a package
forceStop(packageName: String)       // default method
kill(packageName: String)            // default method
```

### Package Management

```
// Install
installPackage(localPath: String, reinstall: Boolean, vararg args: String)
installPackage(localPath: String, reinstall: Boolean, receiver: InstallReceiver,
               maxTimeout: Long, maxTimeToOutput: Long, unit: TimeUnit, vararg args: String)
installPackages(apks: List<File>, reinstall: Boolean, installOptions: List<String>,
                timeout: Long, unit: TimeUnit)

// Uninstall
uninstallPackage(packageName: String): String?      // Returns error or null
uninstallApp(packageName: String, vararg args: String): String?

// Sync APK to device temp then install
syncPackageToDevice(localPath: String): String       // Returns remote path
installRemotePackage(remotePath: String, reinstall: Boolean, vararg args: String)
removeRemotePackage(remotePath: String)
```

### File Transfer

```
// Direct pull/push
pullFile(remotePath: String, localPath: String)
pushFile(localPath: String, remotePath: String)
push(localPaths: String[], remotePath: String)

// Check if file exists
statFile(remotePath: String): FileStat?

// Structured file services
getSyncService(): SyncService
getFileListingService(): FileListingService
```

### Screen Capture & Recording

```
// Screenshot (returns raw pixel data)
getScreenshot(): RawImage
getScreenshot(timeout: Long, unit: TimeUnit): RawImage

// Screen recording
startScreenRecorder(remotePath: String, options: ScreenRecorderOptions,
                    receiver: IShellOutputReceiver)
supportsFeature(IDevice.Feature.SCREEN_RECORD): Boolean
```

### Port Forwarding

```
// Forward local port to device port
createForward(localPort: Int, remotePort: Int)
createForward(localPort: Int, socketName: String, namespace: DeviceUnixSocketNamespace)
removeForward(localPort: Int)

// Reverse: device port → host port
createReverse(devicePort: Int, hostPort: Int)
removeReverse(devicePort: Int)
```

### Process Info

```
getClients(): Client[]                              // Running processes
getClient(applicationName: String): Client?
hasClients(): Boolean
getClientName(pid: Int): String
```

### Misc

```
reboot(into: String?)                               // null = normal reboot
getMountPoint(name: String): String?
getHardwareCharacteristics(): Set<String>
```

---

## 3. Output Receivers

### `CollectingOutputReceiver` — simplest way to capture shell output

```kotlin
val receiver = CollectingOutputReceiver()
device.executeShellCommand("pm list packages", receiver)
val output: String = receiver.getOutput()
```

### `MultiLineReceiver` — line-by-line processing

```kotlin
class MyReceiver : MultiLineReceiver() {
    override fun processNewLines(lines: Array<String>) { /* ... */ }
    override fun isCancelled(): Boolean = false
}
```

### `InstallReceiver` — install result parsing

```kotlin
val receiver = InstallReceiver()
device.installRemotePackage(path, true, receiver)
if (receiver.isSuccessfullyCompleted()) { /* ... */ }
else { receiver.getErrorMessage() }
```

---

## 4. File Services

### `SyncService` — binary file transfer

```kotlin
val sync = device.getSyncService()
sync.pullFile("/data/data/com.app/databases/db.sqlite", "/local/path/db.sqlite", monitor)
sync.pushFile("/local/file.txt", "/sdcard/file.txt", monitor)
sync.close()
```

### `FileListingService` — browse device filesystem

```kotlin
val fls = device.getFileListingService()
val root = fls.getRoot()
val children = fls.getChildrenSync(root)  // blocking
children.forEach { entry ->
    entry.name         // filename
    entry.isDirectory  // type check
    entry.fullPath     // absolute path
}
```

---

## 5. Screen Capture

### Screenshot → BufferedImage

```kotlin
val rawImage: RawImage = device.getScreenshot()
val buffered: BufferedImage = rawImage.asBufferedImage()
ImageIO.write(buffered, "png", File("screenshot.png"))
```

### Screen Recording

```kotlin
val options = ScreenRecorderOptions.Builder()
    .setBitRate(6_000_000)          // 6 Mbps
    .setSize(1080, 1920)            // optional
    .setTimeLimit(180, TimeUnit.SECONDS)
    .setShowTouches(true)
    .build()

// Starts recording on device (blocks until recording completes)
device.startScreenRecorder("/sdcard/recording.mp4", options, CollectingOutputReceiver())

// Then pull the file
device.pullFile("/sdcard/recording.mp4", "/local/recording.mp4")
```

---

## 6. Common Shell Commands Reference

These commands are executed via `device.executeShellCommand(cmd, receiver)` or
`adbController.executeShellCommand(serial, cmd)`.

### App Management
| Command | Description |
|---|---|
| `pm list packages` | List all installed packages |
| `pm list packages -3` | List third-party packages only |
| `pm clear <pkg>` | Clear app data |
| `pm clear --cache-only <pkg>` | Clear cache only (API 28+) |
| `am force-stop <pkg>` | Force stop app |
| `am start -n <pkg>/<activity>` | Start a specific activity |
| `am start -a android.intent.action.VIEW -d <uri>` | Open deep link |
| `am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:<pkg>` | Open App Info |
| `monkey -p <pkg> -c android.intent.category.LAUNCHER 1` | Launch app |
| `am broadcast -a <action>` | Send broadcast |
| `am startservice -n <pkg>/<service>` | Start service |

### Permissions
| Command | Description |
|---|---|
| `dumpsys package <pkg> \| grep permission` | List app permissions |
| `pm grant <pkg> <permission>` | Grant a runtime permission |
| `pm revoke <pkg> <permission>` | Revoke a runtime permission |
| `dumpsys package <pkg> \| grep "granted=true"` | List granted permissions |

### Device Settings
| Command | Description |
|---|---|
| `settings put global window_animation_scale 0` | Disable window animations |
| `settings put global transition_animation_scale 0` | Disable transition animations |
| `settings put global animator_duration_scale 0` | Disable animator scale |
| `settings get global window_animation_scale` | Read current value |
| `setprop debug.layout true` | Show layout bounds |
| `setprop debug.hwui.overdraw show` | Show overdraw |
| `setprop debug.hwui.profile visual_bars` | GPU profiling bars |
| `service call activity 1599295570` | Reload debug props (after setprop) |
| `settings put global always_finish_activities 1` | Don't keep activities |
| `cmd uimode night yes` | Dark mode on |
| `cmd uimode night no` | Dark mode off |
| `settings put system font_scale 1.0` | Font scale (0.85/1.0/1.15/1.3) |
| `wm density <dpi>` | Set display density |
| `wm density reset` | Reset display density |
| `settings put global sysui_demo_allowed 1` | Enable demo mode |
| `am broadcast -a com.android.systemui.demo -e command enter` | Enter demo mode |
| `am broadcast -a com.android.systemui.demo -e command exit` | Exit demo mode |
| `svc wifi enable` / `svc wifi disable` | Toggle Wi-Fi |
| `svc data enable` / `svc data disable` | Toggle mobile data |
| `settings put global airplane_mode_on 1` | Airplane mode on |
| `settings put global stay_on_while_plugged_in 3` | Stay awake (0=off, 3=any) |

### Network & Proxy
| Command | Description |
|---|---|
| `settings put global http_proxy <host>:<port>` | Set HTTP proxy |
| `settings put global http_proxy :0` | Clear HTTP proxy |
| `settings get global http_proxy` | Read current proxy |

### Misc
| Command | Description |
|---|---|
| `getprop <property>` | Read a system property |
| `wm size` | Get screen resolution |
| `dumpsys battery` | Battery info |
| `input text "<text>"` | Type text |
| `input keyevent <code>` | Send key event |

### Key Event Codes
| Code | Key |
|---|---|
| 3 | HOME |
| 4 | BACK |
| 24 | VOLUME_UP |
| 25 | VOLUME_DOWN |
| 26 | POWER |
| 66 | ENTER |
| 187 | APP_SWITCH (Recents) |

---

## 7. Threading Rules

| API | Thread | Notes |
|---|---|---|
| `AndroidSdkUtils.getDebugBridge(project)` | **EDT only** | Initializes bridge, accesses project SDK |
| `AndroidSdkUtils.findAdb(project)` | Any | Just searches filesystem |
| `AndroidDebugBridge.getBridge()` | Any | Returns cached singleton |
| `bridge.getDevices()` | Any | Returns cached array |
| `IDevice.getProperty()` | Any | Returns cached value (may be null if not loaded yet) |
| `IDevice.getPropertySync()` | Background | Blocking network call |
| `IDevice.executeShellCommand()` | Background | Blocking I/O |
| `IDevice.getScreenshot()` | Background | Blocking I/O |
| `IDevice.pullFile() / pushFile()` | Background | Blocking I/O |
| `IDevice.installPackage()` | Background | Long-running blocking |
| `IDeviceChangeListener` callbacks | ddmlib thread | Must post to EDT for UI updates |

---

## 8. IntelliJ Platform APIs (Key ones used)

| Class | Purpose |
|---|---|
| `ApplicationManager.getApplication().invokeLater {}` | Post work to EDT |
| `ApplicationManager.getApplication().executeOnPooledThread {}` | Run on background thread |
| `PersistentStateComponent<T>` | Persist state in `.idea/` XML files |
| `@Service(Service.Level.PROJECT)` | Project-scoped service (auto-created) |
| `NotificationGroupManager` | Show balloon notifications |
| `ToolWindowFactory` + `DumbAware` | Register tool windows |
| `Disposable` + `Disposer.register()` | Lifecycle and cleanup management |
| `ColoredListCellRenderer<T>` | Styled combo/list renderers |
| `ComboBox<T>` (IntelliJ) | Themed combo box (use instead of JComboBox) |
| `AllIcons.*` | Standard IDE icons |
| `JBUI.Borders.*` | DPI-aware borders/insets |
| `JBColor(light, dark)` | Theme-aware colors |
| `SimpleTextAttributes` | Text styling for colored renderers |

