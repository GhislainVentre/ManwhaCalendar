package com.ghislainventre.manhwacalendar.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.ghislainventre.manhwacalendar.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mises à jour depuis la release « latest » de GitHub : le build publie l'APK et un version.txt
 * qui contient son versionCode. L'appli compare ce numéro au sien, télécharge l'APK puis ouvre
 * l'installeur d'Android (les séries suivies sont conservées, la clé de signature ne change pas).
 */
object AppUpdater {
    private const val RELEASE_URL = "https://github.com/GhislainVentre/ManwhaCalendar/releases/download/latest"
    private const val VERSION_URL = "$RELEASE_URL/version.txt"
    private const val APK_URL = "$RELEASE_URL/ManhwaCalendar.apk"

    fun installedVersionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** versionCode de la dernière version publiée, ou null si le fichier est absent ou illisible. */
    suspend fun latestVersionCode(): Long? = withContext(Dispatchers.IO) {
        val connection = open(VERSION_URL)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            connection.inputStream.bufferedReader().use { it.readText() }.trim().toLongOrNull()
        } finally {
            connection.disconnect()
        }
    }

    /** Télécharge l'APK dans le cache ; [onProgress] reçoit 0..1 (ou rien si la taille est inconnue). */
    suspend fun download(context: Context, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "ManhwaCalendar.apk")
        val partial = File(dir, "ManhwaCalendar.apk.part")
        val connection = open(APK_URL)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error(tr("erreur ", "error ") + connection.responseCode)
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var percent = -1L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        // Un appel par pour-cent suffit pour la barre de progression.
                        if (total > 0 && done * 100 / total != percent) {
                            percent = done * 100 / total
                            onProgress(done.toFloat() / total)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        target
    }

    /**
     * Ouvre l'installeur d'Android. La première fois, Android demande d'autoriser
     * Manhwa Calendar à installer des applis, puis revient à l'installation.
     */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Texte ajouté sous un retour, pour savoir sur quelle version et quel téléphone il a été écrit. */
    fun deviceSummary(context: Context): String =
        "Version ${installedVersionName(context)} (${installedVersionCode(context)}) · " +
            "Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ManhwaCalendar")
        }
}
