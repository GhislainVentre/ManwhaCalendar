package com.ghislainventre.manhwacalendar.ui

import com.ghislainventre.manhwacalendar.data.FollowedSeries
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dayFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
private val shortFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)
private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.FRENCH)

fun Instant.localDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

private fun String.capitalized() = replaceFirstChar { it.titlecase(Locale.FRENCH) }

fun dayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Aujourd'hui"
    today.plusDays(1) -> "Demain"
    today.minusDays(1) -> "Hier"
    else -> longDate(date)
}

/** « Jeudi 9 octobre ». */
fun longDate(date: LocalDate): String = dayFormatter.format(date).capitalized()

/** « Jeu », sans le point d'abréviation. */
fun weekdayShort(date: LocalDate): String = weekdayFormatter.format(date).removeSuffix(".").capitalized()

fun shortDate(instant: Instant): String = shortFormatter.format(instant.localDate())

/** « Aujourd'hui », « Demain », « Dans 3 jours », « Dans 2 semaines ». */
fun untilLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days <= 0L -> "Aujourd'hui"
        days == 1L -> "Demain"
        days < 7L -> "Dans $days jours"
        days < 14L -> "Dans 1 semaine"
        else -> "Dans ${days / 7} semaines"
    }
}

/** Version courte pour les badges : « Demain », « Dans 3 j », « 12 oct. ». */
fun shortUntil(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days <= 0L -> "Aujourd'hui"
        days == 1L -> "Demain"
        days < 7L -> "Dans $days j"
        else -> dayMonthFormatter.format(date)
    }
}

/** Légende d'un jour de l'agenda : relative dans la semaine, sinon la date complète. */
fun dayCaption(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    if (ChronoUnit.DAYS.between(today, date) < 7L) untilLabel(date, today) else longDate(date)

fun relativePast(instant: Instant, now: Instant = Instant.now()): String {
    val d = Duration.between(instant, now)
    return when {
        d.toMinutes() < 60 -> "il y a ${d.toMinutes().coerceAtLeast(1)} min"
        d.toHours() < 24 -> "il y a ${d.toHours()} h"
        d.toDays() < 30 -> "il y a ${d.toDays()} j"
        else -> "le ${shortDate(instant)}"
    }
}

fun rhythm(intervalDays: Double?): String? = when {
    intervalDays == null -> null
    intervalDays < 1.5 -> "quotidien"
    intervalDays in 5.5..8.5 -> "hebdomadaire"
    intervalDays in 12.0..16.0 -> "toutes les 2 semaines"
    intervalDays in 26.0..35.0 -> "mensuel"
    else -> "tous les ${intervalDays.toInt()} jours env."
}

fun plural(count: Int, singular: String, plural: String) = "$count ${if (count > 1) plural else singular}"

fun chapterLabel(number: String?) = number?.let { "Ch. $it" } ?: "Oneshot"

fun nextChapterLabel(s: FollowedSeries): String {
    val n = s.latestChapter?.number?.toDoubleOrNull() ?: return "Nouveau chapitre"
    return "Ch. ${n.toInt() + 1}"
}

fun unknownReason(s: FollowedSeries) = when {
    s.isFinished -> "Série terminée"
    s.lastCheckedAt == null -> "Pas encore vérifiée"
    s.recentChapters.size < 2 -> "Pas assez de chapitres pour estimer"
    else -> "En pause ou rythme irrégulier"
}

fun statusLabel(status: String?) = when (status) {
    "ongoing" -> "en cours"
    "completed" -> "terminée"
    "hiatus" -> "en pause"
    "cancelled" -> "annulée"
    else -> "inconnu"
}
