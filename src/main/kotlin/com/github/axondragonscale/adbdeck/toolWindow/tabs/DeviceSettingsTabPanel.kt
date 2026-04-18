package com.github.axondragonscale.adbdeck.toolwindow.tabs

import com.github.axondragonscale.adbdeck.AdbDeckBundle
import com.github.axondragonscale.adbdeck.adb.DeviceSettingsCommands
import com.github.axondragonscale.adbdeck.adb.TestingCommands
import com.github.axondragonscale.adbdeck.model.AdbResult
import com.github.axondragonscale.adbdeck.toolwindow.ActionContext
import com.github.axondragonscale.adbdeck.toolwindow.components.SettingToggleRow
import com.github.axondragonscale.adbdeck.toolwindow.components.horizontalSpacerConstraints
import com.github.axondragonscale.adbdeck.toolwindow.components.iconButton
import com.github.axondragonscale.adbdeck.util.runAdbActionWithPackage
import com.intellij.icons.AllIcons
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
 *
 * Uses a two-column GridBagLayout for toggle rows:
 *   col 0 (weightx=1): label   col 1: OnOffButton
 * All other rows (separators, sliders, buttons) span both columns via gridwidth=2.
 * This guarantees every OnOffButton is aligned to the same column.
 */
class DeviceSettingsTabPanel(private val ctx: ActionContext) : JPanel(BorderLayout()) {

    private val toggleRows = mutableListOf<SettingToggleRow>()

    // Constraint helpers for the three-column content grid:
    //   col 0: label (natural width — GBL makes all col-0 cells = widest label)
    //   col 1: OnOffButton (natural width, immediately after label)
    //   col 2: spacer (weightx=1, absorbs remaining space)
    // Section/other rows span all 3 columns.
    private fun spanConstraints(row: Int) = GridBagConstraints().apply {
        gridx = 0; gridy = row; gridwidth = 3
        weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
        anchor = GridBagConstraints.NORTHWEST
    }
    private fun labelConstraints(row: Int) = GridBagConstraints().apply {
        gridx = 0; gridy = row
        anchor = GridBagConstraints.WEST
        insets = JBUI.insets(3, 0, 3, 8)
    }
    private fun toggleConstraints(row: Int) = GridBagConstraints().apply {
        gridx = 1; gridy = row
        anchor = GridBagConstraints.WEST
        insets = JBUI.insets(3, 12, 3, 0)
    }
    private fun spacerConstraints(row: Int) = GridBagConstraints().apply {
        gridx = 2; gridy = row
        weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
    }

    init {
        border = JBUI.Borders.empty(0, 4, 4, 4)

        val content = JPanel(GridBagLayout())
        var row = 0

        fun serial() = ctx.getSelectedDeviceSerial() ?: ""

        // ── Header: title + refresh ──
        content.add(JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 0, 4, 0)
            add(JBLabel("Device Settings").apply {
                font = font.deriveFont(java.awt.Font.BOLD, font.size + 1f)
            }, BorderLayout.WEST)
            add(iconButton(AllIcons.Actions.Refresh, "Refresh all settings from device") { refreshAll() }, BorderLayout.EAST)
        }, spanConstraints(row++))

        // ── Developer Options ──
        content.add(TitledSeparator("Developer Options").apply {
            border = JBUI.Borders.emptyTop(8)
        }, spanConstraints(row++))

        fun addToggle(label: String, onRead: () -> Boolean, onWrite: (Boolean) -> Unit) {
            val t = SettingToggleRow(label, onRead, onWrite)
            toggleRows.add(t)
            content.add(t.label, labelConstraints(row))
            content.add(t.toggle, toggleConstraints(row))
            content.add(JPanel().apply { isOpaque = false }, spacerConstraints(row))
            row++
        }

        addToggle(
            AdbDeckBundle.message("settings.animations"),
            { DeviceSettingsCommands.getAnimationsEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setAnimations(ctx.adbController, serial(), it) },
        )
        addToggle(
            AdbDeckBundle.message("settings.layoutBounds"),
            { DeviceSettingsCommands.getLayoutBoundsEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setLayoutBounds(ctx.adbController, serial(), it) },
        )
        addToggle(
            AdbDeckBundle.message("settings.showOverdraw"),
            { DeviceSettingsCommands.getOverdrawEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setOverdraw(ctx.adbController, serial(), it) },
        )
        addToggle(
            AdbDeckBundle.message("settings.dontKeepActivities"),
            { DeviceSettingsCommands.getDontKeepActivitiesEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setDontKeepActivities(ctx.adbController, serial(), it) },
        )
        addToggle(
            AdbDeckBundle.message("settings.stayAwake"),
            { DeviceSettingsCommands.getStayAwakeEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setStayAwake(ctx.adbController, serial(), it) },
        )
        addToggle(
            AdbDeckBundle.message("settings.showRefreshRate"),
            { DeviceSettingsCommands.getRefreshRateEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setRefreshRate(ctx.adbController, serial(), it) },
        )
        addToggle(
            AdbDeckBundle.message("settings.showSurfaceUpdates"),
            { DeviceSettingsCommands.getSurfaceUpdatesEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setSurfaceUpdates(ctx.adbController, serial(), it) },
        )
        addToggle(
            AdbDeckBundle.message("settings.profileHwui"),
            { DeviceSettingsCommands.getHwuiProfilingEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setHwuiProfiling(ctx.adbController, serial(), it) },
        )

        // ── Display & Appearance ──
        content.add(TitledSeparator("Display & Appearance").apply {
            border = JBUI.Borders.emptyTop(16)
        }, spanConstraints(row++))

        addToggle(
            AdbDeckBundle.message("settings.darkMode"),
            { DeviceSettingsCommands.getDarkModeEnabled(ctx.adbController, serial()) },
            { DeviceSettingsCommands.setDarkMode(ctx.adbController, serial(), it) },
        )

        fun addSlider(label: String, stops: Int, defaultIndex: Int, onChange: (Int) -> Unit) {
            content.add(JBLabel(label), labelConstraints(row))
            val slider = JSlider(0, stops - 1, defaultIndex).apply {
                majorTickSpacing = 1; paintTicks = true; paintLabels = false; snapToTicks = true
                preferredSize = Dimension(JBUI.scale(220), preferredSize.height)
                addChangeListener { if (!valueIsAdjusting) onChange(value) }
            }
            content.add(slider, toggleConstraints(row))
            content.add(JPanel().apply { isOpaque = false }, spacerConstraints(row))
            row++
        }

        // Font Size
        addSlider(
            label = "Font Size:",
            stops = 4,
            defaultIndex = 1,
        ) { index ->
            val scale = listOf(0.85f, 1.0f, 1.15f, 1.3f)[index]
            ApplicationManager.getApplication().executeOnPooledThread {
                DeviceSettingsCommands.setFontScale(ctx.adbController, serial(), scale)
            }
        }

        // Display Size
        addSlider(
            label = "Display Size:",
            stops = 5,
            defaultIndex = 2,
        ) { index ->
            ApplicationManager.getApplication().executeOnPooledThread {
                if (index == 2) {
                    DeviceSettingsCommands.resetDisplayScale(ctx.adbController, serial())
                } else {
                    val stepFraction = listOf(-0.20f, -0.10f, 0f, 0.10f, 0.20f)[index]
                    DeviceSettingsCommands.setDisplayScale(ctx.adbController, serial(), 1.0f + stepFraction)
                }
            }
        }

        // ── Configuration Changes ──
        content.add(TitledSeparator("Configuration Changes").apply {
            border = JBUI.Borders.emptyTop(16)
        }, spanConstraints(row++))

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
        ), spanConstraints(row++))

        // ── Process & Memory ──
        content.add(TitledSeparator("Process & Memory").apply {
            border = JBUI.Borders.emptyTop(16)
        }, spanConstraints(row++))

        content.add(createButtonRow(
            JButton("Simulate Process Death").apply {
                isFocusPainted = false
                toolTipText = "Kill app process (app must be in background)"
                addActionListener {
                    executeTestAction { s, p -> TestingCommands.simulateProcessDeath(ctx.adbController, s, p) }
                }
            },
        ), spanConstraints(row++))

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
        }, spanConstraints(row++))

        // Glue to push everything to the top
        content.add(JPanel(), GridBagConstraints().apply {
            gridx = 0; gridy = row; gridwidth = 3; weighty = 1.0; fill = GridBagConstraints.VERTICAL
        })

        add(JBScrollPane(content).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
    }

    // ── Row builders ──


    private fun createButtonRow(vararg buttons: JButton): JPanel {
        return JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(2, 0)
            for ((i, btn) in buttons.withIndex()) {
                add(btn, GridBagConstraints().apply {
                    gridx = i; gridy = 0; anchor = GridBagConstraints.WEST; insets = JBUI.insetsRight(8)
                })
            }
            add(JPanel(), horizontalSpacerConstraints(buttons.size))
        }
    }

    private fun executeTestAction(action: (String, String) -> AdbResult) {
        ctx.runAdbActionWithPackage(action)
    }

    fun refreshAll() {
        ctx.getSelectedDeviceSerial() ?: return
        toggleRows.forEach { it.refresh() }
    }
}
