package com.yks2027.tracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YksApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
    }
}

object NotificationChannels {
    const val TIMER_RUNNING = "timer_running"
    const val TIMER_DONE = "timer_done"

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                TIMER_RUNNING,
                "Çalışan sayaç",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Sayaç çalışırken görünen sessiz bildirim"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                TIMER_DONE,
                "Süre doldu",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Odak süresi bittiğinde sesli/titreşimli uyarı"
                enableVibration(true)
            },
        )
    }
}
