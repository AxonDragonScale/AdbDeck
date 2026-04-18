package com.github.axondragonscale.adbdeck.model

/**
 * A saved deep link bookmark with a user-facing label and a URI.
 * Must have no-arg constructor and var properties for IntelliJ XML serialization.
 */
data class DeepLinkBookmark(
    var label: String = "",
    var uri: String = "",
)

