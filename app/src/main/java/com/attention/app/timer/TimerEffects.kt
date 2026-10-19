package com.attention.app.timer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.attention.domain.AttentionState

/**
 * Platform effects for the persisted Attention timer.
 *
 * Domain commands own the timer state. This seam is called only after a command
 * has committed successfully, so a platform failure cannot roll back a saved
 * time record. The methods are intentionally idempotent because state
 * restoration and user intents can observe the same transition more than once.
 */
interface TimerEffects {
    fun started()
    fun paused()
    fun resumed()
    fun stopped()
    fun recovered()
    fun synchronize(state: AttentionState)
}

/**
 * Android adapter for [TimerEffects]. It owns the recovery notification,
 * notification permission feedback, and foreground service lifecycle. Callers
 * do not need to know which Android API implements each effect.
 */
class AndroidTimerEffects internal constructor(
    private val platform: TimerPlatform,
    private val onFeedback: (String) -> Unit,
) : TimerEffects {
    private var foregroundServiceRequested = false

    constructor(
        context: Context,
        onFeedback: (String) -> Unit = {},
    ) : this(AndroidTimerPlatform(context.applicationContext), onFeedback)

    override fun started() {
        if (foregroundServiceRequested) return
        clearRecoveryPrompt()
        ensureForegroundService()
    }

    override fun paused() {
        ensureForegroundService()
    }

    override fun resumed() {
        ensureForegroundService()
    }

    override fun stopped() {
        if (!foregroundServiceRequested) return
        clearRecoveryPrompt()
        stopForegroundService()
    }

    override fun recovered() {
        clearRecoveryPrompt()
        ensureForegroundService()
    }

    override fun synchronize(state: AttentionState) {
        if (state.activeTimer == null) {
            stopForegroundService()
        } else {
            ensureForegroundService()
        }
    }

    private fun clearRecoveryPrompt() {
        runPlatformEffect(
            failureMessage = "计时已保存，但恢复提示清理失败。",
            effect = platform::clearRecoveryPrompt,
        )
    }

    private fun ensureForegroundService() {
        if (foregroundServiceRequested) return
        if (!platform.notificationsEnabled()) {
            onFeedback("通知权限未开启，计时仍会保存；通知栏控制不可用。")
        }
        val started = runPlatformEffect(
            failureMessage = "计时已保存，但前台计时服务启动失败。",
            effect = platform::startForegroundService,
        )
        if (started) foregroundServiceRequested = true
    }

    private fun stopForegroundService() {
        if (!foregroundServiceRequested) return
        val stopped = runPlatformEffect(
            failureMessage = "计时已结束，但前台计时服务停止失败。",
            effect = platform::stopForegroundService,
        )
        if (stopped) foregroundServiceRequested = false
    }

    private fun runPlatformEffect(failureMessage: String, effect: () -> Unit): Boolean =
        runCatching(effect)
            .onFailure { onFeedback(failureMessage) }
            .isSuccess
}

/** Small port that keeps Android calls behind [AndroidTimerEffects]. */
internal interface TimerPlatform {
    fun clearRecoveryPrompt()
    fun startForegroundService()
    fun stopForegroundService()
    fun notificationsEnabled(): Boolean
}

private class AndroidTimerPlatform(
    private val context: Context,
) : TimerPlatform {
    override fun clearRecoveryPrompt() {
        AttentionBootReceiver.cancelRecoveryPrompt(context)
    }

    override fun startForegroundService() {
        ContextCompat.startForegroundService(context, AttentionTimerService.intent(context))
    }

    override fun stopForegroundService() {
        context.stopService(AttentionTimerService.intent(context))
    }

    override fun notificationsEnabled(): Boolean =
        (BuildVersion.isNotificationPermissionGranted(context) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled())
}

/** Isolated API check so the adapter remains easy to exercise with a fake port. */
private object BuildVersion {
    fun isNotificationPermissionGranted(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
