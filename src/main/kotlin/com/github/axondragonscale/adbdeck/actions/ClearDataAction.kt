package com.github.axondragonscale.adbdeck.actions

import com.github.axondragonscale.adbdeck.AdbDeckBundle
import com.github.axondragonscale.adbdeck.adb.AdbController
import com.github.axondragonscale.adbdeck.adb.AppCommands
import com.github.axondragonscale.adbdeck.model.AdbResult
import com.github.axondragonscale.adbdeck.state.AdbDeckStateService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages

class ClearDataAction : BaseAdbAction() {
    override fun execute(adb: AdbController, serial: String, pkg: String): AdbResult =
        AppCommands.clearData(adb, serial, pkg)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val state = project.service<AdbDeckStateService>()
        val pkg = state.lastPackageName
        if (state.confirmDestructiveActions && pkg.isNotBlank()) {
            val ok = Messages.showYesNoDialog(
                project,
                AdbDeckBundle.message("confirm.clearData.message", pkg),
                AdbDeckBundle.message("confirm.clearData.title"),
                Messages.getWarningIcon()
            )
            if (ok != Messages.YES) return
        }
        super.actionPerformed(e)
    }
}
