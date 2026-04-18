package com.github.axondragonscale.adbdeck.toolwindow.tabs

import com.github.axondragonscale.adbdeck.AdbDeckBundle
import com.github.axondragonscale.adbdeck.adb.AppCommands
import com.github.axondragonscale.adbdeck.adb.AppListCommands
import com.github.axondragonscale.adbdeck.adb.PermissionCommands
import com.github.axondragonscale.adbdeck.model.InstalledAppInfo
import com.github.axondragonscale.adbdeck.state.AdbDeckStateService
import com.github.axondragonscale.adbdeck.toolwindow.ActionContext
import com.github.axondragonscale.adbdeck.toolwindow.components.iconButton
import com.github.axondragonscale.adbdeck.util.notifyAdbDeck
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * All Apps tab: searchable, filterable list of installed apps with context menu.
 * Filter uses exclusive radio-style selection: All (default), User, System, Debuggable.
 */
class AppsOnDeviceTabPanel(private val ctx: ActionContext) : JPanel(BorderLayout()) {

    private val stateService = ctx.project.service<AdbDeckStateService>()

    private var allApps: List<InstalledAppInfo> = emptyList()
    private var filteredApps: List<InstalledAppInfo> = emptyList()
    private val tableModel = AppListTableModel()
    private val table = JBTable(tableModel)

    private enum class AppFilter { ALL, USER, SYSTEM, DEBUGGABLE }
    private var currentFilter = AppFilter.USER

    private var searchQuery = ""
    private val searchField = JBTextField().apply {
        emptyText.text = "Search apps..."
    }

    init {
        border = JBUI.Borders.empty(4)

        // ── Search bar ──
        val searchPanel = JPanel(BorderLayout(4, 0)).apply {
            border = JBUI.Borders.emptyBottom(4)
            add(JBLabel(AllIcons.Actions.Search), BorderLayout.WEST)
            add(searchField, BorderLayout.CENTER)
        }

        searchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                searchQuery = searchField.text.trim()
                applyFilters()
            }
        })

        // ── Filter radio buttons (exclusive selection with clear visual indicator) ──
        val filterPanel = JPanel(BorderLayout()).apply {
            val filtersRow = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply {
                val group = ButtonGroup()

                fun addFilter(label: String, filter: AppFilter, selected: Boolean = false) {
                    val rb = JRadioButton(label, selected).apply {
                        isFocusPainted = false
                        border = JBUI.Borders.empty(3, 6)
                        addActionListener { currentFilter = filter; applyFilters() }
                    }
                    group.add(rb)
                    add(rb)
                }

                addFilter("All", AppFilter.ALL)
                addFilter("User", AppFilter.USER, selected = true)
                addFilter("System", AppFilter.SYSTEM)
                addFilter("Debuggable", AppFilter.DEBUGGABLE)
            }
            add(filtersRow, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                add(iconButton(AllIcons.Actions.Refresh, "Refresh app list") { loadApps() })
            }, BorderLayout.EAST)
        }

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(searchPanel)
            add(filterPanel)
        }
        add(topPanel, BorderLayout.NORTH)

        // ── Table ──
        table.apply {
            setShowGrid(false)
            rowHeight = JBUI.scale(24)
            tableHeader.reorderingAllowed = false
            autoCreateRowSorter = true

            columnModel.getColumn(0).preferredWidth = 120
            columnModel.getColumn(1).preferredWidth = 200
            columnModel.getColumn(2).preferredWidth = 80
            columnModel.getColumn(3).preferredWidth = 60

            // Type column coloring
            columnModel.getColumn(3).cellRenderer = object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
                ): Component {
                    val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    if (!isSelected) {
                        foreground = if (value == "System") JBColor.GRAY else JBColor.foreground()
                    }
                    return comp
                }
            }

            // Double-click → set as target
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val row = table.rowAtPoint(e.point)
                        if (row >= 0) {
                            val modelRow = table.convertRowIndexToModel(row)
                            setAsTarget(filteredApps[modelRow])
                        }
                    }
                }
            })

            // Right-click context menu
            PopupHandler.installPopupMenu(this, createContextActionGroup(), "AdbDeckAppsPopup")
        }

        add(JBScrollPane(table), BorderLayout.CENTER)
    }

    fun loadApps() {
        val serial = ctx.getSelectedDeviceSerial()
        if (serial == null) {
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val apps = AppListCommands.listInstalledApps(ctx.adbController, serial)
            ApplicationManager.getApplication().invokeLater {
                allApps = apps
                applyFilters()
            }
        }
    }

    private fun applyFilters() {
        filteredApps = allApps.filter { app ->
            // Type filter (exclusive radio)
            when (currentFilter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystemApp
                AppFilter.SYSTEM -> app.isSystemApp
                AppFilter.DEBUGGABLE -> app.isDebuggable
            }
        }.filter { app ->
            // Search
            if (searchQuery.isBlank()) true
            else {
                val q = searchQuery.lowercase()
                app.packageName.lowercase().contains(q) || app.appName.lowercase().contains(q)
            }
        }
        tableModel.fireTableDataChanged()
    }

    private fun createContextActionGroup(): DefaultActionGroup {
        return DefaultActionGroup().apply {
            add(object : AnAction("Set as Target Package", "Set as the active target package", AllIcons.Actions.SetDefault) {
                override fun actionPerformed(e: AnActionEvent) { getSelectedApp()?.let { setAsTarget(it) } }
            })
            add(Separator.getInstance())
            add(object : AnAction("Open App", "Launch the app", AllIcons.Actions.Execute) {
                override fun actionPerformed(e: AnActionEvent) { getSelectedApp()?.let { executeOnApp(it) { s, p -> AppCommands.openApp(ctx.adbController, s, p) } } }
            })
            add(object : AnAction("App Info", "Open app info screen", AllIcons.General.Information) {
                override fun actionPerformed(e: AnActionEvent) { getSelectedApp()?.let { executeOnApp(it) { s, p -> AppCommands.openAppInfo(ctx.adbController, s, p) } } }
            })
            add(object : AnAction("Force Stop", "Force stop the app", AllIcons.Actions.Suspend) {
                override fun actionPerformed(e: AnActionEvent) { getSelectedApp()?.let { executeOnApp(it) { s, p -> AppCommands.forceStop(ctx.adbController, s, p) } } }
            })
            add(Separator.getInstance())
            add(object : AnAction("Clear App Data", "Clear all app data and cache", AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    val app = getSelectedApp() ?: return
                    if (stateService.confirmDestructiveActions) {
                        val ok = Messages.showYesNoDialog(
                            ctx.project,
                            AdbDeckBundle.message("confirm.clearData.message", app.packageName),
                            AdbDeckBundle.message("confirm.clearData.title"),
                            Messages.getWarningIcon()
                        )
                        if (ok != Messages.YES) return
                    }
                    executeOnApp(app) { s, p -> AppCommands.clearData(ctx.adbController, s, p) }
                }
            })
            add(object : AnAction("Uninstall", "Uninstall the app", AllIcons.Vcs.Remove) {
                override fun actionPerformed(e: AnActionEvent) {
                    val app = getSelectedApp() ?: return
                    if (stateService.confirmDestructiveActions) {
                        val ok = Messages.showYesNoDialog(
                            ctx.project,
                            AdbDeckBundle.message("confirm.uninstall.message", app.packageName),
                            AdbDeckBundle.message("confirm.uninstall.title"),
                            Messages.getWarningIcon()
                        )
                        if (ok != Messages.YES) return
                    }
                    executeOnApp(app) { s, p -> AppCommands.uninstall(ctx.adbController, s, p) }
                }
            })
            add(object : AnAction("Disable / Enable", "Toggle app enabled state", null) {
                override fun actionPerformed(e: AnActionEvent) {
                    val app = getSelectedApp() ?: return
                    executeOnApp(app) { s, p ->
                        if (app.isEnabled) AppListCommands.disableApp(ctx.adbController, s, p)
                        else AppListCommands.enableApp(ctx.adbController, s, p)
                    }
                }
            })
            add(Separator.getInstance())
            add(object : AnAction("Grant All Permissions", "Grant all dangerous runtime permissions", AllIcons.Actions.Checked) {
                override fun actionPerformed(e: AnActionEvent) {
                    val app = getSelectedApp() ?: return
                    executeOnApp(app) { s, p ->
                        val perms = PermissionCommands.parsePermissions(ctx.adbController, s, p)
                        val results = PermissionCommands.grantAllDangerous(ctx.adbController, s, p, perms)
                        com.github.axondragonscale.adbdeck.model.AdbResult(
                            "pm grant (all)", "Granted ${results.count { it.isSuccess }}", "", 0
                        )
                    }
                }
            })
            add(object : AnAction("Revoke All Permissions", "Revoke all dangerous runtime permissions", AllIcons.Actions.Cancel) {
                override fun actionPerformed(e: AnActionEvent) {
                    val app = getSelectedApp() ?: return
                    executeOnApp(app) { s, p ->
                        val perms = PermissionCommands.parsePermissions(ctx.adbController, s, p)
                        val results = PermissionCommands.revokeAllDangerous(ctx.adbController, s, p, perms)
                        com.github.axondragonscale.adbdeck.model.AdbResult(
                            "pm revoke (all)", "Revoked ${results.count { it.isSuccess }}", "", 0
                        )
                    }
                }
            })
            add(Separator.getInstance())
            add(object : AnAction("Copy Package Name", "Copy package name to clipboard", AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) {
                    getSelectedApp()?.let {
                        CopyPasteManager.getInstance().setContents(StringSelection(it.packageName))
                    }
                }
            })
        }
    }

    private fun getSelectedApp(): InstalledAppInfo? {
        val row = table.selectedRow
        if (row < 0) return null
        val modelRow = table.convertRowIndexToModel(row)
        return filteredApps.getOrNull(modelRow)
    }

    private fun setAsTarget(app: InstalledAppInfo) {
        stateService.lastPackageName = app.packageName
        ctx.project.notifyAdbDeck("Target package set to: ${app.packageName}", NotificationType.INFORMATION)
    }

    private fun executeOnApp(
        app: InstalledAppInfo,
        action: (serial: String, pkg: String) -> com.github.axondragonscale.adbdeck.model.AdbResult
    ) {
        val serial = ctx.getSelectedDeviceSerial() ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = action(serial, app.packageName)
            ApplicationManager.getApplication().invokeLater {
                ctx.logToConsole(result.command, if (result.isSuccess) result.output else result.error)
                val type = if (result.isSuccess) NotificationType.INFORMATION else NotificationType.ERROR
                val msg = if (result.isSuccess) result.output.take(100).ifBlank { "Done" } else result.error.take(100)
                ctx.project.notifyAdbDeck(msg, type)
            }
        }
    }

    // ── Table Model ──

    inner class AppListTableModel : AbstractTableModel() {
        private val columns = arrayOf("App Name", "Package Name", "Version", "Type")

        override fun getRowCount() = filteredApps.size
        override fun getColumnCount() = columns.size
        override fun getColumnName(column: Int) = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val app = filteredApps[rowIndex]
            return when (columnIndex) {
                0 -> app.appName
                1 -> app.packageName
                2 -> app.displayVersion
                3 -> app.appType.label
                else -> ""
            }
        }
    }
}

