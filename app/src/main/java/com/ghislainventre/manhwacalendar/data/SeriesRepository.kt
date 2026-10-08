package com.ghislainventre.manhwacalendar.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Séries suivies, stockées localement, et leur mise à jour depuis MangaDex et les sites web. */
class SeriesRepository(
    context: Context,
    private val api: MangaDexApi = MangaDexApi(),
    private val madara: MadaraSource = MadaraSource(WebPageScraper(context)),
) {

    private val prefs = context.getSharedPreferences("manhwa_calendar", Context.MODE_PRIVATE)
    private val refreshMutex = Mutex()
    /** Dernière recherche d'une série MangaDex sur les sites web, par identifiant. */
    private val webLookups = ConcurrentHashMap<String, Instant>()

    private val _followed = MutableStateFlow(load())
    val followed: StateFlow<List<FollowedSeries>> = _followed.asStateFlow()

    private val _language = MutableStateFlow(
        runCatching { ChapterLanguage.valueOf(prefs.getString(KEY_LANGUAGE, null) ?: "") }
            .getOrDefault(ChapterLanguage.FR_EN)
    )
    val language: StateFlow<ChapterLanguage> = _language.asStateFlow()

    private val _sites = MutableStateFlow(loadSites())
    /** Sites web interrogés par la recherche (MangaDex en premier). */
    val sites: StateFlow<List<Site>> = _sites.asStateFlow()

    /** Une source de recherche : MangaDex ou un site web activé. */
    class SearchSource(val name: String, val search: suspend (String) -> List<MangaSummary>)

    fun searchSources(): List<SearchSource> = _sites.value.filter { it.enabled }.map { site ->
        if (site.name == MANGADEX_NAME) {
            SearchSource(site.name) { q -> api.search(q, manhwaOnly = true) }
        } else {
            SearchSource(site.name) { q -> madara.search(site, q) }
        }
    }

    fun setSiteEnabled(name: String, enabled: Boolean) =
        saveSites(_sites.value.map { if (it.name == name) it.copy(enabled = enabled) else it })

    /** Ajoute un site Madara à partir de son adresse ; renvoie false si l'adresse est invalide. */
    fun addSite(address: String): Boolean {
        val url = address.trim().let { if (it.startsWith("http")) it else "https://$it" }.trimEnd('/')
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.") ?: return false
        if (!host.contains('.') || _sites.value.any { it.baseUrl.equals(url, ignoreCase = true) }) return false
        val short = host.substringBefore('.').replaceFirstChar { it.uppercase() }
        val name = if (_sites.value.any { it.name == short }) host else short
        saveSites(_sites.value + Site(name, url))
        return true
    }

    fun removeSite(name: String) = saveSites(_sites.value.filterNot { it.name == name })

    fun isFollowed(id: String) = _followed.value.any { it.id == id }

    /**
     * Manhwa populaires proches des séries suivies. Les genres viennent de MangaDex ; pour une
     * série suivie sur un site web, on prend la fiche MangaDex la plus pertinente pour son titre.
     */
    suspend fun recommendations(): List<Recommendation> {
        val followed = _followed.value
        val liked = api.mangas(followed.filter { it.source == Source.MANGADEX }.map { it.id }).toMutableList()
        followed.filter { it.source == Source.WEB }.take(MAX_WEB_LOOKUPS).forEach { series ->
            try {
                api.search(series.title, manhwaOnly = false).firstOrNull()?.let { liked += it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Une série introuvable sur MangaDex ne compte simplement pas dans le profil.
            }
        }
        val profile = Recommender.profile(liked)
        val top = Recommender.topTags(profile)
        if (top.isEmpty()) return emptyList()
        val candidates = api.popularWithTags(top.map { it.id }, _language.value.codes)
        val exclude = followed.map { it.id }.toSet() + liked.map { it.id }
        return Recommender.rank(candidates, profile, exclude)
    }

    suspend fun follow(manga: MangaSummary) {
        if (isFollowed(manga.id)) return
        val series = FollowedSeries(
            manga.id, manga.title, manga.coverUrl, manga.status,
            source = manga.source, pageUrl = manga.pageUrl, siteName = manga.siteName,
        )
        mutate { it + series }
        try {
            refreshOne(series, notifyNew = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // La série reste suivie ; la prochaine vérification réessaiera.
        }
    }

    fun unfollow(id: String) = mutate { list -> list.filterNot { it.id == id } }

    /** Remet une série retirée par erreur, avec ses chapitres déjà connus. */
    fun restore(series: FollowedSeries) = mutate { list -> if (list.any { it.id == series.id }) list else list + series }

    fun markSeen(id: String) = mutate { list -> list.map { if (it.id == id) it.copy(hasNew = false) else it } }

    /** Marque un chapitre (et les précédents) comme lu, ou le démarque. */
    fun setRead(id: String, chapter: Chapter, read: Boolean) =
        mutate { list -> list.map { if (it.id == id) it.withRead(chapter, read) else it } }

    fun markAllRead(id: String) = mutate { list -> list.map { if (it.id == id) it.withAllRead() else it } }

    fun markAllSeen() = mutate { list -> list.map { it.copy(hasNew = false) } }

    fun setLanguage(language: ChapterLanguage) {
        _language.value = language
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    /** Met à jour toutes les séries et renvoie les nouveaux chapitres détectés. */
    suspend fun refreshAll(): List<NewChapterEvent> = refreshMutex.withLock {
        val events = mutableListOf<NewChapterEvent>()
        var lastError: Exception? = null
        for (series in _followed.value) {
            try {
                refreshOne(series, notifyNew = true)?.let(events::add)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
            delay(250) // limite de débit de l'API MangaDex
        }
        if (events.isEmpty() && lastError != null && _followed.value.isNotEmpty()) throw lastError
        events
    }

    private suspend fun refreshOne(series: FollowedSeries, notifyNew: Boolean): NewChapterEvent? {
        val fetched = when (series.source) {
            Source.MANGADEX -> {
                val manga = runCatching { api.manga(series.id) }.getOrNull()
                val chapters = api.latestChapters(series.id, _language.value.codes)
                val status = manga?.status ?: series.status
                // Beaucoup de séries n'ont presque plus de chapitres sur MangaDex (retraits, équipes parties) :
                // on bascule alors sur un site de lecture qui en a de plus récents.
                val web = if (looksAbandoned(chapters, status)) findOnWeb(series, chapters) else null
                if (web != null) {
                    Fetched(manga?.title, manga?.coverUrl, status, web.page.chapters.sortedByDescending { it.readableAt }, web)
                } else {
                    Fetched(manga?.title, manga?.coverUrl, status, chapters)
                }
            }
            Source.WEB -> {
                val page = madara.series(series.url)
                Fetched(page.title, page.coverUrl, null, page.chapters.sortedByDescending { it.readableAt })
            }
        }
        val chapters = fetched.chapters
        val sorted = chapters.sortedWith(Chapter.NEWEST_FIRST)
        val status = fetched.status ?: series.status
        val estimate = ReleaseEstimator.estimate(chapters, finished = status == "completed" || status == "cancelled")

        val previous = series.latestChapter
        val latest = sorted.firstOrNull()
        // Comparaison par identifiant : les dates relatives (« 2 hours ago ») bougent à chaque lecture.
        val isNew = previous != null && latest != null && latest.id != previous.id &&
            !latest.readableAt.isBefore(previous.readableAt)

        val updated = (_followed.value.firstOrNull { it.id == series.id } ?: return null).copy(
            title = fetched.title ?: series.title,
            source = if (fetched.web != null) Source.WEB else series.source,
            pageUrl = fetched.web?.pageUrl ?: series.pageUrl,
            siteName = fetched.web?.site?.name ?: series.siteName,
            coverUrl = fetched.coverUrl ?: series.coverUrl,
            status = status,
            recentChapters = sorted.distinctBy { it.number ?: it.id }.take(MAX_STORED_CHAPTERS),
            nextEstimate = estimate.next,
            intervalDays = estimate.intervalDays,
            lastCheckedAt = Instant.now(),
            hasNew = series.hasNew || isNew,
        )
        mutate { list -> list.map { if (it.id == series.id) updated else it } }
        return if (isNew && notifyNew) NewChapterEvent(updated, latest!!) else null
    }

    private class Fetched(
        val title: String?,
        val coverUrl: String?,
        val status: String?,
        val chapters: List<Chapter>,
        val web: WebMatch? = null,
    )

    private class WebMatch(val site: Site, val pageUrl: String, val page: MadaraSource.SeriesPage)

    /** Aucun chapitre dans les langues choisies, ou plus rien depuis longtemps sur une série en cours. */
    private fun looksAbandoned(chapters: List<Chapter>, status: String?): Boolean {
        val newest = chapters.maxOfOrNull { it.readableAt } ?: return true
        if (status == "completed" || status == "cancelled") return false
        return newest.isBefore(Instant.now().minus(STALE_AFTER))
    }

    /**
     * Cherche la série sur les sites activés, sous ses titres anglais MangaDex, et renvoie la première
     * page qui va plus loin que les [known] chapitres MangaDex. Une recherche par série et par jour au plus,
     * car chaque site passe par une WebView.
     */
    private suspend fun findOnWeb(series: FollowedSeries, known: List<Chapter>): WebMatch? {
        val now = Instant.now()
        if (webLookups[series.id]?.isAfter(now.minus(WEB_LOOKUP_INTERVAL)) == true) return null
        webLookups[series.id] = now

        val titles = (orEmptyOnError { api.titles(series.id) } + series.title).distinctBy(TitleMatch::normalize)
        val sites = _sites.value.filter { it.enabled && it.name != MANGADEX_NAME }
        val hits = coroutineScope {
            sites.map { site ->
                async {
                    orEmptyOnError { madara.search(site, titles.first()) }
                        .filter { it.pageUrl != null && TitleMatch.matches(it.title, titles) }
                        .map { site to it.pageUrl!! }
                }
            }.awaitAll().flatten()
        }
        val knownTop = known.maxOfOrNull { it.number?.toDoubleOrNull() ?: -1.0 } ?: -1.0
        for ((site, url) in hits.take(MAX_WEB_PAGES)) {
            val page = try {
                madara.series(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }
            val top = page.chapters.maxOfOrNull { it.number?.toDoubleOrNull() ?: -1.0 } ?: continue
            if (top > knownTop) return WebMatch(site, url, page)
        }
        return null
    }

    private suspend fun <T> orEmptyOnError(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private fun mutate(transform: (List<FollowedSeries>) -> List<FollowedSeries>) {
        _followed.update(transform)
        save(_followed.value)
    }

    // --- Persistance JSON dans les SharedPreferences ---

    private fun load(): List<FollowedSeries> = runCatching {
        JSONArray(prefs.getString(KEY_FOLLOWED, "[]")).objects().map { it.toSeries() }
    }.getOrDefault(emptyList())

    private fun save(list: List<FollowedSeries>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_FOLLOWED, array.toString()).apply()
    }

    private fun FollowedSeries.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        putOpt("coverUrl", coverUrl)
        putOpt("status", status)
        putOpt("nextEstimate", nextEstimate?.toEpochMilli())
        putOpt("intervalDays", intervalDays)
        putOpt("lastCheckedAt", lastCheckedAt?.toEpochMilli())
        put("hasNew", hasNew)
        put("source", source.name)
        putOpt("pageUrl", pageUrl)
        putOpt("siteName", siteName)
        putOpt("lastReadNumber", lastReadNumber)
        putOpt("lastReadId", lastReadId)
        put("chapters", JSONArray().apply {
            recentChapters.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    putOpt("number", c.number)
                    putOpt("title", c.title)
                    put("language", c.language)
                    put("readableAt", c.readableAt.toEpochMilli())
                    putOpt("externalUrl", c.externalUrl)
                })
            }
        })
    }

    private fun JSONObject.toSeries() = FollowedSeries(
        id = getString("id"),
        title = getString("title"),
        coverUrl = optStringOrNull("coverUrl"),
        status = optStringOrNull("status"),
        recentChapters = optJSONArray("chapters")?.objects().orEmpty().map { c ->
            Chapter(
                id = c.getString("id"),
                number = c.optStringOrNull("number"),
                title = c.optStringOrNull("title"),
                language = c.optString("language"),
                readableAt = Instant.ofEpochMilli(c.getLong("readableAt")),
                externalUrl = c.optStringOrNull("externalUrl"),
            )
        }.sortedWith(Chapter.NEWEST_FIRST),
        nextEstimate = optEpoch("nextEstimate"),
        intervalDays = if (has("intervalDays")) optDouble("intervalDays") else null,
        lastCheckedAt = optEpoch("lastCheckedAt"),
        hasNew = optBoolean("hasNew"),
        source = when (optString("source")) {
            "WEB", "TOONGOD" -> Source.WEB
            else -> Source.MANGADEX
        },
        pageUrl = optStringOrNull("pageUrl"),
        siteName = optStringOrNull("siteName") ?: if (optString("source") == "TOONGOD") "ToonGod" else null,
        lastReadNumber = if (has("lastReadNumber")) optDouble("lastReadNumber") else null,
        lastReadId = optStringOrNull("lastReadId"),
    )

    private fun loadSites(): List<Site> {
        val defaults = listOf(Site(MANGADEX_NAME, "https://mangadex.org")) + MadaraSource.DEFAULT_SITES
        val saved = runCatching {
            JSONArray(prefs.getString(KEY_SITES, null) ?: return defaults).objects().map {
                Site(it.getString("name"), it.getString("url"), it.optBoolean("enabled", true))
            }
        }.getOrNull() ?: return defaults
        return saved
    }

    private fun saveSites(list: List<Site>) {
        _sites.value = list
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("name", it.name).put("url", it.baseUrl).put("enabled", it.enabled)) }
        prefs.edit().putString(KEY_SITES, array.toString()).apply()
    }

    private fun JSONObject.optEpoch(key: String) = if (has(key)) Instant.ofEpochMilli(getLong(key)) else null

    private companion object {
        const val KEY_FOLLOWED = "followed"
        const val KEY_LANGUAGE = "language"
        const val KEY_SITES = "sites"
        const val MANGADEX_NAME = "MangaDex"
        const val MAX_STORED_CHAPTERS = 15
        const val MAX_WEB_LOOKUPS = 15
        const val MAX_WEB_PAGES = 3
        val STALE_AFTER: Duration = Duration.ofDays(30)
        val WEB_LOOKUP_INTERVAL: Duration = Duration.ofHours(12)
    }
}
