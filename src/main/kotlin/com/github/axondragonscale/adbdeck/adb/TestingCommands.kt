package com.github.axondragonscale.adbdeck.adb

import com.github.axondragonscale.adbdeck.model.AdbResult

/**
 * ADB commands for testing utilities: process death, trim memory, ANR, etc.
 * All methods are blocking and should be called from a background thread.
 */
object TestingCommands {

    /**
     * Simulates process death by killing the app process without force-stop.
     * The app must be in the background for this to work.
     * Tests onSaveInstanceState / state restoration.
     */
    fun simulateProcessDeath(adb: AdbController, serial: String, pkg: String): AdbResult =
        adb.executeShellCommand(serial, "am kill $pkg")

    /**
     * Sends a trim memory signal to the app at the specified level.
     */
    fun triggerTrimMemory(adb: AdbController, serial: String, pkg: String, level: TrimMemoryLevel): AdbResult =
        adb.executeShellCommand(serial, "am send-trim-memory $pkg ${level.value}")

    enum class TrimMemoryLevel(val value: String, val label: String) {
        RUNNING_MODERATE("RUNNING_MODERATE", "Running Moderate"),
        RUNNING_LOW("RUNNING_LOW", "Running Low"),
        RUNNING_CRITICAL("RUNNING_CRITICAL", "Running Critical"),
        UI_HIDDEN("UI_HIDDEN", "UI Hidden"),
        BACKGROUND("BACKGROUND", "Background"),
        MODERATE("MODERATE", "Moderate"),
        COMPLETE("COMPLETE", "Complete"),
    }

    /**
     * Revokes a permission, force-stops, and re-launches the app.
     * Common flow for testing permission request UX.
     */
    fun revokePermissionAndRestart(
        adb: AdbController,
        serial: String,
        pkg: String,
        permission: String,
    ): AdbResult {
        val revokeResult = PermissionCommands.revokePermission(adb, serial, pkg, permission)
        if (!revokeResult.isSuccess) return revokeResult
        return AppCommands.killAndRestart(adb, serial, pkg)
    }

    /**
     * Rotates the screen. Direction: 0=natural, 1=90°, 2=180°, 3=270°.
     */
    fun rotateScreen(adb: AdbController, serial: String, rotation: Int): AdbResult {
        // Disable auto-rotate first
        adb.executeShellCommand(serial, "settings put system accelerometer_rotation 0")
        return adb.executeShellCommand(serial, "settings put system user_rotation $rotation")
    }

    /**
     * Enables auto-rotate.
     */
    fun enableAutoRotate(adb: AdbController, serial: String): AdbResult =
        adb.executeShellCommand(serial, "settings put system accelerometer_rotation 1")

    /**
     * Gets the current rotation (0-3).
     */
    fun getCurrentRotation(adb: AdbController, serial: String): Int {
        val result = adb.executeShellCommand(serial, "settings get system user_rotation")
        return result.output.trim().toIntOrNull() ?: 0
    }
}

