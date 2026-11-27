package com.attention.app.fuguang.prototype

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attention.app.ui.theme.AttentionTheme

@Composable
fun PrototypePhoneShell(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onNavigateAgenda: () -> Unit,
    onTabMessage: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().prototypeGridBackground(),
    ) {
        PrototypeTopBar()
        Box(modifier = Modifier.weight(1f).fillMaxWidth().prototypeGridBackground()) {
            content()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FuguangPrototypeColors.paper)
                .padding(horizontal = 16.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(FuguangPrototypeColors.ink, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(6.dp))
            Text(
                "助手只读 · 改表要你点头",
                style = fuguangPrototypeType.micro,
                color = FuguangPrototypeColors.ink2,
            )
        }
        PrototypeComposer(
            value = input,
            onValueChange = onInputChange,
            onSend = onSend,
        )
        PrototypeTabBar(
            selectedIndex = 3,
            onSelect = { index ->
                when (index) {
                    0 -> onNavigateAgenda()
                    3 -> onTabMessage("已经在对话里")
                    else -> onTabMessage("这一版只做对话与日程")
                }
            },
        )
    }
}

fun Modifier.prototypeGridBackground(
    paper: Color = FuguangPrototypeColors.paper2,
    ink: Color = FuguangPrototypeColors.ink,
): Modifier = drawBehind {
    drawRect(paper)
    val small = 20.dp.toPx()
    val minorColor = ink.copy(alpha = 0.075f)
    val majorColor = ink.copy(alpha = 0.09f)
    var x = 0f
    while (x <= size.width) {
        drawLine(
            color = if ((x / small).toInt() % 5 == 0) majorColor else minorColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1.dp.toPx(),
        )
        x += small
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(
            color = if ((y / small).toInt() % 5 == 0) majorColor else minorColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
        )
        y += small
    }
}

@Composable
fun PrototypeTopBar(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val type = fuguangPrototypeType
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(FuguangPrototypeColors.paper)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = FuguangPrototypeColors.ink2)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("浮光主 Agent", style = type.title.copy(fontWeight = FontWeight.Bold), color = FuguangPrototypeColors.ink)
            Text("已接入飞书 · 3 个群 · 2 位小助手", style = type.micro, color = FuguangPrototypeColors.ink3)
        }
        Surface(
            shape = CircleShape,
            color = FuguangPrototypeColors.lime,
        ) {
            Text(
                "目标 7.5",
                style = type.micro.copy(fontWeight = FontWeight.ExtraBold),
                color = FuguangPrototypeColors.sageDeep,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = FuguangPrototypeColors.pink,
            border = BorderStroke(1.dp, FuguangPrototypeColors.ink.copy(alpha = 0.12f)),
        ) {}
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(FuguangPrototypeColors.line))
}

@Composable
fun PrototypeCat(
    @DrawableRes drawable: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

@Composable
fun PrototypeCard(
    modifier: Modifier = Modifier,
    background: Color = FuguangPrototypeColors.card,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(PrototypeCardRadius), ambientColor = FuguangPrototypeColors.shadow)
            .testTag("prototype-card"),
        shape = RoundedCornerShape(PrototypeCardRadius),
        color = background,
        border = BorderStroke(1.dp, FuguangPrototypeColors.line),
        content = content,
    )
}

@Composable
fun PrototypeButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PrototypeButtonStyle = PrototypeButtonStyle.PRIMARY,
    enabled: Boolean = true,
) {
    val background = when {
        !enabled -> FuguangPrototypeColors.ink.copy(alpha = 0.08f)
        style == PrototypeButtonStyle.PRIMARY -> FuguangPrototypeColors.ink
        style == PrototypeButtonStyle.LIME -> FuguangPrototypeColors.lime
        style == PrototypeButtonStyle.HOT -> FuguangPrototypeColors.hotDeep
        else -> Color.Transparent
    }
    val foreground = when {
        !enabled -> FuguangPrototypeColors.ink3
        style == PrototypeButtonStyle.PRIMARY -> FuguangPrototypeColors.lime
        style == PrototypeButtonStyle.HOT -> FuguangPrototypeColors.white
        else -> FuguangPrototypeColors.ink2
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(11.dp),
        color = background,
        border = if (style == PrototypeButtonStyle.GHOST) {
            BorderStroke(1.4.dp, FuguangPrototypeColors.ink.copy(alpha = 0.2f))
        } else {
            null
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = fuguangPrototypeType.label.copy(fontWeight = FontWeight.Bold),
                color = foreground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
            )
        }
    }
}

enum class PrototypeButtonStyle {
    PRIMARY,
    LIME,
    HOT,
    GHOST,
}

@Composable
fun PrototypeReceipt(
    text: String,
    modifier: Modifier = Modifier,
    collapsedCount: Int = 0,
    onClick: (() -> Unit)? = null,
) {
    val type = fuguangPrototypeType
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(14.dp).background(FuguangPrototypeColors.sage, CircleShape), contentAlignment = Alignment.Center) {
                Text("✓", style = type.micro, color = Color(0xFF2F4118))
            }
            Spacer(Modifier.width(7.dp))
            Text(text, style = type.caption, color = FuguangPrototypeColors.ink3, modifier = Modifier.weight(1f))
            if (collapsedCount > 0) {
                Text("前 $collapsedCount 步已收起", style = type.micro, color = FuguangPrototypeColors.ink3.copy(alpha = 0.7f))
                Spacer(Modifier.width(6.dp))
            }
            Text("回看 ›", style = type.caption, color = FuguangPrototypeColors.ink3)
        }
    }
    if (onClick == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = FuguangPrototypeColors.ink.copy(alpha = 0.035f),
            content = body,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = FuguangPrototypeColors.ink.copy(alpha = 0.035f),
            content = body,
        )
    }
}

@Composable
fun AssistantBubble(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    showAvatar: Boolean = true,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (showAvatar) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(FuguangPrototypeColors.ink),
            )
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.width(36.dp))
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 5.dp),
            color = FuguangPrototypeColors.ai,
        ) {
            Text(
                text = text,
                style = fuguangPrototypeType.body,
                color = FuguangPrototypeColors.ink,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.86f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 5.dp, bottomStart = 16.dp),
            color = FuguangPrototypeColors.me,
        ) {
            Text(
                text = text,
                style = fuguangPrototypeType.body,
                color = FuguangPrototypeColors.ink,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun PrototypeComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FuguangPrototypeColors.paper)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(1f).height(38.dp),
            shape = CircleShape,
            color = FuguangPrototypeColors.card,
            border = BorderStroke(1.dp, FuguangPrototypeColors.ink.copy(alpha = 0.18f)),
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text("说点什么…", style = fuguangPrototypeType.body, color = FuguangPrototypeColors.ink3)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth().testTag("prototype-input"),
                        textStyle = fuguangPrototypeType.body.copy(color = FuguangPrototypeColors.ink),
                        singleLine = true,
                        cursorBrush = SolidColor(FuguangPrototypeColors.ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    )
                }
                Icon(Icons.Filled.Mic, "语音", tint = FuguangPrototypeColors.ink3, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = onSend,
            enabled = value.isNotBlank(),
            modifier = Modifier.size(38.dp).testTag("prototype-send"),
            shape = CircleShape,
            color = if (value.isNotBlank()) FuguangPrototypeColors.ink else FuguangPrototypeColors.ink.copy(alpha = 0.08f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("↑", style = fuguangPrototypeType.title, color = if (value.isNotBlank()) FuguangPrototypeColors.lime else FuguangPrototypeColors.ink3)
            }
        }
    }
}

private data class PrototypeTab(val label: String, val icon: ImageVector)

@Composable
fun PrototypeTabBar(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val tabs = remember {
        listOf(
            PrototypeTab("日程", Icons.Filled.CalendarMonth),
            PrototypeTab("目标树", Icons.Filled.Polyline),
            PrototypeTab("统计", Icons.Filled.Insights),
            PrototypeTab("对话", Icons.Filled.ChatBubbleOutline),
        )
    }
    Column(modifier = Modifier.fillMaxWidth().background(FuguangPrototypeColors.paper)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(FuguangPrototypeColors.line))
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        tab.icon,
                        tab.label,
                        tint = if (selected) FuguangPrototypeColors.ink else FuguangPrototypeColors.ink3,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        tab.label,
                        style = fuguangPrototypeType.micro.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                        color = if (selected) FuguangPrototypeColors.ink else FuguangPrototypeColors.ink3,
                    )
                }
            }
        }
    }
}

@Composable
fun PrototypeToast(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = FuguangPrototypeColors.ink,
    ) {
        Text(
            text,
            style = fuguangPrototypeType.caption,
            color = FuguangPrototypeColors.lime,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

fun prototypeAnnotated(
    text: String,
    emphasis: List<String> = emptyList(),
    emphasisColor: Color = FuguangPrototypeColors.hotDeep,
): AnnotatedString = buildAnnotatedString {
    append(text)
    emphasis.forEach { phrase ->
        val index = text.indexOf(phrase)
        if (index >= 0) {
            addStyle(
                SpanStyle(color = emphasisColor, fontWeight = FontWeight.Bold),
                index,
                index + phrase.length,
            )
        }
    }
}

@Composable
fun PrototypeDayChip(
    weekday: String,
    date: String,
    selected: Boolean,
    marker: PrototypeDayMarker = PrototypeDayMarker.NONE,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val type = fuguangPrototypeType
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) FuguangPrototypeColors.ink else Color.Transparent,
    ) {
        Box {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    weekday.replace("周", ""),
                    style = type.caption.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) FuguangPrototypeColors.paper else FuguangPrototypeColors.ink2,
                )
                Text(
                    date,
                    style = type.micro,
                    color = if (selected) FuguangPrototypeColors.paper else FuguangPrototypeColors.ink3,
                )
            }
            if (marker != PrototypeDayMarker.NONE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 4.dp)
                        .size(5.dp)
                        .background(
                            when (marker) {
                                PrototypeDayMarker.CHANGED -> FuguangPrototypeColors.sage
                                PrototypeDayMarker.CONFLICT -> FuguangPrototypeColors.hotDeep
                                PrototypeDayMarker.NONE -> Color.Transparent
                            },
                            CircleShape,
                        ),
                )
            }
        }
    }
}

enum class PrototypeDayMarker {
    NONE,
    CHANGED,
    CONFLICT,
}

@Composable
fun PrototypeTinyTag(
    text: String,
    background: Color,
    foreground: Color,
) {
    Surface(shape = CircleShape, color = background) {
        Text(
            text,
            style = fuguangPrototypeType.micro.copy(fontWeight = FontWeight.Bold),
            color = foreground,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}
