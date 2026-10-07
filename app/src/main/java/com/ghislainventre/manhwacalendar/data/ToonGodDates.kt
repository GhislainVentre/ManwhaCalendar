package com.ghislainventre.manhwacalendar.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Lecture des dates affichées par ToonGod (« October 3, 2026 », « 2 hours ago »…). */
object ToonGodDates {
    private val formats = listOf("MMMM d, yyyy", "MMM d, yyyy", "dd/MM/yyyy", "yyyy-MM-dd", "d MMMM yyyy", "MM/dd/yyyy")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }
    private val relative = Regex("""(\d+|an?)\s*(sec|second|min|minute|hour|day|week|month|year)s?\s+ago""", RegexOption.IGNORE_CASE)
    private val number = Regex("""(\d+(?:\.\d+)?)""")

    fun parse(text: String, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Instant? {
        val t = text.trim()
        if (t.isEmpty()) return null
        when (t.lowercase(Locale.ENGLISH)) {
            "today", "just now", "new" -> return now
            "yesterday" -> return now.minus(Duration.ofDays(1))
        }
        relative.find(t)?.let { m ->
            val n = m.groupValues[1].toLongOrNull() ?: 1L
            val unit = when (m.groupValues[2].lowercase(Locale.ENGLISH)) {
                "sec", "second" -> Duration.ofSeconds(n)
                "min", "minute" -> Duration.ofMinutes(n)
                "hour" -> Duration.ofHours(n)
                "day" -> Duration.ofDays(n)
                "week" -> Duration.ofDays(7 * n)
                "month" -> Duration.ofDays(30 * n)
                else -> Duration.ofDays(365 * n)
            }
            return now.minus(unit)
        }
        for (f in formats) {
            val date = runCatching { LocalDate.parse(t, f) }.getOrNull() ?: continue
            return date.atTime(12, 0).atZone(zone).toInstant()
        }
        return null
    }

    /** « Chapter 45.5 - Fin » → « 45.5 ». */
    fun chapterNumber(label: String): String? = number.find(label)?.groupValues?.get(1)

    /** « Chapter 45 - The End » → « The End » ; « Chapter 45 » → null. */
    fun chapterTitle(label: String): String? =
        label.replace(chapterPrefix, "").trim().takeIf { it.isNotEmpty() }

    private val chapterPrefix = Regex("""^\s*(chapter|chap\.?|ch\.?|episode|ep\.?)\s*\d+(\.\d+)?\s*[-:–]?""", RegexOption.IGNORE_CASE)
}
