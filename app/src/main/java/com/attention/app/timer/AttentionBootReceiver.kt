package com.attention.app.timer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.attention.app.MainActivity
import com.attention.app.data.RoomAttentionStateRepository
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.createSettingsStore
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
                        ).state.first()
                    }.getOrNull()
                        ?.takeIf { it.activeTimer != null }
                        ?.let {
                            context.getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(
                                android.app.NotificationChannel(AttentionTimerService.CHANNEL_ID, "Attention 计时器", android.app.NotificationManager.IMPORTANCE_LOW),
                            )
                            val notification = NotificationCompat.Builder(context, AttentionTimerService.CHANNEL_ID)
                                .setSmallIcon(android.R.drawable.ic_dialog_info)
                                .setContentTitle("Attention 计时器需要确认")
                                .setContentText("上次计时状态已保留，请打开应用选择继续或结束。")
                                .setContentIntent(android.app.PendingIntent.getActivity(context, 22, Intent(context, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT))
                                .setAutoCancel(true)
                                .build()
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                NotificationManagerCompat.from(context).notify(AttentionTimerService.NOTIFICATION_ID + 1, notification)
                            }
                        }
                } finally {
                    database.close()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
