package com.ghislainventre.manhwacalendar.data

import android.content.Context
import com.ghislainventre.manhwacalendar.isFrenchUi
import com.ghislainventre.manhwacalendar.tr
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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
    /** Échecs de recherche consécutifs par site. */
    private val siteFailures = ConcurrentHashMap<String, Int>()

    private val _followed = MutableStateFlow(load())
    val followed: StateFlow<List<FollowedSeries>> = _followed.asStateFlow()

    private val _language = MutableStateFlow(
        runCatching { ChapterLanguage.valueOf(prefs.getString(KEY_LANGUAGE, null) ?: "") }
            // Premier lancement : chapitres en anglais sur un téléphone qui n'est pas en français.
            .getOrDefault(if (isFrenchUi) ChapterLanguage.FR_EN else ChapterLanguage.EN)
    )
    val language: StateFlow<ChapterLanguage> = _language.asStateFlow()

    private val _sites = MutableStateFlow(loadSites())
    /** Sites web interrogés par la recherche (MangaDex en premier). */
    val sites: StateFlow<List<Site>> = _sites.asStateFlow()

    /** Une source de recherche : MangaDex ou un site web activé. */
    class SearchSource(val name: String, val search: suspend (String) -> List<MangaSummary>)

    fun searchSources(): List<SearchSource> = webSitesForLanguage(_sites.value.filter { it.enabled }).map { site ->
        if (site.name == MANGADEX_NAME) {
            SearchSource(site.name) { q -> api.search(q, manhwaOnly = true) }
        } else {
            SearchSource(site.name) { q -> searchSite(site, q) }
        }
    }

    /**
     * Recherche sur un site ; après [MAX_SITE_FAILURES] échecs de suite (délai dépassé, site injoignable),
     * le site est désactivé pour ne plus ralentir les recherches. Il reste réactivable dans les réglages.
     */
    private suspend fun searchSite(site: Site, query: String): List<MangaSummary> = try {
        madara.search(site, query).also { siteFailures.remove(site.name) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val failures = siteFailures.merge(site.name, 1) { a, b -> a + b } ?: 1
        if (failures >= MAX_SITE_FAILURES) {
            siteFailures.remove(site.name)
            setSiteEnabled(site.name, false)
            throw IOException("${e.message} · " + tr("site désactivé, réactivable dans les réglages", "site disabled, you can turn it back on in Settings"))
        }
        throw e
    }

    /** Chapitres en anglais seulement : les sites français ne serviraient à rien (et pourraient être choisis au suivi). */
    private fun webSitesForLanguage(sites: List<Site>): List<Site> =
        if (_language.value == ChapterLanguage.EN) sites.filterNot { MadaraSource.isFrench(it.baseUrl) } else sites

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

    /** Suit [manga] ; [alternates] : la même série sur d'autres sites, utilisés si celui-ci ne répond plus. */
    suspend fun follow(manga: MangaSummary, alternates: List<MangaSummary> = emptyList()) {
        if (isFollowed(manga.id)) return
        val series = FollowedSeries(
            manga.id, manga.title, manga.coverUrl, manga.status,
            source = manga.source, pageUrl = manga.pageUrl, siteName = manga.siteName,
            alternates = alternates.mapNotNull { a -> a.pageUrl?.let { Mirror(a.sourceLabel, it) } },
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

    /** Range la série dans les terminées (plus de vérification ni de notification), ou la remet en cours. */
    fun setDone(id: String, done: Boolean) =
        mutate { list -> list.map { if (it.id == id) it.copy(done = done, hasNew = it.hasNew && !done) else it } }

    fun markAllSeen() = mutate { list -> list.map { it.copy(hasNew = false) } }

    fun setLanguage(language: ChapterLanguage) {
        _language.value = language
        val edit = prefs.edit().putString(KEY_LANGUAGE, language.name)
        // Nouvelle langue : les séries délaissées doivent pouvoir chercher tout de suite un site dans cette langue.
        prefs.all.keys.filter { it.startsWith(WEB_LOOKUP_PREFIX) }.forEach(edit::remove)
        edit.apply()
    }

    /** Met à jour toutes les séries et renvoie les nouveaux chapitres détectés. */
    suspend fun refreshAll(): List<NewChapterEvent> = refreshMutex.withLock {
        // Quelques séries à la fois : une par une, une liste avec des sites web lents prenait plusieurs minutes.
        val permits = Semaphore(PARALLEL_REFRESH)
        val outcomes = coroutineScope {
            _followed.value.filterNot { it.done }.map { series ->
                async {
                    permits.withPermit {
                        try {
                            Result.success(refreshOne(series, notifyNew = true))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure(e)
                        } finally {
                            delay(250) // limite de débit de l'API MangaDex
                        }
                    }
                }
            }.awaitAll()
        }
        val events = outcomes.mapNotNull { it.getOrNull() }
        val lastError = outcomes.lastOrNull { it.isFailure }?.exceptionOrNull()
        if (events.isEmpty() && lastError != null) throw lastError
        events
    }

    private suspend fun refreshOne(series: FollowedSeries, notifyNew: Boolean): NewChapterEvent? {
        val fetched = when (series.source) {
            Source.MANGADEX -> {
                val manga = runCatching { api.manga(series.id) }.getOrNull()
                val wanted = _language.value.codes
                val chapters = api.latestChapters(series.id, wanted)
                val status = manga?.status ?: series.status
                // Beaucoup de séries n'ont presque plus de chapitres sur MangaDex (retraits, équipes parties) :
                // on bascule alors sur un site de lecture qui en a de plus récents.
                val web = if (looksAbandoned(chapters, status)) findOnWeb(series, chapters) else null
                if (web != null) {
                    Fetched(manga?.title, manga?.coverUrl, status, web.page.chapters.sortedByDescending { it.readableAt }, web)
                } else {
                    // Rien en français, ni sur les sites : mieux vaut les chapitres anglais qu'une fiche vide
                    // (beaucoup de séries n'ont aucun chapitre français sur MangaDex). L'inverse n'aide pas un lecteur anglais.
                    val shown = if (chapters.isEmpty() && wanted == ChapterLanguage.FR.codes) {
                        api.latestChapters(series.id, ChapterLanguage.FR_EN.codes)
                    } else {
                        chapters
                    }
                    Fetched(manga?.title, manga?.coverUrl, status, shown)
                }
            }
            Source.WEB -> {
                val (page, mirror) = loadWebPage(series)
                Fetched(page.title, page.coverUrl, null, page.chapters.sortedByDescending { it.readableAt }, mirror = mirror)
            }
        }
        // Une réponse vide (site ou API en difficulté) ne doit pas effacer les chapitres déjà connus.
        val chapters = fetched.chapters.ifEmpty { series.recentChapters }
        val sorted = chapters.sortedWith(Chapter.NEWEST_FIRST)
        val status = fetched.status ?: series.status
        val estimate = ReleaseEstimator.estimate(chapters, finished = status == "completed" || status == "cancelled")

        val previous = series.latestChapter
        val latest = sorted.firstOrNull()
        // Comparaison par identifiant : les dates relatives (« 2 hours ago ») bougent à chaque lecture.
        // Par numéro quand il est connu : après un changement de site, le même chapitre a une autre adresse.
        val latestN = latest?.number?.toDoubleOrNull()
        val previousN = previous?.number?.toDoubleOrNull()
        val isNew = previous != null && latest != null && latest.id != previous.id &&
            (if (latestN != null && previousN != null) latestN > previousN else !latest.readableAt.isBefore(previous.readableAt))

        val current = _followed.value.firstOrNull { it.id == series.id } ?: return null
        val updated = current.copy(
            title = fetched.title ?: series.title,
            source = if (fetched.web != null) Source.WEB else series.source,
            pageUrl = fetched.web?.pageUrl ?: fetched.mirror?.url ?: series.pageUrl,
            siteName = fetched.web?.site?.name ?: fetched.mirror?.siteName ?: series.siteName,
            alternates = when {
                fetched.web != null -> fetched.web.alternates
                // Le site en panne passe en dernier secours : il reviendra peut-être.
                fetched.mirror != null -> current.alternates - fetched.mirror + Mirror(series.sourceLabel, series.url)
                else -> current.alternates
            },
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
        /** Site de secours utilisé parce que le site principal ne répondait plus. */
        val mirror: Mirror? = null,
    )

    private class WebMatch(
        val site: Site,
        val pageUrl: String,
        val page: MadaraSource.SeriesPage,
        val alternates: List<Mirror>,
    )

    /** Page de la série sur son site, ou sur le premier site de secours qui répond. */
    private suspend fun loadWebPage(series: FollowedSeries): Pair<MadaraSource.SeriesPage, Mirror?> {
        val failure = try {
            return madara.series(series.url) to null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
        for (mirror in series.alternates) {
            try {
                return madara.series(mirror.url) to mirror
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Site suivant.
            }
        }
        throw failure
    }

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
        // Gardée d'un lancement à l'autre : sinon chaque ouverture de l'appli relançait ces recherches sur tous les sites.
        val now = Instant.now()
        val last = prefs.getLong(WEB_LOOKUP_PREFIX + series.id, 0L)
        if (Instant.ofEpochMilli(last).isAfter(now.minus(WEB_LOOKUP_INTERVAL))) return null
        prefs.edit().putLong(WEB_LOOKUP_PREFIX + series.id, now.toEpochMilli()).apply()

        val titles = (orEmptyOnError { api.titles(series.id) } + series.title).distinctBy(TitleMatch::normalize)
        // En français, les sites français passent en premier : leur page est retenue si elle va assez loin.
        val french = "fr" in _language.value.codes && "en" !in _language.value.codes
        val sites = webSitesForLanguage(_sites.value.filter { it.enabled && it.name != MANGADEX_NAME })
            .sortedByDescending { french && MadaraSource.isFrench(it.baseUrl) }
        val hits = coroutineScope {
            sites.map { site ->
                async {
                    orEmptyOnError { madara.search(site, titles.first(), background = true) }
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
            if (top > knownTop) {
                val others = hits.filter { it.second != url }.map { (s, u) -> Mirror(s.name, u) }
                return WebMatch(site, url, page, others)
            }
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

    // Les chapitres de chaque série ont leur propre clé, réécrite seulement quand la liste change :
    // cocher « lu » ne réécrit que la petite fiche des séries, même avec des centaines de chapitres.

    /** Dernière liste de chapitres enregistrée pour chaque série. */
    private val savedChapters = mutableMapOf<String, List<Chapter>>()

    private fun load(): List<FollowedSeries> = runCatching {
        JSONArray(prefs.getString(KEY_FOLLOWED, "[]")).objects().map { json ->
            // Les anciennes versions gardaient les chapitres dans la fiche de la série.
            val chapters = json.optJSONArray("chapters")
                ?: prefs.getString(chaptersKey(json.getString("id")), null)?.let(::JSONArray)
            json.toSeries(chapters)
        }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun save(list: List<FollowedSeries>) {
        val edit = prefs.edit()
        val array = JSONArray()
        list.forEach { series ->
            array.put(series.toJson())
            if (savedChapters[series.id] !== series.recentChapters) {
                edit.putString(chaptersKey(series.id), series.recentChapters.toJson().toString())
                savedChapters[series.id] = series.recentChapters
            }
        }
        // Chapitres des séries retirées.
        val keep = list.map { chaptersKey(it.id) }.toSet()
        prefs.all.keys.filter { it.startsWith(CHAPTERS_PREFIX) && it !in keep }.forEach(edit::remove)
        savedChapters.keys.retainAll(list.map { it.id }.toSet())
        edit.putString(KEY_FOLLOWED, array.toString()).apply()
    }

    private fun chaptersKey(seriesId: String) = CHAPTERS_PREFIX + seriesId

    private fun List<Chapter>.toJson() = JSONArray().apply {
        forEach { c ->
            put(JSONObject().apply {
                put("id", c.id)
                putOpt("number", c.number)
                putOpt("title", c.title)
                put("language", c.language)
                put("readableAt", c.readableAt.toEpochMilli())
                // Sur les sites web, l'adresse est déjà l'identifiant.
                if (c.externalUrl != c.id) putOpt("externalUrl", c.externalUrl)
            })
        }
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
        if (done) put("done", true)
        if (alternates.isNotEmpty()) {
            put("alternates", JSONArray().apply {
                alternates.forEach { put(JSONObject().put("site", it.siteName).put("url", it.url)) }
            })
        }
    }

    private fun JSONObject.toSeries(chapters: JSONArray?) = FollowedSeries(
        id = getString("id"),
        title = getString("title"),
        coverUrl = optStringOrNull("coverUrl"),
        status = optStringOrNull("status"),
        recentChapters = chapters?.objects().orEmpty().map { c ->
            Chapter(
                id = c.getString("id"),
                number = c.optStringOrNull("number"),
                title = c.optStringOrNull("title"),
                language = c.optString("language"),
                readableAt = Instant.ofEpochMilli(c.getLong("readableAt")),
                externalUrl = c.optStringOrNull("externalUrl") ?: c.getString("id").takeIf { it.startsWith("http") },
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
        done = optBoolean("done"),
        alternates = optJSONArray("alternates")?.objects().orEmpty().map { Mirror(it.getString("site"), it.getString("url")) },
    )

    private fun loadSites(): List<Site> {
        val defaults = listOf(Site(MANGADEX_NAME, "https://mangadex.org")) + MadaraSource.DEFAULT_SITES
        val saved = runCatching {
            JSONArray(prefs.getString(KEY_SITES, null) ?: return defaults).objects().map {
                Site(it.getString("name"), it.getString("url"), it.optBoolean("enabled", true))
            }
        }.getOrNull() ?: return defaults
        if (prefs.getInt(KEY_SITES_VERSION, 0) >= SITES_VERSION) return saved
        // Nouvelle liste par défaut : on ajoute les nouveaux sites et on retire les sites morts,
        // sans toucher aux sites ajoutés ou désactivés par l'utilisateur.
        val kept = saved.filterNot { it.baseUrl.trimEnd('/') in MadaraSource.REMOVED_SITES }
        val added = defaults.filter { d -> kept.none { it.baseUrl.equals(d.baseUrl, ignoreCase = true) || it.name == d.name } }
        return (kept + added).also(::persistSites)
    }

    private fun saveSites(list: List<Site>) {
        _sites.value = list
        persistSites(list)
    }

    private fun persistSites(list: List<Site>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("name", it.name).put("url", it.baseUrl).put("enabled", it.enabled)) }
        prefs.edit().putString(KEY_SITES, array.toString()).putInt(KEY_SITES_VERSION, SITES_VERSION).apply()
    }

    private fun JSONObject.optEpoch(key: String) = if (has(key)) Instant.ofEpochMilli(getLong(key)) else null

    private companion object {
        const val KEY_FOLLOWED = "followed"
        const val KEY_LANGUAGE = "language"
        const val CHAPTERS_PREFIX = "chapters_"
        const val WEB_LOOKUP_PREFIX = "web_lookup_"
        const val KEY_SITES = "sites"
        const val KEY_SITES_VERSION = "sites_version"
        /** À augmenter quand la liste des sites par défaut change. */
        const val SITES_VERSION = 3
        const val MAX_SITE_FAILURES = 2
        const val PARALLEL_REFRESH = 3
        const val MANGADEX_NAME = "MangaDex"
        /** Toute la série, pour qui la commence ; la limite ne sert qu'aux séries démesurées. */
        const val MAX_STORED_CHAPTERS = 1500
        const val MAX_WEB_LOOKUPS = 15
        const val MAX_WEB_PAGES = 3
        val STALE_AFTER: Duration = Duration.ofDays(30)
        val WEB_LOOKUP_INTERVAL: Duration = Duration.ofHours(12)
    }
}
