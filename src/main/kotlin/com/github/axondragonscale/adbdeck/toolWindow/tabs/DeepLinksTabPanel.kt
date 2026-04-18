package com.github.axondragonscale.adbdeck.toolwindow.tabs

import com.github.axondragonscale.adbdeck.adb.IntentCommands
import com.github.axondragonscale.adbdeck.model.DeepLinkBookmark
import com.github.axondragonscale.adbdeck.model.IntentBookmark
import com.github.axondragonscale.adbdeck.model.IntentBookmarkExtra
import com.github.axondragonscale.adbdeck.state.AdbDeckStateService
import com.github.axondragonscale.adbdeck.toolwindow.ActionContext
import com.github.axondragonscale.adbdeck.toolwindow.components.iconButton
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.MnemonicNavigationFilter
import com.intellij.openapi.ui.popup.SpeedSearchFilter
import com.intellij.ui.PopupHandler
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.ButtonGroup
import javax.swing.DefaultCellEditor
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTable
import javax.swing.ListCellRenderer
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Deep Links & Intents tab.
 * Top: Deep link combo (with recents) + Custom Intent builder (scrollable).
 * Bottom: Bookmarks (sticky).
 */
class DeepLinksTabPanel(private val ctx: ActionContext) : JPanel(BorderLayout()) {

    private val stateService = ctx.project.service<AdbDeckStateService>()

    // URI combo — editable, dropdown shows recents
    private val uriComboModel = DefaultComboBoxModel<String>()
    private val uriCombo = ComboBox(uriComboModel).apply {
        isEditable = true
        prototypeDisplayValue = "https://example.com/deep/link/path/example"
    }

    private sealed class BookmarkEntry {
        data class Link(val bookmark: DeepLinkBookmark) : BookmarkEntry()
        data class Intent(val bookmark: IntentBookmark) : BookmarkEntry()
    }

    private val combinedBookmarkEntries = mutableListOf<BookmarkEntry>()
    private val combinedBookmarkTableModel = BookmarkTableModel()
    private val combinedBookmarkTable = JBTable(combinedBookmarkTableModel).apply {
        setShowGrid(false)
        rowHeight = JBUI.scale(24)
        tableHeader.reorderingAllowed = false
        selectionModel.selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
    }

    // Intent builder
    private val intentActionField = JBTextField().apply { emptyText.text = "android.intent.action.VIEW" }
    private val intentDataField = JBTextField()
    private val intentComponentField = JBTextField().apply { emptyText.text = "com.example/.MainActivity" }
    private val intentCategoryField = JBTextField().apply { emptyText.text = "android.intent.category.DEFAULT" }
    private val selectedFlags = mutableSetOf<IntentCommands.IntentFlag>()
    private val flagsField = FlagsDropdownField()
    private val intentTypeGroup = ButtonGroup()
    private var intentType = IntentCommands.IntentType.ACTIVITY

    // Extras — table with enabled, key, type, value
    private data class ExtraRow(
        var enabled: Boolean = true,
        var key: String = "",
        var type: IntentCommands.ExtraType = IntentCommands.ExtraType.STRING,
        var value: String = "",
    )
    private val extras = mutableListOf<ExtraRow>()
    private val extrasTableModel = ExtrasTableModel()
    private val extrasTable: JBTable

    init {
        border = JBUI.Borders.empty(0, 4, 4, 4)

        // ═══════════════════════════════════════════
        // TOP: Scrollable content
        // ═══════════════════════════════════════════
        val content = JPanel(GridBagLayout())
        var row = 0

        fun fillX(r: Int) = GridBagConstraints().apply {
            gridx = 0; gridy = r; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
        }

        // ── Deep Link ──
        content.add(TitledSeparator("Deep Link").apply {
            border = JBUI.Borders.emptyTop(12)
        }, fillX(row++))

        // URI combo + Open + Bookmark buttons
        content.add(JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(4, 0)
            add(uriCombo, GridBagConstraints().apply {
                gridx = 0; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insetsRight(8)
            })
            add(JButton("Open").apply {
                isFocusPainted = false
                addActionListener { openDeepLink() }
            }, GridBagConstraints().apply {
                gridx = 1; gridy = 0; anchor = GridBagConstraints.EAST
                insets = JBUI.insetsRight(4)
            })
            add(iconButton(AllIcons.Nodes.BookmarkGroup, "Save as bookmark") { addBookmark() }, GridBagConstraints().apply {
                gridx = 2; gridy = 0; anchor = GridBagConstraints.EAST
            })
        }, fillX(row++))

        // Enter key opens deep link
        (uriCombo.editor.editorComponent as? JBTextField)?.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) openDeepLink()
            }
        })

        // ── Custom Intent ──
        content.add(TitledSeparator("Custom Intent").apply {
            border = JBUI.Borders.emptyTop(16)
        }, fillX(row++))

        // Intent type
        content.add(JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(4, 0)
            add(JBLabel("Type:").apply {
                preferredSize = Dimension(JBUI.scale(80), preferredSize.height)
            }, GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST; insets = JBUI.insetsRight(8)
            })
            for ((i, type) in IntentCommands.IntentType.entries.withIndex()) {
                val rb = JRadioButton(
                    type.name.lowercase().replaceFirstChar { it.uppercase() },
                    type == IntentCommands.IntentType.ACTIVITY
                ).apply {
                    isFocusPainted = false
                    border = JBUI.Borders.empty(0, 4)
                    addActionListener { intentType = type }
                }
                intentTypeGroup.add(rb)
                add(rb, GridBagConstraints().apply {
                    gridx = i + 1; gridy = 0; anchor = GridBagConstraints.WEST
                })
            }
            add(JPanel(), GridBagConstraints().apply {
                gridx = IntentCommands.IntentType.entries.size + 1; gridy = 0; weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
            })
        }, fillX(row++))

        // Intent fields
        content.add(createLabeledField("Action:", intentActionField), fillX(row++))
        content.add(createLabeledField("Data:", intentDataField), fillX(row++))
        content.add(createLabeledField("Component:", intentComponentField), fillX(row++))
        content.add(createLabeledField("Category:", intentCategoryField), fillX(row++))

        // Flags — dropdown selector styled like a combo box
        content.add(JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(2, 0)
            add(JBLabel("Flags:").apply {
                preferredSize = Dimension(JBUI.scale(80), preferredSize.height)
            }, GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
                insets = JBUI.insetsRight(8)
            })
            add(flagsField, GridBagConstraints().apply {
                gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            })
        }, fillX(row++))

        // Initialize extras table
        extrasTable = JBTable(extrasTableModel).apply {
            setShowGrid(false)
            rowHeight = JBUI.scale(24)
            tableHeader.reorderingAllowed = false
            columnModel.getColumn(0).preferredWidth = 30
            columnModel.getColumn(0).maxWidth = 40
            columnModel.getColumn(0).cellRenderer = object : DefaultTableCellRenderer() {
                private val cb = JCheckBox().apply { horizontalAlignment = JCheckBox.CENTER }
                override fun getTableCellRendererComponent(
                    table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, r: Int, c: Int
                ): Component {
                    cb.isSelected = value as? Boolean ?: true
                    cb.background = if (isSelected) table.selectionBackground else table.background
                    return cb
                }
            }
            columnModel.getColumn(0).cellEditor = DefaultCellEditor(JCheckBox().apply { horizontalAlignment = JCheckBox.CENTER })
            columnModel.getColumn(1).preferredWidth = 120
            columnModel.getColumn(2).preferredWidth = 80
            columnModel.getColumn(2).cellEditor = DefaultCellEditor(ComboBox(IntentCommands.ExtraType.entries.toTypedArray()))
            columnModel.getColumn(3).preferredWidth = 150
        }

        // Extras header + add/remove
        content.add(JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(8, 0, 2, 0)
            add(JBLabel("Extras"), GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST; insets = JBUI.insetsRight(8)
            })
            add(iconButton(AllIcons.General.Add, "Add extra") {
                extras.add(ExtraRow())
                extrasTableModel.fireTableRowsInserted(extras.size - 1, extras.size - 1)
            }, GridBagConstraints().apply {
                gridx = 1; gridy = 0; anchor = GridBagConstraints.WEST; insets = JBUI.insetsRight(4)
            })
            add(iconButton(AllIcons.General.Remove, "Remove selected extra") {
                val selectedRow = extrasTable.selectedRow
                if (selectedRow >= 0) {
                    extras.removeAt(selectedRow)
                    extrasTableModel.fireTableRowsDeleted(selectedRow, selectedRow)
                }
            }, GridBagConstraints().apply {
                gridx = 2; gridy = 0; anchor = GridBagConstraints.WEST
            })
            add(JPanel(), GridBagConstraints().apply {
                gridx = 3; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            })
        }, fillX(row++))

        // Extras table
        content.add(JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 0)
            preferredSize = Dimension(0, JBUI.scale(144))
            add(JBScrollPane(extrasTable), BorderLayout.CENTER)
        }, fillX(row++))

        // Clear (left) + Bookmark + Send (right)
        content.add(JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.emptyTop(8)
            add(JButton("Clear").apply {
                isFocusPainted = false
                addActionListener { clearIntentForm() }
            }, GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
            })
            add(JPanel(), GridBagConstraints().apply {
                gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            })
            add(iconButton(AllIcons.Nodes.BookmarkGroup, "Save intent as bookmark") { addIntentBookmark() }, GridBagConstraints().apply {
                gridx = 2; gridy = 0; anchor = GridBagConstraints.EAST; insets = JBUI.insetsRight(4)
            })
            add(JButton("Send Intent").apply {
                isFocusPainted = false
                addActionListener { sendCustomIntent() }
            }, GridBagConstraints().apply {
                gridx = 3; gridy = 0; anchor = GridBagConstraints.EAST
            })
        }, fillX(row++))

        // Glue
        content.add(JPanel(), GridBagConstraints().apply {
            gridx = 0; gridy = row; weighty = 1.0; fill = GridBagConstraints.VERTICAL
        })

        add(JBScrollPane(content).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)

        // ═══════════════════════════════════════════
        // BOTTOM: Sticky Bookmarks
        // ═══════════════════════════════════════════
        val bottomPanel = JPanel(GridBagLayout())
        var bRow = 0

        fun bFillX(r: Int) = GridBagConstraints().apply {
            gridx = 0; gridy = r; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
        }

        bottomPanel.add(TitledSeparator("Bookmarks").apply {
            border = JBUI.Borders.empty(4, 0, 2, 0)
        }, bFillX(bRow++))

        combinedBookmarkTable.apply {
            columnModel.getColumn(0).preferredWidth = JBUI.scale(60)
            columnModel.getColumn(0).maxWidth = JBUI.scale(80)
            columnModel.getColumn(1).preferredWidth = JBUI.scale(120)
            columnModel.getColumn(2).preferredWidth = JBUI.scale(300)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val row = rowAtPoint(e.point)
                    if (row < 0) return
                    val selected = combinedBookmarkEntries.getOrNull(row) ?: return
                    when (selected) {
                        is BookmarkEntry.Link -> {
                            uriCombo.editor.item = selected.bookmark.uri
                            if (e.clickCount == 2) openDeepLink()
                        }
                        is BookmarkEntry.Intent -> loadIntentBookmark(selected.bookmark)
                    }
                }
            })
        }

        PopupHandler.installPopupMenu(combinedBookmarkTable, createCombinedBookmarkContextMenu(), "AdbDeckBookmarksPopup")

        bottomPanel.add(JPanel(BorderLayout()).apply {
            preferredSize = Dimension(0, JBUI.scale(150))
            add(JBScrollPane(combinedBookmarkTable), BorderLayout.CENTER)
        }, bFillX(bRow++))

        add(bottomPanel, BorderLayout.SOUTH)

        // Load data
        refreshAllBookmarks()
        refreshHistory()
    }

    // ── Helpers ──

    private fun createLabeledField(label: String, field: JBTextField): JPanel {
        return JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(2, 0)
            add(JBLabel(label).apply {
                preferredSize = Dimension(JBUI.scale(80), preferredSize.height)
            }, GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
                insets = JBUI.insetsRight(8)
            })
            add(field, GridBagConstraints().apply {
                gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            })
        }
    }

    private fun getUriText(): String = (uriCombo.editor.item as? String)?.trim() ?: ""

    private fun openDeepLink() {
        val uri = getUriText()
        if (uri.isBlank()) return
        val serial = ctx.getSelectedDeviceSerial() ?: return
        stateService.addDeepLink(uri)
        refreshHistory()

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = ctx.adbController.executeShellCommand(serial, "am start -a android.intent.action.VIEW -d \"$uri\"")
            ApplicationManager.getApplication().invokeLater {
                ctx.logToConsole(result.command, if (result.isSuccess) result.output else result.error)
            }
        }
    }

    private fun sendCustomIntent() {
        val serial = ctx.getSelectedDeviceSerial() ?: return
        val intentExtras = extras.filter { it.enabled && it.key.isNotBlank() }.map {
            IntentCommands.IntentExtra(it.key, it.value, it.type)
        }
        val combinedFlags = if (selectedFlags.isEmpty()) null
        else "0x${Integer.toHexString(selectedFlags.fold(0) { acc, f -> acc or f.value })}"
        val command = IntentCommands.buildCommand(
            type = intentType,
            action = intentActionField.text.trim().ifBlank { null },
            dataUri = intentDataField.text.trim().ifBlank { null },
            component = intentComponentField.text.trim().ifBlank { null },
            category = intentCategoryField.text.trim().ifBlank { null },
            extras = intentExtras,
            flags = combinedFlags,
        )
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = IntentCommands.executeIntent(ctx.adbController, serial, command)
            ApplicationManager.getApplication().invokeLater {
                ctx.logToConsole(result.command, if (result.isSuccess) result.output else result.error)
            }
        }
    }

    private fun clearIntentForm() {
        intentActionField.text = ""
        intentDataField.text = ""
        intentComponentField.text = ""
        intentCategoryField.text = ""
        selectedFlags.clear()
        flagsField.updateText()
        extras.clear()
        extrasTableModel.fireTableDataChanged()
    }

    private fun showFlagsPopup(anchor: java.awt.Component) {
        val step = object : com.intellij.openapi.ui.popup.ListPopupStep<IntentCommands.IntentFlag> {
            override fun getTitle() = "Select Flags"
            override fun getValues() = IntentCommands.IntentFlag.entries.toList()
            override fun isSelectable(value: IntentCommands.IntentFlag?) = true
            override fun getIconFor(value: IntentCommands.IntentFlag?) = null
            override fun getTextFor(value: IntentCommands.IntentFlag?) = value?.label ?: ""
            override fun getSeparatorAbove(value: IntentCommands.IntentFlag?): com.intellij.openapi.ui.popup.ListSeparator? = null
            override fun getDefaultOptionIndex() = -1
            override fun isMnemonicsNavigationEnabled() = false
            override fun getMnemonicNavigationFilter(): MnemonicNavigationFilter<IntentCommands.IntentFlag>? = null
            override fun isSpeedSearchEnabled() = true
            override fun getSpeedSearchFilter(): SpeedSearchFilter<IntentCommands.IntentFlag> = SpeedSearchFilter { it?.label ?: "" }
            override fun isAutoSelectionEnabled() = false
            override fun getFinalRunnable(): Runnable? = null
            override fun hasSubstep(selectedValue: IntentCommands.IntentFlag?) = false
            override fun canceled() {}
            override fun onChosen(selectedValue: IntentCommands.IntentFlag?, finalChoice: Boolean): com.intellij.openapi.ui.popup.PopupStep<*>? {
                if (selectedValue != null) {
                    if (selectedFlags.contains(selectedValue)) selectedFlags.remove(selectedValue)
                    else selectedFlags.add(selectedValue)
                    flagsField.updateText()
                }
                return com.intellij.openapi.ui.popup.PopupStep.FINAL_CHOICE
            }
        }

        val popup = object : com.intellij.ui.popup.list.ListPopupImpl(ctx.project, step) {
            override fun handleSelect(handleFinalChoices: Boolean) {
                val selectedValue = list.selectedValue as? IntentCommands.IntentFlag ?: return
                if (selectedFlags.contains(selectedValue)) selectedFlags.remove(selectedValue)
                else selectedFlags.add(selectedValue)
                flagsField.updateText()
                list.repaint()
            }
        }

        popup.list.cellRenderer = ListCellRenderer { list, value, index, isSelected, cellHasFocus ->
            val flag = value as? IntentCommands.IntentFlag
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(0, 4)
                background = if (isSelected) list.selectionBackground else list.background
                val cb = JCheckBox(flag?.label ?: "").apply {
                    this.isSelected = flag != null && selectedFlags.contains(flag)
                    this.background = if (isSelected) list.selectionBackground else list.background
                    this.foreground = if (isSelected) list.selectionForeground else list.foreground
                    isOpaque = false
                    isFocusPainted = false
                }
                add(cb, BorderLayout.CENTER)
                isOpaque = true
            }
        }

        popup.setMinimumSize(java.awt.Dimension(anchor.width, 0))
        popup.showUnderneathOf(anchor)
        popup.content?.let { content ->
            val popupWindow = javax.swing.SwingUtilities.getWindowAncestor(content)
            popupWindow?.setSize(anchor.width, popupWindow.height)
        }
    }

    private fun updateFlagsSummary() {
        flagsField.updateText()
    }

    /**
     * A combo-box-like component that shows selected flags text (ellipsized) and a dropdown arrow button.
     */
    private inner class FlagsDropdownField : ComboBox<String>() {
        init {
            isEditable = false
            isEnabled = true
            model = DefaultComboBoxModel(arrayOf("None"))

            // Override to open flags popup instead of default combo popup
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (isPopupVisible) hidePopup()
                    showFlagsPopup(this@FlagsDropdownField)
                    e.consume()
                }
            })
            // Intercept the arrow button click too
            for (child in components) {
                child.addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        if (isPopupVisible) hidePopup()
                        showFlagsPopup(this@FlagsDropdownField)
                        e.consume()
                    }
                })
            }
        }

        override fun showPopup() {
            // Suppress default popup
        }

        fun updateText() {
            val displayText = if (selectedFlags.isEmpty()) {
                "None"
            } else {
                val first = selectedFlags.first().label
                val remaining = selectedFlags.size - 1
                if (remaining > 0) "$first (+$remaining)" else first
            }
            model = DefaultComboBoxModel(arrayOf(displayText))
            selectedItem = displayText
        }
    }

    private fun addBookmark() {
        val uri = getUriText()
        val label = Messages.showInputDialog(ctx.project, "Bookmark label:", "Add Bookmark", null)
        if (!label.isNullOrBlank()) {
            stateService.addBookmark(label, uri.ifBlank { label })
            refreshAllBookmarks()
        }
    }

    private fun addIntentBookmark() {
        val label = Messages.showInputDialog(ctx.project, "Bookmark label:", "Save Intent Bookmark", null)
        if (label.isNullOrBlank()) return
        val bookmark = IntentBookmark(
            label = label,
            type = intentType.name,
            action = intentActionField.text.trim(),
            data = intentDataField.text.trim(),
            component = intentComponentField.text.trim(),
            category = intentCategoryField.text.trim(),
            flags = selectedFlags.joinToString(",") { it.name },
            extras = extras.map { IntentBookmarkExtra(it.enabled, it.key, it.type.name, it.value) }.toMutableList(),
        )
        stateService.addIntentBookmark(bookmark)
        refreshAllBookmarks()
    }

    private fun loadIntentBookmark(bookmark: IntentBookmark) {
        intentType = IntentCommands.IntentType.entries.firstOrNull { it.name == bookmark.type } ?: IntentCommands.IntentType.ACTIVITY
        // Update radio buttons
        val enumeration = intentTypeGroup.elements
        var idx = 0
        while (enumeration.hasMoreElements()) {
            val rb = enumeration.nextElement()
            rb.isSelected = IntentCommands.IntentType.entries[idx] == intentType
            idx++
        }
        intentActionField.text = bookmark.action
        intentDataField.text = bookmark.data
        intentComponentField.text = bookmark.component
        intentCategoryField.text = bookmark.category
        selectedFlags.clear()
        if (bookmark.flags.isNotBlank()) {
            bookmark.flags.split(",").forEach { name ->
                IntentCommands.IntentFlag.entries.firstOrNull { it.name == name }?.let { selectedFlags.add(it) }
            }
        }
        flagsField.updateText()
        extras.clear()
        bookmark.extras.forEach { e ->
            extras.add(ExtraRow(
                enabled = e.enabled,
                key = e.key,
                type = IntentCommands.ExtraType.entries.firstOrNull { it.name == e.type } ?: IntentCommands.ExtraType.STRING,
                value = e.value,
            ))
        }
        extrasTableModel.fireTableDataChanged()
    }

    private fun selectedBookmarkEntry(): BookmarkEntry? =
        combinedBookmarkEntries.getOrNull(combinedBookmarkTable.selectedRow)

    private fun createCombinedBookmarkContextMenu(): DefaultActionGroup {
        return DefaultActionGroup().apply {
            add(object : AnAction("Open / Send", "Open deep link or send intent on device", AllIcons.Actions.Execute) {
                override fun actionPerformed(e: AnActionEvent) {
                    when (val selected = selectedBookmarkEntry() ?: return) {
                        is BookmarkEntry.Link -> { uriCombo.editor.item = selected.bookmark.uri; openDeepLink() }
                        is BookmarkEntry.Intent -> { loadIntentBookmark(selected.bookmark); sendCustomIntent() }
                    }
                }
            })
            add(Separator.getInstance())
            add(object : AnAction("Copy URI", "Copy URI to clipboard", AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) {
                    val selected = selectedBookmarkEntry() as? BookmarkEntry.Link ?: return
                    CopyPasteManager.getInstance().setContents(StringSelection(selected.bookmark.uri))
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.isVisible = selectedBookmarkEntry() is BookmarkEntry.Link
                }
            })
            add(object : AnAction("Copy Label", "Copy label to clipboard", AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) {
                    val label = when (val selected = selectedBookmarkEntry() ?: return) {
                        is BookmarkEntry.Link -> selected.bookmark.label
                        is BookmarkEntry.Intent -> selected.bookmark.label
                    }
                    CopyPasteManager.getInstance().setContents(StringSelection(label))
                }
            })
            add(Separator.getInstance())
            add(object : AnAction("Edit Label…", "Rename this bookmark", AllIcons.Actions.Edit) {
                override fun actionPerformed(e: AnActionEvent) {
                    when (val selected = selectedBookmarkEntry() ?: return) {
                        is BookmarkEntry.Link -> {
                            val newLabel = Messages.showInputDialog(ctx.project, "New label:", "Edit Bookmark", null, selected.bookmark.label, null)
                            if (!newLabel.isNullOrBlank()) {
                                stateService.updateBookmark(selected.bookmark.uri, newLabel, selected.bookmark.uri)
                                refreshAllBookmarks()
                            }
                        }
                        is BookmarkEntry.Intent -> {
                            val newLabel = Messages.showInputDialog(ctx.project, "New label:", "Edit Bookmark", null, selected.bookmark.label, null)
                            if (!newLabel.isNullOrBlank()) {
                                stateService.removeIntentBookmark(selected.bookmark.label)
                                stateService.addIntentBookmark(selected.bookmark.copy(label = newLabel))
                                refreshAllBookmarks()
                            }
                        }
                    }
                }
            })
            add(object : AnAction("Remove", "Remove this bookmark", AllIcons.General.Remove) {
                override fun actionPerformed(e: AnActionEvent) {
                    when (val selected = selectedBookmarkEntry() ?: return) {
                        is BookmarkEntry.Link -> { stateService.removeBookmark(selected.bookmark.uri); refreshAllBookmarks() }
                        is BookmarkEntry.Intent -> { stateService.removeIntentBookmark(selected.bookmark.label); refreshAllBookmarks() }
                    }
                }
            })
        }
    }

    private fun refreshAllBookmarks() {
        combinedBookmarkEntries.clear()
        stateService.getBookmarks().forEach { combinedBookmarkEntries.add(BookmarkEntry.Link(it)) }
        stateService.getIntentBookmarks().forEach { combinedBookmarkEntries.add(BookmarkEntry.Intent(it)) }
        combinedBookmarkTableModel.fireTableDataChanged()
    }

    private fun refreshHistory() {
        uriComboModel.removeAllElements()
        stateService.getDeepLinkHistory().forEach { uriComboModel.addElement(it) }
        // Keep the editor text (don't auto-select first item)
        uriCombo.selectedItem = null
    }

    // ── Extras Table Model ──

    inner class ExtrasTableModel : AbstractTableModel() {
        private val cols = arrayOf("", "Key", "Type", "Value")
        override fun getRowCount() = extras.size
        override fun getColumnCount() = cols.size
        override fun getColumnName(c: Int) = cols[c]
        override fun isCellEditable(r: Int, c: Int) = true

        override fun getColumnClass(c: Int): Class<*> = when (c) {
            0 -> java.lang.Boolean::class.java
            else -> String::class.java
        }

        override fun getValueAt(r: Int, c: Int): Any = when (c) {
            0 -> extras[r].enabled
            1 -> extras[r].key
            2 -> extras[r].type
            3 -> extras[r].value
            else -> ""
        }

        override fun setValueAt(value: Any?, r: Int, c: Int) {
            when (c) {
                0 -> extras[r].enabled = value as? Boolean ?: true
                1 -> extras[r].key = value?.toString() ?: ""
                2 -> extras[r].type = value as? IntentCommands.ExtraType ?: IntentCommands.ExtraType.STRING
                3 -> extras[r].value = value?.toString() ?: ""
            }
            fireTableCellUpdated(r, c)
        }
    }

    // ── Bookmark Table Model ──

    inner class BookmarkTableModel : AbstractTableModel() {
        private val cols = arrayOf("Type", "Label", "Value")
        override fun getRowCount() = combinedBookmarkEntries.size
        override fun getColumnCount() = cols.size
        override fun getColumnName(c: Int) = cols[c]
        override fun isCellEditable(r: Int, c: Int) = false

        override fun getValueAt(r: Int, c: Int): Any {
            val entry = combinedBookmarkEntries.getOrNull(r) ?: return ""
            return when (c) {
                0 -> when (entry) { is BookmarkEntry.Link -> "Link"; is BookmarkEntry.Intent -> "Intent" }
                1 -> when (entry) { is BookmarkEntry.Link -> entry.bookmark.label; is BookmarkEntry.Intent -> entry.bookmark.label }
                2 -> when (entry) { is BookmarkEntry.Link -> entry.bookmark.uri; is BookmarkEntry.Intent -> entry.bookmark.type.lowercase().replaceFirstChar { it.uppercase() } }
                else -> ""
            }
        }
    }
}
