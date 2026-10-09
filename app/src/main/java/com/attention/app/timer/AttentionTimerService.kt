package com.attention.app.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.attention.app.MainActivity
import com.attention.app.R
import com.attention.app.data.RoomAttentionStateRepository
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.createSettingsStore
import com.attention.domain.AttentionState
import com.attention.domain.pauseTimer
import com.attention.domain.recalculateExperience
import com.attention.domain.resumeTimer
import com.attention.domain.stopTimer
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AttentionTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { AttentionDatabase.create(this) }
    private val repository by lazy {
        RoomAttentionStateRepository(
            business = RoomBusinessDataRepository(database),
            settings = createSettingsStore(),
        )
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification(null))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            when (intent?.action) {
                ACTION_PAUSE -> repository.updateBusiness { it.pauseTimer(Instant.now()) }
                ACTION_RESUME -> repository.updateBusiness { it.resumeTimer(Instant.now()) }
                ACTION_STOP -> {
                    repository.updateBusiness { it.stopTimer(Instant.now(), ZoneId.systemDefault()).state.recalculateExperience() }
                    stopSelfResult(startId)
                }
            }
            val state = repository.state.first()
            if (state.activeTimer == null) stopSelfResult(startId)
            else getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
        }
        return START_STICKY
    }

    private fun notification(state: AttentionState?): Notification {
        val timer = state?.activeTimer
        val title = if (timer == null) "Attention 计时器" else "Attention 正在计时"
        val text = timer?.let { "目标 ${state?.targets?.firstOrNull { target -> target.id == it.targetId }?.title ?: "未归属活动"}" } ?: "等待计时器状态"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(timer != null)
            .setContentIntent(PendingIntent.getActivity(this, 20, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(action(if (timer?.paused == true) ACTION_RESUME else ACTION_PAUSE, if (timer?.paused == true) "继续" else "暂停"))
            .addAction(action(ACTION_STOP, "结束"))
            .build()
    }

    private fun action(command: String, label: String): NotificationCompat.Action = NotificationCompat.Action.Builder(
        android.R.drawable.ic_media_pause,
        label,
        PendingIntent.getService(this, command.hashCode(), Intent(this, AttentionTimerService::class.java).setAction(command), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
    ).build()

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Attention 计时器", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        runCatching { database.close() }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "attention_timer"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "com.attention.app.timer.PAUSE"
        const val ACTION_RESUME = "com.attention.app.timer.RESUME"
        const val ACTION_STOP = "com.attention.app.timer.STOP"

        fun intent(context: Context): Intent = Intent(context, AttentionTimerService::class.java)
    }
}
