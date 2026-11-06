package com.attention.app.fuguang.week.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.attention.app.fuguang.week.components.FuguangAgentHeader
import com.attention.app.fuguang.week.components.FuguangChatBottomNav
import com.attention.app.fuguang.week.components.FuguangChatComposer
import com.attention.app.fuguang.week.components.FuguangChatMessageBubble
import com.attention.app.fuguang.week.components.FuguangChatTimestamp
import com.attention.app.fuguang.week.components.FuguangMessageAuthor
import com.attention.app.fuguang.week.components.FuguangChatMessageUi
import com.attention.app.fuguang.week.components.fuguangWeekColors
import com.attention.app.fuguang.week.components.fuguangWeekDims
import com.attention.app.fuguang.week.components.fuguangWeekType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 屏 01「她先开口」：纯文字对话页。
 *
 * 按《浮光组件规范》P7 的隶属关系组装——S1 导航条 / 消息流（C7 · C3 · C2+C1 · C4 · C6）/
 * S3 输入区（含 S4）/ S5 标签栏。页面自己不实现任何视觉数值：尺寸与颜色全部取自
 * 组件包的令牌，页面只负责「装在哪、什么时候出现」。
 *
 * 规范里屏 01 有、组件包没有的两件（C4 思考块、C6 建议胶囊）在本文件补齐，尺寸照
 * 规范 P11 的数值写，颜色仍用令牌推导，不引入新的十六进制。
 *
 * 真机上这一步还接不上 Agent：答复是示例数据的固定回放，用来验收「发出去 → 等 →
 * 回来 → 给建议」这条交互骨架。接入真实推理是后续任务。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FuguangChatPage(modifier: Modifier = Modifier) {
    val dims = fuguangWeekDims()
    val messages = remember {
        mutableStateListOf(
            FuguangWeekSampleData.userMessage,
            FuguangWeekSampleData.agentRecognition,
            FuguangWeekSampleData.agentConstraint,
        )
    }
    var composer by remember { mutableStateOf("") }
    // C4 只有一个状态位：null 表示没有正在进行的思考
    var thinking by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf(DEFAULT_SUGGESTIONS) }
    var selectedTab by remember { mutableStateOf(CHAT_TAB_INDEX) }
    var replyIndex by remember { mutableStateOf(0) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    // 新消息到就滚到底：会话页的默认视口永远是最新一条
    LaunchedEffect(messages.size, thinking) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 页面自己吃系统栏与输入法内边距：导航条不再被状态栏压住，
            // 输入区跟着键盘上移（配合 Manifest 的 adjustResize）。
            .systemBarsPadding()
            .imePadding(),
    ) {
        // S1 导航条（内嵌 S2 目标胶囊与图标钮）
        Column(modifier = Modifier.padding(horizontal = dims.screenHorizontal, vertical = 12.dp)) {
            FuguangAgentHeader(onMore = {})
        }

        // 消息流
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = dims.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(dims.itemGap),
        ) {
            FuguangChatTimestamp("今天 21:47")
            messages.forEachIndexed { index, message ->
                FuguangChatMessageBubble(
                    message = message,
                    modifier = Modifier.testTag("fuguang-message-$index"),
                )
            }
            thinking?.let { label ->
                FuguangThinkingBlock(text = label, done = false)
            }
            if (thinking == null) {
                FuguangSuggestionChips(
                    items = suggestions,
                    onPick = { picked ->
                        // 规范 C6：只填进输入框，不发送
                        composer = picked
                    },
                )
            }
        }

        // S3 输入区 + S4 发送键，S5 标签栏
        Column(modifier = Modifier.padding(horizontal = dims.screenHorizontal, vertical = 10.dp)) {
            FuguangChatComposer(
                value = composer,
                onValueChange = { composer = it },
                onSend = {
                    val text = composer.trim()
                    if (text.isNotEmpty()) {
                        messages += FuguangChatMessageUi(
                            id = "user-${messages.size}",
                            author = FuguangMessageAuthor.USER,
                            text = text,
                        )
                        composer = ""
                        suggestions = emptyList()
                        thinking = THINKING_PROGRESS
                        scope.launch {
                            delay(THINKING_MILLIS)
                            messages += nextReply(replyIndex++)
                            thinking = null
                            suggestions = DEFAULT_SUGGESTIONS
                        }
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            FuguangChatBottomNav(
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )
        }
    }
}

/**
 * C4 思考块：AI 还在做事时的短条。规范 P11 定为高 28 / 内距 6·10 / 圆角 10 /
 * 字 11·ink-3 / line-2 底，完成时只把图标换成勾。
 *
 * 这里只实现「进行」一态：本页的完成态由消息落地代替（思考块整条消失），
 * 避免出现「勾还在、消息已到」的双重信号。
 */
@Composable
private fun FuguangThinkingBlock(text: String, done: Boolean) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Surface(
        modifier = Modifier.testTag("fuguang-thinking"),
        shape = RoundedCornerShape(10.dp),
        color = colors.surfaceMuted,
    ) {
        Row(
            modifier = Modifier.height(28.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (done) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.primaryDark,
                    modifier = Modifier.size(10.dp),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = colors.primaryDark,
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(text = text, style = type.micro, color = colors.textMuted)
        }
    }
}

/**
 * C6 建议胶囊：把用户可能想说的话先摆出来，点一下**只填不发**（规范 P11「只填，不发」）。
 * 高 28 / 圆角 15 / 内距 6·12 / 字 11.5·main-deep / surface 底 + 1px main 描边，最多三条。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FuguangSuggestionChips(
    items: List<String>,
    onPick: (String) -> Unit,
) {
    if (items.isEmpty()) return
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.take(MAX_SUGGESTIONS).forEach { item ->
            Surface(
                onClick = { onPick(item) },
                modifier = Modifier.testTag("fuguang-suggestion"),
                shape = CircleShape,
                color = colors.elevated,
                border = BorderStroke(1.dp, colors.primary),
            ) {
                Text(
                    text = item,
                    style = type.label,
                    color = colors.primaryDark,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 回放用的下一句答复，取自示例数据；用完之后停在最后一句，不伪造新内容。 */
private fun nextReply(index: Int): FuguangChatMessageUi = when (index % 2) {
    0 -> FuguangWeekSampleData.agentRecognition.copy(id = "agent-a-$index")
    else -> FuguangWeekSampleData.agentConstraint.copy(id = "agent-b-$index")
}

private const val THINKING_MILLIS = 900L
private const val CHAT_TAB_INDEX = 3
private const val MAX_SUGGESTIONS = 3
private const val THINKING_PROGRESS = "正在核对这周的空档…"

private val DEFAULT_SUGGESTIONS = listOf("把这个月重排一下", "周四下午别排了", "先看看我有哪些目标")
