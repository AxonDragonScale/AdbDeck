package com.github.axondragonscale.adbdeck.state

import com.github.axondragonscale.adbdeck.model.DeepLinkBookmark
import com.github.axondragonscale.adbdeck.model.IntentBookmark
import com.github.axondragonscale.adbdeck.model.SavedCommand
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Persists AdbDeck state per project.
 * Stored in `.idea/adbdeck.xml`.
 */
@State(
    name = "AdbDeckState",
    storages = [Storage("adbdeck.xml")],
)
@Service(Service.Level.PROJECT)
class AdbDeckStateService : PersistentStateComponent<AdbDeckStateService.AdbDeckState> {

    data class AdbDeckState(

        /** Last used package name. */
        var lastPackageName: String = "",

        /** Last used package name per device serial. */
        var lastPackagePerDevice: MutableMap<String, String> = mutableMapOf(),

        /** Whether to auto-detect the package from the project. */
        var autoDetectPackage: Boolean = true,

        /** Recently used deep link URIs. */
        var deepLinkHistory: MutableList<String> = mutableListOf(),

        /** Bookmarked deep links. */
        var deepLinkBookmarks: MutableList<DeepLinkBookmark> = mutableListOf(),

        /** Bookmarked custom intents. */
        var intentBookmarks: MutableList<IntentBookmark> = mutableListOf(),

        /** Saved ADB commands. */
        var savedCommands: MutableList<SavedCommand> = mutableListOf(),

        /** Whether to show confirmation dialogs for destructive actions. */
        var confirmDestructiveActions: Boolean = true,

        /** Number of commands to keep in console history. */
        var consoleHistorySize: Int = 50,

        /** Custom ADB path override (empty = auto-detect). */
        var adbPathOverride: String = "",
    )

    private var myState = AdbDeckState()

    override fun getState(): AdbDeckState = myState

    override fun loadState(state: AdbDeckState) {
        myState = state
    }


    // ── Package ──

    var lastPackageName: String
        get() = myState.lastPackageName
        set(value) { myState.lastPackageName = value }

    fun getLastPackageForDevice(serial: String): String =
        myState.lastPackagePerDevice[serial] ?: myState.lastPackageName

    fun setLastPackageForDevice(serial: String, pkg: String) {
        myState.lastPackagePerDevice[serial] = pkg
        myState.lastPackageName = pkg
    }

    var autoDetectPackage: Boolean
        get() = myState.autoDetectPackage
        set(value) { myState.autoDetectPackage = value }

    // ── Settings ──

    var confirmDestructiveActions: Boolean
        get() = myState.confirmDestructiveActions
        set(value) { myState.confirmDestructiveActions = value }

    var consoleHistorySize: Int
        get() = myState.consoleHistorySize
        set(value) { myState.consoleHistorySize = value }

    var adbPathOverride: String
        get() = myState.adbPathOverride
        set(value) { myState.adbPathOverride = value }

    // ── Deep Links ──

    fun addDeepLink(uri: String) {
        myState.deepLinkHistory.remove(uri)
        myState.deepLinkHistory.add(0, uri)
        // Keep only the last 20 entries
        if (myState.deepLinkHistory.size > 20) {
            myState.deepLinkHistory = myState.deepLinkHistory.take(20).toMutableList()
        }
    }

    fun getDeepLinkHistory(): List<String> = myState.deepLinkHistory.toList()

    // ── Bookmarks ──

    fun addBookmark(label: String, uri: String) {
        myState.deepLinkBookmarks.add(DeepLinkBookmark(label, uri))
    }

    fun removeBookmark(uri: String) {
        myState.deepLinkBookmarks.removeAll { it.uri == uri }
    }

    fun updateBookmark(oldUri: String, label: String, newUri: String) {
        val idx = myState.deepLinkBookmarks.indexOfFirst { it.uri == oldUri }
        if (idx >= 0) {
            myState.deepLinkBookmarks[idx] = DeepLinkBookmark(label, newUri)
        }
    }

    fun getBookmarks(): List<DeepLinkBookmark> = myState.deepLinkBookmarks.toList()

    // ── Saved Commands ──

    fun addSavedCommand(name: String, command: String) {
        myState.savedCommands.add(SavedCommand(name, command))
    }

    fun removeSavedCommand(name: String) {
        myState.savedCommands.removeAll { it.name == name }
    }

    fun getSavedCommands(): List<SavedCommand> = myState.savedCommands.toList()

    // ── Intent Bookmarks ──

    fun addIntentBookmark(bookmark: IntentBookmark) {
        myState.intentBookmarks.add(bookmark)
    }

    fun removeIntentBookmark(label: String) {
        myState.intentBookmarks.removeAll { it.label == label }
    }

    fun getIntentBookmarks(): List<IntentBookmark> = myState.intentBookmarks.toList()
}
