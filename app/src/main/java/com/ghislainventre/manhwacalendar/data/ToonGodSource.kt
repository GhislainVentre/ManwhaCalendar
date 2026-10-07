package com.ghislainventre.manhwacalendar.data

import org.json.JSONObject
import java.net.URLEncoder

/**
 * Lecture de ToonGod (site WordPress au thème « Madara »), via [WebPageScraper].
 * Les chapitres y sont listés en anglais, avec leur date de mise en ligne.
 */
class ToonGodSource(private val scraper: WebPageScraper) {

    data class SeriesPage(val title: String?, val coverUrl: String?, val chapters: List<Chapter>)

    suspend fun search(query: String): List<MangaSummary> {
        val url = "$BASE_URL/?s=${URLEncoder.encode(query, "UTF-8")}&post_type=wp-manga"
        val json = JSONObject(scraper.scrape(url, SEARCH_STATE_JS, SEARCH_EXTRACT_JS))
        return json.getJSONArray("items").objects().mapNotNull { item ->
            val href = item.optStringOrNull("u") ?: return@mapNotNull null
            MangaSummary(
                id = href,
                title = item.optStringOrNull("t") ?: return@mapNotNull null,
                coverUrl = item.optStringOrNull("i"),
                status = null,
                originalLanguage = null,
                year = null,
                source = Source.TOONGOD,
                pageUrl = href,
                latestChapter = item.optStringOrNull("l"),
            )
        }
    }

    suspend fun series(pageUrl: String): SeriesPage {
        val json = JSONObject(scraper.scrape(pageUrl, SERIES_STATE_JS, SERIES_EXTRACT_JS))
        val chapters = json.getJSONArray("ch").objects().mapNotNull { c ->
            val href = c.optStringOrNull("u") ?: return@mapNotNull null
            val label = c.optString("n").trim()
            Chapter(
                id = href,
                number = ToonGodDates.chapterNumber(label),
                title = ToonGodDates.chapterTitle(label),
                language = "en",
                readableAt = ToonGodDates.parse(c.optString("d")) ?: return@mapNotNull null,
                externalUrl = href,
            )
        }
        return SeriesPage(json.optStringOrNull("title"), json.optStringOrNull("cover"), chapters)
    }

    private companion object {
        const val BASE_URL = "https://www.toongod.org"

        // Fonctions JS partagées : page bloquée par Cloudflare et URL d'image (chargement différé).
        private const val HELPERS = """
            function blocked(){return /just a moment|attention required|un instant/i.test(document.title||'');}
            function img(el){if(!el)return '';return el.getAttribute('data-src')||el.getAttribute('data-lazy-src')||el.getAttribute('src')||'';}
            function txt(el){return el?(el.textContent||'').trim():'';}
        """

        const val SEARCH_STATE_JS = """(function(){$HELPERS
            if(blocked()||document.readyState!=='complete')return 0;
            return document.querySelector('.c-tabs-item__content, .search-wrap, .c-search-header__wrapper')?2:1;})()"""

        const val SEARCH_EXTRACT_JS = """(function(){$HELPERS
            var items=[];
            document.querySelectorAll('.c-tabs-item__content').forEach(function(el){
              var a=el.querySelector('.post-title a');if(!a)return;
              items.push({u:a.href,t:txt(a),i:img(el.querySelector('img')),l:txt(el.querySelector('.latest-chap .chapter a'))});
            });
            return JSON.stringify({items:items});})()"""

        const val SERIES_STATE_JS = """(function(){$HELPERS
            if(blocked()||document.readyState!=='complete')return 0;
            return document.querySelectorAll('li.wp-manga-chapter').length>0?2:1;})()"""

        const val SERIES_EXTRACT_JS = """(function(){$HELPERS
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
