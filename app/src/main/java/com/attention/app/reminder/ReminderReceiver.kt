package com.attention.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "日程提醒", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Attention 日程提醒")
            .setContentText(intent.getStringExtra(EXTRA_TITLE).orEmpty())
            .setAutoCancel(true)
            .build()
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(intent.getStringExtra(EXTRA_ID).orEmpty().hashCode(), notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "attention_schedule_reminders"
        const val EXTRA_ID = "schedule_id"
        const val EXTRA_TITLE = "schedule_title"
    }
}
