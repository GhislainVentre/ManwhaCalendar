package com.ghislainventre.manhwacalendar.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Séries suivies et chapitres en JSON, tels qu'enregistrés dans les SharedPreferences.
 *
 * La lecture est tolérante : une fiche ou un chapitre illisible est ignoré au lieu de faire
 * échouer toute la liste (qui serait alors réenregistrée vide, et toutes les séries perdues).
 */
internal object SeriesCodec {

    /** [raw] : la liste des fiches ; [chaptersOf] : les chapitres enregistrés à part pour une série. */
    fun decodeList(raw: String, chaptersOf: (String) -> String?): List<FollowedSeries> =
        JSONArray(raw).objects().mapNotNull { json ->
            runCatching {
                // Les anciennes versions gardaient les chapitres dans la fiche de la série.
                val chapters = json.optJSONArray("chapters")
                    ?: chaptersOf(json.getString("id"))?.let { runCatching { JSONArray(it) }.getOrNull() }
                json.toSeries(chapters)
            }.getOrNull()
        }

    fun encodeSeries(series: FollowedSeries): JSONObject = series.toJson()

    fun encodeChapters(chapters: List<Chapter>): JSONArray = chapters.toJson()

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
        title = optStringOrNull("title") ?: "Sans titre",
        coverUrl = optStringOrNull("coverUrl"),
        status = optStringOrNull("status"),
        recentChapters = chapters?.objects().orEmpty().mapNotNull { c ->
            runCatching {
                Chapter(
                    id = c.getString("id"),
                    number = c.optStringOrNull("number"),
                    title = c.optStringOrNull("title"),
                    language = c.optString("language"),
                    readableAt = Instant.ofEpochMilli(c.getLong("readableAt")),
                    externalUrl = c.optStringOrNull("externalUrl") ?: c.getString("id").takeIf { it.startsWith("http") },
                )
            }.getOrNull()
        }.sortedWith(Chapter.NEWEST_FIRST),
        nextEstimate = optEpoch("nextEstimate"),
        intervalDays = if (has("intervalDays")) optDouble("intervalDays").takeIf { it.isFinite() } else null,
        lastCheckedAt = optEpoch("lastCheckedAt"),
        hasNew = optBoolean("hasNew"),
        source = when (optString("source")) {
            "WEB", "TOONGOD" -> Source.WEB
            else -> Source.MANGADEX
        },
        pageUrl = optStringOrNull("pageUrl"),
        siteName = optStringOrNull("siteName") ?: if (optString("source") == "TOONGOD") "ToonGod" else null,
        lastReadNumber = if (has("lastReadNumber")) optDouble("lastReadNumber").takeIf { it.isFinite() } else null,
        lastReadId = optStringOrNull("lastReadId"),
        done = optBoolean("done"),
        alternates = optJSONArray("alternates")?.objects().orEmpty().mapNotNull { a ->
            val url = a.optStringOrNull("url") ?: return@mapNotNull null
            Mirror(a.optStringOrNull("site") ?: url, url)
        },
    )

    private fun JSONObject.optEpoch(key: String) =
        if (has(key) && !isNull(key)) runCatching { Instant.ofEpochMilli(getLong(key)) }.getOrNull() else null
}
