package com.ghislainventre.manhwacalendar.data

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Séries suivies, stockées localement, et leur mise à jour depuis MangaDex. */
class SeriesRepository(context: Context, private val api: MangaDexApi = MangaDexApi()) {

    private val prefs = context.getSharedPreferences("manhwa_calendar", Context.MODE_PRIVATE)
    private val refreshMutex = Mutex()

    private val _followed = MutableStateFlow(load())
    val followed: StateFlow<List<FollowedSeries>> = _followed.asStateFlow()

    private val _language = MutableStateFlow(
        runCatching { ChapterLanguage.valueOf(prefs.getString(KEY_LANGUAGE, null) ?: "") }
            .getOrDefault(ChapterLanguage.FR_EN)
    )
    val language: StateFlow<ChapterLanguage> = _language.asStateFlow()

    suspend fun search(query: String, manhwaOnly: Boolean) = api.search(query, manhwaOnly)

    fun isFollowed(id: String) = _followed.value.any { it.id == id }

    suspend fun follow(manga: MangaSummary) {
        if (isFollowed(manga.id)) return
        val series = FollowedSeries(manga.id, manga.title, manga.coverUrl, manga.status)
        mutate { it + series }
        runCatching { refreshOne(series, notifyNew = false) }
    }

    fun unfollow(id: String) = mutate { list -> list.filterNot { it.id == id } }

    fun markSeen(id: String) = mutate { list -> list.map { if (it.id == id) it.copy(hasNew = false) else it } }

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
            } catch (e: Exception) {
                lastError = e
            }
            delay(250) // limite de débit de l'API MangaDex
        }
        if (events.isEmpty() && lastError != null && _followed.value.isNotEmpty()) throw lastError
        events
    }

    private suspend fun refreshOne(series: FollowedSeries, notifyNew: Boolean): NewChapterEvent? {
        val manga = runCatching { api.manga(series.id) }.getOrNull()
        val chapters = api.latestChapters(series.id, _language.value.codes)
        val status = manga?.status ?: series.status
        val estimate = ReleaseEstimator.estimate(chapters, finished = status == "completed" || status == "cancelled")

        val previous = series.latestChapter
        val latest = chapters.firstOrNull()
        val isNew = previous != null && latest != null && latest.readableAt.isAfter(previous.readableAt)

        val updated = (_followed.value.firstOrNull { it.id == series.id } ?: return null).copy(
            title = manga?.title ?: series.title,
            coverUrl = manga?.coverUrl ?: series.coverUrl,
            status = status,
            recentChapters = chapters.distinctBy { it.number ?: it.id }.take(MAX_STORED_CHAPTERS),
            nextEstimate = estimate.next,
            intervalDays = estimate.intervalDays,
            lastCheckedAt = Instant.now(),
            hasNew = series.hasNew || isNew,
        )
        mutate { list -> list.map { if (it.id == series.id) updated else it } }
        return if (isNew && notifyNew) NewChapterEvent(updated, latest!!) else null
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
        },
        nextEstimate = optEpoch("nextEstimate"),
        intervalDays = if (has("intervalDays")) optDouble("intervalDays") else null,
        lastCheckedAt = optEpoch("lastCheckedAt"),
        hasNew = optBoolean("hasNew"),
    )

    private fun JSONObject.optEpoch(key: String) = if (has(key)) Instant.ofEpochMilli(getLong(key)) else null

    private companion object {
        const val KEY_FOLLOWED = "followed"
        const val KEY_LANGUAGE = "language"
        const val MAX_STORED_CHAPTERS = 15
    }
}
