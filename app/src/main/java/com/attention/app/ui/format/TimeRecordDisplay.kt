package com.attention.app.ui.format

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.attention.app.ui.theme.attentionTokens
import com.attention.domain.TimeEntryRow
import com.attention.domain.TimeEntrySource
import java.time.ZoneId

/**
 * 稳定的界面标识。界面测试按标识定位区域，不依赖文案措辞或组件层级。
 */
object TimeRecordTags {
    const val ROW = "time-record-row"
    const val DURATION = "time-entry-duration"
    const val SOURCE_MARKER = "time-entry-source-marker"
    const val PRECISE_TIME = "time-entry-precise-time"
    const val PLANNED_TIME = "time-entry-planned-time"
    const val ACTUAL_TIME = "time-entry-actual-time"
    const val SCHEDULE_LABEL = "time-entry-schedule-label"
    const val SCHEDULE_MISSING = "time-entry-schedule-missing"
}

/**
 * 一条时间记录的展示件。
 *
 * 三件事在这里被说清楚：时长用自然语言表达；来源用文字标记表达；
 * 计划时间与实际时间分区展示，且只有计时记录才显示精确时间——
 * 按计划确认的记录只显示“按计划记录”，不伪装成精确计时结果。
 *
 * 颜色、间距、圆角与字体全部来自主题令牌，这里不写裸色值与裸 dp。
 */
@Composable
fun TimeRecordDisplay(
    row: TimeEntryRow,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val tokens = attentionTokens()

    Column(
        modifier = modifier
            .testTag(TimeRecordTags.ROW)
            .padding(tokens.spacing.small),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.small),
    ) {
        Text(
            text = DurationFormat.ofMinutes(row.entry.durationMinutes),
            style = tokens.typography.title,
            color = tokens.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(TimeRecordTags.DURATION),
        )

        TimeEntrySourceMarker(row = row)

        // 精确时间只属于计时记录。按计划确认的记录在这里没有这一行，
        // 而不是有一行被显示成空白。
        PreciseTimeLine(row = row, zone = zone)

        TimeRecordScheduleLabel(row = row)

        PlannedAndActualTime(row = row, zone = zone)
    }
}

/**
 * 精确的实际时间。读模型说这条记录没有精确时间时，这里什么都不渲染。
 */
@Composable
fun PreciseTimeLine(
    row: TimeEntryRow,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val tokens = attentionTokens()
    val precise = PreciseTimeFormat.intervalOf(row, zone) ?: return

    Text(
        text = precise,
        style = tokens.typography.caption,
        color = tokens.colorScheme.onSurfaceVariant,
        modifier = modifier.testTag(TimeRecordTags.PRECISE_TIME),
    )
}

/**
 * 时间记录来源标记。每个来源都有文字，不靠颜色单独传达信息。
 */
@Composable
fun TimeEntrySourceMarker(
    row: TimeEntryRow,
    modifier: Modifier = Modifier,
) {
    val tokens = attentionTokens()
    val semantic = when (row.source) {
        TimeEntrySource.TIMER -> tokens.brand.timingInProgress
        TimeEntrySource.SCHEDULE_CONFIRMED -> tokens.brand.scheduled
        else -> tokens.brand.unattributed
    }

    Text(
        text = TimeEntrySourceFormat.labelOf(row.source),
        style = tokens.typography.caption,
        color = semantic.onContainer,
        modifier = modifier
            .testTag(TimeRecordTags.SOURCE_MARKER)
            .background(semantic.container, RoundedCornerShape(tokens.radius.small))
            .padding(tokens.spacing.small),
    )
}

/**
 * 关联日程的表达。日程还在就显示标题；日程已被移除时显示读模型给出的说明，
 * 记录本身依然可见。说明文字只从 [TimeEntryRow.scheduleLabel] 取，不另写一份。
 */
@Composable
fun TimeRecordScheduleLabel(
    row: TimeEntryRow,
    modifier: Modifier = Modifier,
) {
    val tokens = attentionTokens()
    if (row.entry.scheduleEntryId == null) return

    if (row.scheduleMissing) {
        Text(
            text = row.scheduleLabel,
            style = tokens.typography.caption,
            color = tokens.brand.error.onContainer,
            modifier = modifier
                .testTag(TimeRecordTags.SCHEDULE_MISSING)
                .background(tokens.brand.error.container, RoundedCornerShape(tokens.radius.small))
                .padding(tokens.spacing.small),
        )
        return
    }

    Text(
        text = row.scheduleLabel,
        style = tokens.typography.caption,
        color = tokens.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.testTag(TimeRecordTags.SCHEDULE_LABEL),
    )
}

/**
 * 计划时间与实际时间的分离展示件。
 *
 * 两个区域始终存在，因此各自都能被独立断言；实际时间区域在没有精确时间时
 * 明确写出“按计划记录”，而不是显示空白或编造一个时间。
 */
@Composable
fun PlannedAndActualTime(
    row: TimeEntryRow,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val tokens = attentionTokens()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.small),
    ) {
        TimeRegion(
            tag = TimeRecordTags.PLANNED_TIME,
            label = "计划时间",
            value = plannedTimeTextOf(row),
            valueColor = tokens.colorScheme.onSurfaceVariant,
        )
        TimeRegion(
            tag = TimeRecordTags.ACTUAL_TIME,
            label = "实际时间",
            value = PreciseTimeFormat.intervalOf(row, zone) ?: TimeEntrySourceFormat.SCHEDULE_CONFIRMED_LABEL,
            valueColor = tokens.colorScheme.onSurface,
        )
    }
}

/**
 * 计划时间的取值。日程被移除时直接用读模型的说明，不另造一个说法。
 */
private fun plannedTimeTextOf(row: TimeEntryRow): String = when {
    row.scheduleMissing -> row.scheduleLabel
    else -> TimeEntrySourceFormat.plannedTimeOf(row.schedule)
}

@Composable
private fun TimeRegion(
    tag: String,
    label: String,
    value: String,
    valueColor: Color,
) {
    val tokens = attentionTokens()

    Row(
        // 合并子节点的语义：整个区域是一句话，屏幕阅读器与界面测试都能整块读到。
        modifier = Modifier
            .testTag(tag)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.small),
    ) {
        Text(
            text = label,
            style = tokens.typography.caption,
            color = tokens.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = tokens.typography.caption,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
