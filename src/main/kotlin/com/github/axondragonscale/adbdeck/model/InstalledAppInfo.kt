package com.github.axondragonscale.adbdeck.model

/**
 * Represents an installed app on the device.
 */
data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val isDebuggable: Boolean,
) {
    enum class AppType(val label: String) {
        USER("User"),
        SYSTEM("System"),
    }

    val appType: AppType get() = if (isSystemApp) AppType.SYSTEM else AppType.USER

    val displayVersion: String get() = if (versionName.isNotBlank()) versionName else versionCode.toString()
}

