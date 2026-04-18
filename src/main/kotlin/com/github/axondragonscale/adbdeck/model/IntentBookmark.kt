package com.github.axondragonscale.adbdeck.model

/**
 * A saved custom intent bookmark.
 * Must have no-arg constructor and var properties for IntelliJ XML serialization.
 */
data class IntentBookmark(
    var label: String = "",
    var type: String = "ACTIVITY",
    var action: String = "",
    var data: String = "",
    var component: String = "",
    var category: String = "",
    var flags: String = "",         // comma-separated IntentFlag names
    var extras: MutableList<IntentBookmarkExtra> = mutableListOf(),
)

data class IntentBookmarkExtra(
    var enabled: Boolean = true,
    var key: String = "",
    var type: String = "STRING",    // ExtraType name
    var value: String = "",
)

