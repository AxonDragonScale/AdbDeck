package com.github.axondragonscale.adbdeck.model

/**
 * Represents the result of an ADB command execution.
 */
data class AdbResult(
    val command: String,
    val output: String,
    val error: String,
    val exitCode: Int,
) {
    val isSuccess: Boolean get() = exitCode == 0

    companion object {
        /** Creates a synthetic success result (for aggregated operations). */
        fun success(command: String, output: String) = AdbResult(command, output, "", 0)
    }
}

