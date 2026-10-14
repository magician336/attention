package com.attention.app.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 纯格式化 seam：时长以中文自然语言表达，用户看到的是可直接读出的句子片段，
 * 不是数字加单位的拼接。
 *
 * 断言只读格式化层的输出，不触碰 Compose、房间与领域引擎。
 */
class DurationFormatTest {

    @Test
    fun over_an_hour_shows_hours_and_minutes_together() {
        assertEquals("1 小时 15 分钟", DurationFormat.ofMinutes(75))
    }

    @Test
    fun under_an_hour_stays_short() {
        assertEquals("45 分钟", DurationFormat.ofMinutes(45))
    }

    @Test
    fun a_single_minute_is_not_blank() {
        assertEquals("1 分钟", DurationFormat.ofMinutes(1))
    }

    @Test
    fun exactly_one_hour_does_not_carry_empty_minutes() {
        assertEquals("1 小时", DurationFormat.ofMinutes(60))
    }

    @Test
    fun zero_has_its_own_explicit_expression() {
        val zero = DurationFormat.ofMinutes(0)

        assertEquals("零时长必须有自己的表达，不能落回空串", "0 分钟", zero)
    }

    @Test
    fun minutes_beyond_one_hour_are_carried_into_hours() {
        assertEquals("2 小时 5 分钟", DurationFormat.ofMinutes(125))
    }

    @Test
    fun minutes_below_one_hour_never_promote_a_zero_hour() {
        assertEquals("59 分钟", DurationFormat.ofMinutes(59))
    }

    @Test
    fun a_negative_duration_is_read_as_zero_rather_than_printed_as_is() {
        assertEquals(DurationFormat.ofMinutes(0), DurationFormat.ofMinutes(-30))
    }
}