package com.attention.app.fuguang.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FuguangC1Avatar(
    state: FuguangAvatarState,
    modifier: Modifier = Modifier,
    image: Painter? = null,
    initial: String? = null,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    val fallback = initial?.trim()?.take(1).orEmpty()
    val background = when (state) {
        FuguangAvatarState.GLOW -> roles.accentTint
        FuguangAvatarState.IMAGE_FAILED -> roles.accentDeep2
        else -> roles.accentDeep
    }
    Box(
        modifier = modifier.size(dims.avatarSize).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state == FuguangAvatarState.IMAGE && image != null -> Image(
                painter = image,
                contentDescription = "头像",
                modifier = Modifier.size(dims.avatarSize).clip(CircleShape),
            )
            state == FuguangAvatarState.GLOW -> Box(Modifier.size(8.dp).background(roles.accentDeep, CircleShape))
            fallback.isNotEmpty() -> Text(fallback, style = type.t95.copy(fontSize = 11.sp), color = roles.surface)
        }
    }
}

@Composable
fun FuguangC2AiBubble(
    segments: List<FuguangTextSegment>,
    modifier: Modifier = Modifier,
    showAvatar: Boolean = true,
    avatarState: FuguangAvatarState = FuguangAvatarState.GLOW,
    avatarImage: Painter? = null,
    avatarInitial: String? = null,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    // 折叠是纯呈现状态（不跨屏共享、不进业务模型），因此留在组件内：
    // 规范 P11 要求「超长折叠」，而不是把多出来的段落直接丢掉。
    var expanded by remember(segments) { mutableStateOf(false) }
    val overflow = segments.size > COLLAPSED_SEGMENT_LIMIT
    val visible = if (expanded) segments else segments.take(COLLAPSED_SEGMENT_LIMIT)
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showAvatar) {
            FuguangC1Avatar(state = avatarState, image = avatarImage, initial = avatarInitial)
        } else {
            Spacer(Modifier.size(dims.avatarSize))
        }
        Surface(
            modifier = Modifier.widthIn(max = dims.bubbleMaxWidth),
            shape = RoundedCornerShape(topStart = 5.dp, topEnd = 15.dp, bottomEnd = 15.dp, bottomStart = 15.dp),
            color = roles.surface,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visible.forEach { segment ->
                    Text(bubbleText(segment, roles.mainDeep), style = type.t135, color = roles.ink)
                }
                if (overflow) {
                    Text(
                        text = if (expanded) "收起" else "展开全部 ${segments.size} 段",
                        style = type.t95,
                        color = roles.mainDeep,
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .testTag("bubble-expand"),
                    )
                }
            }
        }
    }
}

/** 折叠阈值：超过 4 段就收起，与规范 P11「正文超过 4 行要折叠」一致。 */
private const val COLLAPSED_SEGMENT_LIMIT = 4

@Composable
fun FuguangC3UserBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            modifier = Modifier.widthIn(max = dims.bubbleMaxWidth),
            shape = RoundedCornerShape(topStart = 15.dp, topEnd = 5.dp, bottomEnd = 15.dp, bottomStart = 15.dp),
            color = roles.mainSoft,
        ) {
            Text(
                text = text,
                style = type.t135,
                color = roles.ink,
                maxLines = 4,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            )
        }
    }
}

private fun bubbleText(segment: FuguangTextSegment, emphasisColor: Color) = buildAnnotatedString {
    append(segment.text)
    segment.emphasis.forEach { phrase ->
        val start = segment.text.indexOf(phrase)
        if (start >= 0) {
            addStyle(SpanStyle(color = emphasisColor, fontWeight = FontWeight.Medium), start, start + phrase.length)
        }
    }
}
