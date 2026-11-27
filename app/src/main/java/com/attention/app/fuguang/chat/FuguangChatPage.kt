package com.attention.app.fuguang.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.attention.app.fuguang.components.FuguangA1State
import com.attention.app.fuguang.components.FuguangAdjustmentCardUi
import com.attention.app.fuguang.components.FuguangB1Button
import com.attention.app.fuguang.components.FuguangButtonKind
import com.attention.app.fuguang.components.FuguangC2AiBubble
import com.attention.app.fuguang.components.FuguangC3UserBubble
import com.attention.app.fuguang.components.FuguangC4ThinkingBlock
import com.attention.app.fuguang.components.FuguangC5ReceiptRow
import com.attention.app.fuguang.components.FuguangC6SuggestionCapsules
import com.attention.app.fuguang.components.FuguangC7TimestampDivider
import com.attention.app.fuguang.components.FuguangComparisonRowUi
import com.attention.app.fuguang.components.FuguangFeishuCardUi
import com.attention.app.fuguang.components.FuguangK1AdjustmentCard
import com.attention.app.fuguang.components.FuguangK2FeishuCard
import com.attention.app.fuguang.components.FuguangK3ComparisonCard
import com.attention.app.fuguang.components.FuguangO1BottomSheet
import com.attention.app.fuguang.components.FuguangS1NavigationBar
import com.attention.app.fuguang.components.FuguangS3InputArea
import com.attention.app.fuguang.components.FuguangS5TabBar
import com.attention.app.fuguang.components.FuguangStateMode
import com.attention.app.fuguang.components.FuguangTextSegment
import com.attention.app.fuguang.components.FuguangW1WeekSwipe
import com.attention.app.fuguang.components.fuguangRoles
import com.attention.app.fuguang.components.fuguangType
import com.attention.agent.AgentOutcome
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 主 Agent 对话页（设计稿第 4 页）。
 *
 * 两种模式,由 [conversation] 决定:
 * - **真模型模式**([conversation] 非 null):发送的请求经会话入口到达助手运行时
 *   (`WorkspaceAgentRuntime.run`),终态映射为答复气泡;消息从空开始,建议胶囊不出现,
 *   「一周重排」的 K1/K2/K3/O1 剧本不走。模型未配置时运行时以 Failed 收敛,
 *   页面把失败原因原样讲给用户——宁可明说不可用,不静默降级。
 * - **演示模式**([conversation] 为 null,debug 独立入口与组件验收用):
 *   按「一周重排」剧本演完整链路。用到的族:S 骨架(导航/输入/发送/标签栏)、
 *   C 对话(气泡/思考/回执/建议/时间分隔)、K 卡片(调整卡/飞书卡/对比条)、
 *   O 弹层(三个落点)、B 按钮、A 状态(空态)。
 *
 * 页面自己不实现任何视觉数值:颜色、字号、尺寸全部来自 `FuguangDesignTokens`,
 * 页面只负责「装在哪、什么时候出现」。
 *
 * S1 的「更多」是「开新对话」:清空消息后落到 A1 空态(规范要求空态必须有出口)。
 */
@Composable
internal fun FuguangChatPage(
    modifier: Modifier = Modifier,
    showTabBar: Boolean = true,
    applyWindowInsets: Boolean = true,
    conversation: AgentConversation? = null,
    stateReady: Boolean = true,
) {
    val liveMode = conversation != null
    val messages = remember {
        mutableStateListOf<ChatItem>().apply {
            if (!liveMode) {
                addAll(
                    listOf(
                        ChatItem.User(FuguangChatDemoData.userText),
                        ChatItem.Agent(FuguangChatDemoData.agentRecognition),
                        ChatItem.Agent(FuguangChatDemoData.agentConstraint),
                    ),
                )
            }
        }
    }
    var composer by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf<ThinkingState?>(null) }
    var suggestions by remember { mutableStateOf(if (liveMode) emptyList() else FuguangChatDemoData.suggestions) }
    // 会话时间戳:真模型用打开对话那一刻的真实时刻,演示模式用剧本的固定时刻。
    val sessionStamp = remember(liveMode) {
        if (liveMode) "今天 ${LocalTime.now().format(STAMP_TIME_FORMAT)}" else FuguangChatDemoData.sessionStamp
    }
    var selectedTab by remember { mutableStateOf(CHAT_TAB_INDEX) }
    var adjustment by remember { mutableStateOf(FuguangChatDemoData.adjustmentCard) }
    var sheetOpen by remember { mutableStateOf(false) }
    var selectedRelocation by remember { mutableStateOf(FuguangChatDemoData.relocationOptions.first().id) }
    var weekOpen by remember { mutableStateOf(false) }
    var weekIndex by remember { mutableStateOf(WEEK_DEFAULT_INDEX) }
    var turn by remember { mutableStateOf(0) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size, thinking) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    /** 一次 Agent 回话：先思考再落地，落地后给出下一轮建议。(演示模式专用) */
    fun reply(build: () -> List<ChatItem>) {
        suggestions = emptyList()
        thinking = ThinkingState(THINKING_PROGRESS, loading = true)
        scope.launch {
            delay(THINKING_MILLIS)
            thinking = ThinkingState(THINKING_DONE, loading = false)
            delay(DONE_MILLIS)
            messages += build()
            thinking = null
            suggestions = FuguangChatDemoData.suggestions
        }
    }

    /**
     * 真模型模式的一次对话:用户气泡先落地,C4 转圈等运行时终态,终态映射成答复气泡。
     * 上一轮未结束前忽略新的发送(不排队);状态未加载完时把请求挡在本地,
     * 不让模型对着一份空状态做决定。
     */
    fun ask(text: String) {
        if (thinking != null) return
        if (!stateReady) {
            messages += ChatItem.Agent(FuguangTextSegment(text = STATE_NOT_READY_HINT))
            return
        }
        messages += ChatItem.User(text)
        composer = ""
        suggestions = emptyList()
        thinking = ThinkingState(LIVE_THINKING, loading = true)
        val entry = requireNotNull(conversation) { "ask 只在真模型模式下被调用" }
        scope.launch {
            // 运行时承诺仅协程取消时抛出;这里再兜一层,任何意外都折算成对用户可读的失败。
            val outcome = runCatching { entry.ask(text) }
                .getOrElse { failure ->
                    AgentOutcome.Failed(failure.message ?: failure.javaClass.simpleName, emptyList())
                }
            thinking = null
            messages += ChatItem.Agent(FuguangTextSegment(text = outcome.toReplyText()))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (applyWindowInsets) {
                        Modifier.systemBarsPadding().imePadding()
                    } else {
                        Modifier
                    },
                ),
        ) {
            // S1 导航条（内含 S2 目标胶囊）。「更多」= 开新对话，落到 A1 空态。
            // 组件包 11-15 版把 goalLabel 并成 summaryLabel，并去掉导航条上的计数徽标
            // （待确认条数改由日程块上的 AdjustmentBadge 表达），因此只传胶囊文案。
            FuguangS1NavigationBar(
                name = FuguangChatDemoData.agentName,
                roleLabel = FuguangChatDemoData.agentRole,
                summaryLabel = FuguangChatDemoData.goalLabel,
                onMore = {
                    messages.clear()
                    if (!liveMode) {
                        adjustment = FuguangChatDemoData.adjustmentCard
                        suggestions = FuguangChatDemoData.suggestions
                    }
                },
            )

            if (messages.isEmpty()) {
                // A1 空态：空、载、错三态都必须有出口，这里给「先写一句」。
                Box(
                    Modifier.weight(1f).padding(horizontal = PAGE_HORIZONTAL),
                    contentAlignment = Alignment.Center,
                ) {
                    FuguangA1State(
                        mode = FuguangStateMode.EMPTY,
                        title = "这周还空着",
                        description = "说一句话，我先给你排",
                        actionLabel = "先写一句",
                        onAction = { composer = "帮我把这周排一下" },
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scroll)
                        .padding(horizontal = PAGE_HORIZONTAL),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FuguangC7TimestampDivider(sessionStamp)
                    messages.forEachIndexed { index, item ->
                        when (item) {
                            is ChatItem.User -> FuguangC3UserBubble(
                                text = item.text,
                                modifier = Modifier.testTag("message-$index"),
                            )

                            is ChatItem.Agent -> FuguangC2AiBubble(
                                segments = listOf(item.segment),
                                // 连续 AI 消息只有首条带头像，与规范 P11 一致
                                showAvatar = index == 0 || messages[index - 1] !is ChatItem.Agent,
                                modifier = Modifier.testTag("message-$index"),
                            )

                            is ChatItem.Proposal -> FuguangK1AdjustmentCard(
                                card = adjustment,
                                onToggleItem = { id ->
                                    adjustment = adjustment.copy(
                                        items = adjustment.items.map {
                                            if (it.id == id) it.copy(accepted = !it.accepted) else it
                                        },
                                    )
                                },
                                // 主按钮：提交四条调整，接着把「撞车」摊开
                                onPrimary = {
                                    adjustment = adjustment.copy(status = "已提交")
                                    reply {
                                        listOf(
                                            ChatItem.Agent(FuguangChatDemoData.agentConflict),
                                            ChatItem.Feishu(FuguangChatDemoData.feishuCard),
                                            ChatItem.Comparison(
                                                FuguangChatDemoData.comparisonTitle,
                                                FuguangChatDemoData.comparisonRows,
                                            ),
                                            ChatItem.FindPlace,
                                        )
                                    }
                                },
                                // 次按钮：回路，不跳屏（规范：主按钮只往前）
                                onSecondary = {
                                    reply { listOf(ChatItem.Agent(FuguangChatDemoData.agentHandOver)) }
                                },
                            )

                            is ChatItem.Feishu -> FuguangK2FeishuCard(card = item.card)

                            is ChatItem.Comparison -> FuguangK3ComparisonCard(
                                title = item.title,
                                rows = item.rows,
                            )

                            is ChatItem.FindPlace -> FuguangB1Button(
                                label = "帮我找位置",
                                kind = FuguangButtonKind.PRIMARY,
                                onClick = { sheetOpen = true },
                                modifier = Modifier.testTag("find-place"),
                            )

                            is ChatItem.Receipt -> FuguangC5ReceiptRow(
                                text = item.text,
                                relativeTime = item.time,
                            )
                        }
                    }
                    thinking?.let { state ->
                        FuguangC4ThinkingBlock(text = state.label, loading = state.loading)
                    }
                    if (thinking == null) {
                        FuguangC6SuggestionCapsules(
                            suggestions = suggestions,
                            onSelect = { picked ->
                                // 规范 C6：只把句子填进输入框，不发送
                                composer = picked
                            },
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = PAGE_HORIZONTAL, vertical = 10.dp)) {
                FuguangS3InputArea(
                    value = composer,
                    onValueChange = { composer = it },
                    onSend = {
                        val text = composer.trim()
                        if (text.isNotEmpty()) {
                            if (liveMode) {
                                ask(text)
                            } else {
                                messages += ChatItem.User(text)
                                composer = ""
                                turn += 1
                                val first = turn == 1
                                reply {
                                    if (first) {
                                        // 第一次请求：认出要挪什么，并给出 K1 调整卡
                                        listOf(
                                            ChatItem.Agent(FuguangChatDemoData.agentRecognition),
                                            ChatItem.Proposal(adjustment),
                                        )
                                    } else {
                                        listOf(ChatItem.Agent(FuguangChatDemoData.agentConstraint))
                                    }
                                }
                            }
                        }
                    },
                )
                // S5 只在「一屏一世界」的独立预览里出现：装进应用外壳后由外壳提供底部导航。
                if (showTabBar) {
                    FuguangS5TabBar(selectedIndex = selectedTab, onSelect = { selectedTab = it })
                }
            }
        }

        // O1 底部弹层：全站唯一的浮层，最多三个选项，每个都写清代价
        if (sheetOpen) {
            FuguangO1BottomSheet(
                title = "这段群面放哪儿？",
                subtitle = "三个位置代价不一样，你挑一个。",
                options = FuguangChatDemoData.relocationOptions,
                selectedId = selectedRelocation,
                onSelect = { selectedRelocation = it },
                // 规范 P8：落点定案不能回单日页，要先看到七天全景（06）
                onConfirm = {
                    sheetOpen = false
                    weekOpen = true
                },
                onCancel = { sheetOpen = false },
            )
        }

        // 06 整周详细：落点定案后必须看到七天全景（规范 P8「为什么没有 05→03」）
        if (weekOpen) {
            Box(Modifier.fillMaxSize().background(fuguangRoles().ink.copy(alpha = 0.18f))) {
                Surface(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = fuguangRoles().paper,
                ) {
                    Column(modifier = Modifier.padding(PAGE_HORIZONTAL)) {
                        Text("改好的这周", style = fuguangType().t17, color = fuguangRoles().ink)
                        Text("左右滑动看七天", style = fuguangType().t11, color = fuguangRoles().ink3)
                        FuguangW1WeekSwipe(
                            days = FuguangChatDemoData.weekDays,
                            selectedIndex = weekIndex,
                            onSelectedChange = { weekIndex = it },
                        )
                        FuguangB1Button(
                            label = "就这么定",
                            kind = FuguangButtonKind.PRIMARY,
                            onClick = {
                                weekOpen = false
                                messages += ChatItem.Receipt(
                                    text = "已应用 ${adjustment.items.count { it.accepted }} 项调整",
                                    time = "刚刚",
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("week-confirm"),
                        )
                    }
                }
            }
        }
    }
}

/** 消息流里能出现的东西：说话、卡片、动作、回执。 */
private sealed interface ChatItem {
    data class User(val text: String) : ChatItem
    data class Agent(val segment: FuguangTextSegment) : ChatItem
    data class Proposal(val card: FuguangAdjustmentCardUi) : ChatItem
    data class Feishu(val card: FuguangFeishuCardUi) : ChatItem
    data class Comparison(val title: String, val rows: List<FuguangComparisonRowUi>) : ChatItem
    data object FindPlace : ChatItem
    data class Receipt(val text: String, val time: String) : ChatItem
}

/**
 * 对话页的会话入口:一次请求换一个领域终态。
 * 生产装配由组合根用 `WorkspaceAgentRuntime.run` 实现;传 null 即演示模式。
 */
internal fun interface AgentConversation {
    suspend fun ask(request: String): AgentOutcome
}

/**
 * 助手终态到答复气泡文本的映射。答复原样透出;失败与预算耗尽翻译成
 * 用户能读懂、也讲清后果的话——失败原因里没有密钥与请求体(运行时已保证),
 * 原样展示是「宁可明说不可用」的最小实现。
 */
internal fun AgentOutcome.toReplyText(): String = when (this) {
    is AgentOutcome.Answered -> text
    is AgentOutcome.Failed -> "我没能完成这次请求:$reason"
    is AgentOutcome.BudgetExhausted -> "这次的思考预算用完了,还没有得出结果。换个更具体的说法再试一次?"
    is AgentOutcome.PendingConfirmation ->
        "这个操作需要你确认后才会执行,确认卡片还没有上线,这次没有做任何改动。"
}

/** C4 的两种状态：进行中（转圈）与完成（勾，文案不变灰）。 */
private data class ThinkingState(val label: String, val loading: Boolean)

private const val THINKING_MILLIS = 900L
private const val DONE_MILLIS = 500L
private const val CHAT_TAB_INDEX = 0
private const val WEEK_DEFAULT_INDEX = 2
private val PAGE_HORIZONTAL = 16.dp
private const val THINKING_PROGRESS = "正在核对这周的空档…"
private const val THINKING_DONE = "已核对 12 个空闲段"
private const val LIVE_THINKING = "浮光正在想…"
private const val STATE_NOT_READY_HINT = "你的数据还在读取,稍等一下再发,免得我对着一份空状态做决定。"
private val STAMP_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
