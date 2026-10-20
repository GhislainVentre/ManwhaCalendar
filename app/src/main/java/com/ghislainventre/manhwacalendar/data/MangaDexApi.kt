package com.ghislainventre.manhwacalendar.data

import com.ghislainventre.manhwacalendar.isFrenchUi
import com.ghislainventre.manhwacalendar.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime

/** Client minimal de l'API publique MangaDex (https://api.mangadex.org/docs/). */
class MangaDexApi {

    suspend fun search(query: String, manhwaOnly: Boolean): List<MangaSummary> {
        val params = mutableListOf(
            "title" to query,
            "limit" to "20",
            "includes[]" to "cover_art",
            "order[relevance]" to "desc",
            "contentRating[]" to "safe",
            "contentRating[]" to "suggestive",
        )
        if (manhwaOnly) params += "originalLanguage[]" to "ko"
        val json = get("/manga", params)
        return json.getJSONArray("data").objects().map(::parseManga)
    }

    suspend fun manga(id: String): MangaSummary {
        val json = get("/manga/$id", listOf("includes[]" to "cover_art"))
        return parseManga(json.getJSONObject("data"))
    }

    /**
     * Titres anglais et romanisés d'une série (titre principal puis variantes), pour la chercher sur les sites,
     * puis ses titres français : certains sites français affichent la série sous son nom français.
     */
    suspend fun titles(id: String): List<String> {
        val attrs = get("/manga/$id", emptyList()).getJSONObject("data").getJSONObject("attributes")
        val all = listOfNotNull(attrs.optJSONObject("title")) + attrs.optJSONArray("altTitles")?.objects().orEmpty()
        return listOf("en", "ko-ro", "ja-ro", "fr").flatMap { lang -> all.mapNotNull { it.optStringOrNull(lang) } }.distinct()
    }

    /** Fiches de plusieurs séries en une requête (100 au plus), avec leurs genres. */
    suspend fun mangas(ids: List<String>): List<MangaSummary> {
        if (ids.isEmpty()) return emptyList()
        val params = mutableListOf("limit" to "100", "includes[]" to "cover_art")
        listOf("safe", "suggestive", "erotica", "pornographic").forEach { params += "contentRating[]" to it }
        ids.distinct().take(100).forEach { params += "ids[]" to it }
        return get("/manga", params).getJSONArray("data").objects().map(::parseManga)
    }

    /** Manhwa les plus suivis ayant au moins un des genres donnés et des chapitres dans l'une des langues. */
    suspend fun popularWithTags(tagIds: List<String>, languages: List<String>): List<MangaSummary> {
        val params = mutableListOf(
            "limit" to "100",
            "includes[]" to "cover_art",
            "includedTagsMode" to "OR",
            "order[followedCount]" to "desc",
            "originalLanguage[]" to "ko",
            "contentRating[]" to "safe",
            "contentRating[]" to "suggestive",
            "hasAvailableChapters" to "true",
        )
        tagIds.forEach { params += "includedTags[]" to it }
        languages.forEach { params += "availableTranslatedLanguage[]" to it }
        return get("/manga", params).getJSONArray("data").objects().map(::parseManga)
    }

    /**
     * Tous les chapitres traduits (jusqu'à [max]), du plus récent au plus ancien, par pages de 500 :
     * il faut toute la liste pour qui commence une série.
     */
    suspend fun latestChapters(mangaId: String, languages: List<String>, max: Int = 2000): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        var offset = 0
        while (offset < max) {
            val params = mutableListOf(
                "limit" to FEED_PAGE.toString(),
                "offset" to offset.toString(),
                "order[readableAt]" to "desc",
            )
            languages.forEach { params += "translatedLanguage[]" to it }
            val json = get("/manga/$mangaId/feed", params)
            val page = json.getJSONArray("data").objects()
            chapters += page.mapNotNull(::parseChapter)
            offset += FEED_PAGE
            if (page.size < FEED_PAGE || offset >= json.optInt("total")) break
        }
        return chapters
    }

    private fun parseManga(obj: JSONObject): MangaSummary {
        val id = obj.getString("id")
        val attrs = obj.getJSONObject("attributes")
        val fileName = obj.optJSONArray("relationships")?.objects()
            ?.firstOrNull { it.optString("type") == "cover_art" }
            ?.optJSONObject("attributes")?.optString("fileName")
            ?.takeIf { it.isNotEmpty() }
        return MangaSummary(
            id = id,
            title = pickTitle(attrs),
            coverUrl = fileName?.let { "https://uploads.mangadex.org/covers/$id/$it.256.jpg" },
            status = attrs.optStringOrNull("status"),
            originalLanguage = attrs.optStringOrNull("originalLanguage"),
            year = if (attrs.isNull("year")) null else attrs.optInt("year"),
            tags = parseTags(attrs),
        )
    }

    /** Genres et thèmes seulement : le format (« Long Strip », « Full Color »…) est commun à tous les manhwa. */
    private fun parseTags(attrs: JSONObject): List<Tag> =
        attrs.optJSONArray("tags")?.objects().orEmpty().mapNotNull { tag ->
            val tagAttrs = tag.optJSONObject("attributes") ?: return@mapNotNull null
            if (tagAttrs.optString("group") !in setOf("genre", "theme")) return@mapNotNull null
            val name = tagAttrs.optJSONObject("name")?.optStringOrNull("en") ?: return@mapNotNull null
            Tag(tag.getString("id"), name)
        }

    private fun pickTitle(attrs: JSONObject): String {
        val titles = attrs.optJSONObject("title") ?: JSONObject()
        val alt = attrs.optJSONArray("altTitles")?.objects().orEmpty()
        // Le titre dans la langue du téléphone : un lecteur anglais ne reconnaît pas le titre français.
        for (lang in if (isFrenchUi) listOf("fr", "en") else listOf("en")) {
            titles.optStringOrNull(lang)?.let { return it }
            alt.firstNotNullOfOrNull { it.optStringOrNull(lang) }?.let { return it }
        }
        return titles.keys().asSequence().firstNotNullOfOrNull { titles.optStringOrNull(it) } ?: tr("Sans titre", "Untitled")
    }

    private fun parseChapter(obj: JSONObject): Chapter? {
        val attrs = obj.getJSONObject("attributes")
        val date = attrs.optStringOrNull("readableAt") ?: attrs.optStringOrNull("publishAt") ?: return null
        return Chapter(
            id = obj.getString("id"),
            number = attrs.optStringOrNull("chapter"),
            title = attrs.optStringOrNull("title")?.takeIf { it.isNotBlank() },
            language = attrs.optString("translatedLanguage"),
            // Une date mal formée ne doit pas faire échouer toute la liste : le chapitre est ignoré.
            readableAt = runCatching { OffsetDateTime.parse(date).toInstant() }.getOrNull() ?: return null,
            externalUrl = attrs.optStringOrNull("externalUrl"),
        )
    }

    /**
     * Une requête ; si MangaDex limite le débit (429) ou est momentanément indisponible (503),
     * une seconde tentative après la pause qu'il demande.
     */
    private suspend fun get(path: String, params: List<Pair<String, String>>): JSONObject {
        val query = params.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
        val url = "$BASE_URL$path?$query"
        repeat(MAX_ATTEMPTS - 1) {
            when (val response = request(url)) {
                is Response.Ok -> return response.json
                is Response.RetryAfter -> delay(response.ms)
            }
        }
        return when (val response = request(url)) {
            is Response.Ok -> response.json
            is Response.RetryAfter -> throw IOException(tr("MangaDex est surchargé, réessaie plus tard", "MangaDex is overloaded, try again later"))
        }
    }

    private sealed interface Response {
        class Ok(val json: JSONObject) : Response
        class RetryAfter(val ms: Long) : Response
    }

    private suspend fun request(url: String): Response = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            when (val code = connection.responseCode) {
                in 200..299 -> Response.Ok(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
                429, 503 -> Response.RetryAfter(
                    (connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull() ?: 2L).coerceIn(1L, 10L) * 1_000
                )
                else -> throw IOException(tr("MangaDex a répondu $code", "MangaDex returned $code"))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val BASE_URL = "https://api.mangadex.org"
        const val USER_AGENT = "ManhwaCalendar/1.0 (Android)"
        const val FEED_PAGE = 500
        const val MAX_ATTEMPTS = 2
    }
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
