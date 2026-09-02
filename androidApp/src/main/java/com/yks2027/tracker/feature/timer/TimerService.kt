package com.yks2027.tracker.feature.timer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yks2027.tracker.MainActivity
import com.yks2027.tracker.NotificationChannels
import com.yks2027.tracker.R

/**
 * PRD §7.2 layer 2 — foreground service while RUNNING. The chronometer-countdown
 * notification lets the OS render the live remaining time with zero notification
 * updates from us. UX only; the exact alarm is the completion guarantee.
 */
class TimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val endAt = intent?.getLongExtra(EXTRA_END_AT, -1L) ?: -1L
        val stopwatchBase = intent?.getLongExtra(EXTRA_STOPWATCH_BASE, -1L) ?: -1L
        if (endAt <= 0 && stopwatchBase < 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        val notification = if (endAt > 0) {
            buildNotification(
                title = "Sayaç çalışıyor",
                text = "Odak süresi devam ediyor",
                whenMs = endAt,
                countDown = true,
            )
        } else {
            buildNotification(
                title = "Kronometre çalışıyor",
                text = "Serbest çalışma süresi sayılıyor",
                whenMs = stopwatchBase,
                countDown = false,
            )
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        return START_NOT_STICKY
    }

    private fun buildNotification(title: String, text: String, whenMs: Long, countDown: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannels.TIMER_RUNNING)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setUsesChronometer(true)
            .setChronometerCountDown(countDown)
            .setWhen(whenMs)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val EXTRA_END_AT = "end_at"
        private const val EXTRA_STOPWATCH_BASE = "stopwatch_base"

        fun startCountdown(context: Context, endAt: Long) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimerService::class.java).putExtra(EXTRA_END_AT, endAt),
            )
        }

        /** v1.2 — native count-up chronometer: base = now - elapsed-so-far. */
        fun startStopwatch(context: Context, elapsedMs: Long) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimerService::class.java)
                    .putExtra(EXTRA_STOPWATCH_BASE, System.currentTimeMillis() - elapsedMs),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }
}
