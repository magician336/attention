package com.attention.app.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionTextButton
import com.attention.app.ui.theme.attentionTokens
import com.attention.workspace.state.ChatActions
import com.attention.workspace.state.ChatMessage
import com.attention.workspace.state.ChatRole
import com.attention.workspace.state.ChatScreenState
import com.attention.workspace.state.CHAT_SEND_FAILED_MESSAGE
import com.attention.workspace.state.chatRetryHistory
import com.attention.workspace.state.chatTimeLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * AI 对话页。消息发送、等待与失败重试都在这里闭环，[AiChatService] 是唯一的模型边界；
 * 换真实实现时只替换这里创建的服务，页面与状态都不用改。
 *
 * 页面自己持有会话状态：离开对话页会取消进行中的请求并清空这段会话。
 * 当前需求没有要求跨页保留对话，因此没有把状态上浮到组合根。
 */
@Composable
internal fun ChatScreen() {
    val service: AiChatService = remember { MockAiChatService() }
    val scope: CoroutineScope = rememberCoroutineScope()
    val session: ChatSession = remember(service, scope) { ChatSession(service, scope) }
    ChatScreenContent(state = session.state, actions = session)
}

/**
 * 无状态的页面主体：只接收投影好的 [ChatScreenState] 与 intent 接口，
 * 便于以后直接给这一层写 Compose 测试，也避免页面自己去解释发送规则。
 */
@Composable
private fun ChatScreenContent(
    state: ChatScreenState,
    actions: ChatActions,
) {
    val tokens = attentionTokens()
    val listState = rememberLazyListState()
    // 新消息或「正在回复」出现时滚到底部；空对话不发，因此不会出现负下标。
    LaunchedEffect(state.messages.size, state.sending) {
        val lastIndex: Int = state.messages.lastIndex + (if (state.sending) 1 else 0)
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }
    Column(
        Modifier.fillMaxSize().imePadding().padding(tokens.spacing.large),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.medium),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("AI 对话", style = tokens.typography.display, modifier = Modifier.weight(1f))
            AttentionTextButton(
                onClick = actions::clear,
                enabled = state.messages.isNotEmpty() || state.input.isNotEmpty(),
                modifier = Modifier.testTag("chat-clear"),
            ) { Text("清空") }
        }
        if (state.messages.isEmpty() && !state.sending) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "有什么想聊的？在下面输入一条消息开始对话。",
                    style = tokens.typography.body,
                    color = tokens.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("chat-empty"),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("chat-messages"),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.medium),
            ) {
                items(items = state.messages) { message -> ChatMessageRow(message) }
                if (state.sending) {
                    item { ChatLoadingRow() }
                }
            }
        }
        state.error?.let { message ->
            AttentionTextButton(
                onClick = actions::retryLast,
                modifier = Modifier.testTag("chat-retry"),
            ) {
                Text(message, style = tokens.typography.caption, color = tokens.brand.error.accent)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.medium),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = actions::updateInput,
                modifier = Modifier.weight(1f).testTag("chat-input"),
                placeholder = { Text("输入消息…") },
                minLines = 1,
                maxLines = 4,
            )
            AttentionButton(
                onClick = actions::send,
                enabled = state.canSend,
                modifier = Modifier.testTag("chat-send"),
            ) {
                if (state.sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = tokens.colorScheme.onPrimary,
                    )
                } else {
                    Text("发送")
                }
            }
        }
    }
}

/**
 * 一条消息：用户靠右、AI 靠左，两种底色；长文本在气泡宽度内自动换行，
 * 时间单独放在气泡下方，不挤占正文行宽。
 */
@Composable
private fun ChatMessageRow(message: ChatMessage) {
    val tokens = attentionTokens()
    val fromUser: Boolean = message.role == ChatRole.USER
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (fromUser) "chat-message-user" else "chat-message-assistant"),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.small),
        ) {
            Surface(
                shape = RoundedCornerShape(tokens.radius.medium),
                color = if (fromUser) {
                    tokens.brand.timingInProgress.container
                } else {
                    tokens.colorScheme.surfaceContainerHigh
                },
                contentColor = if (fromUser) {
                    tokens.brand.timingInProgress.onContainer
                } else {
                    tokens.colorScheme.onSurface
                },
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    text = message.text,
                    style = tokens.typography.body,
                    modifier = Modifier.padding(
                        horizontal = tokens.spacing.medium,
                        vertical = tokens.spacing.small,
                    ),
                )
            }
            Text(
                text = chatTimeLabel(message.timestampEpochMillis),
                style = tokens.typography.caption,
                color = tokens.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 等待回复时的占位行：让 800ms 的 mock 延迟可见，而不是只剩一个被禁用的发送按钮。 */
@Composable
private fun ChatLoadingRow() {
    val tokens = attentionTokens()
    Row(
        modifier = Modifier.fillMaxWidth().testTag("chat-loading"),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(tokens.spacing.medium),
            strokeWidth = 2.dp,
            color = tokens.brand.timingInProgress.accent,
        )
        Text(
            text = "AI 正在回复…",
            style = tokens.typography.caption,
            color = tokens.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = tokens.spacing.small),
        )
    }
}

/**
 * 对话页的会话状态与 intent 实现。
 *
 * 它把「追加用户消息 / 等待 / 成功追加回复 / 失败保留重试目标」这套时序收在一处，
 * 页面只渲染 [ChatScreenState] 并转发 [ChatActions]。挂起请求在离开页面时由
 * rememberCoroutineScope 取消；[clear] 也会主动取消还在等待的回复。
 */
private class ChatSession(
    private val service: AiChatService,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ChatActions {
    var state: ChatScreenState by mutableStateOf(ChatScreenState())
        private set

    private var pendingReply: Job? = null

    override fun updateInput(value: String) {
        state = state.copy(input = value)
    }

    override fun send() {
        if (!state.canSend) return
        val text: String = state.input.trim()
        if (text.isEmpty()) return
        val history: List<ChatMessage> = state.messages + ChatMessage(
            role = ChatRole.USER,
            text = text,
            timestampEpochMillis = clock(),
        )
        // 先把用户消息落进列表并进入发送中，网络等待期间界面就有可看的内容。
        state = state.copy(messages = history, input = "", sending = true, error = null)
        requestReply(history)
    }

    override fun retryLast() {
        if (state.sending) return
        val history: List<ChatMessage> = chatRetryHistory(state) ?: return
        state = state.copy(sending = true, error = null)
        requestReply(history)
    }

    override fun clear() {
        pendingReply?.cancel()
        pendingReply = null
        state = ChatScreenState()
    }

    private fun requestReply(history: List<ChatMessage>) {
        pendingReply?.cancel()
        pendingReply = scope.launch {
            try {
                val reply: String = service.reply(history)
                state = state.copy(
                    messages = state.messages + ChatMessage(
                        role = ChatRole.ASSISTANT,
                        text = reply,
                        timestampEpochMillis = clock(),
                    ),
                    sending = false,
                    error = null,
                )
            } catch (cancelled: CancellationException) {
                // 离开页面或清空对话会取消这个协程；取消不是发送失败，不能显示重试。
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(sending = false, error = CHAT_SEND_FAILED_MESSAGE)
            }
        }
    }
}
