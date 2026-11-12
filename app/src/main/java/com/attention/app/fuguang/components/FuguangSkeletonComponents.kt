package com.attention.app.fuguang.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FuguangS1NavigationBar(
    name: String,
    roleLabel: String,
    goalLabel: String,
    modifier: Modifier = Modifier,
    pendingCount: Int? = null,
    onBack: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    val safeName = name.trim().ifEmpty { "助手" }
    Surface(modifier = modifier.fillMaxWidth(), color = roles.paper) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(dims.navHeight).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = roles.ink)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = safeName.take(8),
                        style = type.t135.copy(fontWeight = FontWeight.SemiBold),
                        color = roles.ink,
                        maxLines = 1,
                    )
                    Text(
                        text = roleLabel,
                        style = type.t95,
                        color = roles.ink3,
                        maxLines = 1,
                    )
                }
                FuguangS2GoalCapsule(goalLabel)
                if (pendingCount != null) {
                    Spacer(Modifier.width(5.dp))
                    FuguangB2Badge("${pendingCount}项", FuguangBadgeKind.COUNT)
                }
                if (onMore != null) {
                    IconButton(onClick = onMore, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.MoreHoriz, "更多", tint = roles.ink3, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(dims.hairline).background(roles.line2))
        }
    }
}

@Composable
fun FuguangS2GoalCapsule(
    label: String,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    Surface(
        modifier = modifier.height(dims.goalHeight),
        shape = CircleShape,
        color = roles.accentTint,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(5.dp).background(roles.accentDeep, CircleShape))
            Text(
                text = label,
                style = type.t95.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                color = roles.accentDeep,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun FuguangS3InputArea(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "说说这周想怎么安排…",
    onMic: () -> Unit = {},
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.weight(1f).height(dims.inputHeight),
            shape = CircleShape,
            color = roles.surface,
            border = BorderStroke(dims.hairline, if (focused) roles.main else roles.line),
        ) {
            Row(
                modifier = Modifier.padding(start = 13.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = type.t125, color = roles.ink4, maxLines = 1)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.testTag("input-s3"),
                        textStyle = type.t125.copy(color = roles.ink),
                        singleLine = true,
                        cursorBrush = SolidColor(roles.main),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    )
                }
                IconButton(onClick = onMic, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Mic, "语音输入", tint = roles.ink3, modifier = Modifier.size(17.dp))
                }
            }
        }
        FuguangS4SendButton(
            enabled = value.isNotBlank(),
            onClick = onSend,
        )
    }
}

@Composable
fun FuguangS4SendButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val dims = fuguangDims()
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(dims.sendSize).testTag("send-s4"),
        shape = CircleShape,
        color = if (enabled) roles.mainDeep else roles.n3,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("↑", color = if (enabled) roles.surface else roles.ink4, fontSize = 15.sp)
        }
    }
}

private data class FuguangTabItem(val label: String, val icon: ImageVector)

@Composable
fun FuguangS5TabBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    val items = remember {
        listOf(
            FuguangTabItem("对话", Icons.Filled.ChatBubbleOutline),
            FuguangTabItem("日程", Icons.Filled.CalendarToday),
            FuguangTabItem("专注", Icons.Filled.CenterFocusStrong),
            FuguangTabItem("回顾", Icons.Filled.History),
        )
    }
    Surface(modifier = modifier.fillMaxWidth(), color = roles.paper) {
        Row(
            modifier = Modifier.height(dims.tabBarHeight).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val selected = selectedIndex == index
                Surface(
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = roles.paper,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(24.dp)
                                .background(if (selected) roles.mainDeep else roles.paper, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selected) roles.surface else roles.ink3,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            text = item.label,
                            style = type.t95.copy(fontSize = 9.sp),
                            color = if (selected) roles.mainDeep else roles.ink3,
                        )
                    }
                }
            }
        }
    }
}
