@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.example.opentv.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.opentv.data.PlaylistEntry
import com.example.opentv.ui.AppViewModel
import com.example.opentv.ui.components.TvTextField

@Composable
fun SettingsScreen(vm: AppViewModel) {
    BackHandler { vm.goHome() }

    val ui = vm.ui
    var urlInput by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addPlaylistFile(uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Playlists", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (ui.busy) "Working…" else "Back returns to channels",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (ui.playlists.isEmpty()) {
            item {
                Text(
                    "No playlists yet. Add one below. The app doesn't include any channels of its own.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(ui.playlists, key = { it.id }) { p ->
                PlaylistRow(
                    entry = p,
                    onRefresh = { vm.refreshPlaylist(p.id) },
                    onRemove = { vm.removePlaylist(p.id) },
                )
            }
        }

        item { SectionTitle("Add a playlist") }
        item {
            TvTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = "https://example.com/playlist.m3u8",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    vm.addPlaylistUrl(urlInput)
                    urlInput = ""
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add from URL")
                }
                Button(onClick = {
                    try {
                        picker.launch(arrayOf("*/*"))
                    } catch (e: ActivityNotFoundException) {
                        vm.postMessage("No file picker on this device. Use a URL instead.")
                    }
                }) { Text("Load from file…") }
                Button(onClick = vm::addDemoPlaylist) { Text("Add demo streams") }
            }
        }

        item { SectionTitle("Playback") }
        item {
            ToggleRow(
                title = "Resume last channel on launch",
                subtitle = "Open straight into the channel you were watching",
                checked = ui.autoplayLast,
                onToggle = { vm.setAutoplayLast(!ui.autoplayLast) },
            )
        }

        item { SectionTitle("Remote controls while watching") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "Up / Down  or  CH+ / CH-:  next / previous channel",
                    "Left:  open channel list",
                    "Right:  add or remove favourite",
                    "OK:  show channel info  (or retry after an error)",
                    "0-9:  jump to a channel number, then wait or press OK",
                    "Play/Pause:  pause or resume",
                    "Back:  return to channels",
                ).forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp),
    )
}

@Composable
private fun PlaylistRow(entry: PlaylistEntry, onRefresh: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${entry.channelCount} channels  ·  ${entry.url ?: "local copy"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        if (entry.url != null) {
            Button(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Refresh")
            }
            Spacer(Modifier.width(12.dp))
        }
        Button(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Remove")
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colors.surface,
            focusedContainerColor = colors.surfaceVariant,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Box(
                Modifier
                    .background(if (checked) colors.primary else colors.border, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text(
                    if (checked) "On" else "Off",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (checked) colors.onPrimary else colors.onSurface,
                )
            }
        }
    }
}
