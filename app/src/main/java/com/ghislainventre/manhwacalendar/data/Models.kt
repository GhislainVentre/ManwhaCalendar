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
    /** Dernier chapitre marqué comme lu : tous les chapitres de numéro inférieur ou égal sont lus. */
    val lastReadNumber: Double? = null,
    /** Identifiant du dernier chapitre marqué comme lu, pour les chapitres sans numéro. */
    val lastReadId: String? = null,
) {
    val sourceLabel: String get() = siteName ?: "MangaDex"
    val latestChapter: Chapter? get() = recentChapters.firstOrNull()

    /** Vrai dès qu'un chapitre a été marqué comme lu au moins une fois. */
    val tracksReading: Boolean get() = lastReadNumber != null || lastReadId != null

    fun isRead(chapter: Chapter): Boolean {
        val n = chapter.number?.toDoubleOrNull()
        return if (n != null && lastReadNumber != null) n <= lastReadNumber else chapter.id == lastReadId
    }

    /** Chapitres connus pas encore lus ; null si l'utilisateur n'a jamais rien marqué comme lu. */
    val unreadCount: Int? get() = if (tracksReading) recentChapters.count { !isRead(it) } else null

    /** Le plus ancien chapitre connu non lu : celui à lire ensuite. */
    val nextToRead: Chapter? get() = if (tracksReading) recentChapters.lastOrNull { !isRead(it) } else null

    /** Marque [chapter] et tous les précédents comme lus, ou le démarque (avec les suivants). */
    fun withRead(chapter: Chapter, read: Boolean): FollowedSeries {
        val n = chapter.number?.toDoubleOrNull()
        if (read) {
            return copy(
                lastReadNumber = n ?: lastReadNumber,
                lastReadId = chapter.id,
                hasNew = hasNew && chapter.id != latestChapter?.id,
            )
        }
        if (n == null) return copy(lastReadId = null)
        // Le dernier lu devient le chapitre connu juste avant celui-ci.
        val previous = recentChapters
            .filter { (it.number?.toDoubleOrNull() ?: Double.MAX_VALUE) < n }
            .maxByOrNull { it.number!!.toDouble() }
        return copy(lastReadNumber = previous?.number?.toDouble(), lastReadId = previous?.id)
    }

    /** Marque tous les chapitres connus comme lus. */
    fun withAllRead(): FollowedSeries {
        val newest = recentChapters.maxByOrNull { it.number?.toDoubleOrNull() ?: Double.MIN_VALUE } ?: return this
        return withRead(newest, true).copy(hasNew = false)
    }
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
