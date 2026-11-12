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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 主 Agent 对话页（设计稿第 4 页）。
 *
 * 这一版把「一周重排」那条完整链路走通，用到的族：
 * S 骨架（导航/输入/发送/标签栏）、C 对话（气泡/思考/回执/建议/时间分隔）、
 * K 卡片（调整卡/飞书卡/对比条）、O 弹层（三个落点）、B 按钮、A 状态（空态）。
 *
 * 页面自己不实现任何视觉数值：颜色、字号、尺寸全部来自 `FuguangDesignTokens`，
 * 页面只负责「装在哪、什么时候出现」。
 *
 * 链路：发送 → C4 思考 → C2 答复 + K1 调整卡 → 采纳 → K2 飞书卡 + K3 对比条
 * → B1「帮我找位置」→ O1 落点弹层 → C5 回执行。
 * S1 的「更多」是「开新对话」：清空消息后落到 A1 空态（规范要求空态必须有出口）。
 */
@Composable
internal fun FuguangChatPage(
    modifier: Modifier = Modifier,
    showTabBar: Boolean = true,
    applyWindowInsets: Boolean = true,
) {
    val messages = remember {
        mutableStateListOf(
            ChatItem.User(FuguangChatDemoData.userText),
            ChatItem.Agent(FuguangChatDemoData.agentRecognition),
            ChatItem.Agent(FuguangChatDemoData.agentConstraint),
        )
    }
    var composer by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf<ThinkingState?>(null) }
    var suggestions by remember { mutableStateOf(FuguangChatDemoData.suggestions) }
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

    /** 一次 Agent 回话：先思考再落地，落地后给出下一轮建议。 */
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
            // S1 导航条（内含 S2 目标胶囊与 B2 计数徽标）。「更多」= 开新对话，落到 A1 空态。
            FuguangS1NavigationBar(
                name = FuguangChatDemoData.agentName,
                roleLabel = FuguangChatDemoData.agentRole,
                goalLabel = FuguangChatDemoData.goalLabel,
                // B2 计数徽标：只有还有待确认的调整时才出现，规格上限 99+
                pendingCount = adjustment.items.count { it.accepted }.takeIf { adjustment.status == "待确认" },
                onMore = {
                    messages.clear()
                    adjustment = FuguangChatDemoData.adjustmentCard
                    suggestions = FuguangChatDemoData.suggestions
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
                    FuguangC7TimestampDivider(FuguangChatDemoData.sessionStamp)
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

/** C4 的两种状态：进行中（转圈）与完成（勾，文案不变灰）。 */
private data class ThinkingState(val label: String, val loading: Boolean)

private const val THINKING_MILLIS = 900L
private const val DONE_MILLIS = 500L
private const val CHAT_TAB_INDEX = 0
private const val WEEK_DEFAULT_INDEX = 2
private val PAGE_HORIZONTAL = 16.dp
private const val THINKING_PROGRESS = "正在核对这周的空档…"
private const val THINKING_DONE = "已核对 12 个空闲段"
