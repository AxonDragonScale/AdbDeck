package com.github.axondragonscale.adbdeck.model

/**
 * Represents a single permission declared by an app.
 */
data class PermissionInfo(
    val name: String,
    val protectionLevel: String,
    val isGranted: Boolean,
    val isRuntime: Boolean,
) {
    val isDangerous: Boolean get() = isRuntime || protectionLevel.contains("dangerous")
}

