package com.ghislainventre.manhwacalendar.data

import java.time.Instant

/** Origine des chapitres : l'API MangaDex, ou un site web de lecture (thème WordPress « Madara »). */
enum class Source { MANGADEX, WEB }

/** Site de lecture interrogé par la recherche. */
data class Site(val name: String, val baseUrl: String, val enabled: Boolean = true)

/** Résultat de recherche. Pour un site web, [pageUrl] est la page de la série. */
data class MangaSummary(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val status: String?,
    val originalLanguage: String?,
    val year: Int?,
    val source: Source = Source.MANGADEX,
    val pageUrl: String? = null,
    val latestChapter: String? = null,
    val siteName: String? = null,
    /** Genres et thèmes MangaDex (vide pour les sites web). */
    val tags: List<Tag> = emptyList(),
) {
    val sourceLabel: String get() = siteName ?: "MangaDex"
}

/** Genre ou thème MangaDex ; [name] est le nom anglais fourni par l'API. */
data class Tag(val id: String, val name: String)

/** Série proposée, avec les genres en commun qui l'expliquent (du plus apprécié au moins apprécié). */
data class Recommendation(val manga: MangaSummary, val reasons: List<String>)

/** Un chapitre publié. */
data class Chapter(
    val id: String,
    val number: String?,
    val title: String?,
    val language: String,
    val readableAt: Instant,
    val externalUrl: String?,
) {
    val url: String get() = externalUrl ?: if (id.startsWith("http")) id else "https://mangadex.org/chapter/$id"
}

/** Série suivie par l'utilisateur, avec son état de sortie connu. */
data class FollowedSeries(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val status: String?,
    val recentChapters: List<Chapter> = emptyList(),
    val nextEstimate: Instant? = null,
    val intervalDays: Double? = null,
    val lastCheckedAt: Instant? = null,
    val hasNew: Boolean = false,
    val source: Source = Source.MANGADEX,
    val pageUrl: String? = null,
    val siteName: String? = null,
) {
    val sourceLabel: String get() = siteName ?: "MangaDex"
    val latestChapter: Chapter? get() = recentChapters.firstOrNull()
    val isFinished: Boolean get() = status == "completed" || status == "cancelled"
    val url: String get() = pageUrl ?: "https://mangadex.org/title/$id"
}

/** Un nouveau chapitre détecté lors d'une vérification. */
data class NewChapterEvent(val series: FollowedSeries, val chapter: Chapter)

enum class ChapterLanguage(val codes: List<String>, val label: String) {
    FR(listOf("fr"), "Français"),
    EN(listOf("en"), "Anglais"),
    FR_EN(listOf("fr", "en"), "Français + anglais"),
}
