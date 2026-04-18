package com.github.axondragonscale.adbdeck.actions

import com.github.axondragonscale.adbdeck.adb.AdbController
import com.github.axondragonscale.adbdeck.adb.DeviceSettingsCommands
import com.github.axondragonscale.adbdeck.model.AdbResult

class ToggleAnimationsAction : BaseAdbAction() {
    override fun execute(adb: AdbController, serial: String, pkg: String): AdbResult {
        val current = DeviceSettingsCommands.getAnimationsEnabled(adb, serial)
        return DeviceSettingsCommands.setAnimations(adb, serial, !current)
    }
}

