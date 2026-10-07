package com.ghislainventre.manhwacalendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ReleaseEstimatorTest {

    private val base = Instant.parse("2026-09-01T10:00:00Z")

    private fun chapter(number: String?, daysAfterBase: Double, lang: String = "en") = Chapter(
        id = "$number-$lang-$daysAfterBase",
        number = number,
        title = null,
        language = lang,
        readableAt = base.plus(Duration.ofMinutes((daysAfterBase * 24 * 60).toLong())),
        externalUrl = null,
    )

    @Test
    fun weeklySeriesPredictsNextWeek() {
        val chapters = (0..4).map { chapter("${10 + it}", it * 7.0) }
        val now = base.plus(Duration.ofDays(30))
        val estimate = ReleaseEstimator.estimate(chapters, finished = false, now = now)
        assertEquals(7.0, estimate.intervalDays!!, 0.01)
        assertEquals(base.plus(Duration.ofDays(35)), estimate.next)
    }

    @Test
    fun bulkUploadsCountAsOneRelease() {
        val chapters = listOf(
            chapter("1", 0.0), chapter("2", 0.01), chapter("3", 0.02),
            chapter("4", 7.0), chapter("5", 14.0),
        )
        assertEquals(3, ReleaseEstimator.releaseDates(chapters).size)
    }

    @Test
    fun translationsOfSameChapterUseFirstDate() {
        val chapters = listOf(chapter("1", 0.0, "en"), chapter("1", 3.0, "fr"), chapter("2", 7.0, "en"))
        assertEquals(listOf(base, base.plus(Duration.ofDays(7))), ReleaseEstimator.releaseDates(chapters))
    }

    @Test
    fun slightlyLateSeriesRollsForward() {
        val chapters = (0..3).map { chapter("${it + 1}", it * 7.0) }
        val now = base.plus(Duration.ofDays(29)) // attendu au jour 28
        val estimate = ReleaseEstimator.estimate(chapters, finished = false, now = now)
        assertEquals(base.plus(Duration.ofDays(35)), estimate.next)
    }

    @Test
    fun longSilenceMeansHiatus() {
        val chapters = (0..3).map { chapter("${it + 1}", it * 7.0) }
        val now = base.plus(Duration.ofDays(90))
        assertNull(ReleaseEstimator.estimate(chapters, finished = false, now = now).next)
    }

    @Test
    fun finishedSeriesHasNoNextRelease() {
        val chapters = (0..3).map { chapter("${it + 1}", it * 7.0) }
        val estimate = ReleaseEstimator.estimate(chapters, finished = true, now = base)
        assertNull(estimate.next)
        assertNotNull(estimate.intervalDays)
    }

    @Test
    fun singleChapterGivesNoEstimate() {
        assertNull(ReleaseEstimator.estimate(listOf(chapter("1", 0.0)), finished = false, now = base).next)
    }
}
