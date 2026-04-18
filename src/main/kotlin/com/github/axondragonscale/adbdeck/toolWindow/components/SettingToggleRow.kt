package com.github.axondragonscale.adbdeck.toolwindow.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * A reusable setting row: [✓ Label]
 * Uses IntelliJ's [JBCheckBox] for a native settings-style checkbox.
 * Reads current state asynchronously and toggles on click.
 */
class SettingToggleRow(
    label: String,
    private val onRead: () -> Boolean,
    private val onWrite: (Boolean) -> Unit,
) : JPanel(BorderLayout()) {

    private val checkBox = JBCheckBox(label).apply {
        isFocusPainted = false
        addActionListener {
            val newState = isSelected
            ApplicationManager.getApplication().executeOnPooledThread {
                onWrite(newState)
            }
        }
    }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(2, 0)
        add(checkBox, BorderLayout.WEST)
    }

    /**
     * Reads the current value from the device asynchronously and updates the checkbox.
     */
    fun refresh() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val currentValue = onRead()
            ApplicationManager.getApplication().invokeLater {
                checkBox.isSelected = currentValue
            }
        }
    }
}
