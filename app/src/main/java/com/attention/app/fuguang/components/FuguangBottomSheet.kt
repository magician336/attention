package com.attention.app.fuguang.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FuguangO1BottomSheet(
    title: String,
    subtitle: String,
    options: List<FuguangRelocationOptionUi>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    // 页面浮层要盖满整屏：遮罩只盖住面板高度的话，上半屏没被压暗，
    // 「停下来了」这个信号就散了。面板本身仍贴底、高度随内容。
    Box(modifier = modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(roles.ink.copy(alpha = 0.18f)))
        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = roles.paper,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(32.dp).height(4.dp).background(roles.line, CircleShape))
                }
                Text(title, style = type.t17, color = roles.ink)
                Text(subtitle, style = type.t11, color = roles.ink3)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    options.take(3).forEach { option ->
                        FuguangO1OptionRow(option = option, selected = option.id == selectedId, onClick = { onSelect(option.id) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FuguangB1Button("先不改", FuguangButtonKind.SECONDARY, onCancel, Modifier.width(82.dp))
                    val selected = options.firstOrNull { it.id == selectedId }
                    FuguangB1Button(if (selected == null) "就放这里" else "就放${selected.title}", FuguangButtonKind.PRIMARY, onConfirm, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FuguangO1OptionRow(
    option: FuguangRelocationOptionUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("o1-option-${option.id}"),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) roles.mainTint else roles.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) roles.mainDeep else roles.line),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).background(if (selected) roles.mainDeep else roles.n3, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(option.indexLabel, style = type.t11.copy(fontWeight = FontWeight.Medium), color = if (selected) roles.surface else roles.ink3)
                }
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(option.title, style = type.t125.copy(fontWeight = FontWeight.Medium), color = roles.ink)
                    Text(option.subtitle, style = type.t95, color = roles.ink3)
                }
                if (option.recommended) FuguangB2Badge("推荐", FuguangBadgeKind.TARGET)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = "代价：${option.cost}",
                style = type.t95,
                color = roles.ink2,
                modifier = Modifier.fillMaxWidth().background(roles.accentTint.copy(alpha = 0.6f), RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}