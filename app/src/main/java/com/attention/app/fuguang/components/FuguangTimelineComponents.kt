package com.attention.app.fuguang.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FuguangT2Timeline(
    blocks: List<FuguangScheduleBlockUi>,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    val startMinute = 6 * 60
    val hourHeight = dims.timelineHeight / 16f
    val labels = (6..22 step 2).toList()
    Row(modifier = modifier.fillMaxWidth().height(dims.timelineHeight)) {
        Box(Modifier.width(26.dp).fillMaxHeight()) {
            labels.forEachIndexed { index, hour ->
                Text(
                    text = "%02d".format(hour),
                    style = type.t8,
                    color = roles.ink3,
                    modifier = Modifier.offset(y = hourHeight * (2f * index)).padding(top = 2.dp),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            labels.forEachIndexed { index, _ ->
                Box(Modifier.fillMaxWidth().offset(y = hourHeight * (2f * index)).height(1.dp).background(roles.line))
            }
            blocks.forEach { block ->
                val top = hourHeight * ((block.startMinute - startMinute).coerceAtLeast(0) / 60f)
                val height = (hourHeight * ((block.endMinute - block.startMinute).coerceAtLeast(18) / 60f)).coerceAtLeast(MIN_BLOCK_HEIGHT)
                FuguangT3ScheduleBlock(
                    block = block,
                    compact = height <= MIN_BLOCK_HEIGHT + 0.5.dp,
                    showSubtitle = height >= TWO_LINE_BLOCK_HEIGHT,
                    modifier = Modifier.fillMaxWidth(0.96f).offset(x = 11.dp, y = top).height(height),
                )
            }
        }
    }
}

private fun Dp.coerceAtLeast(minimum: Dp): Dp = if (this < minimum) minimum else this

/** 规范 P12：最小时高 18；矮到这个高度时只留色条，不画文字。 */
private val MIN_BLOCK_HEIGHT = 18.dp

/** 一行标题 + 一行副行 + 上下内距大约需要的高度；不到这个高度就先丢副行。 */
private val TWO_LINE_BLOCK_HEIGHT = 34.dp

@Composable
fun FuguangT3ScheduleBlock(
    block: FuguangScheduleBlockUi,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showSubtitle: Boolean = true,
) {
    val type = fuguangType()
    val roles = fuguangRoles()
    val colors = scheduleColors(block.kind, block.state)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = colors.background,
        border = if (block.state == FuguangScheduleState.NEW) BorderStroke(3.dp, roles.accentDeep) else null,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(colors.bar))
            // 矮块（含 18dp 下限的块）只留色条：硬塞文字会被块边框切掉，
            // 规范 P12 也是这么定的（"矮到 18 时只留色条"）。
            if (compact) return@Row
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = block.title,
                    style = type.t95.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (block.state == FuguangScheduleState.CANCELLED) TextDecoration.LineThrough else TextDecoration.None,
                )
                if (block.subtitle.isNotBlank() && showSubtitle) {
                    Text(
                        block.subtitle,
                        style = type.t95.copy(fontSize = 8.5.sp),
                        color = colors.text.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private data class ScheduleColors(val background: Color, val bar: Color, val text: Color)

@Composable
private fun scheduleColors(kind: FuguangScheduleKind, state: FuguangScheduleState): ScheduleColors {
    val roles = fuguangRoles()
    if (state == FuguangScheduleState.CANCELLED) return ScheduleColors(roles.n3, roles.n1, roles.ink4)
    return when (kind) {
        FuguangScheduleKind.GROUP_INTERVIEW -> ScheduleColors(roles.accent, roles.accentDeep2, roles.accentDeep2)
        FuguangScheduleKind.CLASS -> ScheduleColors(roles.mainTint, roles.mainDeep, roles.mainInk)
        FuguangScheduleKind.IELTS -> ScheduleColors(roles.accentTint, roles.accentDeep, roles.accentDeep)
        FuguangScheduleKind.MEETING -> ScheduleColors(roles.n2, roles.n1, roles.inkOnN)
        FuguangScheduleKind.TASK -> ScheduleColors(roles.n3, roles.n1, roles.inkOnN)
    }
}
