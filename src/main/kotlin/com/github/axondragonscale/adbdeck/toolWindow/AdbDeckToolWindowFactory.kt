package com.github.axondragonscale.adbdeck.toolwindow

import com.github.axondragonscale.adbdeck.adb.DeviceService
import com.intellij.openapi.components.service
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ui.JBUI
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.Timer

/**
 * Factory that creates the AdbDeck tool window with tabbed layout.
 * Shows a "No device connected" message when no device is available.
 * Automatically detects device connection and shows the full UI.
 */
class AdbDeckToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val deviceService = project.service<DeviceService>()
        val contentManager = toolWindow.contentManager

        var controller: AdbDeckToolWindow? = null
        var isShowingTabs = false

        // ── Empty state: "No device connected" ──
        val emptyPanel = JPanel(GridBagLayout()).apply {
            add(JLabel("No device connected").apply {
                horizontalAlignment = SwingConstants.CENTER
                foreground = JBUI.CurrentTheme.Label.disabledForeground()
            })
        }

        fun showEmptyState() {
            if (!isShowingTabs && contentManager.contentCount > 0) return
            contentManager.removeAllContents(true)
            val content = ContentFactory.getInstance().createContent(emptyPanel, "", false)
            content.isCloseable = false
            contentManager.addContent(content)
            isShowingTabs = false
        }

        fun showTabs() {
            if (isShowingTabs) return
            contentManager.removeAllContents(true)

            val ctrl = controller ?: AdbDeckToolWindow(project, toolWindow.disposable).also { controller = it }

            val tabs = listOf(
                "App" to ctrl.appTab,
                "All Apps" to ctrl.appsOnDeviceTab,
                "Deep Links" to ctrl.deepLinksTab,
                "Settings" to ctrl.deviceSettingsTab,
                "Commands" to ctrl.commandsTab,
            )

            for ((title, panel) in tabs) {
                val content = ContentFactory.getInstance().createContent(panel, title, false)
                content.isCloseable = false
                contentManager.addContent(content)
            }

            contentManager.addContentManagerListener(object : ContentManagerListener {
                override fun selectionChanged(event: ContentManagerEvent) {
                    when (event.content.displayName) {
                        "App" -> ctrl.appTab.loadPermissions()
                        "All Apps" -> ctrl.appsOnDeviceTab.loadApps()
                        "Settings" -> ctrl.deviceSettingsTab.refreshAll()
                    }
                }
            })

            isShowingTabs = true

            // Trigger initial data load
            ctrl.getPackageSelectorPanel().refreshPackages()
        }

        // Show initial state
        showEmptyState()

        // Poll for device availability
        val pollTimer = Timer(2000) {
            val hasDevice = deviceService.getSelectedDeviceSerial() != null
            if (hasDevice && !isShowingTabs) {
                showTabs()
            } else if (!hasDevice && isShowingTabs) {
                showEmptyState()
            }
        }
        pollTimer.isRepeats = true
        pollTimer.start()

        // Stop polling when the tool window is disposed
        Disposer.register(toolWindow.disposable, Disposable { pollTimer.stop() })
    }

    override fun shouldBeAvailable(project: Project) = true
}
