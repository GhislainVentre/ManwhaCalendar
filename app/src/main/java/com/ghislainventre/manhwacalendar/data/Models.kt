package com.ghislainventre.manhwacalendar.data

import java.time.Instant

/** Site où les chapitres sont vérifiés. */
enum class Source(val label: String) {
    MANGADEX("MangaDex"),
    TOONGOD("ToonGod"),
}

/** Résultat de recherche. Pour ToonGod, [pageUrl] est la page de la série. */
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
)

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
) {
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
