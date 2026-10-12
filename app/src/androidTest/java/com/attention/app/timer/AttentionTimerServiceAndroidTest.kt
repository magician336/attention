package com.attention.app.timer

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.Target
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttentionTimerServiceAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AttentionDatabase

    @Before
    fun resetTimerState() = runBlocking {
        context.stopService(AttentionTimerService.intent(context))
        database = AttentionDatabase.create(context)
        RoomBusinessDataRepository(database).replace(AttentionState())
        NotificationManagerCompat.from(context).cancel(AttentionTimerService.NOTIFICATION_ID)
    }

    @After
    fun stopServiceAndCloseDatabase() {
        context.stopService(AttentionTimerService.intent(context))
        database.close()
    }

    @Test
    fun running_notification_has_pause_and_stop_actions_from_room_state() = runBlocking {
        RoomBusinessDataRepository(database).replace(
            AttentionState(
                targets = listOf(Target(id = "service-target", title = "后台阅读")),
                activeTimer = ActiveTimer(
                    targetId = "service-target",
                    startedAtEpochMillis = System.currentTimeMillis(),
                    lastPlanningDate = "2026-10-12",
                ),
            ),
        )

        startService()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            waitForNotification()

            val notification = NotificationManagerCompat.from(context)
                .activeNotifications
                .firstOrNull { it.id == AttentionTimerService.NOTIFICATION_ID }
            assertNotNull(notification)
            assertEquals(2, notification!!.notification.actions.size)
            assertEquals("暂停", notification.notification.actions[0].title)
            assertEquals("结束", notification.notification.actions[1].title)
            assertTrue(notification.notification.extras.getCharSequence("android.title").toString().contains("正在计时"))
            assertTrue(notification.notification.extras.getCharSequence("android.text").toString().contains("后台阅读"))
        } else {
            // Android 13+ can revoke POST_NOTIFICATIONS. The foreground service
            // and Room timer must still work even though the notification is
            // intentionally hidden from the drawer.
            waitForRoom { it.activeTimer != null }
        }

        context.startService(AttentionTimerService.intent(context).setAction(AttentionTimerService.ACTION_PAUSE))
        waitForRoom { it.activeTimer?.paused == true }

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            val paused = NotificationManagerCompat.from(context)
                .activeNotifications
                .first { it.id == AttentionTimerService.NOTIFICATION_ID }
            assertEquals("继续", paused.notification.actions[0].title)
            assertTrue(paused.notification.extras.getCharSequence("android.title").toString().contains("已暂停"))
        }
    }

    @Test
    fun service_without_active_timer_stops_and_removes_notification() = runBlocking {
        startService()
        waitUntil { !isServiceRunning() }

        assertFalse(isServiceRunning())
        assertNull(
            NotificationManagerCompat.from(context)
                .activeNotifications
                .firstOrNull { it.id == AttentionTimerService.NOTIFICATION_ID },
        )
    }

    @Test
    fun notification_permission_denied_still_persists_explicit_timer_stop() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (permissionGranted) {
            instrumentation.uiAutomation.executeShellCommand("pm revoke ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}").close()
        }
        try {
            assertFalse(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
            )
            RoomBusinessDataRepository(database).replace(
                AttentionState(
                    targets = listOf(Target(id = "permission-target", title = "权限测试")),
                    activeTimer = ActiveTimer(
                        targetId = "permission-target",
                        startedAtEpochMillis = System.currentTimeMillis() - 120_000L,
                        lastPlanningDate = "2026-10-12",
                    ),
                ),
            )

            startService()
            waitForRoom { it.activeTimer != null }
            context.startService(AttentionTimerService.intent(context).setAction(AttentionTimerService.ACTION_STOP))
            waitForRoom { it.activeTimer == null && it.timeEntries.isNotEmpty() }

            val stopped = RoomBusinessDataRepository(database).read()
            assertNull(stopped.activeTimer)
            assertEquals(2, stopped.timeEntries.single().durationMinutes)
        } finally {
            if (permissionGranted) {
                instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}").close()
            }
        }
    }

    private fun startService() {
        ContextCompat.startForegroundService(context, AttentionTimerService.intent(context))
    }

    private suspend fun waitForNotification() {
        waitUntil {
            NotificationManagerCompat.from(context)
                .activeNotifications
                .any { it.id == AttentionTimerService.NOTIFICATION_ID }
        }
    }

    private suspend fun waitForRoom(predicate: (AttentionState) -> Boolean) {
        repeat(20) {
            if (predicate(RoomBusinessDataRepository(database).read())) return
            kotlinx.coroutines.delay(100)
        }
        assertTrue("Timed out waiting for timer service state", predicate(RoomBusinessDataRepository(database).read()))
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(20) {
            if (predicate()) return
            kotlinx.coroutines.delay(100)
        }
        assertTrue("Timed out waiting for timer service state", predicate())
    }

    private fun isServiceRunning(): Boolean = context
        .getSystemService(ActivityManager::class.java)
        .getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == AttentionTimerService::class.java.name }
}
