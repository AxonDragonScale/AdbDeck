package com.github.axondragonscale.adbdeck.toolwindow.tabs

import com.github.axondragonscale.adbdeck.AdbDeckBundle
import com.github.axondragonscale.adbdeck.adb.AppCommands
import com.github.axondragonscale.adbdeck.adb.PermissionCommands
import com.github.axondragonscale.adbdeck.model.AdbResult
import com.github.axondragonscale.adbdeck.model.PermissionInfo
import com.github.axondragonscale.adbdeck.state.AdbDeckStateService
import com.github.axondragonscale.adbdeck.toolwindow.ActionContext
import com.github.axondragonscale.adbdeck.toolwindow.PackageSelectorPanel
import com.github.axondragonscale.adbdeck.toolwindow.components.actionRow
import com.github.axondragonscale.adbdeck.toolwindow.components.fillXConstraints
import com.github.axondragonscale.adbdeck.toolwindow.components.iconButton
import com.github.axondragonscale.adbdeck.util.notifyAdbDeck
import com.github.axondragonscale.adbdeck.util.runAdbActionWithPackage
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.DefaultCellEditor
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTable
import javax.swing.table.AbstractTableModel

class AppTabPanel(
    private val ctx: ActionContext,
    private val packageSelectorPanel: PackageSelectorPanel,
) : JPanel(BorderLayout()) {

    private val stateService = ctx.project.service<AdbDeckStateService>()

    private var permissions: List<PermissionInfo> = emptyList()
    private var filteredPermissions: List<PermissionInfo> = emptyList()
    private val tableModel = PermissionTableModel()
    private val table = JBTable(tableModel)

    private enum class Filter { ALL, DANGEROUS, GRANTED, DENIED }
    private var currentFilter = Filter.ALL

    private val detailsPanel = JPanel(GridBagLayout()).apply {
        border = JBUI.Borders.empty(2, 2, 0, 0)
        isVisible = false
    }

    init {
        border = JBUI.Borders.empty(0, 4, 4, 4)

        // ── Top: Package + Actions (GridBagLayout for stable sizing) ──
        val topPanel = JPanel(GridBagLayout())
        var gridRow = 0

        // Package dropdown — wrapped to add top margin without mutating the shared panel
        val pkgWrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(12)
            add(packageSelectorPanel, BorderLayout.CENTER)
        }
        topPanel.add(pkgWrapper, fillXConstraints(gridRow++))

        // Package details separator
        topPanel.add(TitledSeparator("Package Details").apply {
            border = JBUI.Borders.emptyTop(12)
        }, fillXConstraints(gridRow++))
        topPanel.add(detailsPanel, fillXConstraints(gridRow++))

        // Actions separator
        topPanel.add(TitledSeparator("Actions").apply {
            border = JBUI.Borders.emptyTop(16)
        }, fillXConstraints(gridRow++))

        // Action rows
        val actions = listOf<Triple<String, Icon, (String, String) -> AdbResult>>(
            Triple("Open App", AllIcons.Actions.Execute) { s, p -> AppCommands.openApp(ctx.adbController, s, p) },
            Triple("Force Stop", AllIcons.Actions.Suspend) { s, p -> AppCommands.forceStop(ctx.adbController, s, p) },
            Triple("Kill & Restart", AllIcons.Actions.Restart) { s, p -> AppCommands.killAndRestart(ctx.adbController, s, p) },
            Triple("App Info", AllIcons.General.Information) { s, p -> AppCommands.openAppInfo(ctx.adbController, s, p) },
        )
        for ((label, icon, action) in actions) {
            topPanel.add(actionRow(label, icon, label) {
                executeAction { s, p -> action(s, p) }
            }, fillXConstraints(gridRow++))
        }

        topPanel.add(actionRow("Clear App Data", AllIcons.Actions.GC, "Clear all app data and cache") {
            confirmAndExecute(
                AdbDeckBundle.message("confirm.clearData.title"),
                { pkg -> AdbDeckBundle.message("confirm.clearData.message", pkg) }
            ) { s, p -> AppCommands.clearData(ctx.adbController, s, p) }
        }, fillXConstraints(gridRow++))

        topPanel.add(actionRow("Uninstall", AllIcons.Vcs.Remove, "Uninstall the app") {
            confirmAndExecute(
                AdbDeckBundle.message("confirm.uninstall.title"),
                { pkg -> AdbDeckBundle.message("confirm.uninstall.message", pkg) }
            ) { s, p ->
                val result = AppCommands.uninstall(ctx.adbController, s, p)
                if (result.isSuccess) {
                    ApplicationManager.getApplication().invokeLater {
                        packageSelectorPanel.refreshPackages()
                    }
                }
                result
            }
        }, fillXConstraints(gridRow++))

        topPanel.add(actionRow("Grant All Permissions", AllIcons.Actions.Checked, "Grant all dangerous runtime permissions") {
            executeAction { s, p ->
                val perms = PermissionCommands.parsePermissions(ctx.adbController, s, p)
                val results = PermissionCommands.grantAllDangerous(ctx.adbController, s, p, perms)
                AdbResult.success("pm grant (all dangerous)", "Granted ${results.count { it.isSuccess }} permissions")
            }
        }, fillXConstraints(gridRow++))

        topPanel.add(actionRow("Revoke All Permissions", AllIcons.Actions.Cancel, "Revoke all dangerous runtime permissions") {
            executeAction { s, p ->
                val perms = PermissionCommands.parsePermissions(ctx.adbController, s, p)
                val results = PermissionCommands.revokeAllDangerous(ctx.adbController, s, p, perms)
                AdbResult.success("pm revoke (all dangerous)", "Revoked ${results.count { it.isSuccess }} permissions")
            }
        }, fillXConstraints(gridRow++))

        add(topPanel, BorderLayout.NORTH)

        // ── Permissions section — fills remaining space ──
        val permSection = JPanel(BorderLayout())

        val permHeader = JPanel(GridBagLayout())
        permHeader.add(TitledSeparator("Permissions").apply {
            border = JBUI.Borders.emptyTop(16)
        }, fillXConstraints(0).apply { gridy = 0 })
        val filterBar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 0)
            val filtersRow = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                val group = ButtonGroup()
                for (f in Filter.entries) {
                    val lbl = when (f) {
                        Filter.ALL -> "All"; Filter.DANGEROUS -> "Dangerous"
                        Filter.GRANTED -> "Granted"; Filter.DENIED -> "Denied"
                    }
                    val rb = JRadioButton(lbl, f == Filter.ALL).apply {
                        isFocusPainted = false
                        border = JBUI.Borders.empty(0, 4)
                        addActionListener { currentFilter = f; applyFilter() }
                    }
                    group.add(rb); add(rb)
                }
            }
            add(filtersRow, BorderLayout.WEST)
            add(iconButton(AllIcons.Actions.Refresh, "Refresh") { loadPermissions() }, BorderLayout.EAST)
        }
        permHeader.add(filterBar, fillXConstraints(0).apply { gridy = 1 })
        permSection.add(permHeader, BorderLayout.NORTH)

        table.apply {
            setShowGrid(false)
            rowHeight = JBUI.scale(24)
            tableHeader.reorderingAllowed = false
            columnModel.getColumn(0).preferredWidth = JBUI.scale(300)
            columnModel.getColumn(1).preferredWidth = JBUI.scale(70)
            columnModel.getColumn(1).maxWidth = JBUI.scale(90)
            columnModel.getColumn(1).cellRenderer = ButtonRenderer()
            columnModel.getColumn(1).cellEditor = ButtonEditor()
        }
        permSection.add(JBScrollPane(table), BorderLayout.CENTER)
        add(permSection, BorderLayout.CENTER)

        packageSelectorPanel.addPackageChangeListener {
            loadPermissions()
            loadPackageDetails()
        }
    }


    // ── Data loading ──

    private fun loadPackageDetails() {
        val serial = ctx.getSelectedDeviceSerial()
        val pkg = ctx.getSelectedPackage()
        if (serial == null || pkg == null) {
            detailsPanel.isVisible = false; revalidate(); repaint(); return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val details = AppCommands.getPackageDetails(ctx.adbController, serial, pkg)
            ApplicationManager.getApplication().invokeLater {
                detailsPanel.removeAll()
                if (details.isNotEmpty()) {
                    var r = 0
                    for ((key, value) in details) {
                        detailsPanel.add(JBLabel(key).apply { font = font.deriveFont(Font.BOLD) },
                            GridBagConstraints().apply {
                                gridx = 0; gridy = r; anchor = GridBagConstraints.WEST
                                insets = JBUI.insets(2, 0, 2, 12)
                            })
                        detailsPanel.add(JBLabel(value),
                            GridBagConstraints().apply {
                                gridx = 1; gridy = r; anchor = GridBagConstraints.WEST
                                weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
                                insets = JBUI.insets(2, 0)
                            })
                        r++
                    }
                    detailsPanel.isVisible = true
                } else {
                    detailsPanel.isVisible = false
                }
                revalidate(); repaint()
            }
        }
    }

    fun loadPermissions() {
        val serial = ctx.getSelectedDeviceSerial()
        val pkg = ctx.getSelectedPackage()
        if (serial == null || pkg == null) { permissions = emptyList(); applyFilter(); return }
        ApplicationManager.getApplication().executeOnPooledThread {
            val perms = PermissionCommands.parsePermissions(ctx.adbController, serial, pkg)
            ApplicationManager.getApplication().invokeLater { permissions = perms; applyFilter() }
        }
    }

    private fun applyFilter() {
        filteredPermissions = when (currentFilter) {
            Filter.ALL -> permissions
            Filter.DANGEROUS -> permissions.filter { it.isDangerous }
            Filter.GRANTED -> permissions.filter { it.isGranted }
            Filter.DENIED -> permissions.filter { !it.isGranted }
        }
        tableModel.fireTableDataChanged()
    }

    private fun confirmAndExecute(title: String, messageFn: (String) -> String, action: (String, String) -> AdbResult) {
        val pkg = ctx.getSelectedPackage()
        if (pkg == null) {
            ctx.project.notifyAdbDeck(AdbDeckBundle.message("notification.noPackage"), NotificationType.WARNING)
            return
        }
        if (stateService.confirmDestructiveActions) {
            if (Messages.showYesNoDialog(ctx.project, messageFn(pkg), title, Messages.getWarningIcon()) != Messages.YES) return
        }
        executeAction(action)
    }

    private fun executeAction(action: (String, String) -> AdbResult) {
        ctx.runAdbActionWithPackage(action)
    }

    // ── Permission Table ──

    inner class PermissionTableModel : AbstractTableModel() {
        private val cols = arrayOf("Permission", "Action")
        override fun getRowCount() = filteredPermissions.size
        override fun getColumnCount() = cols.size
        override fun getColumnName(c: Int) = cols[c]
        override fun isCellEditable(r: Int, c: Int) = c == 1
        override fun getValueAt(r: Int, c: Int): Any {
            val p = filteredPermissions[r]; return when (c) { 0 -> p.name; 1 -> p; else -> "" }
        }
    }

    inner class ButtonRenderer : javax.swing.table.DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, sel: Boolean, focus: Boolean, row: Int, col: Int
        ): Component {
            val p = value as? PermissionInfo ?: return super.getTableCellRendererComponent(table, value, sel, focus, row, col)
            return JButton(if (p.isGranted) "Revoke" else "Grant").apply { isEnabled = p.isDangerous; border = JBUI.Borders.empty(2, 6) }
        }
    }

    inner class ButtonEditor : DefaultCellEditor(JCheckBox()) {
        private val button = JButton()
        private var perm: PermissionInfo? = null
        init { button.addActionListener { perm?.let { togglePermission(it) }; fireEditingStopped() } }
        override fun getTableCellEditorComponent(table: JTable, value: Any?, sel: Boolean, row: Int, col: Int): Component {
            perm = value as? PermissionInfo
            button.text = if (perm?.isGranted == true) "Revoke" else "Grant"
            button.isEnabled = perm?.isDangerous == true; return button
        }
        override fun getCellEditorValue(): Any = perm ?: ""
    }

    private fun togglePermission(perm: PermissionInfo) {
        val serial = ctx.getSelectedDeviceSerial() ?: return
        val pkg = ctx.getSelectedPackage() ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = if (perm.isGranted) PermissionCommands.revokePermission(ctx.adbController, serial, pkg, perm.name)
            else PermissionCommands.grantPermission(ctx.adbController, serial, pkg, perm.name)
            ApplicationManager.getApplication().invokeLater {
                ctx.logToConsole(result.command, if (result.isSuccess) result.output else result.error)
                loadPermissions()
            }
        }
    }
}
