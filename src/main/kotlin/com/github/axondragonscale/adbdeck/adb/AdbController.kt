package com.github.axondragonscale.adbdeck.adb

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.github.axondragonscale.adbdeck.model.AdbResult
import com.github.axondragonscale.adbdeck.model.DeviceInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Core service for executing ADB commands and managing device connections.
 * Uses ddmlib for device listing and falls back to the `adb` binary for shell commands.
 */
@Service(Service.Level.PROJECT)
class AdbController(private val project: Project) {

    private val logger = thisLogger()

    /**
     * Returns the path to the `adb` binary, resolved from the project's Android SDK.
     * Falls back to ANDROID_HOME environment variable.
     */
    fun getAdbPath(): String? {
        // Primary: Use the project-aware API (returns File with adbPath)
        val searchResult = AndroidSdkUtils.findAdb(project)
        val adbFile = searchResult.adbPath
        if (adbFile != null && adbFile.exists()) {
            return adbFile.absolutePath
        }

        // Fallback: Try findValidAndroidSdkPath (not project-specific)
        val sdkDir = AndroidSdkUtils.findValidAndroidSdkPath()
        if (sdkDir != null) {
            val adb = sdkDir.resolve("platform-tools/adb")
            if (adb.exists()) return adb.absolutePath
        }

        // Last resort: ANDROID_HOME / ANDROID_SDK_ROOT env variable
        val envSdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (envSdk != null) {
            val adb = java.io.File(envSdk, "platform-tools/adb")
            if (adb.exists()) return adb.absolutePath
        }

        return null
    }

    /**
     * Ensures the ADB debug bridge is initialized.
     *
     * **Must be called on the EDT.** [AndroidSdkUtils.getDebugBridge] requires EDT access
     * because it may trigger SDK resolution and bridge creation.
     *
     * After this call, [AndroidDebugBridge.getBridge] will return the initialized bridge
     * from any thread.
     */
    fun ensureBridgeInitialized() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        AndroidSdkUtils.getDebugBridge(project)
    }

    /**
     * Lists all connected devices as [DeviceInfo] models.
     *
     * Uses the thread-safe [AndroidDebugBridge.getBridge] static accessor.
     * The bridge must have been previously initialized via [ensureBridgeInitialized] on EDT.
     *
     * **Safe to call from any thread.**
     */
    fun getConnectedDevices(): List<DeviceInfo> {
        val bridge = AndroidDebugBridge.getBridge() ?: return emptyList()
        if (!bridge.isConnected || !bridge.hasInitialDeviceList()) return emptyList()
        return bridge.devices.mapNotNull { device ->
            try {
                device.toDeviceInfo()
            } catch (e: Exception) {
                logger.warn("Failed to read device info for ${device.serialNumber}", e)
                null
            }
        }
    }

    /**
     * Executes an ADB shell command on the given device.
     *
     * @param deviceSerial the target device serial number
     * @param command the shell command to execute (without the `adb shell` prefix)
     * @return [AdbResult] with the command output
     */
    // ── Internal ──

    private fun adbNotFound(command: String) = AdbResult(
        command = command,
        output = "",
        error = "ADB not found. Please configure Android SDK.",
        exitCode = -1,
    )

    fun executeShellCommand(deviceSerial: String, command: String): AdbResult {
        val adb = getAdbPath() ?: return adbNotFound(command)
        val fullCommand = listOf(adb, "-s", deviceSerial, "shell", command)
        return runProcess(fullCommand, displayCommand = "adb -s $deviceSerial shell $command")
    }

    fun executeAdbCommand(deviceSerial: String, vararg args: String): AdbResult {
        val adb = getAdbPath() ?: return adbNotFound(args.joinToString(" "))
        val fullCommand = listOf(adb, "-s", deviceSerial) + args.toList()
        return runProcess(fullCommand, displayCommand = "adb -s $deviceSerial ${args.joinToString(" ")}")
    }

    fun executeGlobalAdbCommand(vararg args: String): AdbResult {
        val adb = getAdbPath() ?: return adbNotFound(args.joinToString(" "))
        val fullCommand = listOf(adb) + args.toList()
        return runProcess(fullCommand, displayCommand = "adb ${args.joinToString(" ")}")
    }

    private fun runProcess(command: List<String>, displayCommand: String): AdbResult {
        return try {
            logger.info("Executing: $displayCommand")
            val process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()

            val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText().trim() }
            val exited = process.waitFor(30, TimeUnit.SECONDS)
            val exitCode = if (exited) process.exitValue() else -1

            if (!exited) process.destroyForcibly()

            AdbResult(
                command = displayCommand,
                output = stdout,
                error = stderr,
                exitCode = exitCode,
            )
        } catch (e: Exception) {
            logger.error("Failed to execute: $displayCommand", e)
            AdbResult(
                command = displayCommand,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1,
            )
        }
    }

    companion object {
        /**
         * Maps a ddmlib [IDevice] to our [DeviceInfo] model.
         */
        fun IDevice.toDeviceInfo(): DeviceInfo {
            val isEmu = isEmulator
            val model = getProperty(IDevice.PROP_DEVICE_MODEL)

            val avdDisplayName = if (isEmu) {
                getProperty(IDevice.PROP_DEVICE_BOOT_QEMU_DISPLAY_NAME)
                    ?: @Suppress("DEPRECATION") avdName
            } else null

            val deviceName = when {
                isEmu && !avdDisplayName.isNullOrBlank() -> avdDisplayName.replace('_', ' ')
                else -> {
                    val manufacturer = getProperty(IDevice.PROP_DEVICE_MANUFACTURER)
                    when {
                        model != null && manufacturer != null -> "$manufacturer $model"
                        model != null -> model
                        else -> serialNumber
                    }
                }
            }

            val api = getProperty(IDevice.PROP_BUILD_API_LEVEL)?.toIntOrNull() ?: 0

            val connectionType = when {
                isEmu -> DeviceInfo.ConnectionType.EMULATOR
                serialNumber.contains(":") -> DeviceInfo.ConnectionType.WIFI
                else -> DeviceInfo.ConnectionType.USB
            }

            val deviceState = when (state) {
                IDevice.DeviceState.ONLINE -> DeviceInfo.DeviceState.ONLINE
                IDevice.DeviceState.UNAUTHORIZED -> DeviceInfo.DeviceState.UNAUTHORIZED
                else -> DeviceInfo.DeviceState.OFFLINE
            }

            return DeviceInfo(
                serial = serialNumber,
                name = deviceName,
                model = model ?: "Unknown",
                apiLevel = api,
                isEmulator = isEmu,
                connectionType = connectionType,
                state = deviceState,
            )
        }
    }
}

