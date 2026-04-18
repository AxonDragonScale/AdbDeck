package com.github.axondragonscale.adbdeck.adb

import com.github.axondragonscale.adbdeck.model.InstalledAppInfo

/**
 * Commands for listing and querying installed apps on a device.
 * All methods are blocking and should be called from a background thread.
 */
object AppListCommands {

    /**
     * Fetches all installed apps from the device with metadata.
     * Uses `pm list packages` with flags and `dumpsys package` for details.
     */
    fun listInstalledApps(adb: AdbController, serial: String): List<InstalledAppInfo> {
        val result = adb.executeShellCommand(serial, "pm list packages -f")
        if (!result.isSuccess) return emptyList()

        val packages = result.output.lines()
            .filter { it.startsWith("package:") }
            .mapNotNull { line ->
                val eqIdx = line.lastIndexOf('=')
                if (eqIdx > 0) line.substring(eqIdx + 1).trim() else null
            }
            .filter { it.isNotBlank() }

        val systemPackages = adb.executeShellCommand(serial, "pm list packages -s").parsePackageList()
        val disabledPackages = adb.executeShellCommand(serial, "pm list packages -d").parsePackageList()

        return packages.map { pkg ->
            val result = adb.executeShellCommand(serial, "dumpsys package $pkg")
            val meta = if (result.isSuccess) DumpsysPackageParser.parse(result.output) else DumpsysPackageParser.PackageMetadata()
            InstalledAppInfo(
                packageName = pkg,
                appName = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                versionName = meta.versionName,
                versionCode = meta.versionCode,
                isSystemApp = pkg in systemPackages,
                isEnabled = pkg !in disabledPackages,
                isDebuggable = meta.isDebuggable,
            )
        }
    }

    fun disableApp(adb: AdbController, serial: String, pkg: String) =
        adb.executeShellCommand(serial, "pm disable-user --user 0 $pkg")

    fun enableApp(adb: AdbController, serial: String, pkg: String) =
        adb.executeShellCommand(serial, "pm enable $pkg")
}

