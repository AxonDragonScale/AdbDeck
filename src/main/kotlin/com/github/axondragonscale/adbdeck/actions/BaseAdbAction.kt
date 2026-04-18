package com.github.axondragonscale.adbdeck.actions

import com.github.axondragonscale.adbdeck.adb.AdbController
import com.github.axondragonscale.adbdeck.adb.DeviceService
import com.github.axondragonscale.adbdeck.model.AdbResult
import com.github.axondragonscale.adbdeck.state.AdbDeckStateService
import com.github.axondragonscale.adbdeck.util.notifyAdbDeck
import com.github.axondragonscale.adbdeck.util.notificationType
import com.github.axondragonscale.adbdeck.util.summaryMessage
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service

/**
 * Base class for AdbDeck actions that need a device + package.
 */
abstract class BaseAdbAction : AnAction() {

    abstract fun execute(adb: AdbController, serial: String, pkg: String): AdbResult

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val deviceService = project.service<DeviceService>()
        val adb = project.service<AdbController>()
        val state = project.service<AdbDeckStateService>()

        val serial = deviceService.getSelectedDeviceSerial()
        if (serial == null) {
            project.notifyAdbDeck("No device selected", NotificationType.WARNING)
            return
        }
        val pkg = state.lastPackageName
        if (pkg.isBlank()) {
            project.notifyAdbDeck("No package set", NotificationType.WARNING)
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = execute(adb, serial, pkg)
            ApplicationManager.getApplication().invokeLater {
                project.notifyAdbDeck(result.summaryMessage, result.notificationType)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

