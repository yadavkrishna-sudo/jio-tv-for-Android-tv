package com.example.opentv.data

data class Channel(
    val id: String,
    /** Position in the merged channel list, starting at 1. Used for number-key zapping. */
    val number: Int,
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
    val userAgent: String? = null,
    val referrer: String? = null,
    val playlistId: String = "",
)

data class PlaylistEntry(
    val id: String,
    val name: String,
    /** Remote playlists have a URL and can be refreshed. Local files / the demo list have null. */
    val url: String?,
    val channelCount: Int = 0,
)
