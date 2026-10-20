package com.ghislainventre.manhwacalendar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchGroupingTest {

    private fun web(site: String, title: String, latest: String?) = MangaSummary(
        id = "https://$site/manga/${title.hashCode()}",
        title = title,
        coverUrl = null,
        status = null,
        originalLanguage = null,
        year = null,
        source = Source.WEB,
        pageUrl = "https://$site/manga/${title.hashCode()}",
        latestChapter = latest,
        siteName = site,
    )

    @Test
    fun sameSeriesOnSeveralSitesIsGroupedOnce() {
        val groups = SearchGrouping.group(
            listOf(
                listOf(web("a", "Solo Leveling", "Chapter 200"), web("a", "Other", "Chapter 3")) to 900L,
                listOf(web("b", "solo leveling", "Chapter 201")) to 4000L,
            ),
        )
        assertEquals(2, groups.size)
        assertEquals("solo leveling", groups[0].title)
        assertEquals(2, groups[0].hits.size)
    }

    @Test
    fun bestSiteHasMostChaptersThenIsFastest() {
        val group = SearchGrouping.group(
            listOf(
                listOf(web("slow", "Hound", "Chapter 90")) to 8000L,
                listOf(web("fast", "Hound", "Chapter 90")) to 1000L,
                listOf(web("behind", "Hound", "Chapter 40")) to 500L,
            ),
        ).single()
        assertEquals("fast", group.best.siteName)
        assertEquals(listOf("slow", "behind"), group.alternates.map { it.siteName })
    }

    @Test
    fun frenchReaderGetsFrenchSiteEvenWhenBehind() {
        val results = listOf(
            listOf(web("toongod.org", "Hound", "Chapter 120")) to 500L,
            listOf(web("sushiscan.net", "Hound", "Chapitre 95")) to 3000L,
        )
        assertEquals("toongod.org", SearchGrouping.group(results).single().best.siteName)
        assertEquals("sushiscan.net", SearchGrouping.group(results, preferFrench = true).single().best.siteName)
    }

    @Test
    fun frenchSiteWinsATie() {
        val group = SearchGrouping.group(
            listOf(
                listOf(web("toongod.org", "Hound", "Chapter 95")) to 500L,
                listOf(web("lelmanga.com", "Hound", "Chapitre 95")) to 3000L,
            ),
        ).single()
        assertEquals("lelmanga.com", group.best.siteName)
    }
}
