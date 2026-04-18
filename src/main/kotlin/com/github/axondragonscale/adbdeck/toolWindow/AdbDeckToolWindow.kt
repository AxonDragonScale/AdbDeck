package com.github.axondragonscale.adbdeck.toolwindow

import com.github.axondragonscale.adbdeck.adb.AdbController
import com.github.axondragonscale.adbdeck.adb.DeviceService
import com.github.axondragonscale.adbdeck.toolwindow.tabs.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

/**
 * Main controller for the AdbDeck tool window.
 * Implements [ActionContext] so all tabs can access device, package, console, and project.
 * No longer a JPanel — just holds state. Tabs are created separately via the factory.
 */
class AdbDeckToolWindow(
    override val project: Project,
    parentDisposable: Disposable,
) : Disposable, ActionContext {

    override val adbController: AdbController = project.service()
    private val deviceService: DeviceService = project.service()
    private val packageSelectorPanel = PackageSelectorPanel(project)

    // Tabs
    val appTab = AppTabPanel(this, packageSelectorPanel)
    val appsOnDeviceTab = AppsOnDeviceTabPanel(this)
    val deepLinksTab = DeepLinksTabPanel(this)
    val deviceSettingsTab = DeviceSettingsTabPanel(this)
    val commandsTab = CommandsTabPanel(this)

    init {
        Disposer.register(parentDisposable, this)
    }

    // ── ActionContext implementation ──

    override fun getSelectedDeviceSerial(): String? = deviceService.getSelectedDeviceSerial()

    override fun getSelectedPackage(): String? = packageSelectorPanel.getSelectedPackage()

    override fun logToConsole(command: String, output: String) {
        commandsTab.consolePanel.append(command, output)
    }

    fun getPackageSelectorPanel(): PackageSelectorPanel = packageSelectorPanel

    override fun dispose() {
        // Child disposables are cleaned up by Disposer
    }
}
