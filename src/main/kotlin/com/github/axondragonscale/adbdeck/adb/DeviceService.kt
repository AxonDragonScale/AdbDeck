package com.github.axondragonscale.adbdeck.adb

import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.github.axondragonscale.adbdeck.adb.AdbController.Companion.toDeviceInfo
import com.github.axondragonscale.adbdeck.model.DeviceInfo
import com.intellij.execution.ExecutionTargetManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

/**
 * Reads the currently selected device from Android Studio's execution target selector
 * (the device dropdown in the toolbar).
 *
 * Uses [ExecutionTargetManager.getActiveTarget] and checks if it's an [AndroidExecutionTarget]
 * to get the running [IDevice]s.
 */
@Service(Service.Level.PROJECT)
class DeviceService(private val project: Project) {

    private val logger = thisLogger()

    /**
     * Returns the serial number of the device currently selected in Android Studio's
     * device dropdown, or null if no Android device is selected.
     *
     * **Must be called on the EDT** (ExecutionTargetManager access).
     */
    fun getSelectedDeviceSerial(): String? {
        return getSelectedDevice()?.serialNumber
    }

    /**
     * Returns the [IDevice] currently selected in Android Studio's device dropdown,
     * or null if no Android device is selected.
     *
     * **Must be called on the EDT.**
     */
    fun getSelectedDevice(): IDevice? {
        val target = ExecutionTargetManager.getActiveTarget(project)
        if (target is AndroidExecutionTarget) {
            val devices = target.runningDevices
            return devices.firstOrNull()
        }
        return null
    }

    /**
     * Returns the [DeviceInfo] for the currently selected device, or null.
     *
     * **Must be called on the EDT.**
     */
    fun getSelectedDeviceInfo(): DeviceInfo? {
        return try {
            getSelectedDevice()?.toDeviceInfo()
        } catch (e: Exception) {
            logger.warn("Failed to get device info from execution target", e)
            null
        }
    }

    /**
     * Returns the display name of the currently selected execution target.
     *
     * **Must be called on the EDT.**
     */
    fun getSelectedTargetDisplayName(): String {
        val target = ExecutionTargetManager.getActiveTarget(project)
        return target.displayName
    }
}
