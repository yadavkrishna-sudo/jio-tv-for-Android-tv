package com.example.opentv.data

import java.util.UUID

/**
 * Parser for M3U / M3U8 playlists.
 *
 * Supports: #EXTINF attributes (tvg-id, tvg-name, tvg-logo, group-title), #EXTGRP,
 * #EXTVLCOPT http-user-agent / http-referrer, Kodi-style "url|User-Agent=...&Referer=..."
 * suffixes, and bare lists of URLs. Only http(s) entries are kept.
 */
object M3uParser {

    private val ATTR = Regex("""([A-Za-z0-9_\-]+)\s*=\s*"([^"]*)"""")

    fun parse(content: String, playlistId: String = ""): List<Channel> {
        val result = ArrayList<Channel>()

        var attrs: Map<String, String> = emptyMap()
        var title: String? = null
        var pendingGroup: String? = null
        var ua: String? = null
        var referrer: String? = null

        fun reset() {
            attrs = emptyMap()
            title = null
            pendingGroup = null
            ua = null
            referrer = null
        }

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) continue

            if (line.startsWith("#")) {
                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        val (head, name) = splitExtinf(line)
                        attrs = ATTR.findAll(head).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        title = name
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) ->
                        pendingGroup = line.substringAfter(':').trim()
                    line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                        val kv = line.substringAfter(':')
                        val key = kv.substringBefore('=').trim().lowercase()
                        val value = kv.substringAfter('=', "").trim()
                        when (key) {
                            "http-user-agent" -> ua = value.ifEmpty { null }
                            "http-referrer", "http-referer" -> referrer = value.ifEmpty { null }
                        }
                    }
                }
                continue
            }

            // A non-comment line is the stream URL for the entry collected above.
            val (url, pipeHeaders) = splitKodiHeaders(line)
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                reset()
                continue
            }

            val name = (title?.takeIf { it.isNotBlank() }
                ?: attrs["tvg-name"]?.takeIf { it.isNotBlank() }
                ?: url.substringAfter("://").substringBefore('/'))
                .trim()
            val group = (attrs["group-title"]?.takeIf { it.isNotBlank() }
                ?: pendingGroup?.takeIf { it.isNotBlank() }
                ?: "Uncategorized")
                .trim()

            result += Channel(
                id = stableId(name, url),
                number = 0,
                name = name,
                url = url,
                logo = attrs["tvg-logo"]?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) },
                group = group,
                userAgent = pipeHeaders["user-agent"] ?: ua,
                referrer = pipeHeaders["referer"] ?: pipeHeaders["referrer"] ?: referrer,
                playlistId = playlistId,
            )
            reset()
        }
        return result
    }

    /** Splits "#EXTINF:-1 a=\"x, y\" b=\"z\",Display Name" at the first comma that is outside quotes. */
    private fun splitExtinf(line: String): Pair<String, String> {
        var inQuotes = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) return line.substring(0, i) to line.substring(i + 1).trim()
        }
        return line to ""
    }

    private fun splitKodiHeaders(line: String): Pair<String, Map<String, String>> {
        val idx = line.indexOf('|')
        if (idx < 0) return line to emptyMap()
        val url = line.substring(0, idx).trim()
        val headers = line.substring(idx + 1).split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq).trim().lowercase() to part.substring(eq + 1).trim()
        }.toMap()
        return url to headers
    }

    internal fun stableId(name: String, url: String): String =
        UUID.nameUUIDFromBytes("$name|$url".toByteArray()).toString().take(18)
}
