package com.github.axondragonscale.adbdeck.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/** Notification group ID registered in plugin.xml. */
const val NOTIFICATION_GROUP_ID = "AdbDeck"

/**
 * Shows a balloon notification using the AdbDeck notification group.
 */
fun Project.notifyAdbDeck(message: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP_ID)
        .createNotification(message, type)
        .notify(this)
}

