package com.attention.app.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.attention.app.MainActivity
import com.attention.app.data.RoomAttentionStateRepository
import com.attention.app.data.backup.FileImportBackupStore
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.createSettingsStore
import com.attention.domain.AttentionCommand
import com.attention.domain.AttentionState
import com.attention.domain.formatMinuteOfDay
import com.attention.domain.pauseTimer
import com.attention.domain.resumeTimer
import com.attention.domain.timerTargetLabel
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the timer visible while the app is not in the foreground.
 *
 * Every action goes through [RoomAttentionStateRepository]. The notification
 * is only a projection of persisted [AttentionState], so notification and
 * workspace controls share the same domain commands and Room transaction.
 */
class AttentionTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()
    private val database by lazy { AttentionDatabase.create(this) }
    private val repository by lazy {
        RoomAttentionStateRepository(
            business = RoomBusinessDataRepository(database),
            settings = createSettingsStore(),
            backupStore = FileImportBackupStore(this),
        )
    }
    private var stateJob: Job? = null
    private val timerEffects by lazy {
        AndroidTimerEffects(
            context = this,
            onFeedback = {},
            serviceAlreadyRunning = true,
        )
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // A foreground service must publish a notification immediately after
        // startForegroundService(). Room state is projected once the observer
        // starts in onStartCommand.
        startForeground(NOTIFICATION_ID, notification(null))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        observeState()
        scope.launch {
            commandMutex.withLock {
                when (intent?.action) {
                    ACTION_PAUSE -> {
                        repository.updateBusiness { it.pauseTimer(Instant.now()) }
                        timerEffects.paused()
                    }
                    ACTION_RESUME -> {
                        repository.updateBusiness { it.resumeTimer(Instant.now()) }
                        timerEffects.resumed()
                    }
                    ACTION_STOP -> {
                        repository.executeCommand(
                            AttentionCommand.StopTimer,
                            now = Instant.now(),
                            zone = ZoneId.systemDefault(),
                        )
                        timerEffects.stopped()
                    }
                }

                // The observer normally handles this transition. Reading once
                // here makes auto-stop deterministic during process teardown.
                val state = repository.state.first()
                if (state.activeTimer == null) stopService(startId)
            }
        }
        // A process restart is useful while a persisted timer is active. The
        // observer immediately stops the service again when no timer exists.
        return START_STICKY
    }

    private fun observeState() {
        if (stateJob?.isActive == true) return
        stateJob = scope.launch {
            repository.state
                .catch { stopService() }
                .collectLatest { state ->
                    if (state.activeTimer == null) stopService()
                    else notifyState(state)
                }
        }
    }

    private fun notifyState(state: AttentionState) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(state))
        }
    }

    private fun stopService(startId: Int? = null) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (startId == null) stopSelf() else stopSelfResult(startId)
    }

    private fun notification(state: AttentionState?): Notification {
        val content = notificationContent(state)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(content.active)
            .setContentIntent(mainActivityIntent())

        if (content.active) {
            builder.addAction(
                action(
                    command = if (content.paused) ACTION_RESUME else ACTION_PAUSE,
                    label = if (content.paused) "继续" else "暂停",
                    icon = if (content.paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                ),
            )
            builder.addAction(action(ACTION_STOP, "结束", android.R.drawable.ic_menu_close_clear_cancel))
        }
        return builder.build()
    }

    private fun mainActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN_APP,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(command: String, label: String, icon: Int): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            icon,
            label,
            PendingIntent.getService(
                this,
                command.hashCode(),
                Intent(this, AttentionTimerService::class.java).setAction(command),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Attention 计时器", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateJob?.cancel()
        scope.cancel()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { database.close() }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "attention_timer"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "com.attention.app.timer.PAUSE"
        const val ACTION_RESUME = "com.attention.app.timer.RESUME"
        const val ACTION_STOP = "com.attention.app.timer.STOP"

        private const val REQUEST_OPEN_APP = 20

        fun intent(context: Context): Intent = Intent(context, AttentionTimerService::class.java)

        /** Stable, testable projection used by the Android notification adapter. */
        internal fun notificationContent(
            state: AttentionState?,
            now: Instant = Instant.now(),
        ): TimerNotificationContent {
            val timer = state?.activeTimer ?: return TimerNotificationContent(
                title = "Attention 计时器",
                text = "等待计时器状态",
                active = false,
                paused = false,
            )
            val target = state.timerTargetLabel(timer.targetId)
            val status = if (timer.paused) "已暂停" else "正在计时"
            val elapsedMillis = if (timer.paused) {
                timer.accumulatedMillis
            } else {
                timer.accumulatedMillis + (now.toEpochMilli() - timer.startedAtEpochMillis).coerceAtLeast(0)
            }
            return TimerNotificationContent(
                title = "Attention $status",
                text = "目标 $target · $status · 时长 ${formatElapsed(elapsedMillis)}",
                active = true,
                paused = timer.paused,
            )
        }

        private fun formatElapsed(elapsedMillis: Long): String {
            val totalMinutes = (elapsedMillis.coerceAtLeast(0) / 60_000L).toInt()
            return formatMinuteOfDay(totalMinutes)
        }
    }
}

internal data class TimerNotificationContent(
    val title: String,
    val text: String,
    val active: Boolean,
    val paused: Boolean,
)
