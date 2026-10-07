package com.ghislainventre.manhwacalendar.ui

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
private val shortFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

fun Instant.localDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

fun dayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Aujourd'hui"
    today.plusDays(1) -> "Demain"
    today.minusDays(1) -> "Hier"
    else -> dayFormatter.format(date).replaceFirstChar { it.titlecase(Locale.FRENCH) }
}

fun shortDate(instant: Instant): String = shortFormatter.format(instant.localDate())

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
