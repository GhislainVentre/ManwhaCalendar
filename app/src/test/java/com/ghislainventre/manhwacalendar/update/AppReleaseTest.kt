package com.ghislainventre.manhwacalendar.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppReleaseTest {

    private val json = """
        {
          "versionCode": 205,
          "versionName": "1.1.105",
          "sizeBytes": 18400000,
          "history": [
            {"versionCode": 203, "versionName": "1.1.103", "title": "Plus ancienne", "body": ""},
            {"versionCode": 205, "versionName": "1.1.105", "title": "Lecteur français", "body": "• Dates FR"},
            {"versionCode": 204, "versionName": "1.1.104", "title": "", "body": "sans titre"},
            "illisible"
          ]
        }
    """.trimIndent()

    @Test
    fun readsVersionSizeAndNotesNewestFirst() {
        val release = AppRelease.parse(json)!!
        assertEquals(205L, release.versionCode)
        assertEquals("1.1.105", release.versionName)
        assertEquals(18_400_000L, release.sizeBytes)
        assertEquals(listOf(205L, 203L), release.history.map { it.versionCode })
        assertEquals("• Dates FR", release.history.first().body)
    }

    @Test
    fun showsOnlyChangesSinceInstalledVersion() {
        val release = AppRelease.parse(json)!!
        assertEquals(listOf(205L), release.changesSince(203).map { it.versionCode })
        assertEquals(listOf(205L, 203L), release.changesSince(150).map { it.versionCode })
        assertEquals(emptyList<ReleaseNote>(), release.changesSince(205))
    }

    @Test
    fun rejectsInvalidFiles() {
        assertNull(AppRelease.parse("205"))
        assertNull(AppRelease.parse("{}"))
        assertNull(AppRelease.parse("pas du json"))
    }

    @Test
    fun missingFieldsStayEmpty() {
        val release = AppRelease.parse("""{"versionCode": 12}""")!!
        assertNull(release.versionName)
        assertNull(release.sizeBytes)
        assertEquals(emptyList<ReleaseNote>(), release.history)
    }
}
