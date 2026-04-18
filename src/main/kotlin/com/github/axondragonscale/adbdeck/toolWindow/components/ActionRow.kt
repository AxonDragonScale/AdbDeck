package com.github.axondragonscale.adbdeck.toolwindow.components

import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * A clickable action row with icon, label, and hover highlight.
 * Used in the App tab for lifecycle actions.
 */
fun actionRow(label: String, icon: Icon, tooltip: String, onClick: () -> Unit): JPanel {
    val h = JBUI.scale(26)
    return object : JPanel(BorderLayout()) {
        override fun getPreferredSize() = Dimension(super.getPreferredSize().width, h)
        override fun getMaximumSize() = Dimension(Int.MAX_VALUE, h)
        override fun getMinimumSize() = Dimension(0, h)
    }.apply {
        isOpaque = false
        border = JBUI.Borders.empty(2, 2)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        val btn = JButton(label, icon).apply {
            toolTipText = tooltip
            isFocusPainted = false; isBorderPainted = false; isContentAreaFilled = false
            isRolloverEnabled = false
            horizontalAlignment = SwingConstants.LEFT
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { onClick() }
        }
        add(btn, BorderLayout.CENTER)

        val row = this
        val hover = object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                row.isOpaque = true
                row.background = JBUI.CurrentTheme.List.Hover.background(false)
                row.repaint()
            }
            override fun mouseExited(e: MouseEvent) {
                row.isOpaque = false
                row.repaint()
            }
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1) onClick()
            }
        }
        addMouseListener(hover)
        btn.addMouseListener(hover)
    }
}

