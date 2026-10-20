package com.ghislainventre.manhwacalendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SeriesCodecTest {

    private val chapter = Chapter(
        id = "https://site.example/serie/chapitre-12",
        number = "12",
        title = null,
        language = "fr",
        readableAt = Instant.ofEpochMilli(1_700_000_000_000),
        externalUrl = "https://site.example/serie/chapitre-12",
    )

    private val series = FollowedSeries(
        id = "abc",
        title = "Solo Leveling",
        coverUrl = "https://img.example/c.jpg",
        status = "ongoing",
        recentChapters = listOf(chapter),
        nextEstimate = Instant.ofEpochMilli(1_700_500_000_000),
        intervalDays = 7.0,
        lastCheckedAt = Instant.ofEpochMilli(1_700_100_000_000),
        hasNew = true,
        source = Source.WEB,
        pageUrl = "https://site.example/serie",
        siteName = "Site",
        lastReadNumber = 11.0,
        lastReadId = "x",
        done = true,
        alternates = listOf(Mirror("Autre", "https://autre.example/serie")),
    )

    @Test
    fun roundTripKeepsEverything() {
        val raw = "[${SeriesCodec.encodeSeries(series)}]"
        val chapters = SeriesCodec.encodeChapters(series.recentChapters).toString()
        val decoded = SeriesCodec.decodeList(raw) { if (it == "abc") chapters else null }
        assertEquals(listOf(series), decoded)
    }

    @Test
    fun brokenSeriesDoesNotLoseTheOthers() {
        val raw = """[{"title":"sans identifiant"},{"id":"ok","title":"Gardée"},42]"""
        val decoded = SeriesCodec.decodeList(raw) { null }
        assertEquals(listOf("ok"), decoded.map { it.id })
    }

    @Test
    fun brokenChaptersAreSkippedOneByOne() {
        val raw = """[{"id":"s","title":"T"}]"""
        val chapters = """[{"id":"a","number":"1","language":"en","readableAt":1000},{"id":"b","readableAt":"pas une date"},{"number":"3"}]"""
        val decoded = SeriesCodec.decodeList(raw) { chapters }.single()
        assertEquals(listOf("a"), decoded.recentChapters.map { it.id })
    }

    @Test
    fun unreadableChapterListKeepsTheSeries() {
        val decoded = SeriesCodec.decodeList("""[{"id":"s","title":"T"}]""") { "{pas du json" }.single()
        assertTrue(decoded.recentChapters.isEmpty())
    }

    @Test
    fun oldFormatsAreStillRead() {
        val raw = """[{"id":"s","title":"T","source":"TOONGOD","chapters":[{"id":"https://toongod.org/c/1","number":"1","language":"en","readableAt":5}]}]"""
        val decoded = SeriesCodec.decodeList(raw) { null }.single()
        assertEquals(Source.WEB, decoded.source)
        assertEquals("ToonGod", decoded.siteName)
        assertEquals("https://toongod.org/c/1", decoded.recentChapters.single().url)
        assertNull(decoded.lastReadNumber)
    }
}
