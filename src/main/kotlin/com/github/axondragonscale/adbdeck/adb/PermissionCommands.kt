package com.github.axondragonscale.adbdeck.adb

import com.github.axondragonscale.adbdeck.model.AdbResult
import com.github.axondragonscale.adbdeck.model.PermissionInfo

/**
 * Permission-related ADB commands. Parses `dumpsys package` output.
 * All methods are blocking and should be called from a background thread.
 */
object PermissionCommands {

    private val PERMISSION_REGEX = Regex("""([\w.]+):\s+granted=(\w+)""")

    /**
     * Parses all permissions for a package from `dumpsys package`.
     */
    fun parsePermissions(adb: AdbController, serial: String, pkg: String): List<PermissionInfo> {
        val result = adb.executeShellCommand(serial, "dumpsys package $pkg")
        if (!result.isSuccess) return emptyList()

        val output = result.output
        val permissions = mutableListOf<PermissionInfo>()

        // Parse runtime permissions (dangerous)
        val runtimeSection = extractSection(output, "runtime permissions:")
        for (line in runtimeSection) {
            val match = PERMISSION_REGEX.find(line.trim()) ?: continue
            val name = match.groupValues[1]
            val granted = match.groupValues[2] == "true"
            permissions.add(PermissionInfo(name, "dangerous", granted, isRuntime = true))
        }

        // Parse install permissions (normal/signature)
        val installSection = extractSection(output, "install permissions:")
        for (line in installSection) {
            val match = PERMISSION_REGEX.find(line.trim()) ?: continue
            val name = match.groupValues[1]
            val granted = match.groupValues[2] == "true"
            permissions.add(PermissionInfo(name, "normal", granted, isRuntime = false))
        }

        return permissions.distinctBy { it.name }
    }

    fun grantPermission(adb: AdbController, serial: String, pkg: String, permission: String): AdbResult =
        adb.executeShellCommand(serial, "pm grant $pkg $permission")

    fun revokePermission(adb: AdbController, serial: String, pkg: String, permission: String): AdbResult =
        adb.executeShellCommand(serial, "pm revoke $pkg $permission")

    fun grantAllDangerous(adb: AdbController, serial: String, pkg: String, permissions: List<PermissionInfo>): List<AdbResult> =
        permissions.filter { it.isDangerous && !it.isGranted }
            .map { grantPermission(adb, serial, pkg, it.name) }

    fun revokeAllDangerous(adb: AdbController, serial: String, pkg: String, permissions: List<PermissionInfo>): List<AdbResult> =
        permissions.filter { it.isDangerous && it.isGranted }
            .map { revokePermission(adb, serial, pkg, it.name) }

    /**
     * Extracts lines belonging to a section from dumpsys output.
     * Sections start with the given header and end when indentation decreases.
     */
    private fun extractSection(output: String, header: String): List<String> {
        val lines = output.lines()
        val startIdx = lines.indexOfFirst { it.trimStart().startsWith(header) }
        if (startIdx == -1) return emptyList()

        val sectionLines = mutableListOf<String>()
        val baseIndent = lines[startIdx].length - lines[startIdx].trimStart().length

        for (i in (startIdx + 1) until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val indent = line.length - line.trimStart().length
            if (indent <= baseIndent) break
            sectionLines.add(line)
        }
        return sectionLines
    }
}

