package com.ghislainventre.manhwacalendar.data

import java.util.Locale

/** Comparaison de titres d'une même série écrits différemment selon les sites. */
object TitleMatch {
    /** « The Iron-Blooded Sword Hound’s Revenge » → « ironbloodedswordhoundsrevenge ». */
    fun normalize(title: String): String =
        title.lowercase(Locale.ROOT)
            .replace(Regex("""^\s*the\s+"""), "")
            .filter { it.isLetterOrDigit() }

    /** Vrai si [candidate] est l'un des [titles] (casse, ponctuation et « The » initial ignorés). */
    fun matches(candidate: String, titles: Collection<String>): Boolean {
        val key = normalize(candidate)
        return key.length >= MIN_LENGTH && titles.any { normalize(it) == key }
    }

    private const val MIN_LENGTH = 4
}
