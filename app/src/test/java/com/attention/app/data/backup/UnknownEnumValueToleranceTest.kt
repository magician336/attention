package com.attention.app.data.backup

import com.attention.domain.GoalCadence
import com.attention.domain.LaunchDestination
import com.attention.domain.ScheduleFrequency
import com.attention.domain.TimeEntrySource
import com.attention.domain.asAgendaEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 备份里出现当前版本不认识的枚举**值**时，整份备份必须仍然导入得进来，只是那个字段落到
 * 声明过的默认值。
 *
 * 枚举在备份 JSON 里是按名称持久化的（kotlinx.serialization 的枚举按名编码，`ignoreUnknownKeys`
 * 只放宽键名、不放宽枚举值）。只要某个枚举值在**没有升版本号**的情况下原地漂移——重命名、
 * 删除、或备份来自一个比本机更新的版本——整个 `decode` 就会抛 `SerializationException`，
 * 把这份备份里全部的目标、日程和时间记录一起挡在门外。对一个用户来说，「我的一条时间记录
 * 认不出来」应该只损失那一条记录，而不是整份备份。
 */
class UnknownEnumValueToleranceTest {

    /**
     * 夹具里的记录一律挂在 `t1` 上，所以 `targets` 必须真的有这个目标：`validate` 会校验交叉引用，
     * 让红信号纯粹落在枚举上，而不是被「阶段目标不存在」这种无关校验先打断。
     */
    private fun backupJson(
        settings: String = "{}",
        goalStages: String = "[]",
        timeEntries: String = "[]",
        schedules: String = "[]",
        recurrenceRules: String = "[]",
        futureGoalRules: String = "[]",
        launchDestination: String = "\"AGENDA\"",
        lastOpenedDestination: String = "\"AGENDA\"",
    ) = """{"version":1,"settings":$settings,"targets":[{"id":"t1","title":"学习"}],"goalStages":$goalStages,"timeEntries":$timeEntries,""" +
        """"schedules":$schedules,"recurrenceRules":$recurrenceRules,"migrations":[],"futureGoalRules":$futureGoalRules,""" +
        """"periodSnapshots":[],"targetMoves":[],"milestones":[],"experience":0,"activeTimer":null,""" +
        """"onboardingCompleted":true,"launchDestination":$launchDestination,""" +
        """"lastOpenedDestination":$lastOpenedDestination,"notificationsEnabled":true}"""

    /** 四个枚举的未知值都落到清单里显式声明的默认值，而不是抛异常。 */
    @Test
    fun an_unrecognized_enum_value_falls_back_to_the_declared_default() {
        val restored = AttentionBackupCodec.decode(
            backupJson(
                goalStages = """[{"id":"g1","targetId":"t1","cadence":"SPRINTLY","targetMinutes":60,"startDate":"2026-10-15"}]""",
                timeEntries = """[{"id":"e1","planningDate":"2026-10-15","durationMinutes":30,"source":"FROZEN_AT_MIDNIGHT"}]""",
                recurrenceRules = """[{"id":"r1","targetId":"t1","title":"晨间","frequency":"EVERY_FORTNIGHT","startDate":"2026-10-15"}]""",
                futureGoalRules = """[{"id":"f1","targetId":"t1","cadence":"WHENEVER","targetMinutes":60,"effectiveFrom":"2026-10-15"}]""",
                launchDestination = "\"SOME_FUTURE_PAGE\"",
                lastOpenedDestination = "\"SOME_FUTURE_PAGE\"",
            ),
        )

        assertEquals(LaunchDestination.AGENDA, restored.launchDestination)
        assertEquals(LaunchDestination.AGENDA, restored.lastOpenedDestination)
        assertEquals(TimeEntrySource.MANUAL, restored.timeEntries.single().source)
        assertEquals(ScheduleFrequency.DAILY, restored.recurrenceRules.single().frequency)
        assertEquals(GoalCadence.DAILY, restored.futureGoalRules.single().cadence)
        assertEquals(GoalCadence.DAILY, restored.goalStages.single().cadence)
    }

    /**
     * 容错的粒度是字段，不是备份：认不出的枚举旁边那些认得出的数据必须照样进来。
     *
     * 这是「整份备份被拒」与「单字段降级」的区别，也是这条性质存在的唯一理由。
     */
    @Test
    fun records_around_an_unrecognized_enum_value_still_import() {
        val restored = AttentionBackupCodec.decode(
            backupJson(
                settings = """{"planningDayBoundaryMinutes":180}""",
                goalStages = """[{"id":"g1","targetId":"t1","cadence":"SPRINTLY","targetMinutes":60,"startDate":"2026-10-15"}]""",
                timeEntries = """[{"id":"e1","planningDate":"2026-10-15","durationMinutes":30,"source":"TIMER"}]""",
            ),
        )

        assertEquals(180, restored.settings.planningDayBoundaryMinutes)
        assertEquals("g1", restored.goalStages.single().id)
        assertEquals(30, restored.timeEntries.single().durationMinutes)
        assertEquals(TimeEntrySource.TIMER, restored.timeEntries.single().source)
    }

    /** 已知的枚举值照旧按名往返，不被容错逻辑改写。 */
    @Test
    fun a_recognized_enum_value_still_round_trips_unchanged() {
        val restored = AttentionBackupCodec.decode(
            backupJson(
                goalStages = """[{"id":"g1","targetId":"t1","cadence":"MONTHLY","targetMinutes":60,"startDate":"2026-10-15"}]""",
                timeEntries = """[{"id":"e1","planningDate":"2026-10-15","durationMinutes":30,"source":"IMPORT"}]""",
                recurrenceRules = """[{"id":"r1","targetId":"t1","title":"晨间","frequency":"WEEKLY","startDate":"2026-10-15"}]""",
                launchDestination = "\"STATISTICS\"",
                lastOpenedDestination = "\"TARGETS\"",
            ),
        )

        assertEquals(LaunchDestination.STATISTICS, restored.launchDestination)
        assertEquals(LaunchDestination.TARGETS, restored.lastOpenedDestination)
        assertEquals(GoalCadence.MONTHLY, restored.goalStages.single().cadence)
        assertEquals(TimeEntrySource.IMPORT, restored.timeEntries.single().source)
        assertEquals(ScheduleFrequency.WEEKLY, restored.recurrenceRules.single().frequency)
    }

    /** 旧入口名仍然折成日程：容错不能把「兼容映射」这条既有性质顶掉。 */
    @Test
    fun legacy_entry_names_still_fold_into_the_schedule_entry() {
        val restored = AttentionBackupCodec.decode(
            backupJson(launchDestination = "\"TODAY\"", lastOpenedDestination = "\"ALL_DATES\""),
        )

        assertEquals(LaunchDestination.AGENDA, restored.launchDestination.asAgendaEntry())
        assertEquals(LaunchDestination.AGENDA, restored.lastOpenedDestination.asAgendaEntry())
    }

    /**
     * 仍然**不是错误的东西**必须继续当错误：容错只覆盖「枚举名认不出」，不覆盖缺字段、坏版本。
     *
     * 如果哪天兜底被写得太宽，这条会拦住它——一个把结构坏了的备份也悄悄导进来的导入，
     * 会让用户在毫不知情的情况下丢掉数据。
     */
    @Test
    fun a_structurally_broken_backup_is_still_rejected() {
        val missingVersion = """{"settings":{},"targets":[],"goalStages":[],"timeEntries":[],"schedules":[],"recurrenceRules":[],"migrations":[],"futureGoalRules":[],"periodSnapshots":[],"targetMoves":[],"milestones":[],"experience":0,"activeTimer":null,"onboardingCompleted":true,"launchDestination":"AGENDA","lastOpenedDestination":"AGENDA","notificationsEnabled":true}"""
        val wrongVersion = backupJson().replace("\"version\":1", "\"version\":2")

        assertThrows(IllegalArgumentException::class.java) { AttentionBackupCodec.decode(missingVersion) }
        assertThrows(IllegalArgumentException::class.java) { AttentionBackupCodec.decode(wrongVersion) }
    }
}
