package com.ghislainventre.manhwacalendar.data

import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant

/**
 * Lecture des sites de manhwa construits sur WordPress avec le thème « Madara »
 * (ToonGod, ManhwaTop, MangaRead…) ou « MangaReader » (Sushiscan, Lelmanga…), via [WebPageScraper].
 * Les deux acceptent la même adresse de recherche ; l'extraction essaie l'un puis l'autre.
 */
class MadaraSource(private val scraper: WebPageScraper) {

    data class SeriesPage(val title: String?, val coverUrl: String?, val chapters: List<Chapter>)

    /** [background] : recherche lancée par une vérification, qui ne doit pas ralentir celles de l'utilisateur. */
    suspend fun search(site: Site, query: String, background: Boolean = false): List<MangaSummary> {
        val url = "${site.baseUrl.trimEnd('/')}/?s=${URLEncoder.encode(query, "UTF-8")}&post_type=wp-manga"
        // Recherche : délai court, un site lent ne doit pas faire attendre les autres résultats.
        val json = JSONObject(scraper.scrape(url, SEARCH_STATE_JS, SEARCH_EXTRACT_JS, timeoutMs = SEARCH_TIMEOUT_MS, background = background))
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
        val json = JSONObject(scraper.scrape(pageUrl, SERIES_STATE_JS, SERIES_EXTRACT_JS, background = true))
        // Les chapitres sont listés du plus récent au plus ancien. Une date illisible (format inconnu,
        // badge « NEW » sans texte) reprend celle du chapitre plus récent au lieu de faire disparaître le chapitre.
        var newerDate = Instant.now()
        val host = runCatching { java.net.URI(pageUrl).host }.getOrNull()?.removePrefix("www.")
        val language = if (host in FRENCH_HOSTS) "fr" else "en"
        val chapters = json.getJSONArray("ch").objects().mapNotNull { c ->
            val href = c.optStringOrNull("u") ?: return@mapNotNull null
            val label = c.optString("n").trim()
            val date = MadaraDates.parse(c.optString("d")) ?: newerDate
            newerDate = date
            Chapter(
                id = href,
                number = MadaraDates.chapterNumber(label),
                title = MadaraDates.chapterTitle(label),
                language = language,
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
            Site("CoffeeManga", "https://coffeemanga.moe"),
            Site("S2Manga", "https://s2manga.com"),
            Site("ManhuaPlus", "https://manhuaplus.com"),
            Site("TopManhua", "https://www.topmanhua.fan"),
            Site("Toonily", "https://toonily.com"),
            Site("KunManga", "https://kunmanga.com"),
            Site("ManhuaFast", "https://manhuafast.com"),
            Site("Webtoon.xyz", "https://www.webtoon.xyz"),
            Site("1stKissManga", "https://1stkissmanga.me"),
            Site("MangaWeebs", "https://mangaweebs.org"),
            Site("Manhuaga", "https://manhuaga.com"),
            Site("Manga68", "https://manga68.com"),
            Site("Arenascans", "https://arenascan.com"),
            // Sites en français.
            Site("Sushiscan", "https://sushiscan.net"),
            Site("Lelmanga", "https://www.lelmanga.com"),
            Site("Pantheon Scan", "https://pantheon-scan.com"),
            Site("Mangas Origines", "https://mangas-origines.fr"),
            Site("Manga Scantrad", "https://manga-scantrad.io"),
            Site("Astral Manga", "https://astral-manga.fr"),
            Site("ToonFR", "https://toonfr.com"),
        )

        /** Sites dont les chapitres sont en français ; les autres publient en anglais. */
        private val FRENCH_HOSTS = setOf(
            "sushiscan.net", "lelmanga.com", "pantheon-scan.com", "mangas-origines.fr",
            "manga-scantrad.io", "astral-manga.fr", "toonfr.com",
        )

        /** Sites retirés de la liste par défaut (injoignables, ou déménagés) : enlevés aussi des listes déjà enregistrées. */
        val REMOVED_SITES = setOf(
            "https://manhwaclan.com", "https://www.readmanhua.net", "https://coffeemanga.io",
            // Pas un site WordPress : ses pages n'ont rien de lisible pour l'appli.
            "https://scantrad-union.com",
        )

        private const val SEARCH_TIMEOUT_MS = 15_000L

        // Fonctions JS partagées : page bloquée par Cloudflare et URL d'image (chargement différé).
        private const val HELPERS = """
            function blocked(){return /just a moment|attention required|un instant/i.test(document.title||'');}
            function img(el){if(!el)return '';return el.getAttribute('data-src')||el.getAttribute('data-lazy-src')||el.getAttribute('src')||'';}
            function txt(el){return el?(el.textContent||'').trim():'';}
        """

        private const val SEARCH_STATE_JS = """(function(){$HELPERS
            if(blocked()||document.readyState!=='complete')return 0;
            return document.querySelector('.c-tabs-item__content, .search-wrap, .c-search-header__wrapper, .listupd')?2:1;})()"""

        private const val SEARCH_EXTRACT_JS = """(function(){$HELPERS
            var items=[];
            document.querySelectorAll('.c-tabs-item__content').forEach(function(el){
              var a=el.querySelector('.post-title a');if(!a)return;
              items.push({u:a.href,t:txt(a),i:img(el.querySelector('img')),l:txt(el.querySelector('.latest-chap .chapter a'))});
            });
            if(!items.length)document.querySelectorAll('.listupd .bsx').forEach(function(el){
              var a=el.querySelector('a');if(!a||!a.href)return;
              var t=txt(el.querySelector('.tt'))||a.getAttribute('title')||'';if(!t)return;
              items.push({u:a.href,t:t,i:img(el.querySelector('img')),l:txt(el.querySelector('.epxs'))});
            });
            return JSON.stringify({items:items});})()"""

        private const val SERIES_STATE_JS = """(function(){$HELPERS
            if(blocked()||document.readyState!=='complete')return 0;
            return document.querySelectorAll('li.wp-manga-chapter, #chapterlist li').length>0?2:1;})()"""

        private const val SERIES_EXTRACT_JS = """(function(){$HELPERS
            var ch=[];
            document.querySelectorAll('li.wp-manga-chapter').forEach(function(li){
              var a=li.querySelector('a');if(!a)return;
              var d=li.querySelector('.chapter-release-date');var date='';
              if(d){var i=d.querySelector('i');var n=d.querySelector('a[title]');date=i?txt(i):(n?n.getAttribute('title'):txt(d));}
              ch.push({u:a.href,n:txt(a),d:date});
            });
            if(!ch.length)document.querySelectorAll('#chapterlist li').forEach(function(li){
              var a=li.querySelector('a');if(!a)return;
              ch.push({u:a.href,n:txt(li.querySelector('.chapternum'))||txt(a),d:txt(li.querySelector('.chapterdate'))});
            });
            return JSON.stringify({title:txt(document.querySelector('.post-title h1, .post-title h3, h1.entry-title')),
              cover:img(document.querySelector('.summary_image img, .thumb img')),ch:ch});})()"""
    }
}
