package com.example.opentv.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Stores playlists (raw .m3u text cached on disk, metadata + user state in SharedPreferences).
 * The app ships with no channels; everything comes from playlists the user adds.
 */
class PlaylistRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("open_live_tv", Context.MODE_PRIVATE)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun cacheFile(id: String) = File(context.filesDir, "playlist_$id.m3u")

    // ---------- playlists ----------

    fun playlists(): List<PlaylistEntry> {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PlaylistEntry(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    url = o.optString("url").takeIf { it.isNotEmpty() },
                    channelCount = o.optInt("count"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun savePlaylists(list: List<PlaylistEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("url", it.url ?: "")
                    .put("count", it.channelCount)
            )
        }
        prefs.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }

    suspend fun addFromUrl(url: String): Result<PlaylistEntry> = withContext(Dispatchers.IO) {
        runCatching { register(nameFromUrl(url), url, download(url)) }
    }

    suspend fun addFromUri(uri: Uri): Result<PlaylistEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IOException("Couldn't open that file")
            register(displayName(uri), null, text)
        }
    }

    suspend fun addDemo(): Result<PlaylistEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.assets.open("demo.m3u").bufferedReader().use { it.readText() }
            val existing = playlists().firstOrNull { it.name == DEMO_NAME && it.url == null }
            register(DEMO_NAME, null, text, reuseId = existing?.id)
        }
    }

    suspend fun refresh(id: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val entry = playlists().first { it.id == id }
            val url = entry.url ?: throw IOException("Local playlists can't be refreshed")
            val text = download(url)
            val n = M3uParser.parse(text).size
            if (n == 0) throw IOException("Refreshed playlist is empty, keeping the old copy")
            cacheFile(id).writeText(text)
            savePlaylists(playlists().map { if (it.id == id) it.copy(channelCount = n) else it })
            n
        }
    }

    fun remove(id: String) {
        cacheFile(id).delete()
        savePlaylists(playlists().filter { it.id != id })
    }

    /** Parses every cached playlist, de-duplicates, and numbers channels 1..N. */
    fun loadChannels(): List<Channel> {
        val seen = HashSet<String>()
        val all = ArrayList<Channel>()
        for (p in playlists()) {
            val f = cacheFile(p.id)
            if (!f.exists()) continue
            for (c in M3uParser.parse(f.readText(), p.id)) if (seen.add(c.id)) all += c
        }
        return all.mapIndexed { i, c -> c.copy(number = i + 1) }
    }

    private fun register(name: String, url: String?, text: String, reuseId: String? = null): PlaylistEntry {
        val count = M3uParser.parse(text).size
        if (count == 0) throw IOException("No http(s) streams found in that playlist")

        val existing = if (url != null) playlists().firstOrNull { it.url == url } else null
        val id = reuseId ?: existing?.id ?: UUID.randomUUID().toString().take(8)
        cacheFile(id).writeText(text)

        val entry = PlaylistEntry(id, existing?.name ?: name, url, count)
        val current = playlists()
        savePlaylists(if (current.any { it.id == id }) current.map { if (it.id == id) entry else it } else current + entry)
        return entry
    }

    private fun download(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "OpenLiveTV/1.0").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Server returned HTTP ${resp.code}")
            return resp.body?.string() ?: throw IOException("Empty response")
        }
    }

    private fun nameFromUrl(url: String): String {
        val u = Uri.parse(url)
        return (u.host ?: "Playlist") + (u.lastPathSegment?.let { " / $it" } ?: "")
    }

    private fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: "Local playlist"

    // ---------- user state ----------

    var favorites: Set<String>
        get() = prefs.getStringSet(KEY_FAV, emptySet())?.toSet() ?: emptySet()
        set(v) = prefs.edit().putStringSet(KEY_FAV, v).apply()

    var recents: List<String>
        get() = prefs.getString(KEY_RECENT, "")?.split(',')?.filter { it.isNotEmpty() } ?: emptyList()
        set(v) = prefs.edit().putString(KEY_RECENT, v.joinToString(",")).apply()

    var lastChannelId: String?
        get() = prefs.getString(KEY_LAST, null)
        set(v) = prefs.edit().putString(KEY_LAST, v).apply()

    var autoplayLast: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTOPLAY, v).apply()

    private companion object {
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_FAV = "favorites"
        const val KEY_RECENT = "recents"
        const val KEY_LAST = "last_channel"
        const val KEY_AUTOPLAY = "autoplay_last"
        const val DEMO_NAME = "Demo streams"
    }
}
