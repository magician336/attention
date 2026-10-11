package com.attention.app.timer

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.Target
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttentionBootReceiverAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AttentionDatabase

    @Before
    fun setUp() = runBlocking {
        context.stopService(AttentionTimerService.intent(context))
        database = AttentionDatabase.create(context)
        RoomBusinessDataRepository(database).replace(
            AttentionState(
                targets = listOf(Target(id = "boot-target", title = "开机恢复")),
                timeEntries = emptyList(),
                activeTimer = ActiveTimer(
                    targetId = "boot-target",
                    startedAtEpochMillis = 10L,
                    paused = true,
                    lastPlanningDate = "2026-10-12",
                ),
            ),
        )
        NotificationManagerCompat.from(context).cancel(AttentionBootReceiver.RECOVERY_NOTIFICATION_ID)
    }

    @After
    fun tearDown() = runBlocking {
        NotificationManagerCompat.from(context).cancel(AttentionBootReceiver.RECOVERY_NOTIFICATION_ID)
        if (!database.isOpen) database = AttentionDatabase.create(context)
        RoomBusinessDataRepository(database).replace(AttentionState())
        database.close()
    }

    @Test
    fun boot_posts_one_recovery_prompt_without_starting_service_or_writing_timer_entries() = runBlocking {
        val before = RoomBusinessDataRepository(database).read()
        database.close()

        val receiver = AttentionBootReceiver()
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        waitUntil {
            !notificationsEnabled() || NotificationManagerCompat.from(context)
                .activeNotifications
                .any { it.id == AttentionBootReceiver.RECOVERY_NOTIFICATION_ID }
        }

        database = AttentionDatabase.create(context)
        val after = RoomBusinessDataRepository(database).read()
        assertEquals(before.activeTimer, after.activeTimer)
        assertEquals(before.timeEntries, after.timeEntries)
        assertFalse(isServiceRunning())
        if (notificationsEnabled()) {
            val prompts = NotificationManagerCompat.from(context).activeNotifications
                .filter { it.id == AttentionBootReceiver.RECOVERY_NOTIFICATION_ID }
            assertEquals(1, prompts.size)
            assertNotNull(prompts.single().notification.contentIntent)
            assertTrue(prompts.single().notification.extras.getCharSequence("android.title").toString().contains("需要确认"))

            AttentionBootReceiver.cancelRecoveryPrompt(context)
            assertFalse(
                NotificationManagerCompat.from(context).activeNotifications
                    .any { it.id == AttentionBootReceiver.RECOVERY_NOTIFICATION_ID },
            )
        }
    }

    @Test
    fun boot_without_active_timer_does_not_post_recovery_prompt() = runBlocking {
        RoomBusinessDataRepository(database).replace(AttentionState())
        database.close()

        AttentionBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        delay(300)

        assertFalse(
            NotificationManagerCompat.from(context).activeNotifications
                .any { it.id == AttentionBootReceiver.RECOVERY_NOTIFICATION_ID },
        )
        assertFalse(isServiceRunning())
    }

    private fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(30) {
            if (predicate()) return
            delay(100)
        }
        assertTrue("Timed out waiting for boot recovery prompt", predicate())
    }

    private fun isServiceRunning(): Boolean = context
        .getSystemService(ActivityManager::class.java)
        .getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == AttentionTimerService::class.java.name }
}
