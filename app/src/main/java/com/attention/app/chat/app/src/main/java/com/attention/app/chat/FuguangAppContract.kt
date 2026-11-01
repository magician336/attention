package com.attention.app.chat

import androidx.compose.ui.graphics.Color

enum class FuguangScheduleRange(val label: String) {
    DAY("日"),
    THREE_DAYS("三天"),
    WEEK("周"),
    MONTH("月"),
}

enum class FuguangScheduleTone {
    NORMAL,
    ACTIVE,
    WARNING,
}

data class FuguangScheduleNotice(
    val source: String,
    val message: String,
)

data class FuguangScheduleItem(
    val id: String,
    val startLabel: String,
    val endLabel: String,
    val title: String,
    val detail: String,
    val tone: FuguangScheduleTone = FuguangScheduleTone.NORMAL,
)

data class FuguangGapItem(
    val id: String,
    val label: String,
    val afterItemIndex: Int = 0,
)

data class FuguangDayScheduleState(
    val title: String,
    val subtitle: String,
    val selectedRange: FuguangScheduleRange = FuguangScheduleRange.DAY,
    val notice: FuguangScheduleNotice? = null,
    val items: List<FuguangScheduleItem>,
    val gaps: List<FuguangGapItem> = emptyList(),
    val footer: String = "点按任意日程即可修改时间 · 空白处长按新建",
)

data class FuguangWeekDay(
    val index: Int,
    val weekday: String,
    val dayOfMonth: String,
    val isToday: Boolean = false,
)

data class FuguangWeekEvent(
    val id: String,
    val dayIndex: Int,
    val startMinuteOfDay: Int,
    val timeLabel: String,
    val title: String,
    val tone: FuguangScheduleTone,
)

data class FuguangWeekScheduleState(
    val title: String,
    val subtitle: String,
    val selectedRange: FuguangScheduleRange = FuguangScheduleRange.WEEK,
    val days: List<FuguangWeekDay>,
    val events: List<FuguangWeekEvent>,
    val weeklyActualHours: String,
    val weeklyTargetHours: String,
    val weeklyDetail: String,
    val footer: String = "左右滑动切换日 / 周 · 点按某一天进入该日视图",
)

data class FuguangLearningGoal(
    val id: String,
    val title: String,
    val detail: String,
    val progress: Float,
    val color: Color,
)

data class FuguangInvestmentSegment(
    val label: String,
    val value: Float,
    val color: Color,
)

data class FuguangReviewItem(
    val id: String,
    val title: String,
    val note: String,
)

data class FuguangCountdown(
    val title: String,
    val dateLabel: String,
    val days: String,
)

data class FuguangLearningState(
    val title: String,
    val subtitle: String,
    val reportLabel: String,
    val longTermTitle: String,
    val longTermMeta: String,
    val goals: List<FuguangLearningGoal>,
    val weeklyTitle: String,
    val weeklyActual: String,
    val weeklyRate: String,
    val weeklyProgress: Float,
    val weeklySegments: List<FuguangInvestmentSegment>,
    val weeklyDetail: String,
    val reviewTitle: String,
    val reviewMeta: String,
    val reviewItems: List<FuguangReviewItem>,
    val chartTitle: String,
    val chartMeta: String,
    val chartValues: List<Float>,
    val countdowns: List<FuguangCountdown>,
)

data class FuguangAuthorizationState(
    val title: String = "变更授权 · 3 级",
    val subtitle: String = "判定条件可程序化，不靠「重要性」判断",
    val silentTitle: String = "① 静默",
    val silentCondition: String = "同一天内 + 时长不变 + 非外部承诺",
    val silentExampleTitle: String = "晨读 · 雅思听力精听",
    val silentExampleDetail: String = "45 分钟 · 已完成后由助手顺延",
    val notifyTitle: String = "② 事后通知",
    val notifyCondition: String = "跨天 / 时长变动 >15 分钟 / 动到你临时排的事",
    val confirmTitle: String = "③ 事前确认",
    val confirmCondition: String = "外部承诺 / 跨 Agent 冲突 / 判定不确定",
    val confirmMessage: String = "李老师：周四课调至周五 10:00 · 与你 19:30 雅思写作冲突",
    val ironRule: String = "判定不确定时一律升级到 ③。宁可多问一次，不可偷偷改一次——前者只是烦，后者是永久失信。",
    val undoChangeId: String = "authorization-undo",
)

data class FuguangSchedulingRule(
    val id: String,
    val index: String,
    val title: String,
    val body: String,
    val accent: Color,
)

data class FuguangSchedulingRulesState(
    val title: String = "排程规则 · 5 条",
    val subtitle: String = "五问的结论，开发按这个实现，不要再各自发明",
    val rules: List<FuguangSchedulingRule>,
    val footer: String = "一句话：宁可少排、宁可多问；不可编数字、不可偷改。",
)

data class FuguangAssistantCreationState(
    val title: String = "浮光主 Agent",
    val subtitle: String = "正在为你建一个小助手",
    val userMessage: String = "我想考雅思，目标 7.5，12 月 20 号考。想要个长期计划盯着我。",
    val agentMessage: String = "长期的事，我给你建个雅思助手。它只管做什么、多久、你什么时段学得进去；具体几点由我排，不然会跟课表打架。",
    val formTitle: String = "新建小助手",
    val formSubtitle: String = "跑起来之前，只问三件事",
    val goalLabel: String = "雅思 · 目标 7.5  2026-12-20 考试 · 剩 53 天",
    val hoursOptions: List<Int> = listOf(6, 10, 12),
    val selectedHours: Int = 10,
    val input: String = "",
    val constraintLabel: String = "周日晚家教不排 · 最晚到 21:30",
    val createLabel: String = "创建，让它先出草案",
    val note: String = "建好后它先出草案，我看过你的课表再回你确认——不会直接改日程。",
    val nextSteps: List<String> = listOf("它先出草案，只看你能投入多久", "我落成钟点，排出本周", "你点确认，才进日程"),
    val suggestions: List<FuguangChatSuggestion> = emptyList(),
    val inputPlaceholder: String = "说点什么…",
)

data class FuguangAssistantPlanState(
    val title: String = "雅思助手",
    val subtitle: String = "小 Agent · 正在排计划",
    val goalChip: String = "目标 7.5",
    val heroTitle: String = "目标 雅思 7.5",
    val heroDetail: String = "12 月 20 日考试 · 剩 53 天",
    val heroPath: String = "基础 → 单项 → 套题 → 冲刺",
    val weeklyQuota: String = "10h",
    val availabilityTitle: String = "档期体检 · 未来 14 天",
    val availabilityChip: String = "可用 10.5 h/周",
    val availabilityRows: List<Pair<String, String>> = emptyList(),
    val availabilityNote: String = "能排出 10 小时，余量只剩 30 分钟——够，但不宽松。",
    val allocationTitle: String = "每周 10 小时怎么分",
    val allocationAction: String = "由我定 · 可改",
    val allocationSegments: List<FuguangInvestmentSegment> = emptyList(),
    val allocationDetail: String = "听力 3.5h · 阅读 2.5h · 写作 2.5h · 口语 1.5h",
    val anchorsTitle: String = "建议的时段 · 只给锚点，不给钟点",
    val anchors: List<String> = emptyList(),
    val note: String = "为什么不给钟点：你的课表每周都在变。我定死钟点，会跟课程、和主 Agent 已排的事打架——钟点归它。",
    val input: String = "",
    val status: String = "草案已回传主 Agent · 等它落成具体钟点",
    val suggestions: List<FuguangChatSuggestion> = emptyList(),
    val inputPlaceholder: String = "说说这个时段合不合适…",
)

data class FuguangPlanRow(
    val label: String,
    val duration: String,
)

data class FuguangAgentConfirmationState(
    val title: String = "浮光主 Agent",
    val subtitle: String = "统筹中 · 雅思助手在等确认",
    val agentMessage: String = "雅思助手交回草案了。我按你的课表落成钟点——这周 7 天排下 6 天，周六差 45 分钟。",
    val planTitle: String = "本周计划 · 10/26–11/01",
    val shortfall: String = "差 45 min",
    val planRows: List<FuguangPlanRow> = emptyList(),
    val decisionTitle: String = "周六这 45 分钟，你想怎么办？",
    val choices: List<String> = emptyList(),
    val selectedChoice: Int = 0,
    val input: String = "",
    val confirmLabel: String = "按①写入日程",
    val cancelLabel: String = "都别改",
    val note: String = "写入后日程页立刻更新，并在「最近动态」留痕；24 小时内可撤回。",
    val suggestions: List<FuguangChatSuggestion> = emptyList(),
    val inputPlaceholder: String = "说点什么…",
)

data class FuguangResponsibilityState(
    val title: String = "谁定什么 · 责任边界",
    val subtitle: String = "三个角色各管一段，越界就出乱子",
    val roles: List<Pair<String, String>> = emptyList(),
    val ironRule: String = "铁律：小 Agent 不碰钟点，主 Agent 不改配额——要改，先回来问你。",
    val explanationTitle: String = "为什么必须这么分",
    val explanation: String = "配额是「我想投入多少」，一个月才变一次；钟点是「这周哪几天有空」，每周都在变。混在一处就会互相覆盖。",
)

data class FuguangAppState(
    val daySchedule: FuguangDayScheduleState,
    val weekSchedule: FuguangWeekScheduleState,
    val conversationHub: FuguangConversationHubState,
    val mainAgentChat: FuguangChatUiState,
    val assistantDetail: FuguangAssistantDetailState,
    val learning: FuguangLearningState,
    val authorization: FuguangAuthorizationState,
    val schedulingRules: FuguangSchedulingRulesState,
    val assistantCreation: FuguangAssistantCreationState,
    val assistantPlan: FuguangAssistantPlanState,
    val agentConfirmation: FuguangAgentConfirmationState,
    val responsibility: FuguangResponsibilityState,
)

/** 任务 2 实现这个接口即可把所有页面的用户动作接到 ViewModel，不需要改 UI 组件。 */
interface FuguangAppActions {
    fun onNavigate(destination: FuguangBottomDestination) = Unit
    fun onBack() = Unit
    fun onOpenAgent(agentId: String) = Unit
    fun onOpenSchedule(id: String) = Unit
    fun onAddSchedule() = Unit
    fun onOpenNotice() = Unit
    fun onSelectScheduleRange(range: FuguangScheduleRange) = Unit
    fun onSelectWeekDay(dayIndex: Int) = Unit
    fun onOpenWeeklyReport() = Unit
    fun onOpenGoal(goalId: String) = Unit
    fun onOpenReviewItem(itemId: String) = Unit
    fun onChatInputChange(value: String) = Unit
    fun onSendChat() = Unit
    fun onSuggestionClick(suggestion: FuguangChatSuggestion) = Unit
    fun onSelectWeeklyHours(hours: Int) = Unit
    fun onEditConstraints() = Unit
    fun onCreateAssistant() = Unit
    fun onSelectPlanChoice(index: Int) = Unit
    fun onConfirmPlan() = Unit
    fun onCancelPlan() = Unit
    fun onUndoChange(changeId: String) = Unit
}

object FuguangNoOpActions : FuguangAppActions




