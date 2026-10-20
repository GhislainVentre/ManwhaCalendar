package com.ghislainventre.manhwacalendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(expected, parse("3 Oct 2026"))
    }

    @Test
    fun relativeDates() {
        assertEquals(now.minus(Duration.ofHours(2)), parse("2 hours ago"))
        assertEquals(now.minus(Duration.ofDays(1)), parse("a day ago"))
        assertEquals(now.minus(Duration.ofMinutes(15)), parse("15 mins ago"))
        assertEquals(now.minus(Duration.ofHours(3)), parse("3 hrs ago"))
        assertEquals(now.minus(Duration.ofDays(14)), parse("2 wks ago"))
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

    @Test
    fun frenchDates() {
        assertEquals(Instant.parse("2025-05-16T12:00:00Z"), parse("16 mai 2025"))
        assertEquals(Instant.parse("2026-10-03T12:00:00Z"), parse("3 octobre 2026"))
        assertEquals(Instant.parse("2026-10-03T12:00:00Z"), parse("3 oct. 2026"))
        assertEquals(now.minus(Duration.ofHours(2)), parse("il y a 2 heures"))
        assertEquals(now.minus(Duration.ofDays(1)), parse("il y a un jour"))
        assertEquals(now.minus(Duration.ofDays(1)), parse("Hier"))
    }

    @Test
    fun frenchChapterLabels() {
        assertEquals("206", MadaraDates.chapterNumber(" Chapitre 206"))
        assertNull(MadaraDates.chapterTitle("Chapitre 206"))
    }

    @Test
    fun frenchDateVariants() {
        assertEquals(Instant.parse("2026-10-01T12:00:00Z"), parse("1er octobre 2026"))
        assertEquals(Instant.parse("2026-10-03T12:00:00Z"), parse("3 oct 2026"))
        assertEquals(Instant.parse("2026-09-12T12:00:00Z"), parse("12 sept 2026"))
    }

    @Test
    fun chapterNumberFollowsChapterKeyword() {
        assertEquals("15", MadaraDates.chapterNumber("Saison 2 - Chapitre 15"))
        assertEquals("45.5", MadaraDates.chapterNumber("Chapitre 45,5"))
        assertEquals("12", MadaraDates.chapterNumber("Ep. 12"))
        assertEquals("120", MadaraDates.chapterNumber("Solo Leveling 120"))
    }

    @Test
    fun volumesAreNotChapters() {
        assertTrue(MadaraDates.isVolume(" Volume 1"))
        assertTrue(MadaraDates.isVolume("Tome 12"))
        assertFalse(MadaraDates.isVolume("Chapitre 1"))
    }
}
