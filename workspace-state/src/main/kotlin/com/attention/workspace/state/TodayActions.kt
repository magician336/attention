package com.attention.workspace.state

import com.attention.domain.ScheduleEntry

/**
 * 今天工作台的 intent 接口。页面只发出 intent，实际落地（写 Room / DataStore、
 * 启动前台计时服务、申请通知权限、给出通知不可用的提示）由组合根 adapter 完成；
 * 页面因此拿不到 `AttentionViewModel`，也拿不到仓储，更拿不到任何 Android API
 * （ADR-0005 第 4 节「Android adapter 归属组合根」）。
 *
 * 计时相关的 intent 在这里而不是另一套接口：ADR-0005 第 2 节规定每个页面只暴露一对
 * `*ScreenState` / `*Actions`，今天工作台目前也没有第二个页面级入口需要拆分。
 */
interface TodayActions {
    /**
     * 记一笔实际投入。[targetId] 为 null 表示记为未归属活动。
     * [planningDate] 是页面正在显示的那一个规划日，记录因此落在用户看到的规划日上。
     */
    fun recordTime(planningDate: String, minutes: Int, targetId: String?)

    /**
     * 编辑一条实际投入，只调整时长、目标归属与备注。
     * 记录的规划日与来源由领域层保持不变，因此这里不接收它们。
     */
    fun editTime(entryId: String, minutes: Int, targetId: String?, note: String)

    /** 删除一条实际投入，并给出一次撤销机会。撤销的提示由组合根展示。 */
    fun deleteTime(entryId: String)

    /** 保存规划日界线与每日容量。[dailyCapacityMinutes] 为 null 表示清除容量上限。 */
    fun saveDailyPlanningSettings(boundaryMinutes: Int, dailyCapacityMinutes: Int?)

    /** 选择周起始日。 */
    fun selectWeekStartDay(day: Int)

    /**
     * 开始计时。[targetId] 为 null 表示开始一段未归属计时。
     *
     * 真正的平台动作（启动前台计时服务、撤掉恢复提示、在通知不可用时给出提示）
     * 由组合根 adapter 在这次 intent 成功之后完成，页面不触达 Android API。
     */
    fun startTimer(targetId: String?)

    /**
     * 从「今日安排」里的一条日程起计时。
     *
     * 与 [startTimer] 分开是有意的：这里必须把整条 [entry] 一起带出，产出的时间记录
     * 才保留同一个日程来源关联（`TimeEntrySource.SCHEDULE`）。只传 `targetId` 会丢掉来源。
     */
    fun startTimerFromEntry(entry: ScheduleEntry)

    /** 暂停计时。计时器生命周期本身不随暂停变化，前台服务仍在运行。 */
    fun pauseTimer()

    /** 恢复一段已暂停的计时。 */
    fun resumeTimer()

    /**
     * 「从此刻继续」：放弃之前累计的时长，从现在重新开始计。
     * 组合根 adapter 在成功之后重新启动前台计时服务并撤掉恢复提示。
     */
    fun recoverTimer()

    /**
     * 结束并保存计时。结算、投入经验与达成里程碑仍由领域层计算，页面不参与。
     * 组合根 adapter 在成功之后停掉前台计时服务。
     */
    fun stopTimer()

    /** 把正在计时的目标切换到 [targetId]（null 表示未归属活动）。 */
    fun switchTimer(targetId: String?)

    /**
     * 申请通知权限。页面只在用户真的设置过提醒时刻时发出它，
     * 真正的申请动作（找到 Activity、发起权限对话框）由组合根 adapter 完成。
     */
    fun requestNotificationPermission()

    /** 完成首次设置流程：不再显示首次设置卡片。 */
    fun finishOnboarding()

    /**
     * 保存首次设置里的一步规划设置。[dailyCapacityMinutes] 为 null 表示清除容量上限。
     * 三个字段一起给出，因为这一流程写的就是整份规划设置。
     */
    fun saveOnboardingPlanningSettings(
        boundaryMinutes: Int,
        weekStartDay: Int,
        dailyCapacityMinutes: Int?,
    )

    /**
     * 创建首个计划。[onSuccess] 在创建成功后调用，页面据此进入下一步；
     * 组合根 adapter 需要它是因为目标 id 由仓储生成，只有 adapter 知道保存是否成功。
     */
    fun createOnboardingTarget(title: String, onSuccess: () -> Unit)

    /** 创建首次设置里的第一条日程。提醒留空时不带 [reminderMinuteOfDay]。 */
    fun createOnboardingSchedule(draft: OnboardingScheduleDraft)

    /**
     * 更新「今日安排」里的一条日程。[reminderMinuteOfDay] 是提醒时刻在一天内的分钟数，
     * 绝对时刻的换算由组合根 adapter 按注入的时区完成。
     */
    fun updateTodaySchedule(entry: ScheduleEntry, reminderMinuteOfDay: Int?)

    /** 确认一条日程已完成并记入时间记录。 */
    fun confirmScheduleCompleted(entryId: String)

    /** 在已完成与未完成之间切换一条日程。 */
    fun toggleScheduleCompleted(entryId: String)
}

/**
 * 首次设置里「创建第一条日程」的那份草稿。
 *
 * 领域对象 `ScheduleEntry` 由组合根 adapter 构造：页面不碰领域类型，
 * 也不决定一条日程在数据库里的样子。
 */
data class OnboardingScheduleDraft(
    val planningDate: String,
    val title: String,
    val startMinute: Int?,
    val endMinute: Int?,
    val estimatedMinutes: Int?,
    val note: String,
    val targetId: String?,
)
