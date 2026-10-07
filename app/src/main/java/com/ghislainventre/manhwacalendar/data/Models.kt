package com.ghislainventre.manhwacalendar.data

import java.time.Instant

/** Résultat de recherche MangaDex. */
data class MangaSummary(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val status: String?,
    val originalLanguage: String?,
    val year: Int?,
)

/** Un chapitre publié (traduit) sur MangaDex. */
data class Chapter(
    val id: String,
    val number: String?,
    val title: String?,
    val language: String,
    val readableAt: Instant,
    val externalUrl: String?,
) {
    val url: String get() = externalUrl ?: "https://mangadex.org/chapter/$id"
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
) {
    val latestChapter: Chapter? get() = recentChapters.firstOrNull()
    val isFinished: Boolean get() = status == "completed" || status == "cancelled"
    val url: String get() = "https://mangadex.org/title/$id"
}

/** Un nouveau chapitre détecté lors d'une vérification. */
data class NewChapterEvent(val series: FollowedSeries, val chapter: Chapter)

enum class ChapterLanguage(val codes: List<String>, val label: String) {
    FR(listOf("fr"), "Français"),
    EN(listOf("en"), "Anglais"),
    FR_EN(listOf("fr", "en"), "Français + anglais"),
}
