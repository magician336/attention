package com.attention.app.ui.format

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.MainActivity
import com.attention.app.ui.theme.AttentionTheme
import com.attention.domain.ScheduleEntry
import com.attention.domain.TimeEntry
import com.attention.domain.TimeEntryRow
import com.attention.domain.TimeEntrySource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 界面 seam：时间记录行自己说清来源，计划时间与实际时间分区展示，
 * 只有计时记录才显示精确时间，关联的日程不存在时给出说明而不是消失。
 *
 * 断言只读用户能看到的文案与区域是否存在，不读内部调用。
 * 测试自己搭一个宿主挂载格式化层，因为页面级接入属于另一张票。
 */
@RunWith(AndroidJUnit4::class)
class TimeRecordDisplayTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun a_schedule_confirmed_record_is_marked_and_shows_no_precise_time() {
        mountRow(scheduleConfirmedRow())

        composeRule.onNodeWithTag("time-entry-source-marker").assertTextIs("按计划记录")
        composeRule.onNodeWithTag("time-entry-precise-time").assertDoesNotExist()
    }

    @Test
    fun a_timed_record_shows_its_precise_start_and_end() {
        mountRow(timerRow())

        composeRule.onNodeWithTag("time-entry-source-marker").assertTextIs("计时")
        composeRule.onNodeWithTag("time-entry-precise-time").assertTextIs("09:30 – 11:15")
    }

    @Test
    fun planned_time_and_actual_time_are_two_independently_readable_regions() {
        mountRow(timerRow())

        composeRule.onNodeWithTag("time-entry-planned-time").assertTextIs("计划时间 09:00 – 10:30")
        composeRule.onNodeWithTag("time-entry-actual-time").assertTextIs("实际时间 09:30 – 11:15")
    }

    @Test
    fun a_schedule_confirmed_record_states_actual_time_without_faking_precision() {
        mountRow(scheduleConfirmedRow())

        composeRule.onNodeWithTag("time-entry-planned-time").assertTextIs("计划时间 09:00 – 10:30")
        composeRule.onNodeWithTag("time-entry-actual-time").assertTextIs("实际时间 按计划记录")
    }

    @Test
    fun a_record_whose_schedule_is_gone_says_so_and_stays_visible() {
        mountRow(scheduleConfirmedRow(schedule = null))

        composeRule.onNodeWithTag("time-entry-schedule-missing").assertTextIs("所属日程已删除")
        composeRule.onNodeWithTag("time-entry-duration").assertTextIs("1 小时 15 分钟")
        composeRule.onNodeWithTag("time-entry-planned-time").assertTextIs("计划时间 所属日程已删除")
    }

    @Test
    fun a_manual_record_shows_natural_language_duration_without_a_marker_of_precise_timing() {
        mountRow(manualRow())

        composeRule.onNodeWithTag("time-entry-source-marker").assertTextIs("事后补记")
        composeRule.onNodeWithTag("time-entry-precise-time").assertDoesNotExist()
        composeRule.onNodeWithTag("time-entry-planned-time").assertTextIs("计划时间 未关联日程")
    }

    @Test
    fun the_duration_reads_as_natural_language_in_the_row() {
        mountRow(scheduleConfirmedRow(durationMinutes = 45))

        composeRule.onNodeWithTag("time-entry-duration").assertTextIs("45 分钟")
    }

    private fun mountRow(row: TimeEntryRow) {
        composeRule.activity.setContent {
            AttentionTheme(darkTheme = false) {
                TimeRecordDisplay(row = row, zone = FIXED_ZONE)
            }
        }
    }

    private fun SemanticsNodeInteraction.assertTextIs(expected: String) {
        val actual = fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" ").orEmpty()
        assertEquals("界面文案必须是 '$expected'，实际是 '$actual'", expected, actual)
    }
}

/** 固定时区：让精确时间的期望值是字面量，不随设备时区漂移。 */
private val FIXED_ZONE: ZoneId = ZoneOffset.UTC

private const val SCHEDULE_ID = "schedule-1"

private fun scheduleFixture(): ScheduleEntry = ScheduleEntry(
    id = SCHEDULE_ID,
    planningDate = "2026-03-10",
    title = "整理季度复盘",
    startMinute = 9 * 60,
    endMinute = 10 * 60 + 30,
)

private fun scheduleConfirmedRow(
    durationMinutes: Int = 75,
    schedule: ScheduleEntry? = scheduleFixture(),
): TimeEntryRow = TimeEntryRow(
    entry = TimeEntry(
        id = "entry-1",
        planningDate = "2026-03-10",
        durationMinutes = durationMinutes,
        source = TimeEntrySource.SCHEDULE_CONFIRMED,
        occurredAtEpochMillis = null,
        scheduleEntryId = SCHEDULE_ID,
    ),
    source = TimeEntrySource.SCHEDULE_CONFIRMED,
    schedule = schedule,
)

/** 2026-03-10 09:30Z 开始的 105 分钟计时，精确结束时间是 11:15。 */
private fun timerRow(): TimeEntryRow = TimeEntryRow(
    entry = TimeEntry(
        id = "entry-2",
        planningDate = "2026-03-10",
        durationMinutes = 105,
        source = TimeEntrySource.TIMER,
        occurredAtEpochMillis = 1_773_135_000_000L,
        scheduleEntryId = SCHEDULE_ID,
    ),
    source = TimeEntrySource.TIMER,
    schedule = scheduleFixture(),
)

private fun manualRow(): TimeEntryRow = TimeEntryRow(
    entry = TimeEntry(
        id = "entry-3",
        planningDate = "2026-03-10",
        durationMinutes = 75,
        source = TimeEntrySource.MANUAL,
        occurredAtEpochMillis = null,
        scheduleEntryId = null,
    ),
    source = TimeEntrySource.MANUAL,
    schedule = null,
)