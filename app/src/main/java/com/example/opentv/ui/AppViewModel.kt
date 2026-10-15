package com.example.opentv.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.opentv.data.Channel
import com.example.opentv.data.PlaylistEntry
import com.example.opentv.data.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, SETTINGS, PLAYER }

/** Special category keys. Any other key is a playlist group name. */
object Cat {
    const val ALL = "__all"
    const val FAV = "__fav"
    const val RECENT = "__recent"
}

data class UiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val playlists: List<PlaylistEntry> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val recents: List<String> = emptyList(),
    val autoplayLast: Boolean = false,
    val message: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PlaylistRepository(app)

    var ui by mutableStateOf(UiState())
        private set
    var screen by mutableStateOf(Screen.HOME)
        private set

    // Home screen state lives here so it survives navigating to the player and back.
    var category by mutableStateOf(Cat.ALL)
    var searchQuery by mutableStateOf("")

    // Playback state
    var current by mutableStateOf<Channel?>(null)
        private set
    /** The list the user is zapping through (the category they started from). */
    var queue by mutableStateOf<List<Channel>>(emptyList())
        private set
    var lastPlayedId by mutableStateOf<String?>(null)
        private set

    private var autoplayHandled = false

    init {
        lastPlayedId = repo.lastChannelId
        viewModelScope.launch {
            reload()
            refreshAllQuietly()
        }
    }

    // ---------- loading ----------

    private suspend fun reload() {
        val channels = withContext(Dispatchers.IO) { repo.loadChannels() }
        ui = ui.copy(
            loading = false,
            channels = channels,
            playlists = repo.playlists(),
            favorites = repo.favorites,
            recents = repo.recents,
            autoplayLast = repo.autoplayLast,
        )
        if (!autoplayHandled) {
            autoplayHandled = true
            if (ui.autoplayLast) {
                channels.firstOrNull { it.id == repo.lastChannelId }?.let { play(it, channels) }
            }
        }
    }

    /** Re-downloads remote playlists in the background so channel lists stay current. */
    private suspend fun refreshAllQuietly() {
        var changed = false
        for (p in repo.playlists()) {
            if (p.url != null && repo.refresh(p.id).isSuccess) changed = true
        }
        if (changed) reload()
    }

    // ---------- home helpers ----------

    fun filtered(): List<Channel> {
        val q = searchQuery.trim()
        if (q.isNotEmpty()) {
            return ui.channels.filter { it.name.contains(q, ignoreCase = true) || it.group.contains(q, ignoreCase = true) }
        }
        return when (category) {
            Cat.ALL -> ui.channels
            Cat.FAV -> ui.channels.filter { it.id in ui.favorites }
            Cat.RECENT -> {
                val byId = ui.channels.associateBy { it.id }
                ui.recents.mapNotNull { byId[it] }
            }
            else -> ui.channels.filter { it.group == category }
        }
    }

    fun isFavorite(id: String) = id in ui.favorites

    fun toggleFavorite(id: String) {
        val next = if (id in ui.favorites) ui.favorites - id else ui.favorites + id
        repo.favorites = next
        ui = ui.copy(favorites = next)
    }

    // ---------- navigation ----------

    fun openSettings() { screen = Screen.SETTINGS }
    fun goHome() { screen = Screen.HOME }

    // ---------- playback ----------

    fun play(channel: Channel, within: List<Channel>) {
        queue = if (within.any { it.id == channel.id }) within else listOf(channel)
        current = channel
        touch(channel)
        screen = Screen.PLAYER
    }

    fun zapTo(channel: Channel) {
        if (queue.none { it.id == channel.id }) queue = ui.channels
        current = channel
        touch(channel)
    }

    /** +1 = next channel in the queue, -1 = previous. Wraps around. */
    fun zap(delta: Int) {
        val q = queue.ifEmpty { ui.channels }
        if (q.isEmpty()) return
        val idx = q.indexOfFirst { it.id == current?.id }
        zapTo(q[Math.floorMod(idx + delta, q.size)])
    }

    /** Jump by channel number (digits typed on the remote). Returns false if no such channel. */
    fun zapToNumber(n: Int): Boolean {
        val ch = ui.channels.firstOrNull { it.number == n } ?: return false
        zapTo(ch)
        return true
    }

    private fun touch(ch: Channel) {
        lastPlayedId = ch.id
        repo.lastChannelId = ch.id
        val recents = (listOf(ch.id) + ui.recents.filter { it != ch.id }).take(20)
        repo.recents = recents
        ui = ui.copy(recents = recents)
    }

    // ---------- playlists ----------

    fun addPlaylistUrl(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) { postMessage("Enter a playlist URL first"); return }
        val url = if ("://" in trimmed) trimmed else "https://$trimmed"
        launchBusy {
            repo.addFromUrl(url).fold(
                onSuccess = { postMessage("Added \"${it.name}\" (${it.channelCount} channels)") },
                onFailure = { postMessage("Couldn't add playlist: ${it.message ?: it.javaClass.simpleName}") },
            )
            reload()
        }
    }

    fun addPlaylistFile(uri: Uri) = launchBusy {
        repo.addFromUri(uri).fold(
            onSuccess = { postMessage("Added \"${it.name}\" (${it.channelCount} channels)") },
            onFailure = { postMessage("Couldn't read file: ${it.message ?: it.javaClass.simpleName}") },
        )
        reload()
    }

    fun addDemoPlaylist() = launchBusy {
        repo.addDemo().fold(
            onSuccess = { postMessage("Demo streams added") },
            onFailure = { postMessage("Couldn't add demo: ${it.message}") },
        )
        reload()
    }

    fun refreshPlaylist(id: String) = launchBusy {
        repo.refresh(id).fold(
            onSuccess = { postMessage("Refreshed ($it channels)") },
            onFailure = { postMessage("Refresh failed: ${it.message ?: it.javaClass.simpleName}") },
        )
        reload()
    }

    fun removePlaylist(id: String) = launchBusy {
        withContext(Dispatchers.IO) { repo.remove(id) }
        category = Cat.ALL
        reload()
        postMessage("Playlist removed")
    }

    fun setAutoplayLast(enabled: Boolean) {
        repo.autoplayLast = enabled
        ui = ui.copy(autoplayLast = enabled)
    }

    // ---------- messages ----------

    fun postMessage(text: String) { ui = ui.copy(message = text) }
    fun clearMessage() { ui = ui.copy(message = null) }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            ui = ui.copy(busy = true)
            try {
                block()
            } finally {
                ui = ui.copy(busy = false)
            }
        }
    }
}
