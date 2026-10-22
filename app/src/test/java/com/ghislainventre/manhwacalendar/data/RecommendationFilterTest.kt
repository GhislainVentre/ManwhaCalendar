package com.ghislainventre.manhwacalendar.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationFilterTest {

    private fun manga(status: String?) = MangaSummary("id", "title", null, status, "ko", null)

    @Test
    fun noFilterKeepsEverything() {
        val filter = RecommendationFilter()
        assertFalse(filter.isActive)
        assertTrue(filter.matches(manga(null), null))
        assertTrue(filter.matches(manga("hiatus"), 3))
    }

    @Test
    fun statusFilterKeepsSelectedStatusesOnly() {
        val filter = RecommendationFilter(statuses = setOf("completed", "cancelled"))
        assertTrue(filter.matches(manga("completed"), null))
        assertTrue(filter.matches(manga("cancelled"), null))
        assertFalse(filter.matches(manga("ongoing"), null))
        assertFalse(filter.matches(manga(null), null))
    }

    @Test
    fun chapterFilterNeedsAKnownCountInRange() {
        val filter = RecommendationFilter(chapters = ChapterRange.MEDIUM)
        assertTrue(filter.matches(manga("ongoing"), 50))
        assertTrue(filter.matches(manga("ongoing"), 99))
        assertFalse(filter.matches(manga("ongoing"), 100))
        assertFalse(filter.matches(manga("ongoing"), 49))
        assertFalse(filter.matches(manga("ongoing"), null))
        assertTrue(1000 in ChapterRange.VERY_LONG)
        assertTrue(0 in ChapterRange.SHORT)
    }

    @Test
    fun aggregateCountUsesHighestChapterNumber() {
        val json = JSONObject(
            """{"result":"ok","volumes":{
                "none":{"volume":"none","chapters":{"120":{"chapter":"120"},"118.5":{"chapter":"118.5"},"none":{"chapter":"none"}}},
                "1":{"volume":"1","chapters":{"1":{"chapter":"1"},"2":{"chapter":"2"}}}}}""",
        )
        assertEquals(120, aggregateChapterCount(json))
    }

    @Test
    fun aggregateWithoutChaptersIsUnknown() {
        assertNull(aggregateChapterCount(JSONObject("""{"result":"ok","volumes":[]}""")))
        assertEquals(2, aggregateChapterCount(JSONObject("""{"volumes":{"none":{"chapters":{"Oneshot":{},"Extra":{}}}}}""")))
    }
}
