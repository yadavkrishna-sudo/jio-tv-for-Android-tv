@file:OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)

package com.example.opentv.ui.player

import android.content.Context
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.opentv.data.Channel
import com.example.opentv.ui.AppViewModel
import com.example.opentv.ui.components.Initials
import kotlinx.coroutines.delay
import android.view.KeyEvent as AndroidKey

@Composable
fun PlayerScreen(vm: AppViewModel) {
    val channel = vm.current
    if (channel == null) {
        LaunchedEffect(Unit) { vm.goHome() }
        return
    }
    PlayerContent(vm, channel)
}

@Composable
private fun PlayerContent(vm: AppViewModel, channel: Channel) {
    val context = LocalContext.current

    val exo = remember {
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var panelOpen by remember { mutableStateOf(false) }
    var infoVisible by remember { mutableStateOf(true) }
    var infoTick by remember { mutableIntStateOf(0) }
    var retryTick by remember { mutableIntStateOf(0) }
    var digits by remember { mutableStateOf("") }
    var flash by remember { mutableStateOf<String?>(null) }
    val keyFocus = remember { FocusRequester() }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) error = null
            }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exo.seekToDefaultPosition()
                    exo.prepare()
                } else {
                    error = e.errorCodeName
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // (Re)load whenever the channel changes or the user asks to retry.
    LaunchedEffect(channel.id, retryTick) {
        error = null
        buffering = true
        exo.setMediaSource(buildSource(context, channel))
        exo.prepare()
        exo.playWhenReady = true
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { exo.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        exo.seekToDefaultPosition()
        exo.play()
    }

    LaunchedEffect(channel.id, infoTick) {
        infoVisible = true
        delay(4500)
        infoVisible = false
    }

    fun commitDigits() {
        val n = digits.toIntOrNull()
        digits = ""
        if (n != null && !vm.zapToNumber(n)) flash = "No channel $n"
    }

    LaunchedEffect(digits) {
        if (digits.isNotEmpty()) {
            delay(1800)
            commitDigits()
        }
    }
    LaunchedEffect(flash) {
        if (flash != null) {
            delay(2200)
            flash = null
        }
    }
    LaunchedEffect(panelOpen) {
        if (!panelOpen) runCatching { keyFocus.requestFocus() }
    }

    BackHandler {
        if (panelOpen) panelOpen = false else vm.goHome()
    }

    fun onKey(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        val native = e.nativeKeyEvent
        val code = native.keyCode

        val digit = when (code) {
            in AndroidKey.KEYCODE_0..AndroidKey.KEYCODE_9 -> code - AndroidKey.KEYCODE_0
            in AndroidKey.KEYCODE_NUMPAD_0..AndroidKey.KEYCODE_NUMPAD_9 -> code - AndroidKey.KEYCODE_NUMPAD_0
            else -> -1
        }
        if (digit >= 0) {
            if (digits.length < 4) digits += digit.toString()
            return true
        }

        // Swallow auto-repeat so holding a key doesn't spawn a stream of channel changes.
        if (native.repeatCount > 0) return true

        return when (code) {
            AndroidKey.KEYCODE_DPAD_UP, AndroidKey.KEYCODE_CHANNEL_UP, AndroidKey.KEYCODE_PAGE_UP -> {
                vm.zap(+1); true
            }
            AndroidKey.KEYCODE_DPAD_DOWN, AndroidKey.KEYCODE_CHANNEL_DOWN, AndroidKey.KEYCODE_PAGE_DOWN -> {
                vm.zap(-1); true
            }
            AndroidKey.KEYCODE_DPAD_LEFT -> {
                panelOpen = true; true
            }
            AndroidKey.KEYCODE_DPAD_RIGHT -> {
                val nowFavourite = !vm.isFavorite(channel.id)
                vm.toggleFavorite(channel.id)
                flash = if (nowFavourite) "Added to favourites" else "Removed from favourites"
                true
            }
            AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER -> {
                when {
                    digits.isNotEmpty() -> commitDigits()
                    error != null -> retryTick++
                    infoVisible -> infoVisible = false
                    else -> infoTick++
                }
                true
            }
            AndroidKey.KEYCODE_MENU, AndroidKey.KEYCODE_INFO -> {
                infoTick++; true
            }
            AndroidKey.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (exo.isPlaying) exo.pause() else exo.play(); true
            }
            AndroidKey.KEYCODE_MEDIA_PLAY -> { exo.play(); true }
            AndroidKey.KEYCODE_MEDIA_PAUSE -> { exo.pause(); true }
            else -> false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    player = exo
                }
            },
        )

        // Invisible focus target that receives remote key presses while the channel panel is closed.
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(keyFocus)
                .onKeyEvent { onKey(it) }
                .focusable()
        )

        if (buffering && error == null) {
            Pill("Loading…", Modifier.align(Alignment.Center))
        }
        if (error != null) {
            ErrorCard(error ?: "", Modifier.align(Alignment.Center))
        }
        flash?.let { Pill(it, Modifier.align(Alignment.TopEnd).padding(32.dp)) }
        if (digits.isNotEmpty()) {
            Text(
                digits,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(40.dp)
                    .background(Color(0xB3000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        if (infoVisible && !panelOpen) {
            InfoBanner(channel, vm.isFavorite(channel.id), Modifier.align(Alignment.BottomStart))
        }

        if (panelOpen) {
            ChannelPanel(
                channels = vm.queue.ifEmpty { vm.ui.channels },
                currentId = channel.id,
                favorites = vm.ui.favorites,
                onPick = {
                    vm.zapTo(it)
                    panelOpen = false
                },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}

// ---------- player source ----------

private fun buildSource(context: Context, ch: Channel): MediaSource {
    val headers = HashMap<String, String>()
    ch.referrer?.let { headers["Referer"] = it }

    val http = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)
        .setUserAgent(ch.userAgent ?: Util.getUserAgent(context, "OpenLiveTV"))
        .setDefaultRequestProperties(headers)

    val item = MediaItem.Builder()
        .setUri(ch.url)
        .apply { guessMimeType(ch.url)?.let { setMimeType(it) } }
        .build()

    return DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)).createMediaSource(item)
}

/** ExoPlayer infers the format from the URL extension; extension-less live URLs are almost always HLS. */
private fun guessMimeType(url: String): String? {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    val ext = path.substringAfterLast('/', "").substringAfterLast('.', "")
    return when {
        ext == "mpd" -> MimeTypes.APPLICATION_MPD
        ext == "m3u8" || ext == "m3u" -> MimeTypes.APPLICATION_M3U8
        ext in setOf("ts", "mp4", "mkv", "mp3", "aac", "webm", "m4a") -> null
        "m3u8" in path -> MimeTypes.APPLICATION_M3U8
        else -> MimeTypes.APPLICATION_M3U8
    }
}

// ---------- overlays ----------

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .background(Color(0xCC000000), RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun ErrorCard(code: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color(0xE6151B23), RoundedCornerShape(16.dp))
            .padding(horizontal = 40.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("This channel isn't playing", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.size(6.dp))
        Text(code, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(14.dp))
        Text(
            "Press OK to retry, or Up / Down to change channel",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InfoBanner(channel: Channel, isFavorite: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            .padding(horizontal = 56.dp, vertical = 32.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.number.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onPrimary,
                    modifier = Modifier
                        .background(colors.primary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(16.dp))
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    if (channel.logo != null) {
                        AsyncImage(channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    } else {
                        Initials(channel.name)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(channel.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(channel.group, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                if (isFavorite) {
                    Spacer(Modifier.width(16.dp))
                    Icon(Icons.Default.Favorite, contentDescription = "Favourite", tint = colors.secondary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.size(12.dp))
            Text(
                "Up/Down channel     Left channel list     Right favourite     0-9 jump to number",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChannelPanel(
    channels: List<Channel>,
    currentId: String,
    favorites: Set<String>,
    onPick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val startIndex = remember { channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 2).coerceAtLeast(0))
    val currentFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(60)
        runCatching { currentFocus.requestFocus() }
    }

    Box(
        modifier
            .fillMaxHeight()
            .width(400.dp)
            .background(Color(0xF2101820))
            .padding(vertical = 24.dp)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(channels, key = { it.id }) { ch ->
                val isCurrent = ch.id == currentId
                Surface(
                    onClick = { onPick(ch) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isCurrent) Modifier.focusRequester(currentFocus) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isCurrent) colors.primary.copy(alpha = 0.18f) else Color.Transparent,
                        contentColor = colors.onSurface,
                        focusedContainerColor = colors.surfaceVariant,
                        focusedContentColor = colors.onSurface,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            ch.number.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.width(48.dp),
                        )
                        Text(
                            ch.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (ch.id in favorites) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
