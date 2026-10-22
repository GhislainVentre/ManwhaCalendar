package com.ghislainventre.manhwacalendar.data

import kotlin.math.sqrt

/**
 * Recommandations par genres : on compte les genres des séries suivies, puis on classe
 * des séries populaires selon les genres qu'elles partagent avec ce profil.
 */
object Recommender {

    /** Nombre de séries suivies portant chaque genre. */
    fun profile(liked: List<MangaSummary>): Map<Tag, Int> =
        liked.flatMap { it.tags.distinct() }.groupingBy { it }.eachCount()

    /** Les genres les plus présents, pour interroger MangaDex. */
    fun topTags(profile: Map<Tag, Int>, count: Int = 6): List<Tag> =
        profile.entries.sortedByDescending { it.value }.take(count).map { it.key }

    /**
     * Classe les [candidates] (supposés déjà triés par popularité) : somme des poids des genres
     * en commun, divisée par la racine du nombre de genres pour ne pas favoriser les séries qui
     * en cumulent beaucoup. À score égal, l'ordre de popularité est conservé.
     */
    fun rank(
        candidates: List<MangaSummary>,
        profile: Map<Tag, Int>,
        exclude: Set<String>,
        max: Int = 20,
    ): List<Recommendation> {
        val weights = profile.mapKeys { it.key.id }
        return candidates
            .filter { it.id !in exclude }
            .distinctBy { it.id }
            .mapNotNull { manga ->
                val shared = manga.tags.distinctBy { it.id }.filter { it.id in weights }
                if (shared.isEmpty()) return@mapNotNull null
                val score = shared.sumOf { weights.getValue(it.id) } / sqrt(manga.tags.size.toDouble())
                val reasons = shared.sortedByDescending { weights.getValue(it.id) }.take(3).map { it.name }
                score to Recommendation(manga, reasons)
            }
            .sortedByDescending { it.first }
            .take(max)
            .map { it.second }
    }
}

/** Tranche de longueur d'une série, en chapitres traduits ([max] inclus ; null : sans limite). */
enum class ChapterRange(val min: Int, val max: Int?) {
    SHORT(0, 49),
    MEDIUM(50, 99),
    LONG(100, 199),
    VERY_LONG(200, null),
    ;

    operator fun contains(count: Int) = count >= min && (max == null || count <= max)
}

/**
 * Filtres de « Pour toi » : statuts MangaDex acceptés (vide : tous) et longueur voulue (null : toutes).
 * Une série dont on ne connaît pas encore le nombre de chapitres est écartée tant qu'une longueur est demandée.
 */
data class RecommendationFilter(val statuses: Set<String> = emptySet(), val chapters: ChapterRange? = null) {
    val isActive: Boolean get() = statuses.isNotEmpty() || chapters != null

    fun matchesStatus(manga: MangaSummary) = statuses.isEmpty() || manga.status in statuses

    fun matches(manga: MangaSummary, chapterCount: Int?): Boolean =
        matchesStatus(manga) && (chapters == null || (chapterCount != null && chapterCount in chapters))

    companion object {
        /** Statuts proposés, dans l'ordre des puces. */
        val STATUSES = listOf("ongoing", "completed", "cancelled")
    }
}
