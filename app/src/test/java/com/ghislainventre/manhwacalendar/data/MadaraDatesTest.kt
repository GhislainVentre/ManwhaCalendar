package com.ghislainventre.manhwacalendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class MadaraDatesTest {

    private val now = Instant.parse("2026-10-07T08:00:00Z")

    private fun parse(text: String) = MadaraDates.parse(text, now, ZoneOffset.UTC)

    @Test
    fun absoluteDates() {
        val expected = Instant.parse("2026-10-03T12:00:00Z")
        assertEquals(expected, parse("October 3, 2026"))
        assertEquals(expected, parse("Oct 3, 2026"))
        assertEquals(expected, parse("03/10/2026"))
    }

    @Test
    fun relativeDates() {
        assertEquals(now.minus(Duration.ofHours(2)), parse("2 hours ago"))
        assertEquals(now.minus(Duration.ofDays(1)), parse("a day ago"))
        assertEquals(now.minus(Duration.ofMinutes(15)), parse("15 mins ago"))
        assertEquals(now.minus(Duration.ofDays(1)), parse("Yesterday"))
    }

    @Test
    fun unknownTextGivesNull() {
        assertNull(parse(""))
        assertNull(parse("soon"))
    }

    @Test
    fun chapterLabels() {
        assertEquals("45.5", MadaraDates.chapterNumber("Chapter 45.5"))
        assertNull(MadaraDates.chapterTitle("Chapter 45"))
        assertEquals("The End", MadaraDates.chapterTitle("Chapter 45 - The End"))
    }
}
