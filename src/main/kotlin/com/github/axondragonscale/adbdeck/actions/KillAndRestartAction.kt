package com.github.axondragonscale.adbdeck.actions

import com.github.axondragonscale.adbdeck.adb.AdbController
import com.github.axondragonscale.adbdeck.adb.AppCommands
import com.github.axondragonscale.adbdeck.model.AdbResult

class KillAndRestartAction : BaseAdbAction() {
    override fun execute(adb: AdbController, serial: String, pkg: String): AdbResult =
        AppCommands.killAndRestart(adb, serial, pkg)
}

