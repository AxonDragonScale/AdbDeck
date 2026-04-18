package com.github.axondragonscale.adbdeck.model

/**
 * Represents a connected Android device or emulator.
 */
data class DeviceInfo(
    val serial: String,
    val name: String,
    val model: String,
    val apiLevel: Int,
    val isEmulator: Boolean,
    val connectionType: ConnectionType,
    val state: DeviceState,
) {
    /** Display label for the device selector dropdown. */
    val displayName: String
        get() = "$name (API $apiLevel) — ${connectionType.label}"

    enum class ConnectionType(val label: String) {
        USB("USB"),
        WIFI("Wi-Fi"),
        EMULATOR("Emulator"),
        ;
    }

    enum class DeviceState {
        ONLINE,
        OFFLINE,
        UNAUTHORIZED,
        ;
    }
}
