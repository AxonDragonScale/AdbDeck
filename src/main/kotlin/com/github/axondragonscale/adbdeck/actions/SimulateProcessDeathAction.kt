package com.github.axondragonscale.adbdeck.actions

import com.github.axondragonscale.adbdeck.adb.AdbController
import com.github.axondragonscale.adbdeck.adb.TestingCommands
import com.github.axondragonscale.adbdeck.model.AdbResult

class SimulateProcessDeathAction : BaseAdbAction() {
    override fun execute(adb: AdbController, serial: String, pkg: String): AdbResult =
        TestingCommands.simulateProcessDeath(adb, serial, pkg)
}

