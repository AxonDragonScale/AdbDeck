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
        // Get all packages with their flags
        val result = adb.executeShellCommand(serial, "pm list packages -f")
        if (!result.isSuccess) return emptyList()

        val packages = result.output.lines()
            .filter { it.startsWith("package:") }
            .mapNotNull { line ->
                // Format: package:/path/to/apk=com.example.app
                val eqIdx = line.lastIndexOf('=')
                if (eqIdx > 0) line.substring(eqIdx + 1).trim() else null
            }
            .filter { it.isNotBlank() }

        // Get system packages
        val systemResult = adb.executeShellCommand(serial, "pm list packages -s")
        val systemPackages = systemResult.output.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .toSet()

        // Get disabled packages
        val disabledResult = adb.executeShellCommand(serial, "pm list packages -d")
        val disabledPackages = disabledResult.output.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .toSet()

        return packages.map { pkg ->
            val info = getPackageInfo(adb, serial, pkg)
            InstalledAppInfo(
                packageName = pkg,
                appName = info.appName.ifBlank { pkg.substringAfterLast('.') },
                versionName = info.versionName,
                versionCode = info.versionCode,
                isSystemApp = pkg in systemPackages,
                isEnabled = pkg !in disabledPackages,
                isDebuggable = info.isDebuggable,
            )
        }
    }

    private data class PackageMetadata(
        val appName: String = "",
        val versionName: String = "",
        val versionCode: Long = 0,
        val isDebuggable: Boolean = false,
    )

    private fun getPackageInfo(adb: AdbController, serial: String, pkg: String): PackageMetadata {
        val result = adb.executeShellCommand(serial, "dumpsys package $pkg")
        if (!result.isSuccess) return PackageMetadata()

        val output = result.output
        var versionName = ""
        var versionCode = 0L
        var isDebuggable = false

        for (line in output.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("versionName=") -> {
                    versionName = trimmed.removePrefix("versionName=")
                }
                trimmed.startsWith("versionCode=") -> {
                    val codeStr = trimmed.removePrefix("versionCode=").split(" ").first()
                    versionCode = codeStr.toLongOrNull() ?: 0
                }
                trimmed.contains("DEBUGGABLE") -> {
                    isDebuggable = true
                }
            }
        }

        return PackageMetadata(
            appName = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() },
            versionName = versionName,
            versionCode = versionCode,
            isDebuggable = isDebuggable,
        )
    }

    fun disableApp(adb: AdbController, serial: String, pkg: String) =
        adb.executeShellCommand(serial, "pm disable-user --user 0 $pkg")

    fun enableApp(adb: AdbController, serial: String, pkg: String) =
        adb.executeShellCommand(serial, "pm enable $pkg")
}

