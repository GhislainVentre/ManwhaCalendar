package com.ghislainventre.manhwacalendar.data

/** Un résultat de recherche, avec le temps de réponse du site et sa position dans la liste du site. */
data class SeriesHit(val manga: MangaSummary, val responseMs: Long, val rank: Int)

/**
 * Une même série trouvée sur un ou plusieurs sites. [best] est le site qui va le plus loin
 * (dernier chapitre le plus élevé), puis le plus rapide ; les autres servent de secours.
 */
class SeriesGroup(val key: String, hits: List<SeriesHit>) {
    val hits: List<SeriesHit> = hits.sortedWith(SearchGrouping.PREFERENCE)
    val best: MangaSummary get() = hits.first().manga
    val title: String get() = best.title
    val coverUrl: String? get() = hits.firstNotNullOfOrNull { it.manga.coverUrl }
    val latestNumber: Double? get() = SearchGrouping.latestNumber(best)
    /** Pages des autres sites, de la meilleure à la moins bonne. */
    val alternates: List<MangaSummary> get() = hits.drop(1).map { it.manga }.filter { it.source == Source.WEB }
}

/** Regroupe les résultats de tous les sites par série, pour ne proposer chaque série qu'une fois. */
object SearchGrouping {

    fun latestNumber(manga: MangaSummary): Double? =
        manga.latestChapter?.let(MadaraDates::chapterNumber)?.toDoubleOrNull()

    val PREFERENCE: Comparator<SeriesHit> =
        compareByDescending<SeriesHit> { latestNumber(it.manga) ?: -1.0 }.thenBy { it.responseMs }

    /** [results] : pour chaque site qui a répondu, ses résultats et son temps de réponse en millisecondes. */
    fun group(results: List<Pair<List<MangaSummary>, Long>>): List<SeriesGroup> {
        val byKey = LinkedHashMap<String, MutableList<SeriesHit>>()
        results.forEach { (items, ms) ->
            items.forEachIndexed { rank, manga ->
                val normalized = TitleMatch.normalize(manga.title)
                val key = if (normalized.length >= MIN_KEY_LENGTH) normalized else manga.id
                byKey.getOrPut(key) { mutableListOf() } += SeriesHit(manga, ms, rank)
            }
        }
        // Les séries présentes sur le plus de sites d'abord : ce sont presque toujours celles cherchées.
        return byKey.map { (key, hits) -> SeriesGroup(key, hits.distinctBy { it.manga.id }) }
            .sortedWith(compareByDescending<SeriesGroup> { it.hits.size }.thenBy { g -> g.hits.minOf { it.rank } })
    }

    private const val MIN_KEY_LENGTH = 4
}
