package com.ghislainventre.manhwacalendar.data

import java.text.Normalizer
import java.util.Locale

/** Comparaison de titres d'une même série écrits différemment selon les sites. */
object TitleMatch {
    /** « The Iron-Blooded Sword Hound’s Revenge » → « ironbloodedswordhoundsrevenge ». */
    fun normalize(title: String): String =
        // Sans accents : « L'Épée » et « L'Epee » sont la même série d'un site à l'autre.
        Normalizer.normalize(title.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace(Regex("""^\s*the\s+"""), "")
            .filter { it.isLetterOrDigit() }

    /** Vrai si [candidate] est l'un des [titles] (casse, ponctuation et « The » initial ignorés). */
    fun matches(candidate: String, titles: Collection<String>): Boolean {
        val key = normalize(candidate)
        return key.length >= MIN_LENGTH && titles.any { normalize(it) == key }
    }

    private const val MIN_LENGTH = 4
}
