package com.github.axondragonscale.adbdeck.toolwindow.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.OnOffButton
import com.intellij.util.ui.JBUI

/**
 * Encapsulates a labelled [OnOffButton] setting.
 * Exposes [label] and [toggle] as separate components so the caller can place
 * them into a shared [java.awt.GridBagLayout], ensuring all toggles in the
 * same grid column are perfectly aligned regardless of label length.
 */
class SettingToggleRow(
    labelText: String,
    private val onRead: () -> Boolean,
    private val onWrite: (Boolean) -> Unit,
) {
    val label = JBLabel(labelText)

    val toggle = OnOffButton().apply {
        addActionListener {
            val newState = isSelected
            ApplicationManager.getApplication().executeOnPooledThread {
                onWrite(newState)
            }
        }
    }

    fun refresh() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val currentValue = onRead()
            ApplicationManager.getApplication().invokeLater {
                toggle.isSelected = currentValue
            }
        }
    }
}
