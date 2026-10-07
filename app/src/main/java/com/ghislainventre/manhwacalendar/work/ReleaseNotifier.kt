package com.ghislainventre.manhwacalendar.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ghislainventre.manhwacalendar.R
import com.ghislainventre.manhwacalendar.data.NewChapterEvent

object ReleaseNotifier {
    private const val CHANNEL_ID = "new_chapters"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifyNewChapter(context: Context, event: NewChapterEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val chapter = event.chapter
        val open = PendingIntent.getActivity(
            context,
            event.series.id.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(chapter.url)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = chapter.number?.let { "Chapitre $it" } ?: "Nouveau chapitre"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.series.title)
            .setContentText("$label disponible (${chapter.language.uppercase()})")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(event.series.id.hashCode(), notification)
    }
}
