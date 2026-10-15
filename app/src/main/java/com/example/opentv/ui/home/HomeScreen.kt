@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.example.opentv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.opentv.data.Channel
import com.example.opentv.ui.AppViewModel
import com.example.opentv.ui.Cat
import com.example.opentv.ui.components.ChannelCard
import com.example.opentv.ui.components.TvTextField
import kotlinx.coroutines.delay

private data class CatItem(val key: String, val label: String, val count: Int)

@Composable
fun HomeScreen(vm: AppViewModel) {
    val ui = vm.ui
    val focusManager = LocalFocusManager.current
    var searching by remember { mutableStateOf(vm.searchQuery.isNotEmpty()) }

    BackHandler(enabled = searching) {
        searching = false
        vm.searchQuery = ""
    }

    val shown = remember(ui.channels, ui.favorites, ui.recents, vm.category, vm.searchQuery) { vm.filtered() }

    val categories = remember(ui.channels, ui.favorites, ui.recents) {
        val counts = ui.channels.groupingBy { it.group }.eachCount()
        buildList {
            add(CatItem(Cat.ALL, "All channels", ui.channels.size))
            add(CatItem(Cat.FAV, "Favourites", ui.channels.count { it.id in ui.favorites }))
            add(CatItem(Cat.RECENT, "Recently watched", ui.recents.size))
            counts.keys.sortedBy { it.lowercase() }.forEach { add(CatItem(it, it, counts.getValue(it))) }
        }
    }

    val gridTitle = when {
        vm.searchQuery.isNotBlank() -> "Search results for \"${vm.searchQuery.trim()}\""
        else -> categories.firstOrNull { it.key == vm.category }?.label ?: "All channels"
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        // ---- top bar ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Open Live TV", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${ui.channels.size} channels",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (searching) {
                TvTextField(
                    value = vm.searchQuery,
                    onValueChange = { vm.searchQuery = it },
                    placeholder = "Search channels or groups",
                    modifier = Modifier.width(440.dp),
                    autoEdit = true,
                    onSubmit = { focusManager.moveFocus(FocusDirection.Down) },
                )
            } else {
                Button(onClick = { searching = true }) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Search")
                }
            }
            Spacer(Modifier.width(16.dp))
            Button(onClick = vm::openSettings) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Playlists")
            }
        }
        Spacer(Modifier.size(20.dp))

        // ---- body ----
        when {
            ui.loading -> CenterMessage("Loading channels…")
            ui.channels.isEmpty() -> EmptyState(onAdd = vm::openSettings)
            else -> Row(Modifier.fillMaxSize()) {
                CategoryRail(
                    items = categories,
                    selectedKey = vm.category,
                    onSelect = { vm.category = it },
                    modifier = Modifier.width(260.dp).fillMaxHeight(),
                )
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text(
                        gridTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                    )
                    if (shown.isEmpty()) {
                        CenterMessage(
                            when {
                                vm.searchQuery.isNotBlank() -> "No channels match your search"
                                vm.category == Cat.FAV -> "No favourites yet. Hold OK on a channel to add one."
                                vm.category == Cat.RECENT -> "Nothing watched yet"
                                else -> "No channels here"
                            }
                        )
                    } else {
                        ChannelGrid(
                            shown = shown,
                            favorites = ui.favorites,
                            lastPlayedId = vm.lastPlayedId,
                            resetKey = vm.category + "|" + vm.searchQuery,
                            onPlay = { vm.play(it, shown) },
                            onFavorite = { vm.toggleFavorite(it.id) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        Text(
                            "Hold OK on a channel to favourite it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRail(
    items: List<CatItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
    ) {
        items(items, key = { it.key }) { item ->
            CategoryChip(
                label = item.label,
                count = item.count,
                selected = item.key == selectedKey,
                onFocus = { onSelect(item.key) },
            )
        }
    }
}

@Composable
private fun CategoryChip(label: String, count: Int, selected: Boolean, onFocus: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onFocus,
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) colors.primary.copy(alpha = 0.16f) else Color.Transparent,
            contentColor = if (selected) colors.primary else colors.onSurfaceVariant,
            focusedContainerColor = colors.surfaceVariant,
            focusedContentColor = colors.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, colors.primary), shape = shape),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            Text("$count", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ChannelGrid(
    shown: List<Channel>,
    favorites: Set<String>,
    lastPlayedId: String?,
    resetKey: String,
    onPlay: (Channel) -> Unit,
    onFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Start near the channel that was playing last, and give it focus when we come back from the player.
    val startIndex = remember { shown.indexOfFirst { it.id == lastPlayedId }.coerceAtLeast(0) }
    val state = rememberLazyGridState(initialFirstVisibleItemIndex = startIndex)
    val entryFocus = remember { FocusRequester() }
    val entryId = shown.firstOrNull { it.id == lastPlayedId }?.id ?: shown.firstOrNull()?.id

    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(resetKey) {
        if (firstRun) firstRun = false else state.scrollToItem(0)
    }
    LaunchedEffect(Unit) {
        repeat(3) {
            try {
                entryFocus.requestFocus()
                return@LaunchedEffect
            } catch (_: Exception) {
                delay(100)
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 170.dp),
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(shown, key = { it.id }) { ch ->
            ChannelCard(
                channel = ch,
                isFavorite = ch.id in favorites,
                onClick = { onPlay(ch) },
                onLongClick = { onFavorite(ch) },
                modifier = if (ch.id == entryId) Modifier.focusRequester(entryFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No channels yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.size(8.dp))
        Text(
            "Add an M3U / M3U8 playlist URL or file to get started.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onAdd, modifier = Modifier.focusRequester(focus)) { Text("Add a playlist") }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
