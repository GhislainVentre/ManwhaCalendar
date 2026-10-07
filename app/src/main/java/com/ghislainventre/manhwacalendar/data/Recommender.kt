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
