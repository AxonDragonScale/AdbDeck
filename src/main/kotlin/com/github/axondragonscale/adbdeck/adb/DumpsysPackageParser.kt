package com.github.axondragonscale.adbdeck.adb

/**
 * Parses `dumpsys package` output into structured metadata.
 * Shared by [AppCommands] (package details) and [AppListCommands] (app list metadata).
 */
object DumpsysPackageParser {

    data class PackageMetadata(
        val versionName: String = "",
        val versionCode: Long = 0,
        val targetSdk: String = "",
        val minSdk: String = "",
        val firstInstallTime: String = "",
        val lastUpdateTime: String = "",
        val isDebuggable: Boolean = false,
        val splits: List<String> = emptyList(),
    )

    /**
     * Parses metadata from raw `dumpsys package` output.
     */
    fun parse(dumpsysOutput: String): PackageMetadata {
        var versionName = ""
        var versionCode = 0L
        var targetSdk = ""
        var minSdk = ""
        var firstInstallTime = ""
        var lastUpdateTime = ""
        var isDebuggable = false
        var splits = emptyList<String>()

        for (line in dumpsysOutput.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("versionName=") ->
                    versionName = trimmed.removePrefix("versionName=")
                trimmed.startsWith("versionCode=") ->
                    versionCode = trimmed.removePrefix("versionCode=").split(" ").first().toLongOrNull() ?: 0
                trimmed.startsWith("targetSdk=") ->
                    targetSdk = trimmed.removePrefix("targetSdk=")
                trimmed.startsWith("minSdk=") ->
                    minSdk = trimmed.removePrefix("minSdk=")
                trimmed.startsWith("firstInstallTime=") ->
                    firstInstallTime = trimmed.removePrefix("firstInstallTime=")
                trimmed.startsWith("lastUpdateTime=") ->
                    lastUpdateTime = trimmed.removePrefix("lastUpdateTime=")
                trimmed.startsWith("pkgFlags=") ->
                    isDebuggable = trimmed.contains("DEBUGGABLE")
                trimmed.contains("DEBUGGABLE") && !trimmed.startsWith("pkgFlags=") ->
                    isDebuggable = true
                trimmed.startsWith("splits=") ->
                    splits = trimmed.removePrefix("splits=")
                        .trim('[', ']')
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() && it != "base" }
            }
        }

        return PackageMetadata(
            versionName = versionName,
            versionCode = versionCode,
            targetSdk = targetSdk,
            minSdk = minSdk,
            firstInstallTime = firstInstallTime,
            lastUpdateTime = lastUpdateTime,
            isDebuggable = isDebuggable,
            splits = splits,
        )
    }
}

