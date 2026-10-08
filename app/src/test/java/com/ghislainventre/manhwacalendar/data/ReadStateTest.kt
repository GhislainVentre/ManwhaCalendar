package com.ghislainventre.manhwacalendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReadStateTest {

    private fun chapter(number: String?) = Chapter(
        id = "id-$number",
        number = number,
        title = null,
        language = "en",
        readableAt = Instant.EPOCH,
        externalUrl = null,
    )

    // Du plus récent au plus ancien, comme dans l'application.
    private val series = FollowedSeries(
        id = "s",
        title = "Série",
        coverUrl = null,
        status = null,
        recentChapters = listOf("12", "11.5", "11", "10").map(::chapter),
        hasNew = true,
    )

    @Test
    fun nothingTrackedBeforeFirstMark() {
        assertNull(series.unreadCount)
        assertNull(series.nextToRead)
        assertFalse(series.isUpToDate)
    }

    @Test
    fun markingAChapterMarksThePreviousOnes() {
        val s = series.withRead(chapter("11"), true)
        assertTrue(s.isRead(chapter("10")))
        assertTrue(s.isRead(chapter("11")))
        assertFalse(s.isRead(chapter("11.5")))
        assertEquals(2, s.unreadCount)
        assertFalse(s.isUpToDate)
        assertEquals("11.5", s.nextToRead?.number)
        assertTrue(s.hasNew)
    }

    @Test
    fun readingTheLatestClearsTheNewBadge() {
        val s = series.withRead(chapter("12"), true)
        assertEquals(0, s.unreadCount)
        assertNull(s.nextToRead)
        assertFalse(s.hasNew)
        assertTrue(s.isUpToDate)
    }

    @Test
    fun unmarkingFallsBackToThePreviousKnownChapter() {
        val s = series.withAllRead().withRead(chapter("11.5"), false)
        assertEquals(11.0, s.lastReadNumber!!, 0.0)
        assertFalse(s.isRead(chapter("11.5")))
        assertFalse(s.isRead(chapter("12")))
        assertEquals("11.5", s.nextToRead?.number)
    }

    @Test
    fun unnumberedChapterIsTrackedById() {
        val oneshot = chapter(null)
        val s = series.copy(recentChapters = listOf(oneshot)).withRead(oneshot, true)
        assertTrue(s.isRead(oneshot))
        assertEquals(0, s.unreadCount)
    }
}

class ChapterOrderTest {

    private fun chapter(number: String?, day: Long) = Chapter(
        id = "id-$number-$day",
        number = number,
        title = null,
        language = "en",
        readableAt = Instant.EPOCH.plusSeconds(day * 86_400),
        externalUrl = null,
    )

    @Test
    fun sortsByNumberThenDate() {
        // Mis en ligne le même jour, ou un ancien chapitre réimporté plus tard.
        val chapters = listOf(chapter("9", 5), chapter("10", 3), chapter("10.5", 3), chapter(null, 9), chapter("2", 8))
        assertEquals(
            listOf("10.5", "10", "9", "2", null),
            chapters.sortedWith(Chapter.NEWEST_FIRST).map { it.number },
        )
    }
}
