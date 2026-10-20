package com.ghislainventre.manhwacalendar.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Lecture des dates affichées par les sites Madara (« October 3, 2026 », « 2 hours ago »…). */
object MadaraDates {
    private val formats = listOf("MMMM d, yyyy", "MMM d, yyyy", "dd/MM/yyyy", "yyyy-MM-dd", "d MMMM yyyy", "MM/dd/yyyy")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) } +
        // Sites français : « 16 mai 2025 », « 3 oct. 2026 ».
        listOf("d MMMM yyyy", "d MMM yyyy", "MMMM d, yyyy").map { DateTimeFormatter.ofPattern(it, Locale.FRENCH) }
    private val relative = Regex("""(\d+|an?)\s*(sec|second|min|minute|hour|day|week|month|year)s?\s+ago""", RegexOption.IGNORE_CASE)
    /** « il y a 2 heures », « il y a un jour ». */
    private val relativeFr = Regex(
        """il y a\s+(\d+|une?)\s*(sec|seconde|min|minute|heure|h|jour|j|semaine|mois|an|année)""",
        RegexOption.IGNORE_CASE,
    )
    private val number = Regex("""(\d+(?:[.,]\d+)?)""")
    /** Numéro qui suit le mot « chapitre » : « Saison 2 - Chapitre 15 » est le chapitre 15, pas le 2. */
    private val numberAfterKeyword = Regex(
        """(?<!\p{L})(?:chapter|chapitre|chap\.?|ch\.?|episode|épisode|ep\.?)\s*(\d+(?:[.,]\d+)?)""",
        RegexOption.IGNORE_CASE,
    )
    private val volume = Regex("""^\s*(volume|vol\.?|tome)\s*\d+\s*$""", RegexOption.IGNORE_CASE)
    /** « 1er octobre 2026 ». */
    private val firstOfMonth = Regex("""\b1er\b""")
    /** Mois abrégés sans point (« 3 oct 2026 ») : Java les attend avec le point. */
    private val frenchShortMonth = Regex("""(?<=\s)(janv|févr|fevr|avr|juil|sept|oct|nov|déc|dec)(?=\s)""")

    fun parse(text: String, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Instant? {
        val t = text.trim()
        if (t.isEmpty()) return null
        when (t.lowercase(Locale.ENGLISH)) {
            "today", "just now", "new" -> return now
            "yesterday", "hier" -> return now.minus(Duration.ofDays(1))
            "aujourd'hui", "aujourd’hui", "nouveau" -> return now
        }
        relativeFr.find(t)?.let { m ->
            val n = m.groupValues[1].toLongOrNull() ?: 1L
            val unit = when (m.groupValues[2].lowercase(Locale.FRENCH)) {
                "sec", "seconde" -> Duration.ofSeconds(n)
                "min", "minute" -> Duration.ofMinutes(n)
                "heure", "h" -> Duration.ofHours(n)
                "jour", "j" -> Duration.ofDays(n)
                "semaine" -> Duration.ofDays(7 * n)
                "mois" -> Duration.ofDays(30 * n)
                else -> Duration.ofDays(365 * n)
            }
            return now.minus(unit)
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
        val french = t.lowercase(Locale.FRENCH).replace(firstOfMonth, "1").replace(frenchShortMonth) { m ->
            when (m.value) {
                "fevr" -> "févr."
                "dec" -> "déc."
                else -> m.value + "."
            }
        }
        for (f in formats) {
            val date = runCatching { LocalDate.parse(t, f) }.getOrNull()
                ?: runCatching { LocalDate.parse(french, f) }.getOrNull() ?: continue
            return date.atTime(12, 0).atZone(zone).toInstant()
        }
        return null
    }

    /** « Chapter 45.5 - Fin » → « 45.5 » ; « Chapitre 45,5 » → « 45.5 ». */
    fun chapterNumber(label: String): String? =
        (numberAfterKeyword.find(label) ?: number.find(label))?.groupValues?.get(1)?.replace(',', '.')

    /** Un tome entier (« Volume 3 », « Tome 3 ») plutôt qu'un chapitre. */
    fun isVolume(label: String): Boolean = volume.matches(label)

    /** « Chapter 45 - The End » → « The End » ; « Chapter 45 » → null. */
    fun chapterTitle(label: String): String? =
        label.replace(chapterPrefix, "").trim().takeIf { it.isNotEmpty() }

    private val chapterPrefix = Regex("""^\s*(chapter|chapitre|chap\.?|ch\.?|episode|épisode|ep\.?)\s*\d+(\.\d+)?\s*[-:–]?""", RegexOption.IGNORE_CASE)
}
