package com.github.axondragonscale.adbdeck.util

import com.github.axondragonscale.adbdeck.model.AdbResult
import com.intellij.notification.NotificationType

/**
 * Derives a [NotificationType] from the result.
 */
val AdbResult.notificationType: NotificationType
    get() = if (isSuccess) NotificationType.INFORMATION else NotificationType.ERROR

/**
 * Returns a concise summary message suitable for notifications.
 */
val AdbResult.summaryMessage: String
    get() = if (isSuccess) output.take(100).ifBlank { "Done" } else error.take(100)

