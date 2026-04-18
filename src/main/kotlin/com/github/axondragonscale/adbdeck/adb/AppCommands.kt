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
     * Returns an ordered map of package detail key-value pairs for display.
     * Uses [DumpsysPackageParser] for parsing.
     */
    fun getPackageDetails(adb: AdbController, serial: String, pkg: String): Map<String, String> {
        val result = adb.executeShellCommand(serial, "dumpsys package $pkg")
        if (!result.isSuccess) return emptyMap()

        val meta = DumpsysPackageParser.parse(result.output)

        return linkedMapOf<String, String>().apply {
            if (meta.versionName.isNotBlank()) put("Version", meta.versionName)
            if (meta.versionCode > 0) put("Version Code", meta.versionCode.toString())
            put("Build Type", if (meta.isDebuggable) "Debug" else "Release")
            if (meta.minSdk.isNotBlank()) put("Min SDK", meta.minSdk)
            if (meta.targetSdk.isNotBlank()) put("Target SDK", meta.targetSdk)
            if (meta.firstInstallTime.isNotBlank()) put("Installed", meta.firstInstallTime)
            if (meta.lastUpdateTime.isNotBlank()) put("Updated", meta.lastUpdateTime)
            if (meta.splits.isNotEmpty()) put("Splits", meta.splits.joinToString(", "))
        }
    }
}

