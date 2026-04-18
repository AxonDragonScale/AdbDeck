package com.github.axondragonscale.adbdeck.adb

import com.github.axondragonscale.adbdeck.model.AdbResult

/**
 * Device settings read/write ADB commands.
 * All methods are blocking and should be called from a background thread.
 */
object DeviceSettingsCommands {

    // ── Animations ──

    fun getAnimationsEnabled(adb: AdbController, serial: String): Boolean {
        val result = adb.executeShellCommand(serial, "settings get global window_animation_scale")
        return result.output.trim() != "0" && result.output.trim() != "0.0"
    }

    fun setAnimations(adb: AdbController, serial: String, enabled: Boolean): AdbResult {
        val value = if (enabled) "1.0" else "0.0"
        adb.executeShellCommand(serial, "settings put global window_animation_scale $value")
        adb.executeShellCommand(serial, "settings put global transition_animation_scale $value")
        return adb.executeShellCommand(serial, "settings put global animator_duration_scale $value")
    }

    // ── Layout Bounds ──

    fun getLayoutBoundsEnabled(adb: AdbController, serial: String): Boolean {
        val result = adb.executeShellCommand(serial, "getprop debug.layout")
        return result.output.trim() == "true"
    }

    fun setLayoutBounds(adb: AdbController, serial: String, enabled: Boolean): AdbResult {
        val value = if (enabled) "true" else "false"
        adb.executeShellCommand(serial, "setprop debug.layout $value")
        return adb.executeShellCommand(serial, "service call activity 1599295570")
    }

    // ── Show Overdraw ──

    fun getOverdrawEnabled(adb: AdbController, serial: String): Boolean {
        val result = adb.executeShellCommand(serial, "getprop debug.hwui.overdraw")
        return result.output.trim() == "show"
    }

    fun setOverdraw(adb: AdbController, serial: String, enabled: Boolean): AdbResult {
        val value = if (enabled) "show" else "false"
        adb.executeShellCommand(serial, "setprop debug.hwui.overdraw $value")
        return adb.executeShellCommand(serial, "service call activity 1599295570")
    }

    // ── Show Refresh Rate ──

    fun getRefreshRateEnabled(adb: AdbController, serial: String): Boolean {
        val result = adb.executeShellCommand(serial, "settings get system show_refresh_rate_overlay")
        return result.output.trim() == "1"
    }

    fun setRefreshRate(adb: AdbController, serial: String, enabled: Boolean): AdbResult {
        val value = if (enabled) "1" else "0"
        return adb.executeShellCommand(serial, "settings put system show_refresh_rate_overlay $value")
    }

    // ── Show Surface Updates ──

    fun getSurfaceUpdatesEnabled(adb: AdbController, serial: String): Boolean {
        val result = adb.executeShellCommand(serial, "getprop debug.hwui.show_dirty_regions")
        return result.output.trim() == "true"
    }

    fun setSurfaceUpdates(adb: AdbController, serial: String, enabled: Boolean): AdbResult {
        val value = if (enabled) "true" else "false"
        adb.executeShellCommand(serial, "setprop debug.hwui.show_dirty_regions $value")
        return adb.executeShellCommand(serial, "service call activity 1599295570")
    }

    // ── Profile HWUI Rendering ──

    fun getHwuiProfilingEnabled(adb: AdbController, serial: String): Boolean {
        val result = adb.executeShellCommand(serial, "getprop debug.hwui.profile")
        return result.output.trim() == "visual_bars"
    }

    fun setHwuiProfiling(adb: AdbController, serial: String, enabled: Boolean): AdbResult {
        val value = if (enabled) "visual_bars" else "\"\""
        adb.executeShellCommand(serial, "setprop debug.hwui.profile $value")
        return adb.executeShellCommand(serial, "service call activity 1599295570")
    }

    // ── Dark Mode ──

    fun getDarkModeEnabled(adb: AdbController, serial: String): Boolean {
        val result = adb.executeShellCommand(serial, "cmd uimode night")
        return result.output.contains("YES", ignoreCase = true)
    }

    fun setDarkMode(adb: AdbController, serial: String, enabled: Boolean): AdbResult {
        val value = if (enabled) "yes" else "no"
        return adb.executeShellCommand(serial, "cmd uimode night $value")
    }

    // ── Don't Keep Activities ──

    fun getDontKeepActivitiesEnabled(adb: AdbController, serial: String): Boolean {
        val result = adb.executeShellCommand(serial, "settings get global always_finish_activities")
        return result.output.trim() == "1"
    }

    fun setDontKeepActivities(adb: AdbController, serial: String, enabled: Boolean): AdbResult {
        val value = if (enabled) "1" else "0"
        return adb.executeShellCommand(serial, "settings put global always_finish_activities $value")
    }

    // ── Stay Awake ──

    fun getStayAwakeEnabled(adb: AdbController, serial: String): Boolean {
        val result = adb.executeShellCommand(serial, "settings get global stay_on_while_plugged_in")
        return result.output.trim() != "0"
    }

    fun setStayAwake(adb: AdbController, serial: String, enabled: Boolean): AdbResult {
        val value = if (enabled) "3" else "0"
        return adb.executeShellCommand(serial, "settings put global stay_on_while_plugged_in $value")
    }

    // ── Demo Mode ──

    fun setDemoMode(adb: AdbController, serial: String, enabled: Boolean): AdbResult {
        return if (enabled) {
            adb.executeShellCommand(serial, "settings put global sysui_demo_allowed 1")
            adb.executeShellCommand(serial, "am broadcast -a com.android.systemui.demo -e command enter")
        } else {
            adb.executeShellCommand(serial, "am broadcast -a com.android.systemui.demo -e command exit")
        }
    }

    // ── Font Scale ──

    fun getFontScale(adb: AdbController, serial: String): Float {
        val result = adb.executeShellCommand(serial, "settings get system font_scale")
        return result.output.trim().toFloatOrNull() ?: 1.0f
    }

    fun setFontScale(adb: AdbController, serial: String, scale: Float): AdbResult =
        adb.executeShellCommand(serial, "settings put system font_scale $scale")

    // ── Display Scale ──

    fun setDisplayScale(adb: AdbController, serial: String, scale: Float): AdbResult {
        // Get the physical density first, then set the override
        val physResult = adb.executeShellCommand(serial, "wm density")
        val physDpi = physResult.output.lines()
            .firstOrNull { it.contains("Physical density") }
            ?.replace(Regex("[^0-9]"), "")
            ?.toIntOrNull() ?: 420
        val targetDpi = (physDpi * scale).toInt()
        return adb.executeShellCommand(serial, "wm density $targetDpi")
    }

    fun resetDisplayScale(adb: AdbController, serial: String): AdbResult =
        adb.executeShellCommand(serial, "wm density reset")
}

