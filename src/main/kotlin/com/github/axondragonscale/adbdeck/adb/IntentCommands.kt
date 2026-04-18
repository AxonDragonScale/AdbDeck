package com.github.axondragonscale.adbdeck.adb

import com.github.axondragonscale.adbdeck.model.AdbResult

/**
 * Custom intent building and execution.
 * All methods are blocking and should be called from a background thread.
 */
object IntentCommands {

    enum class IntentType(val amCommand: String) {
        ACTIVITY("am start"),
        BROADCAST("am broadcast"),
        SERVICE("am startservice"),
    }

    data class IntentExtra(val key: String, val value: String, val type: ExtraType)

    enum class ExtraType(val flag: String) {
        STRING("-e"),
        INT("--ei"),
        BOOLEAN("--ez"),
        FLOAT("--ef"),
        LONG("--el"),
    }

    enum class IntentFlag(val value: Int, val label: String) {
        FLAG_ACTIVITY_NEW_TASK(0x10000000, "NEW_TASK"),
        FLAG_ACTIVITY_CLEAR_TOP(0x04000000, "CLEAR_TOP"),
        FLAG_ACTIVITY_SINGLE_TOP(0x20000000, "SINGLE_TOP"),
        FLAG_ACTIVITY_CLEAR_TASK(0x00008000, "CLEAR_TASK"),
        FLAG_ACTIVITY_NO_HISTORY(0x40000000, "NO_HISTORY"),
        FLAG_ACTIVITY_NO_ANIMATION(0x00010000, "NO_ANIMATION"),
        FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS(0x00800000, "EXCLUDE_FROM_RECENTS"),
        FLAG_ACTIVITY_FORWARD_RESULT(0x02000000, "FORWARD_RESULT"),
        FLAG_ACTIVITY_REORDER_TO_FRONT(0x00020000, "REORDER_TO_FRONT"),
        FLAG_ACTIVITY_MULTIPLE_TASK(0x08000000, "MULTIPLE_TASK"),
    }

    /**
     * Builds a full `am` command string from the given intent parameters.
     */
    fun buildCommand(
        type: IntentType,
        action: String? = null,
        dataUri: String? = null,
        component: String? = null,
        category: String? = null,
        extras: List<IntentExtra> = emptyList(),
        flags: String? = null,
    ): String = buildString {
        append(type.amCommand)
        if (!action.isNullOrBlank()) append(" -a $action")
        if (!dataUri.isNullOrBlank()) append(" -d \"$dataUri\"")
        if (!component.isNullOrBlank()) append(" -n $component")
        if (!category.isNullOrBlank()) append(" -c $category")
        for (extra in extras) {
            append(" ${extra.type.flag} ${extra.key} ${extra.value}")
        }
        if (!flags.isNullOrBlank()) append(" -f $flags")
    }

    fun executeIntent(adb: AdbController, serial: String, command: String): AdbResult =
        adb.executeShellCommand(serial, command)
}

