package com.github.axondragonscale.adbdeck.toolwindow.tabs

import com.github.axondragonscale.adbdeck.AdbDeckBundle
import com.github.axondragonscale.adbdeck.model.SavedCommand
import com.github.axondragonscale.adbdeck.state.AdbDeckStateService
import com.github.axondragonscale.adbdeck.toolwindow.ActionContext
import com.github.axondragonscale.adbdeck.toolwindow.ConsolePanel
import com.github.axondragonscale.adbdeck.toolwindow.components.fillXConstraints
import com.github.axondragonscale.adbdeck.toolwindow.components.iconButton
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.PopupHandler
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

/**
 * Custom Commands tab: command input + saved commands table + console output.
 * Layout consistent with other AdbDeck tabs: GridBagLayout, TitledSeparators, JBTable, PopupHandler.
 */
class CommandsTabPanel(private val ctx: ActionContext) : JPanel(BorderLayout()) {

    private val stateService = ctx.project.service<AdbDeckStateService>()
    val consolePanel = ConsolePanel()

    private val commandField = JBTextField().apply {
        emptyText.text = AdbDeckBundle.message("customCommand.input.placeholder")
    }

    private val shellCheckbox = JCheckBox("Shell command").apply {
        isSelected = true
        isFocusPainted = false
        toolTipText = "When checked, prepends 'adb shell'. When unchecked, sends raw ADB command."
    }

    private val savedCommands = mutableListOf<SavedCommand>()
    private val savedTableModel = SavedCommandTableModel()
    private val savedTable = JBTable(savedTableModel)

    private val commandHistory = mutableListOf<String>()
    private var historyIndex = -1

    init {
        border = JBUI.Borders.empty(0, 4, 4, 4)

        val content = JPanel(GridBagLayout())
        var row = 0

        // ── Command Input ──
        content.add(TitledSeparator("Command Input").apply {
            border = JBUI.Borders.emptyTop(12)
        }, fillXConstraints(row++))

        // Shell checkbox
        content.add(JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.emptyTop(4)
            add(shellCheckbox, GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
            })
            add(JPanel(), GridBagConstraints().apply {
                gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            })
        }, fillXConstraints(row++))

        // Command field + Run button + Save bookmark
        content.add(JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(4, 0)
            add(JBLabel("$").apply {
                border = JBUI.Borders.emptyRight(4)
            }, GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
            })
            add(commandField, GridBagConstraints().apply {
                gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insetsRight(8)
            })
            add(JButton("Run").apply {
                isFocusPainted = false
                addActionListener { runCommand() }
            }, GridBagConstraints().apply {
                gridx = 2; gridy = 0; anchor = GridBagConstraints.EAST
                insets = JBUI.insetsRight(4)
            })
            add(iconButton(AllIcons.Nodes.BookmarkGroup, "Save command") { saveCurrentCommand() }, GridBagConstraints().apply {
                gridx = 3; gridy = 0; anchor = GridBagConstraints.EAST
            })
        }, fillXConstraints(row++))

        // Enter / Up / Down keys
        commandField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> runCommand()
                    KeyEvent.VK_UP -> navigateHistory(-1)
                    KeyEvent.VK_DOWN -> navigateHistory(1)
                }
            }
        })

        // ── Saved Commands ──
        content.add(TitledSeparator("Saved Commands").apply {
            border = JBUI.Borders.emptyTop(16)
        }, fillXConstraints(row++))

        // Table setup
        savedTable.apply {
            setShowGrid(false)
            rowHeight = JBUI.scale(24)
            tableHeader.reorderingAllowed = false
            columnModel.getColumn(0).preferredWidth = JBUI.scale(120)
            columnModel.getColumn(1).preferredWidth = JBUI.scale(300)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val tableRow = rowAtPoint(e.point)
                    if (tableRow < 0) return
                    val cmd = savedCommands.getOrNull(tableRow) ?: return
                    commandField.text = cmd.command
                    if (e.clickCount == 2) runCommand()
                }
            })
        }

        PopupHandler.installPopupMenu(savedTable, createSavedCommandContextMenu(), "AdbDeckSavedCommandsPopup")

        content.add(JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 0)
            preferredSize = Dimension(0, JBUI.scale(160))
            add(JBScrollPane(savedTable), BorderLayout.CENTER)
        }, fillXConstraints(row++))


        add(content, BorderLayout.NORTH)

        // ── Center: Console (takes all remaining height) ──
        add(consolePanel, BorderLayout.CENTER)

        refreshSavedList()
    }

    // ── Actions ──

    private fun runCommand() {
        val cmd = commandField.text.trim()
        if (cmd.isBlank()) return
        val serial = ctx.getSelectedDeviceSerial() ?: return

        commandHistory.remove(cmd)
        commandHistory.add(0, cmd)
        historyIndex = -1

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = if (shellCheckbox.isSelected) {
                ctx.adbController.executeShellCommand(serial, cmd)
            } else {
                ctx.adbController.executeAdbCommand(serial, *cmd.split(" ").toTypedArray())
            }
            ApplicationManager.getApplication().invokeLater {
                ctx.logToConsole(result.command, if (result.isSuccess) result.output else result.error)
            }
        }
    }

    private fun navigateHistory(direction: Int) {
        if (commandHistory.isEmpty()) return
        historyIndex = (historyIndex + direction).coerceIn(-1, commandHistory.size - 1)
        commandField.text = if (historyIndex >= 0) commandHistory[historyIndex] else ""
    }

    private fun saveCurrentCommand() {
        val cmd = commandField.text.trim()
        if (cmd.isBlank()) return
        val name = Messages.showInputDialog(
            ctx.project,
            AdbDeckBundle.message("customCommand.name.prompt"),
            AdbDeckBundle.message("customCommand.save"),
            null,
        )
        if (!name.isNullOrBlank()) {
            stateService.addSavedCommand(name, cmd)
            refreshSavedList()
        }
    }

    private fun selectedSavedCommand(): SavedCommand? =
        savedCommands.getOrNull(savedTable.selectedRow)

    private fun createSavedCommandContextMenu(): DefaultActionGroup {
        return DefaultActionGroup().apply {
            add(object : AnAction("Run", "Run this command on device", AllIcons.Actions.Execute) {
                override fun actionPerformed(e: AnActionEvent) {
                    val cmd = selectedSavedCommand() ?: return
                    commandField.text = cmd.command
                    runCommand()
                }
            })
            add(Separator.getInstance())
            add(object : AnAction("Copy Command", "Copy command to clipboard", AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) {
                    val cmd = selectedSavedCommand() ?: return
                    CopyPasteManager.getInstance().setContents(StringSelection(cmd.command))
                }
            })
            add(object : AnAction("Copy Name", "Copy name to clipboard", AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) {
                    val cmd = selectedSavedCommand() ?: return
                    CopyPasteManager.getInstance().setContents(StringSelection(cmd.name))
                }
            })
            add(Separator.getInstance())
            add(object : AnAction("Remove", "Remove this saved command", AllIcons.General.Remove) {
                override fun actionPerformed(e: AnActionEvent) {
                    val cmd = selectedSavedCommand() ?: return
                    stateService.removeSavedCommand(cmd.name)
                    refreshSavedList()
                }
            })
        }
    }

    private fun refreshSavedList() {
        savedCommands.clear()
        savedCommands.addAll(stateService.getSavedCommands())
        savedTableModel.fireTableDataChanged()
    }

    // ── Table Model ──

    inner class SavedCommandTableModel : AbstractTableModel() {
        private val cols = arrayOf("Name", "Command")
        override fun getRowCount() = savedCommands.size
        override fun getColumnCount() = cols.size
        override fun getColumnName(c: Int) = cols[c]
        override fun isCellEditable(r: Int, c: Int) = false

        override fun getValueAt(r: Int, c: Int): Any {
            val cmd = savedCommands.getOrNull(r) ?: return ""
            return when (c) {
                0 -> cmd.name
                1 -> cmd.command
                else -> ""
            }
        }
    }
}

