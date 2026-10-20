package com.ghislainventre.manhwacalendar.ui

import com.ghislainventre.manhwacalendar.data.FollowedSeries
import com.ghislainventre.manhwacalendar.isFrenchUi
import com.ghislainventre.manhwacalendar.tr
import com.ghislainventre.manhwacalendar.uiLocale
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Formats relus à chaque appel : ils suivent la langue du téléphone.
private val dayFormatter get() = DateTimeFormatter.ofPattern(tr("EEEE d MMMM", "EEEE, MMMM d"), uiLocale)
private val shortFormatter get() = DateTimeFormatter.ofPattern(tr("EEE d MMM", "EEE, MMM d"), uiLocale)
private val dayMonthFormatter get() = DateTimeFormatter.ofPattern(tr("d MMM", "MMM d"), uiLocale)
private val weekdayFormatter get() = DateTimeFormatter.ofPattern("EEE", uiLocale)

fun Instant.localDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

private fun String.capitalized() = replaceFirstChar { it.titlecase(uiLocale) }

private val todayLabel get() = tr("Aujourd'hui", "Today")
private val tomorrowLabel get() = tr("Demain", "Tomorrow")

fun dayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> todayLabel
    today.plusDays(1) -> tomorrowLabel
    today.minusDays(1) -> tr("Hier", "Yesterday")
    else -> longDate(date)
}

/** « Jeudi 9 octobre », « Thursday, October 9 ». */
fun longDate(date: LocalDate): String = dayFormatter.format(date).capitalized()

/** « Jeu », sans le point d'abréviation. */
fun weekdayShort(date: LocalDate): String = weekdayFormatter.format(date).removeSuffix(".").capitalized()

fun shortDate(instant: Instant): String = shortFormatter.format(instant.localDate())

/** « Aujourd'hui », « Demain », « Dans 3 jours », « Dans 2 semaines ». */
fun untilLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days <= 0L -> todayLabel
        days == 1L -> tomorrowLabel
        days < 7L -> tr("Dans $days jours", "In $days days")
        days < 14L -> tr("Dans 1 semaine", "In 1 week")
        else -> tr("Dans ${days / 7} semaines", "In ${days / 7} weeks")
    }
}

/** Version courte pour les badges : « Demain », « Dans 3 j », « 12 oct. ». */
fun shortUntil(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days <= 0L -> todayLabel
        days == 1L -> tomorrowLabel
        days < 7L -> tr("Dans $days j", "In $days d")
        else -> dayMonthFormatter.format(date)
    }
}

/** Légende d'un jour de l'agenda : relative dans la semaine, sinon la date complète. */
fun dayCaption(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    if (ChronoUnit.DAYS.between(today, date) < 7L) untilLabel(date, today) else longDate(date)

fun relativePast(instant: Instant, now: Instant = Instant.now()): String {
    val d = Duration.between(instant, now)
    return when {
        d.toMinutes() < 60 -> tr("il y a ${d.toMinutes().coerceAtLeast(1)} min", "${d.toMinutes().coerceAtLeast(1)} min ago")
        d.toHours() < 24 -> tr("il y a ${d.toHours()} h", "${d.toHours()} h ago")
        d.toDays() < 30 -> tr("il y a ${d.toDays()} j", "${d.toDays()} d ago")
        else -> tr("le ", "on ") + shortDate(instant)
    }
}

fun rhythm(intervalDays: Double?): String? = when {
    intervalDays == null -> null
    intervalDays < 1.5 -> tr("quotidien", "daily")
    intervalDays in 5.5..8.5 -> tr("hebdomadaire", "weekly")
    intervalDays in 12.0..16.0 -> tr("toutes les 2 semaines", "every 2 weeks")
    intervalDays in 26.0..35.0 -> tr("mensuel", "monthly")
    else -> tr("tous les ${intervalDays.toInt()} jours env.", "about every ${intervalDays.toInt()} days")
}

// En français 0 et 1 sont au singulier, en anglais seul 1 l'est.
fun plural(count: Int, singular: String, plural: String) =
    "$count ${if (count > 1 || (count == 0 && !isFrenchUi)) plural else singular}"

fun chapterLabel(number: String?) = number?.let { "Ch. $it" } ?: "Oneshot"

fun nextChapterLabel(s: FollowedSeries): String {
    val n = s.latestChapter?.number?.toDoubleOrNull() ?: return tr("Nouveau chapitre", "New chapter")
    return "Ch. ${n.toInt() + 1}"
}

fun unknownReason(s: FollowedSeries) = when {
    s.isFinished -> tr("Série terminée", "Series completed")
    s.lastCheckedAt == null -> tr("Pas encore vérifiée", "Not checked yet")
    s.recentChapters.size < 2 -> tr("Pas assez de chapitres pour estimer", "Not enough chapters to estimate")
    else -> tr("En pause ou rythme irrégulier", "On break or irregular schedule")
}

fun statusLabel(status: String?) = when (status) {
    "ongoing" -> tr("en cours", "ongoing")
    "completed" -> tr("terminée", "completed")
    "hiatus" -> tr("en pause", "on hiatus")
    "cancelled" -> tr("annulée", "cancelled")
    else -> tr("inconnu", "unknown")
}

private val tagLabels = mapOf(
    "Action" to "Action",
    "Adventure" to "Aventure",
    "Comedy" to "Comédie",
    "Crime" to "Policier",
    "Drama" to "Drame",
    "Fantasy" to "Fantasy",
    "Historical" to "Historique",
    "Horror" to "Horreur",
    "Isekai" to "Isekai",
    "Magical Girls" to "Magical girls",
    "Mecha" to "Mecha",
    "Medical" to "Médical",
    "Mystery" to "Mystère",
    "Philosophical" to "Philosophique",
    "Psychological" to "Psychologique",
    "Romance" to "Romance",
    "Sci-Fi" to "Science-fiction",
    "Slice of Life" to "Tranche de vie",
    "Sports" to "Sport",
    "Superhero" to "Super-héros",
    "Thriller" to "Thriller",
    "Tragedy" to "Tragédie",
    "Wuxia" to "Wuxia",
    "Boys' Love" to "Boys' love",
    "Girls' Love" to "Girls' love",
    "Martial Arts" to "Arts martiaux",
    "Magic" to "Magie",
    "Monsters" to "Monstres",
    "Demons" to "Démons",
    "Reincarnation" to "Réincarnation",
    "Time Travel" to "Voyage dans le temps",
    "Video Games" to "Jeux vidéo",
    "Villainess" to "Méchante",
    "Survival" to "Survie",
    "School Life" to "Vie scolaire",
    "Supernatural" to "Surnaturel",
    "Military" to "Militaire",
    "Office Workers" to "Vie de bureau",
    "Cooking" to "Cuisine",
    "Music" to "Musique",
    "Post-Apocalyptic" to "Post-apocalyptique",
    "Zombies" to "Zombies",
    "Vampires" to "Vampires",
    "Monster Girls" to "Monster girls",
    "Harem" to "Harem",
    "Reverse Harem" to "Harem inversé",
    "Gyaru" to "Gyaru",
    "Animals" to "Animaux",
    "Ghosts" to "Fantômes",
    "Ninja" to "Ninja",
    "Samurai" to "Samouraï",
    "Police" to "Police",
    "Mafia" to "Mafia",
    "Delinquents" to "Délinquants",
    "Genderswap" to "Changement de genre",
    "Aliens" to "Extraterrestres",
)

/** Nom français d'un genre MangaDex quand on le connaît, sinon le nom anglais (celui de l'API). */
fun tagLabel(name: String): String = if (isFrenchUi) tagLabels[name] ?: name else name
