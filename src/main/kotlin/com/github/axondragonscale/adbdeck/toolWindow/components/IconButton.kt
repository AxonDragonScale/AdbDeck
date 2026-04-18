package com.github.axondragonscale.adbdeck.toolwindow.components

import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon
import javax.swing.JButton

/**
 * A square icon-only button with IntelliJ-style hover/press background.
 */
fun iconButton(icon: Icon, tooltip: String, onClick: () -> Unit): JButton {
    val sz = JBUI.scale(24)
    return object : JButton(icon) {
        override fun paintComponent(g: Graphics) {
            if (model.isRollover || model.isPressed) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = if (model.isPressed) JBUI.CurrentTheme.ActionButton.pressedBackground()
                else JBUI.CurrentTheme.ActionButton.hoverBackground()
                g2.fillRoundRect(0, 0, width, height, JBUI.scale(4), JBUI.scale(4))
                g2.dispose()
            }
            super.paintComponent(g)
        }
        override fun getPreferredSize() = Dimension(sz, sz)
        override fun getMinimumSize() = preferredSize
        override fun getMaximumSize() = preferredSize
    }.apply {
        toolTipText = tooltip
        isBorderPainted = false
        isContentAreaFilled = false
        isFocusPainted = false
        isRolloverEnabled = true
        cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        addActionListener { onClick() }
    }
}

