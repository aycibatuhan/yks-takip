package com.yks2027.tracker.feature.timer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yks2027.tracker.MainActivity
import com.yks2027.tracker.NotificationChannels
import com.yks2027.tracker.R
import com.yks2027.tracker.core.datastore.TimerPhase
import com.yks2027.tracker.core.datastore.TimerStateRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** PRD §7.2 layer 3 — fires at end_at even if the process was killed or the device dozed. */
@AndroidEntryPoint
class TimerAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var timerController: TimerController
    @Inject lateinit var timerStateRepository: TimerStateRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val wasBreak = timerStateRepository.snapshot().isBreak
                if (timerController.finalizeCompleted()) {
                    if (wasBreak) {
                        postCompletionNotification(context, "Mola bitti!", "Devam etmeye hazır mısın?")
                    } else {
                        postCompletionNotification(context, "Süre doldu!", "Odak oturumu tamamlandı.")
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * PRD §7.2 layer 4 — alarms are wiped on reboot. If a timer was RUNNING: reschedule the
 * alarm when end_at is still ahead (no FGS from boot — background-start restrictions),
 * or finalize immediately when the device was off past end_at.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var timerStateRepository: TimerStateRepository
    @Inject lateinit var timerController: TimerController
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val state = timerStateRepository.snapshot()
                if (state.phase == TimerPhase.RUNNING && state.endAt != null) {
                    if (state.endAt > System.currentTimeMillis()) {
                        alarmScheduler.schedule(state.endAt)
                    } else if (timerController.finalizeCompleted()) {
                        postCompletionNotification(
                            context,
                            "Süre doldu (cihaz kapalıyken)",
                            "Odak oturumu cihaz kapalıyken tamamlandı.",
                        )
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

internal fun postCompletionNotification(context: Context, title: String, text: String) {
    val nm = NotificationManagerCompat.from(context)
    if (!nm.areNotificationsEnabled()) return // POST_NOTIFICATIONS denied — in-app alert covers it
    val contentIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, NotificationChannels.TIMER_DONE)
        .setSmallIcon(R.drawable.ic_timer_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(contentIntent)
        .build()
    runCatching { nm.notify(43, notification) }
}
