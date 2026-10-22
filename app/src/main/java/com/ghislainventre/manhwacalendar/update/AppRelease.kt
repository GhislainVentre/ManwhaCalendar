package com.ghislainventre.manhwacalendar.update

import com.ghislainventre.manhwacalendar.tr
import org.json.JSONObject
import kotlin.math.roundToLong

/** Nouveautés d'une version publiée : le titre et le corps du message de fusion dans main. */
data class ReleaseNote(val versionCode: Long, val versionName: String, val title: String, val body: String)

/**
 * Dernière version publiée, lue dans release.json (écrit par le build à côté de l'APK).
 * [history] garde les notes des dernières versions, de la plus récente à la plus ancienne :
 * on montre tout ce qui est sorti depuis la version installée, même si certaines ont été sautées.
 */
data class AppRelease(
    val versionCode: Long,
    val versionName: String?,
    val sizeBytes: Long?,
    val history: List<ReleaseNote> = emptyList(),
) {
    fun changesSince(installedVersionCode: Long): List<ReleaseNote> =
        history.filter { it.versionCode > installedVersionCode }

    companion object {
        /** null si le texte n'est pas un release.json valide. Une note illisible est ignorée. */
        fun parse(json: String): AppRelease? = try {
            val root = JSONObject(json)
            val code = root.optLong("versionCode", -1)
            if (code <= 0) {
                null
            } else {
                val entries = root.optJSONArray("history")
                val notes = (0 until (entries?.length() ?: 0)).mapNotNull { i ->
                    val entry = entries?.optJSONObject(i) ?: return@mapNotNull null
                    val entryCode = entry.optLong("versionCode", -1)
                    val title = entry.optString("title").trim()
                    if (entryCode <= 0 || title.isEmpty()) return@mapNotNull null
                    ReleaseNote(entryCode, entry.optString("versionName").trim(), title, entry.optString("body").trim())
                }
                AppRelease(
                    versionCode = code,
                    versionName = root.optString("versionName").trim().ifEmpty { null },
                    sizeBytes = root.optLong("sizeBytes", 0).takeIf { it > 0 },
                    history = notes.sortedByDescending { it.versionCode },
                )
            }
        } catch (e: org.json.JSONException) {
            null
        }

        /** « 18 Mo » / « 18 MB » */
        fun formatSize(bytes: Long): String = "${maxOf(1, (bytes / 1_000_000.0).roundToLong())} " + tr("Mo", "MB")
    }
}
