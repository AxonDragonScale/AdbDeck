package com.github.axondragonscale.adbdeck.model

/**
 * A saved custom ADB command.
 * Must have no-arg constructor and var properties for IntelliJ XML serialization.
 */
data class SavedCommand(
    var name: String = "",
    var command: String = "",
)

