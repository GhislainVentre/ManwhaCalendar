package com.ghislainventre.manhwacalendar.data

import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant

/**
 * Lecture des sites de manhwa construits sur WordPress avec le thème « Madara »
 * (ToonGod, ManhwaTop, MangaRead…), via [WebPageScraper].
 */
class MadaraSource(private val scraper: WebPageScraper) {

    data class SeriesPage(val title: String?, val coverUrl: String?, val chapters: List<Chapter>)

    suspend fun search(site: Site, query: String): List<MangaSummary> {
        val url = "${site.baseUrl.trimEnd('/')}/?s=${URLEncoder.encode(query, "UTF-8")}&post_type=wp-manga"
        // Recherche : délai court, un site lent ne doit pas faire attendre les autres résultats.
        val json = JSONObject(scraper.scrape(url, SEARCH_STATE_JS, SEARCH_EXTRACT_JS, timeoutMs = SEARCH_TIMEOUT_MS))
        return json.getJSONArray("items").objects().mapNotNull { item ->
            val href = item.optStringOrNull("u") ?: return@mapNotNull null
            MangaSummary(
                id = href,
                title = item.optStringOrNull("t") ?: return@mapNotNull null,
                coverUrl = item.optStringOrNull("i"),
                status = null,
                originalLanguage = null,
                year = null,
                source = Source.WEB,
                pageUrl = href,
                latestChapter = item.optStringOrNull("l"),
                siteName = site.name,
            )
        }
    }

    suspend fun series(pageUrl: String): SeriesPage {
        val json = JSONObject(scraper.scrape(pageUrl, SERIES_STATE_JS, SERIES_EXTRACT_JS))
        // Les chapitres sont listés du plus récent au plus ancien. Une date illisible (format inconnu,
        // badge « NEW » sans texte) reprend celle du chapitre plus récent au lieu de faire disparaître le chapitre.
        var newerDate = Instant.now()
        val chapters = json.getJSONArray("ch").objects().mapNotNull { c ->
            val href = c.optStringOrNull("u") ?: return@mapNotNull null
            val label = c.optString("n").trim()
            val date = MadaraDates.parse(c.optString("d")) ?: newerDate
            newerDate = date
            Chapter(
                id = href,
                number = MadaraDates.chapterNumber(label),
                title = MadaraDates.chapterTitle(label),
                language = "en",
                readableAt = date,
                externalUrl = href,
            )
        }
        return SeriesPage(json.optStringOrNull("title"), json.optStringOrNull("cover"), chapters)
    }

    companion object {
        val DEFAULT_SITES = listOf(
            Site("ToonGod", "https://www.toongod.org"),
            Site("ManhwaTop", "https://manhwatop.com"),
            Site("MangaRead", "https://www.mangaread.org"),
            Site("Manhuaus", "https://manhuaus.com"),
            Site("ZinManga", "https://www.zinmanga.net"),
            Site("MangaClash", "https://mangaclash.com"),
            Site("CoffeeManga", "https://coffeemanga.io"),
            Site("S2Manga", "https://s2manga.com"),
            Site("ReadManhua", "https://www.readmanhua.net"),
        )

        /** Sites retirés de la liste par défaut (injoignables) : enlevés aussi des listes déjà enregistrées. */
        val REMOVED_SITES = setOf("https://manhwaclan.com")

        private const val SEARCH_TIMEOUT_MS = 15_000L

        // Fonctions JS partagées : page bloquée par Cloudflare et URL d'image (chargement différé).
        private const val HELPERS = """
            function blocked(){return /just a moment|attention required|un instant/i.test(document.title||'');}
            function img(el){if(!el)return '';return el.getAttribute('data-src')||el.getAttribute('data-lazy-src')||el.getAttribute('src')||'';}
            function txt(el){return el?(el.textContent||'').trim():'';}
        """

        private const val SEARCH_STATE_JS = """(function(){$HELPERS
            if(blocked()||document.readyState!=='complete')return 0;
            return document.querySelector('.c-tabs-item__content, .search-wrap, .c-search-header__wrapper')?2:1;})()"""

        private const val SEARCH_EXTRACT_JS = """(function(){$HELPERS
            var items=[];
            document.querySelectorAll('.c-tabs-item__content').forEach(function(el){
              var a=el.querySelector('.post-title a');if(!a)return;
              items.push({u:a.href,t:txt(a),i:img(el.querySelector('img')),l:txt(el.querySelector('.latest-chap .chapter a'))});
            });
            return JSON.stringify({items:items});})()"""

        private const val SERIES_STATE_JS = """(function(){$HELPERS
            if(blocked()||document.readyState!=='complete')return 0;
            return document.querySelectorAll('li.wp-manga-chapter').length>0?2:1;})()"""

        private const val SERIES_EXTRACT_JS = """(function(){$HELPERS
            var ch=[];
            document.querySelectorAll('li.wp-manga-chapter').forEach(function(li){
              var a=li.querySelector('a');if(!a)return;
              var d=li.querySelector('.chapter-release-date');var date='';
              if(d){var i=d.querySelector('i');var n=d.querySelector('a[title]');date=i?txt(i):(n?n.getAttribute('title'):txt(d));}
              ch.push({u:a.href,n:txt(a),d:date});
            });
            return JSON.stringify({title:txt(document.querySelector('.post-title h1, .post-title h3')),
              cover:img(document.querySelector('.summary_image img')),ch:ch});})()"""
    }
}
