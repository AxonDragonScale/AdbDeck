package com.github.axondragonscale.adbdeck.toolwindow.tabs

import com.github.axondragonscale.adbdeck.AdbDeckBundle
import com.github.axondragonscale.adbdeck.adb.DeviceSettingsCommands
import com.github.axondragonscale.adbdeck.adb.TestingCommands
import com.github.axondragonscale.adbdeck.toolwindow.ActionContext
import com.github.axondragonscale.adbdeck.util.notifyAdbDeck
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.*

/**
 * Testing Utilities tab: process death, trim memory, config changes.
 */
class TestingTabPanel(private val ctx: ActionContext) : JPanel(BorderLayout()) {

    private val trimLevelCombo = ComboBox(TestingCommands.TrimMemoryLevel.entries.toTypedArray()).apply {
        renderer = ListCellRenderer { _, value, _, _, _ ->
            JBLabel(value?.label ?: "")
        }
    }

    init {
        border = JBUI.Borders.empty(4)

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)

            // ── Process & Memory ──
            add(sectionLabel("Process & Memory"))

            add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                maximumSize = Dimension(Int.MAX_VALUE, 36)
                border = JBUI.Borders.empty(2, 0)
                add(JButton("💀 Simulate Process Death").apply {
                    isFocusPainted = false
                    toolTipText = "Kills the app process (app must be in background). Tests saved state restoration."
                    addActionListener { executeTestAction { s, p -> TestingCommands.simulateProcessDeath(ctx.adbController, s, p) } }
                })
                add(JBLabel("App must be in background").apply {
                    foreground = com.intellij.ui.JBColor.GRAY
                    font = font.deriveFont(Font.ITALIC)
                })
            })

            add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                maximumSize = Dimension(Int.MAX_VALUE, 36)
                border = JBUI.Borders.empty(2, 0)
                add(JBLabel("Trim Memory:"))
                add(trimLevelCombo.apply { preferredSize = Dimension(160, 24) })
                add(JButton("📉 Send").apply {
                    isFocusPainted = false
                    addActionListener {
                        val level = trimLevelCombo.item ?: return@addActionListener
                        executeTestAction { s, p -> TestingCommands.triggerTrimMemory(ctx.adbController, s, p, level) }
                    }
                })
            })


            add(Box.createVerticalStrut(16))

            // ── Configuration Changes ──
            add(sectionLabel("Configuration Changes"))

            add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                maximumSize = Dimension(Int.MAX_VALUE, 36)
                border = JBUI.Borders.empty(2, 0)
                add(JButton("🔄 Rotate →").apply {
                    isFocusPainted = false
                    toolTipText = "Rotate screen 90° clockwise"
                    addActionListener {
                        val serial = ctx.getSelectedDeviceSerial() ?: return@addActionListener
                        ApplicationManager.getApplication().executeOnPooledThread {
                            val current = TestingCommands.getCurrentRotation(ctx.adbController, serial)
                            val next = (current + 1) % 4
                            val result = TestingCommands.rotateScreen(ctx.adbController, serial, next)
                            ApplicationManager.getApplication().invokeLater {
                                ctx.logToConsole(result.command, if (result.isSuccess) result.output else result.error)
                            }
                        }
                    }
                })
                add(JButton("↺ Auto-Rotate").apply {
                    isFocusPainted = false
                    addActionListener {
                        val serial = ctx.getSelectedDeviceSerial() ?: return@addActionListener
                        ApplicationManager.getApplication().executeOnPooledThread {
                            val result = TestingCommands.enableAutoRotate(ctx.adbController, serial)
                            ApplicationManager.getApplication().invokeLater {
                                ctx.logToConsole(result.command, if (result.isSuccess) result.output else result.error)
                            }
                        }
                    }
                })
                add(JButton("🌙 Toggle Night Mode").apply {
                    isFocusPainted = false
                    addActionListener {
                        val serial = ctx.getSelectedDeviceSerial() ?: return@addActionListener
                        ApplicationManager.getApplication().executeOnPooledThread {
                            val isDark = DeviceSettingsCommands.getDarkModeEnabled(ctx.adbController, serial)
                            val result = DeviceSettingsCommands.setDarkMode(ctx.adbController, serial, !isDark)
                            ApplicationManager.getApplication().invokeLater {
                                ctx.logToConsole(result.command, if (result.isSuccess) result.output else result.error)
                            }
                        }
                    }
                })
            })

            add(Box.createVerticalGlue())
        }

        add(JBScrollPane(content).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
    }

    private fun sectionLabel(text: String): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            maximumSize = Dimension(Int.MAX_VALUE, 28)
            border = JBUI.Borders.empty(4, 0, 4, 0)
            add(JBLabel(text).apply { font = font.deriveFont(Font.BOLD) })
        }
    }


    private fun executeTestAction(
        action: (serial: String, pkg: String) -> com.github.axondragonscale.adbdeck.model.AdbResult
    ) {
        val serial = ctx.getSelectedDeviceSerial()
        val pkg = ctx.getSelectedPackage()
        if (serial == null) {
            return
        }
        if (pkg == null) {
            ctx.project.notifyAdbDeck(AdbDeckBundle.message("notification.noPackage"), NotificationType.WARNING)
            return
        }
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
}

