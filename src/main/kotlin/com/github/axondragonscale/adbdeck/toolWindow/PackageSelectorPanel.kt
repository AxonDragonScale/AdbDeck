package com.github.axondragonscale.adbdeck.toolwindow

import com.github.axondragonscale.adbdeck.adb.AdbController
import com.github.axondragonscale.adbdeck.adb.DeviceService
import com.github.axondragonscale.adbdeck.adb.PackageDetectionService
import com.github.axondragonscale.adbdeck.state.AdbDeckStateService
import com.github.axondragonscale.adbdeck.toolwindow.components.iconButton
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSeparator

/**
 * Package selector: combo box + square refresh button.
 * Uses GridBagLayout so the button stays square and the combo fills remaining width.
 */
class PackageSelectorPanel(private val project: Project) : JPanel(GridBagLayout()) {

    private val packageDetection = project.service<PackageDetectionService>()
    private val stateService = project.service<AdbDeckStateService>()
    private val adbController = project.service<AdbController>()
    private val deviceService = project.service<DeviceService>()

    private val changeListeners = mutableListOf<() -> Unit>()

    private val comboModel = DefaultComboBoxModel<String>()
    private val packageCombo = ComboBox(comboModel)

    private val refreshButton = iconButton(AllIcons.Actions.Refresh, "Refresh package list from device") { refreshPackages() }

    private var systemSectionStart = -1
    private var projectPackageCount = 0

    init {
        // Combo: fills width, anchored west
        add(packageCombo, GridBagConstraints().apply {
            gridx = 0; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
        })
        // Button: fixed size, 4px gap on left
        add(refreshButton, GridBagConstraints().apply {
            gridx = 1; gridy = 0; insets = java.awt.Insets(0, JBUI.scale(4), 0, 0)
        })

        // Restore last package so the combo isn't empty while loading
        val lastPkg = stateService.lastPackageName
        if (lastPkg.isNotBlank()) {
            comboModel.addElement(lastPkg)
            packageCombo.selectedItem = lastPkg
        }

        // Combo selection change → persist + notify
        packageCombo.addActionListener {
            val selected = packageCombo.selectedItem?.toString()
            if (!selected.isNullOrBlank() && selected != SEPARATOR_ITEM) {
                val deviceSerial = deviceService.getSelectedDeviceSerial()
                val changed = stateService.lastPackageName != selected
                if (deviceSerial != null) {
                    stateService.setLastPackageForDevice(deviceSerial, selected)
                } else {
                    stateService.lastPackageName = selected
                }
                if (changed) changeListeners.forEach { it() }
            }
        }


        // Custom renderer
        packageCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                if (value == SEPARATOR_ITEM) {
                    return JSeparator().apply { preferredSize = Dimension(0, 2) }
                }
                val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (index >= 0 && index < projectPackageCount) {
                    font = font.deriveFont(Font.BOLD)
                }
                return comp
            }
        }
    }

    fun addPackageChangeListener(listener: () -> Unit) { changeListeners.add(listener) }

    fun getSelectedPackage(): String? {
        val item = packageCombo.selectedItem?.toString()
        return if (item.isNullOrBlank() || item == SEPARATOR_ITEM) null else item
    }

    // GridBagLayout respects these for the parent's layout
    override fun getMinimumSize() = Dimension(0, preferredSize.height)
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)

    fun refreshPackages() {
        val deviceSerial = deviceService.getSelectedDeviceSerial()
        val projectPackages = packageDetection.detectApplicationIds().toSet()

        if (deviceSerial == null) {
            val finalList = projectPackages.sorted()
            projectPackageCount = finalList.size
            updateCombo(finalList, emptyList())
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val allResult = adbController.executeShellCommand(deviceSerial, "pm list packages")
            val allPackages = if (allResult.exitCode == 0) {
                allResult.output.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.isNotBlank() }
            } else emptyList()

            val sysResult = adbController.executeShellCommand(deviceSerial, "pm list packages -s")
            val systemPackages = if (sysResult.exitCode == 0) {
                sysResult.output.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .toSet()
            } else emptySet()

            val userPackages = allPackages.filter { it !in systemPackages }
            val section1 = userPackages.filter { it in projectPackages }.sorted()
            val section2 = userPackages.filter { it !in projectPackages }.sorted()
            val section3 = systemPackages.sorted()
            val userList = section1 + section2

            ApplicationManager.getApplication().invokeLater {
                projectPackageCount = section1.size
                updateCombo(userList, section3)
            }
        }
    }


    private fun updateCombo(userApps: List<String>, systemApps: List<String>) {
        val deviceSerial = deviceService.getSelectedDeviceSerial()
        val previousSelection = if (deviceSerial != null) {
            stateService.getLastPackageForDevice(deviceSerial)
        } else {
            packageCombo.selectedItem?.toString()
        }
        comboModel.removeAllElements()

        userApps.forEach { comboModel.addElement(it) }

        if (systemApps.isNotEmpty()) {
            comboModel.addElement(SEPARATOR_ITEM)
            systemSectionStart = userApps.size + 1
            systemApps.forEach { comboModel.addElement(it) }
        }

        when {
            !previousSelection.isNullOrBlank() && previousSelection != SEPARATOR_ITEM
                    && comboModel.getIndexOf(previousSelection) != -1 ->
                packageCombo.selectedItem = previousSelection
            userApps.isNotEmpty() ->
                packageCombo.selectedItem = userApps.first()
        }

        val selected = packageCombo.selectedItem?.toString()
        if (!selected.isNullOrBlank() && selected != SEPARATOR_ITEM) {
            if (deviceSerial != null) {
                stateService.setLastPackageForDevice(deviceSerial, selected)
            } else {
                stateService.lastPackageName = selected
            }
            changeListeners.forEach { it() }
        }
    }

    companion object {
        private const val SEPARATOR_ITEM = "──────────────"
    }
}
