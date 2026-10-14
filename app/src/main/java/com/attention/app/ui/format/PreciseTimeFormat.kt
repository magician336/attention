package com.attention.app.ui.format

import com.attention.domain.TimeEntryRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 精确的实际时间表达。
 *
 * 只有计时来源才可能有精确时间戳，因此精确时间的判断一律读读模型的
 * [TimeEntryRow.hasPreciseTiming]，不在这里重新推导来源规则。
 *
 * 计时记录的时间戳是这一段实际投入的开始时刻（见领域层按规划日切分计时段的实现），
 * 结束时刻由开始时刻加上这条记录的时长得到，因此精确区间是事实而不是估算。
 */
object PreciseTimeFormat {

    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * 精确的实际开始与结束时间。
     *
     * 返回 null 表示这条记录没有精确时间：按计划确认的记录不伪造精确时间。
     */
    fun intervalOf(row: TimeEntryRow, zone: ZoneId): String? {
        if (!row.hasPreciseTiming) return null
        val start = row.entry.occurredAtEpochMillis ?: return null
        val end = start + row.entry.durationMinutes * 60_000L
        return "${clockAt(start, zone)} – ${clockAt(end, zone)}"
    }

    private fun clockAt(epochMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime().format(CLOCK)
}