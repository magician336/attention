package com.attention.app.timer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.attention.app.MainActivity
import com.attention.app.data.RoomAttentionStateRepository
import com.attention.app.data.backup.FileImportBackupStore
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.createSettingsStore
import com.attention.domain.AttentionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AttentionBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AttentionDatabase.create(context)
                try {
                    runCatching {
                        RoomAttentionStateRepository(
                            business = RoomBusinessDataRepository(database),
                            settings = context.createSettingsStore(),
                            backupStore = FileImportBackupStore(context),
                        ).state.first()
                    }.getOrNull()?.takeIf(::shouldPrompt)?.let { postRecoveryPrompt(context) }
                } finally {
                    database.close()
                }
            } finally {
                pending?.finish()
            }
        }
    }

    private fun postRecoveryPrompt(context: Context) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                AttentionTimerService.CHANNEL_ID,
                "Attention 计时器",
                android.app.NotificationManager.IMPORTANCE_LOW,
            ),
        )
        // Notification permission is optional for the recovery path. Room state
        // remains available in the workspace when the drawer is unavailable.
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ||
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) return

        val notification = NotificationCompat.Builder(context, AttentionTimerService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Attention 计时器需要确认")
            .setContentText("上次计时状态已保留，请打开应用选择继续或结束。")
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN_RECOVERY,
                    Intent(context, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(RECOVERY_NOTIFICATION_ID, notification) }
    }

    companion object {
        /** One stable id makes duplicate BOOT_COMPLETED deliveries idempotent. */
        const val RECOVERY_NOTIFICATION_ID = AttentionTimerService.NOTIFICATION_ID + 1

        internal fun shouldPrompt(state: AttentionState): Boolean = state.activeTimer != null

        /** Resolving recovery from the workspace must remove the stale prompt. */
        internal fun cancelRecoveryPrompt(context: Context) {
            NotificationManagerCompat.from(context).cancel(RECOVERY_NOTIFICATION_ID)
        }

        private const val REQUEST_OPEN_RECOVERY = 22
    }
}
