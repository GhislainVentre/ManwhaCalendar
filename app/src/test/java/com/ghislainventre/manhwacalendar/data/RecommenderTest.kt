package com.ghislainventre.manhwacalendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {

    private val action = Tag("a", "Action")
    private val fantasy = Tag("f", "Fantasy")
    private val romance = Tag("r", "Romance")
    private val comedy = Tag("c", "Comedy")

    private fun manga(id: String, vararg tags: Tag) =
        MangaSummary(id, id, null, null, "ko", null, tags = tags.toList())

    @Test
    fun profileCountsSeriesPerTag() {
        val profile = Recommender.profile(listOf(manga("1", action, fantasy), manga("2", action)))
        assertEquals(2, profile[action])
        assertEquals(1, profile[fantasy])
        assertEquals(listOf(action, fantasy), Recommender.topTags(profile))
    }

    @Test
    fun ranksBySharedFavouriteTagsAndExcludesFollowed() {
        val profile = mapOf(action to 3, fantasy to 2, romance to 1)
        val candidates = listOf(
            manga("romance", romance, comedy),
            manga("followed", action, fantasy),
            manga("action-fantasy", action, fantasy),
            manga("unrelated", comedy),
        )
        val result = Recommender.rank(candidates, profile, exclude = setOf("followed"))
        assertEquals(listOf("action-fantasy", "romance"), result.map { it.manga.id })
        assertEquals(listOf("Action", "Fantasy"), result.first().reasons)
    }

    @Test
    fun keepsPopularityOrderOnTies() {
        val profile = mapOf(action to 1)
        val result = Recommender.rank(listOf(manga("popular", action), manga("less", action)), profile, emptySet())
        assertEquals(listOf("popular", "less"), result.map { it.manga.id })
        assertTrue(Recommender.rank(emptyList(), profile, emptySet()).isEmpty())
    }
}
