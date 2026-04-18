package com.github.axondragonscale.adbdeck.adb

import com.github.axondragonscale.adbdeck.model.AdbResult

/**
 * Parses `pm list packages` output into a set of package names.
 * Shared by [PackageSelectorPanel][com.github.axondragonscale.adbdeck.toolwindow.PackageSelectorPanel]
 * and [AppListCommands].
 */
fun AdbResult.parsePackageList(): Set<String> {
    if (!isSuccess) return emptySet()
    return output.lines()
        .filter { it.startsWith("package:") }
        .map { it.removePrefix("package:").trim() }
        .filter { it.isNotBlank() }
        .toSet()
}

