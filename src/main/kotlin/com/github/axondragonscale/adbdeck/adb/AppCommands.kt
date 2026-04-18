package com.github.axondragonscale.adbdeck.adb

import com.github.axondragonscale.adbdeck.model.AdbResult

/**
 * App management ADB commands.
 * All methods are blocking and should be called from a background thread.
 */
object AppCommands {

    fun forceStop(adb: AdbController, serial: String, pkg: String): AdbResult =
        adb.executeShellCommand(serial, "am force-stop $pkg")

    fun clearData(adb: AdbController, serial: String, pkg: String): AdbResult =
        adb.executeShellCommand(serial, "pm clear $pkg")

    fun clearCache(adb: AdbController, serial: String, pkg: String): AdbResult =
        adb.executeShellCommand(serial, "pm clear --cache-only $pkg")

    fun uninstall(adb: AdbController, serial: String, pkg: String): AdbResult =
        adb.executeAdbCommand(serial, "uninstall", pkg)

    fun installApk(adb: AdbController, serial: String, localPath: String): AdbResult =
        adb.executeAdbCommand(serial, "install", "-r", localPath)

    fun openApp(adb: AdbController, serial: String, pkg: String): AdbResult =
        adb.executeShellCommand(serial, "monkey -p $pkg -c android.intent.category.LAUNCHER 1")

    fun openAppInfo(adb: AdbController, serial: String, pkg: String): AdbResult =
        adb.executeShellCommand(serial, "am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:$pkg")

    fun killAndRestart(adb: AdbController, serial: String, pkg: String): AdbResult {
        val stopResult = forceStop(adb, serial, pkg)
        if (!stopResult.isSuccess) return stopResult
        Thread.sleep(500)
        return openApp(adb, serial, pkg)
    }

    /**
     * Returns a map of package detail key-value pairs parsed from `dumpsys package`.
     */
    fun getPackageDetails(adb: AdbController, serial: String, pkg: String): Map<String, String> {
        val result = adb.executeShellCommand(serial, "dumpsys package $pkg")
        if (!result.isSuccess) return emptyMap()

        val raw = mutableMapOf<String, String>()
        val lines = result.output.lines()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("versionName=") ->
                    raw["Version"] = trimmed.removePrefix("versionName=")
                trimmed.startsWith("versionCode=") -> {
                    val code = trimmed.removePrefix("versionCode=").split(" ").firstOrNull() ?: ""
                    raw["Version Code"] = code
                }
                trimmed.startsWith("targetSdk=") ->
                    raw["Target SDK"] = trimmed.removePrefix("targetSdk=")
                trimmed.startsWith("minSdk=") ->
                    raw["Min SDK"] = trimmed.removePrefix("minSdk=")
                trimmed.startsWith("firstInstallTime=") ->
                    raw["Installed"] = trimmed.removePrefix("firstInstallTime=")
                trimmed.startsWith("lastUpdateTime=") ->
                    raw["Updated"] = trimmed.removePrefix("lastUpdateTime=")
                trimmed.startsWith("pkgFlags=") -> {
                    val flags = trimmed.removePrefix("pkgFlags=")
                    raw["Build Type"] = if (flags.contains("DEBUGGABLE")) "Debug" else "Release"
                }
                trimmed.startsWith("splits=") -> {
                    // e.g. splits=[base]  or  splits=[base, config.xxhdpi]
                    val splits = trimmed.removePrefix("splits=")
                        .trim('[', ']')
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() && it != "base" }
                    if (splits.isNotEmpty()) raw["Splits"] = splits.joinToString(", ")
                }
            }
        }

        // Return in a consistent display order
        val orderedKeys = listOf("Version", "Version Code", "Build Type", "Min SDK", "Target SDK", "Installed", "Updated", "Splits")
        val result2 = linkedMapOf<String, String>()
        for (key in orderedKeys) {
            raw[key]?.let { result2[key] = it }
        }
        return result2
    }
}

