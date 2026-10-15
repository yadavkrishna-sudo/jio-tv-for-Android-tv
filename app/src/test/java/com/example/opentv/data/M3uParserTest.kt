package com.example.opentv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {

    @Test
    fun parsesAttributesGroupsAndCommasInsideQuotes() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="a" tvg-logo="http://x.test/logo.png" group-title="News, World",Channel One
            http://example.com/one.m3u8
            #EXTINF:-1 group-title="Music",Channel Two
            https://example.com/two.m3u8
        """.trimIndent()

        val list = M3uParser.parse(m3u)
        assertEquals(2, list.size)
        assertEquals("Channel One", list[0].name)
        assertEquals("News, World", list[0].group)
        assertEquals("http://x.test/logo.png", list[0].logo)
        assertEquals("Music", list[1].group)
        assertNull(list[1].logo)
    }

    @Test
    fun readsVlcOptionsAndKodiPipeHeaders() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,With VLC opts
            #EXTVLCOPT:http-user-agent=TestAgent/1.0
            #EXTVLCOPT:http-referrer=https://ref.test/
            https://example.com/a.m3u8
            #EXTINF:-1,With pipe
            https://example.com/b.m3u8|User-Agent=PipeAgent&Referer=https://pipe.test/
        """.trimIndent()

        val list = M3uParser.parse(m3u)
        assertEquals(2, list.size)
        assertEquals("TestAgent/1.0", list[0].userAgent)
        assertEquals("https://ref.test/", list[0].referrer)
        assertEquals("https://example.com/b.m3u8", list[1].url)
        assertEquals("PipeAgent", list[1].userAgent)
        assertEquals("https://pipe.test/", list[1].referrer)
    }

    @Test
    fun skipsNonHttpAndHandlesBareUrlLists() {
        val m3u = """
            #EXTINF:-1,RTMP thing
            rtmp://example.com/live/stream
            http://example.com/plain.m3u8
        """.trimIndent()

        val list = M3uParser.parse(m3u)
        assertEquals(1, list.size)
        assertEquals("example.com", list[0].name)
        assertEquals("Uncategorized", list[0].group)
    }

    @Test
    fun extgrpAppliesWhenNoGroupTitle() {
        val m3u = """
            #EXTINF:-1,Sports One
            #EXTGRP:Sports
            http://example.com/s1.m3u8
        """.trimIndent()
        assertEquals("Sports", M3uParser.parse(m3u).single().group)
    }
}
