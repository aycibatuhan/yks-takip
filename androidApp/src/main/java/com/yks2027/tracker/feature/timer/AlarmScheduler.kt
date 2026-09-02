package com.yks2027.tracker.feature.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §7.2 layer 3 — the exact alarm at end_at is the completion guarantee (fires even
 * if the process is killed or the device dozes). USE_EXACT_ALARM is declared (auto-
 * granted for timer apps, fine for a sideloaded APK); a windowed alarm is the
 * defense-in-depth fallback if exactness is unavailable.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val alarmManager: AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, TimerAlarmReceiver::class.java).setAction(ACTION_TIMER_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(endAtEpochMs: Long) {
        val pi = pendingIntent()
        val canExact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtEpochMs, pi)
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, endAtEpochMs, 60_000L, pi)
        }
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    companion object {
        const val ACTION_TIMER_FIRE = "com.yks2027.tracker.TIMER_FIRE"
        private const val REQUEST_CODE = 4201
    }
}
