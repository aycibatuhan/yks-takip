package com.yks2027.tracker

import com.yks2027.tracker.core.platform.DesktopTimerCompletionScheduler
import com.yks2027.tracker.core.platform.TrayNotifier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Desktop completion path (spec §3): the coroutine tick fires onDue at end_at (which, in the
 * app, finalizes through TimerController and posts the tray notification); cancel stops it;
 * the running label mirrors countdown/stopwatch state for the window title.
 */
class DesktopTimerSchedulerTest {

    @Test
    fun firesOnDueAtEndAtAndTracksRunningLabel() {
        val notifier = TrayNotifier()
        val fired = CountDownLatch(1)
        val scheduler = DesktopTimerCompletionScheduler(notifier) { fired.countDown() }
        scheduler.showCountdown(System.currentTimeMillis() + 1_000)
        assertEquals("Sayaç çalışıyor", notifier.runningLabel.value)
        scheduler.scheduleCompletion(System.currentTimeMillis() + 800)
        assertTrue("onDue must fire at end_at", fired.await(5, TimeUnit.SECONDS))
        scheduler.hideRunning()
        assertNull(notifier.runningLabel.value)
        scheduler.showStopwatch(0)
        assertEquals("Kronometre çalışıyor", notifier.runningLabel.value)
    }

    @Test
    fun cancelPreventsFiring() {
        val fired = CountDownLatch(1)
        val scheduler = DesktopTimerCompletionScheduler(TrayNotifier()) { fired.countDown() }
        scheduler.scheduleCompletion(System.currentTimeMillis() + 500)
        scheduler.cancelCompletion()
        assertFalse(fired.await(1500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun permissionIsANoOpThatStillContinues() {
        var continued = false
        DesktopTimerCompletionScheduler(TrayNotifier()) {}.ensureNotificationPermission { continued = true }
        assertTrue(continued)
    }
}
