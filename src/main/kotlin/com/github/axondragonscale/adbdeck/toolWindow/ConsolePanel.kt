package com.github.axondragonscale.adbdeck.toolwindow

import com.github.axondragonscale.adbdeck.AdbDeckBundle
import com.github.axondragonscale.adbdeck.toolwindow.components.iconButton
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.JBColor
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Console output panel.
 * Shows timestamped ADB commands and their output with copy support.
 * Uses IDE editor colors and TitledSeparator header consistent with other panels.
 */
class ConsolePanel : JPanel(BorderLayout()) {

    private val textArea = JTextArea().apply {
        isEditable = false
        val scheme = EditorColorsManager.getInstance().globalScheme
        font = scheme.getFont(EditorFontType.CONSOLE_PLAIN).deriveFont(Font.PLAIN, JBUI.Fonts.smallFont().size2D)
        background = JBColor(scheme.defaultBackground, scheme.defaultBackground)
        foreground = JBColor(scheme.defaultForeground, scheme.defaultForeground)
        border = JBUI.Borders.empty(4)
    }

    private var entryCount = 0
    private val maxEntries = 50

    init {
        border = JBUI.Borders.empty()

        // ── Header: TitledSeparator + icon buttons ──
        val header = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.emptyTop(16)
            add(TitledSeparator(AdbDeckBundle.message("console.title")), GridBagConstraints().apply {
                gridx = 0; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
            })
            add(iconButton(AllIcons.Actions.Copy, "Copy") {
                val text = textArea.selectedText ?: textArea.text
                if (text.isNotBlank()) CopyPasteManager.getInstance().setContents(StringSelection(text))
            }, GridBagConstraints().apply {
                gridx = 1; gridy = 0; anchor = GridBagConstraints.EAST
            })
            add(iconButton(AllIcons.Actions.GC, AdbDeckBundle.message("console.clear")) {
                clear()
            }, GridBagConstraints().apply {
                gridx = 2; gridy = 0; anchor = GridBagConstraints.EAST
            })
        }
        add(header, BorderLayout.NORTH)

        // ── Scrollable text area ──
        add(JBScrollPane(textArea).apply {
            border = JBUI.Borders.empty(0, 4)
        }, BorderLayout.CENTER)
    }

    /**
     * Appends a command and its output to the console.
     */
    fun append(command: String, output: String) {
        val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        textArea.append("[$timestamp] $ $command\n")
        if (output.isNotBlank()) {
            textArea.append("$output\n")
        }
        textArea.append("\n")
        // Auto-scroll to bottom
        textArea.caretPosition = textArea.document.length

        entryCount++
        if (entryCount > maxEntries) {
            trimOldEntries()
        }
    }

    /**
     * Clears all console output.
     */
    fun clear() {
        textArea.text = ""
        entryCount = 0
    }

    private fun trimOldEntries() {
        val text = textArea.text
        // Remove first entry (up to second "\n\n")
        val idx = text.indexOf("\n\n")
        if (idx >= 0 && idx + 2 < text.length) {
            textArea.text = text.substring(idx + 2)
            entryCount--
        }
    }
}
