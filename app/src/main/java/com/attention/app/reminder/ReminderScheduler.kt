package com.attention.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.attention.domain.ScheduleEntry

class ReminderScheduler(
    private val context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ReminderAlarmPort {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(entry: ScheduleEntry) {
        val triggerAt = entry.reminderEpochMillis ?: return
        if (triggerAt <= nowMillis()) {
            cancel(entry.id)
            return
        }
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("attention://schedule/${entry.id}"))
            .putExtra(ReminderReceiver.EXTRA_ID, entry.id)
            .putExtra(ReminderReceiver.EXTRA_TITLE, entry.title)
            .putExtra(ReminderReceiver.EXTRA_PLANNING_DATE, entry.planningDate)
        val pending = PendingIntent.getBroadcast(
            context,
            entry.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    override fun cancel(entryId: String) {
        val pending = PendingIntent.getBroadcast(
            context,
            entryId.hashCode(),
            Intent(context, ReminderReceiver::class.java)
                .setData(Uri.parse("attention://schedule/$entryId")),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }
}
