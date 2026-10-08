package com.ghislainventre.manhwacalendar.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatchTest {

    private val titles = listOf("Revenge of the Iron-Blooded Sword Hound", "The Iron-Blooded Sword Hound's Revenge")

    @Test
    fun ignoresCasePunctuationAndLeadingThe() {
        assertTrue(TitleMatch.matches("REVENGE OF THE IRON BLOODED SWORD HOUND", titles))
        assertTrue(TitleMatch.matches("Iron-Blooded Sword Hound’s Revenge", titles))
    }

    @Test
    fun rejectsOtherSeries() {
        assertFalse(TitleMatch.matches("Revenge of the Iron-Blooded Sword Hound Side Story", titles))
        assertFalse(TitleMatch.matches("The", titles))
    }
}
