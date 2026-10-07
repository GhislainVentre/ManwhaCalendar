package com.ghislainventre.manhwacalendar.data

import java.time.Duration
import java.time.Instant

/**
 * Estime la date du prochain chapitre à partir du rythme de publication récent.
 *
 * Les chapitres publiés à quelques heures d'intervalle (mise en ligne groupée) comptent
 * comme une seule sortie, puis on prend la médiane des derniers intervalles.
 */
object ReleaseEstimator {
    private val SAME_RELEASE_WINDOW: Duration = Duration.ofHours(12)
    private const val MAX_INTERVALS = 6
    private const val MAX_INTERVAL_DAYS = 45.0
    private const val MAX_MISSED_RELEASES = 2

    data class Estimate(val next: Instant?, val intervalDays: Double?)

    fun estimate(chapters: List<Chapter>, finished: Boolean, now: Instant = Instant.now()): Estimate {
        val releases = releaseDates(chapters)
        if (releases.size < 2) return Estimate(null, null)

        val intervals = releases.zipWithNext { a, b -> Duration.between(a, b).toMinutes() / (60.0 * 24) }
            .takeLast(MAX_INTERVALS)
        val interval = median(intervals)
        if (finished || interval <= 0.0 || interval > MAX_INTERVAL_DAYS) return Estimate(null, interval)

        val step = Duration.ofMinutes((interval * 24 * 60).toLong())
        var next = releases.last().plus(step)
        var missed = 0
        // Si la date estimée est passée, on avance d'un cycle : léger retard toléré, sinon pause probable.
        while (next.isBefore(now)) {
            if (++missed > MAX_MISSED_RELEASES) return Estimate(null, interval)
            next = next.plus(step)
        }
        return Estimate(next, interval)
    }

    /** Dates de sortie distinctes, de la plus ancienne à la plus récente. */
    internal fun releaseDates(chapters: List<Chapter>): List<Instant> {
        val firstSeen = chapters
            .filter { it.number != null }
            .groupBy { it.number!!.trim() }
            .map { (_, sameNumber) -> sameNumber.minOf { it.readableAt } }
            .sorted()
        val releases = mutableListOf<Instant>()
        for (date in firstSeen) {
            val last = releases.lastOrNull()
            if (last == null || Duration.between(last, date) > SAME_RELEASE_WINDOW) releases += date
        }
        return releases
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
