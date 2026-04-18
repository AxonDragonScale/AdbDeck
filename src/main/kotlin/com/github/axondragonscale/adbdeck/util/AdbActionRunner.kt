package com.github.axondragonscale.adbdeck.util

import com.github.axondragonscale.adbdeck.model.AdbResult
import com.github.axondragonscale.adbdeck.toolwindow.ActionContext
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager

/**
 * Runs an ADB action on a background thread and logs result to console + notification.
 * Centralises the execute-notify-log pattern used across all tab panels.
 */
fun ActionContext.runAdbAction(
    serial: String,
    action: (String) -> AdbResult,
) {
    ApplicationManager.getApplication().executeOnPooledThread {
        val result = action(serial)
        ApplicationManager.getApplication().invokeLater {
            logToConsole(result.command, if (result.isSuccess) result.output else result.error)
            project.notifyAdbDeck(result.summaryMessage, result.notificationType)
        }
    }
}

/**
 * Runs an ADB action requiring both device serial and package name.
 * Shows a warning notification if either is missing.
 */
fun ActionContext.runAdbActionWithPackage(
    action: (serial: String, pkg: String) -> AdbResult,
) {
    val serial = getSelectedDeviceSerial() ?: return
    val pkg = getSelectedPackage() ?: run {
        project.notifyAdbDeck("No package name set.", NotificationType.WARNING)
        return
    }
    runAdbAction(serial) { s -> action(s, pkg) }
}

