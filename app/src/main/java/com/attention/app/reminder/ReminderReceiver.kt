package com.attention.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.attention.app.MainActivity

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "日程提醒", NotificationManager.IMPORTANCE_DEFAULT))
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val planningDate = intent.getStringExtra(EXTRA_PLANNING_DATE).orEmpty()
        val openIntent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_SCHEDULE)
            .setData(Uri.parse("attention://schedule/$id"))
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_PLANNING_DATE, intent.getStringExtra(EXTRA_PLANNING_DATE))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Attention 日程提醒")
            .setContentText(listOf(planningDate, title).filter(String::isNotBlank).joinToString(" · "))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "attention_schedule_reminders"
        const val EXTRA_ID = "schedule_id"
        const val EXTRA_TITLE = "schedule_title"
        const val EXTRA_PLANNING_DATE = "schedule_planning_date"
        const val ACTION_OPEN_SCHEDULE = "com.attention.app.reminder.OPEN_SCHEDULE"
    }
}
