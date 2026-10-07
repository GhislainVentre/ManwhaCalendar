package com.ghislainventre.manhwacalendar

import android.app.Application
import com.ghislainventre.manhwacalendar.data.SeriesRepository
import com.ghislainventre.manhwacalendar.work.ReleaseCheckWorker
import com.ghislainventre.manhwacalendar.work.ReleaseNotifier

class ManhwaCalendarApp : Application() {

    val repository: SeriesRepository by lazy { SeriesRepository(this) }

    override fun onCreate() {
        super.onCreate()
        ReleaseNotifier.createChannel(this)
        ReleaseCheckWorker.schedule(this)
    }
}
