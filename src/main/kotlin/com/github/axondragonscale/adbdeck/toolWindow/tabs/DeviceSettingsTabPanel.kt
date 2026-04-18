package com.github.axondragonscale.adbdeck.toolwindow.tabs

import com.github.axondragonscale.adbdeck.AdbDeckBundle
import com.github.axondragonscale.adbdeck.adb.DeviceSettingsCommands
import com.github.axondragonscale.adbdeck.adb.TestingCommands
import com.github.axondragonscale.adbdeck.model.AdbResult
import com.github.axondragonscale.adbdeck.toolwindow.ActionContext
import com.github.axondragonscale.adbdeck.toolwindow.components.SettingToggleRow
import com.github.axondragonscale.adbdeck.toolwindow.components.iconButton
import com.github.axondragonscale.adbdeck.util.notifyAdbDeck
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.ListCellRenderer

/**
 * Device Settings tab: developer options, display settings, and testing utilities.
 * Uses GridBagLayout with TitledSeparators to match IntelliJ's settings UI style.
 */
class DeviceSettingsTabPanel(private val ctx: ActionContext) : JPanel(BorderLayout()) {

    private val toggleRows = mutableListOf<SettingToggleRow>()

    init {
        border = JBUI.Borders.empty(0, 4, 4, 4)

        val content = JPanel(GridBagLayout())
        var row = 0

        fun fillX(r: Int) = GridBagConstraints().apply {
            gridx = 0; gridy = r; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
        }

        fun serial() = ctx.getSelectedDeviceSerial() ?: ""

        // ── Header: title + refresh ──
        content.add(JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 0, 4, 0)
            add(JBLabel("Device Settings").apply {
                font = font.deriveFont(java.awt.Font.BOLD, font.size + 1f)
            }, BorderLayout.WEST)
            add(iconButton(AllIcons.Actions.Refresh, "Refresh all settings from device") { refreshAll() }, BorderLayout.EAST)
        }, fillX(row++))

        // ── Developer Options ──
        content.add(TitledSeparator("Developer Options").apply {
            border = JBUI.Borders.emptyTop(8)
        }, fillX(row++))

        content.add(addToggle(
            AdbDeckBundle.message("settings.animations"),
            { DeviceSettingsCommands.getAnimationsEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setAnimations(ctx.adbController, serial(), it) },
        ), fillX(row++))

        content.add(addToggle(
            AdbDeckBundle.message("settings.layoutBounds"),
            { DeviceSettingsCommands.getLayoutBoundsEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setLayoutBounds(ctx.adbController, serial(), it) },
        ), fillX(row++))

        content.add(addToggle(
            AdbDeckBundle.message("settings.showOverdraw"),
            { DeviceSettingsCommands.getOverdrawEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setOverdraw(ctx.adbController, serial(), it) },
        ), fillX(row++))

        content.add(addToggle(
            AdbDeckBundle.message("settings.dontKeepActivities"),
            { DeviceSettingsCommands.getDontKeepActivitiesEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setDontKeepActivities(ctx.adbController, serial(), it) },
        ), fillX(row++))

        content.add(addToggle(
            AdbDeckBundle.message("settings.stayAwake"),
            { DeviceSettingsCommands.getStayAwakeEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setStayAwake(ctx.adbController, serial(), it) },
        ), fillX(row++))

        content.add(addToggle(
            AdbDeckBundle.message("settings.showRefreshRate"),
            { DeviceSettingsCommands.getRefreshRateEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setRefreshRate(ctx.adbController, serial(), it) },
        ), fillX(row++))

        content.add(addToggle(
            AdbDeckBundle.message("settings.showSurfaceUpdates"),
            { DeviceSettingsCommands.getSurfaceUpdatesEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setSurfaceUpdates(ctx.adbController, serial(), it) },
        ), fillX(row++))

        content.add(addToggle(
            AdbDeckBundle.message("settings.profileHwui"),
            { DeviceSettingsCommands.getHwuiProfilingEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setHwuiProfiling(ctx.adbController, serial(), it) },
        ), fillX(row++))

        // ── Display & Appearance ──
        content.add(TitledSeparator("Display & Appearance").apply {
            border = JBUI.Borders.emptyTop(16)
        }, fillX(row++))

        content.add(addToggle(
            AdbDeckBundle.message("settings.darkMode"),
            { DeviceSettingsCommands.getDarkModeEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setDarkMode(ctx.adbController, serial(), it) },
        ), fillX(row++))

        // Font Size — AOSP values: 0.85, 1.0, 1.15, 1.3
        content.add(createScaleSliderRow(
            label = "Font Size:",
            stops = 4,
            defaultIndex = 1,
        ) { index ->
            val scale = listOf(0.85f, 1.0f, 1.15f, 1.3f)[index]
            ApplicationManager.getApplication().executeOnPooledThread {
                DeviceSettingsCommands.setFontScale(ctx.adbController, serial(), scale)
            }
        }, fillX(row++))

        // Display Size — uses device physical DPI with evenly spaced offsets
        content.add(createScaleSliderRow(
            label = "Display Size:",
            stops = 5,
            defaultIndex = 2,
        ) { index ->
            ApplicationManager.getApplication().executeOnPooledThread {
                if (index == 2) {
                    DeviceSettingsCommands.resetDisplayScale(ctx.adbController, serial())
                } else {
                    // Map 0..4 to scale offsets: -2, -1, 0, +1, +2 steps
                    // Each step ≈ 10% of physical DPI, matching AOSP ScreenZoomSettings
                    val stepFraction = listOf(-0.20f, -0.10f, 0f, 0.10f, 0.20f)[index]
                    DeviceSettingsCommands.setDisplayScale(ctx.adbController, serial(), 1.0f + stepFraction)
                }
            }
        }, fillX(row++))

        // ── Configuration Changes ──
        content.add(TitledSeparator("Configuration Changes").apply {
            border = JBUI.Borders.emptyTop(16)
        }, fillX(row++))

        content.add(createButtonRow(
            JButton("Rotate →").apply {
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
            },
            JButton("Auto-Rotate").apply {
                isFocusPainted = false
                toolTipText = "Enable auto-rotate"
                addActionListener {
                    val serial = ctx.getSelectedDeviceSerial() ?: return@addActionListener
                    ApplicationManager.getApplication().executeOnPooledThread {
                        val result = TestingCommands.enableAutoRotate(ctx.adbController, serial)
                        ApplicationManager.getApplication().invokeLater {
                            ctx.logToConsole(result.command, if (result.isSuccess) result.output else result.error)
                        }
                    }
                }
            },
        ), fillX(row++))

        // ── Process & Memory ──
        content.add(TitledSeparator("Process & Memory").apply {
            border = JBUI.Borders.emptyTop(16)
        }, fillX(row++))

        content.add(createButtonRow(
            JButton("Simulate Process Death").apply {
                isFocusPainted = false
                toolTipText = "Kill app process (app must be in background)"
                addActionListener {
                    executeTestAction { s, p -> TestingCommands.simulateProcessDeath(ctx.adbController, s, p) }
                }
            },
        ), fillX(row++))

        // Trim Memory row
        val trimLevelCombo = ComboBox(TestingCommands.TrimMemoryLevel.entries.toTypedArray()).apply {
            renderer = ListCellRenderer { _, value, _, _, _ -> JBLabel(value?.label ?: "") }
        }
        content.add(JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(2, 0)
            add(JBLabel("Trim Memory:"), GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST; insets = JBUI.insetsRight(8)
            })
            add(trimLevelCombo, GridBagConstraints().apply {
                gridx = 1; gridy = 0; anchor = GridBagConstraints.WEST; insets = JBUI.insetsRight(8)
            })
            add(JButton("Send").apply {
                isFocusPainted = false
                addActionListener {
                    val level = trimLevelCombo.item ?: return@addActionListener
                    executeTestAction { s, p -> TestingCommands.triggerTrimMemory(ctx.adbController, s, p, level) }
                }
            }, GridBagConstraints().apply {
                gridx = 2; gridy = 0; anchor = GridBagConstraints.WEST
            })
            add(JPanel(), GridBagConstraints().apply {
                gridx = 3; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            })
        }, fillX(row++))

        // Glue to push everything to the top
        content.add(JPanel(), GridBagConstraints().apply {
            gridx = 0; gridy = row; weighty = 1.0; fill = GridBagConstraints.VERTICAL
        })

        add(JBScrollPane(content).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
    }

    // ── Row builders ──

    private fun addToggle(
        label: String,
        onRead: () -> Boolean,
        onWrite: (Boolean) -> Unit,
    ): SettingToggleRow {
        val toggleRow = SettingToggleRow(label, onRead, onWrite).apply {
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
        toggleRows.add(toggleRow)
        return toggleRow
    }

    private fun createScaleSliderRow(
        label: String,
        stops: Int,
        defaultIndex: Int,
        onChange: (Int) -> Unit,
    ): JPanel {
        return JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(4, 0)

            add(JBLabel(label), GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
                insets = JBUI.insetsRight(12)
            })

            val slider = JSlider(0, stops - 1, defaultIndex).apply {
                majorTickSpacing = 1
                paintTicks = true
                paintLabels = false
                snapToTicks = true
                preferredSize = Dimension(JBUI.scale(160), preferredSize.height)
                addChangeListener {
                    if (!valueIsAdjusting) {
                        onChange(value)
                    }
                }
            }

            add(slider, GridBagConstraints().apply {
                gridx = 1; gridy = 0; anchor = GridBagConstraints.WEST
            })

            // Push rest of space to right
            add(JPanel(), GridBagConstraints().apply {
                gridx = 2; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            })
        }
    }

    private fun createButtonRow(vararg buttons: JButton): JPanel {
        return JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(2, 0)
            for ((i, btn) in buttons.withIndex()) {
                add(btn, GridBagConstraints().apply {
                    gridx = i; gridy = 0; anchor = GridBagConstraints.WEST
                    insets = JBUI.insetsRight(8)
                })
            }
            add(JPanel(), GridBagConstraints().apply {
                gridx = buttons.size; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            })
        }
    }

    private fun executeTestAction(action: (String, String) -> AdbResult) {
        val serial = ctx.getSelectedDeviceSerial() ?: return
        val pkg = ctx.getSelectedPackage() ?: return
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

    fun refreshAll() {
        ctx.getSelectedDeviceSerial() ?: return
        toggleRows.forEach { it.refresh() }
    }
}
