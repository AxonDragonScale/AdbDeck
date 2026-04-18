package com.github.axondragonscale.adbdeck.toolwindow

import com.github.axondragonscale.adbdeck.adb.AdbController
import com.intellij.openapi.project.Project

/**
 * Context interface passed to all sections and action executors.
 * Provides access to the selected device, package, console, and project.
 */
interface ActionContext {
    val project: Project
    val adbController: AdbController
    fun getSelectedDeviceSerial(): String?
    fun getSelectedPackage(): String?
    fun logToConsole(command: String, output: String)
}

