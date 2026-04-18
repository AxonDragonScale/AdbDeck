package com.github.axondragonscale.adbdeck.toolwindow.tabs

import com.github.axondragonscale.adbdeck.AdbDeckBundle
import com.github.axondragonscale.adbdeck.adb.AppCommands
import com.github.axondragonscale.adbdeck.adb.PermissionCommands
import com.github.axondragonscale.adbdeck.model.AdbResult
import com.github.axondragonscale.adbdeck.model.PermissionInfo
import com.github.axondragonscale.adbdeck.state.AdbDeckStateService
import com.github.axondragonscale.adbdeck.toolwindow.ActionContext
import com.github.axondragonscale.adbdeck.toolwindow.PackageSelectorPanel
import com.github.axondragonscale.adbdeck.toolwindow.components.iconButton
import com.github.axondragonscale.adbdeck.util.notifyAdbDeck
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
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.DefaultCellEditor
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTable
import javax.swing.SwingConstants
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

        fun fillX(row: Int) = GridBagConstraints().apply {
            gridx = 0; gridy = row; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
        }

        // Package dropdown first (above its separator) — top margin matches the separator below
        topPanel.add(packageSelectorPanel.apply {
            border = JBUI.Borders.emptyTop(12)
        }, fillX(gridRow++))

        // Package details separator
        topPanel.add(TitledSeparator("Package Details").apply {
            border = JBUI.Borders.emptyTop(12)
        }, fillX(gridRow++))
        topPanel.add(detailsPanel, fillX(gridRow++))

        // Actions separator
        topPanel.add(TitledSeparator("Actions").apply {
            border = JBUI.Borders.emptyTop(16)
        }, fillX(gridRow++))

        // Action rows
        val actions = listOf<Triple<String, Icon, (String, String) -> AdbResult>>(
            Triple("Open App", AllIcons.Actions.Execute) { s, p -> AppCommands.openApp(ctx.adbController, s, p) },
            Triple("Force Stop", AllIcons.Actions.Suspend) { s, p -> AppCommands.forceStop(ctx.adbController, s, p) },
            Triple("Kill & Restart", AllIcons.Actions.Restart) { s, p -> AppCommands.killAndRestart(ctx.adbController, s, p) },
            Triple("App Info", AllIcons.General.Information) { s, p -> AppCommands.openAppInfo(ctx.adbController, s, p) },
        )
        for ((label, icon, action) in actions) {
            topPanel.add(createActionRow(label, icon, label) {
                executeAction { s, p -> action(s, p) }
            }, fillX(gridRow++))
        }

        topPanel.add(createActionRow("Clear App Data", AllIcons.Actions.GC, "Clear all app data and cache") {
            confirmAndExecute(
                AdbDeckBundle.message("confirm.clearData.title"),
                AdbDeckBundle.message("confirm.clearData.message", "{pkg}")
            ) { s, p -> AppCommands.clearData(ctx.adbController, s, p) }
        }, fillX(gridRow++))

        topPanel.add(createActionRow("Uninstall", AllIcons.Vcs.Remove, "Uninstall the app") {
            confirmAndExecute(
                AdbDeckBundle.message("confirm.uninstall.title"),
                AdbDeckBundle.message("confirm.uninstall.message", "{pkg}")
            ) { s, p ->
                val result = AppCommands.uninstall(ctx.adbController, s, p)
                if (result.isSuccess) {
                    ApplicationManager.getApplication().invokeLater {
                        packageSelectorPanel.refreshPackages()
                    }
                }
                result
            }
        }, fillX(gridRow++))

        topPanel.add(createActionRow("Grant All Permissions", AllIcons.Actions.Checked, "Grant all dangerous runtime permissions") {
            executeAction { s, p ->
                val perms = PermissionCommands.parsePermissions(ctx.adbController, s, p)
                val results = PermissionCommands.grantAllDangerous(ctx.adbController, s, p, perms)
                AdbResult("pm grant (all dangerous)", "Granted ${results.count { it.isSuccess }} permissions", "", 0)
            }
        }, fillX(gridRow++))

        topPanel.add(createActionRow("Revoke All Permissions", AllIcons.Actions.Cancel, "Revoke all dangerous runtime permissions") {
            executeAction { s, p ->
                val perms = PermissionCommands.parsePermissions(ctx.adbController, s, p)
                val results = PermissionCommands.revokeAllDangerous(ctx.adbController, s, p, perms)
                AdbResult("pm revoke (all dangerous)", "Revoked ${results.count { it.isSuccess }} permissions", "", 0)
            }
        }, fillX(gridRow++))

        add(topPanel, BorderLayout.NORTH)

        // ── Permissions section — fills remaining space ──
        val permSection = JPanel(BorderLayout())

        val permHeader = JPanel(GridBagLayout())
        permHeader.add(TitledSeparator("Permissions").apply {
            border = JBUI.Borders.emptyTop(16)
        }, fillX(0).apply { gridy = 0 })
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
        permHeader.add(filterBar, fillX(0).apply { gridy = 1 })
        permSection.add(permHeader, BorderLayout.NORTH)

        table.apply {
            setShowGrid(false)
            rowHeight = JBUI.scale(28)
            tableHeader.reorderingAllowed = false
            columnModel.getColumn(0).preferredWidth = 300
            columnModel.getColumn(1).preferredWidth = 70
            columnModel.getColumn(1).maxWidth = 90
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

    // ── UI Helpers ──


    private fun createActionRow(label: String, icon: Icon, tooltip: String, onClick: () -> Unit): JPanel {
        val h = JBUI.scale(24)
        return object : JPanel(BorderLayout()) {
            override fun getPreferredSize() = Dimension(super.getPreferredSize().width, h)
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, h)
            override fun getMinimumSize() = Dimension(0, h)
        }.apply {
            isOpaque = false
            border = JBUI.Borders.empty(1, 2)
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)

            val btn = JButton(label, icon).apply {
                toolTipText = tooltip
                isFocusPainted = false; isBorderPainted = false; isContentAreaFilled = false
                isRolloverEnabled = false
                horizontalAlignment = SwingConstants.LEFT
                cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                addActionListener { onClick() }
            }
            add(btn, BorderLayout.CENTER)

            val row = this
            val hover = object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    row.isOpaque = true; row.background = JBUI.CurrentTheme.List.Hover.background(false); row.repaint()
                }
                override fun mouseExited(e: MouseEvent) { row.isOpaque = false; row.repaint() }
                override fun mouseClicked(e: MouseEvent) { if (e.button == MouseEvent.BUTTON1) onClick() }
            }
            addMouseListener(hover); btn.addMouseListener(hover)
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
                                gridx = 0; gridy = r; anchor = GridBagConstraints.WEST; insets = Insets(1, 0, 1, 8)
                            })
                        detailsPanel.add(JBLabel(value),
                            GridBagConstraints().apply {
                                gridx = 1; gridy = r; anchor = GridBagConstraints.WEST
                                weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = Insets(1, 0, 1, 0)
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

    private fun confirmAndExecute(title: String, message: String, action: (String, String) -> AdbResult) {
        if (stateService.confirmDestructiveActions) {
            val pkg = ctx.getSelectedPackage() ?: ""
            if (Messages.showYesNoDialog(ctx.project, message.replace("{pkg}", pkg), title, Messages.getWarningIcon()) != Messages.YES) return
        }
        executeAction(action)
    }

    private fun executeAction(action: (String, String) -> AdbResult) {
        val serial = ctx.getSelectedDeviceSerial()
        val pkg = ctx.getSelectedPackage()
        if (serial == null) { return }
        if (pkg == null) { ctx.project.notifyAdbDeck(AdbDeckBundle.message("notification.noPackage"), NotificationType.WARNING); return }
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = action(serial, pkg)
            ApplicationManager.getApplication().invokeLater {
                ctx.logToConsole(result.command, if (result.isSuccess) result.output else result.error)
                val type = if (result.isSuccess) NotificationType.INFORMATION else NotificationType.ERROR
                val msg = if (result.isSuccess) result.output.take(100).ifBlank { "Done" } else result.error.take(100)
                ctx.project.notifyAdbDeck(msg, type)
            }
        }
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
