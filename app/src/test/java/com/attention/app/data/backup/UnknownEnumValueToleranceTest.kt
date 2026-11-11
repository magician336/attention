package com.attention.app.data.backup

import com.attention.domain.GoalCadence
import com.attention.domain.GoalCadenceSerializer
import com.attention.domain.LaunchDestination
import com.attention.domain.LaunchDestinationSerializer
import com.attention.domain.ScheduleFrequency
import com.attention.domain.ScheduleFrequencySerializer
import com.attention.domain.TimeEntrySource
import com.attention.domain.TimeEntrySourceSerializer
import com.attention.domain.asAgendaEntry
import com.attention.domain.launchDestinationFromPersistedName
import kotlinx.serialization.json.Json
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
 *
 * 这条容错知识现在由备份协议自己的枚举承担（[BackupProtocolEnums]）；领域侧另有一份等价的解析，
 * 服务 Room 的枚举列与 DataStore 的启动入口偏好，[the_backup_path_and_the_database_path_agree_on_an_unrecognized_name]
 * 把两侧钉在一起。
 */
class UnknownEnumValueToleranceTest {

    /** 与 [AttentionBackupCodec] 同配置的宽松 Json，只用来读出协议文档本身。 */
    private val protocolJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

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

    /**
     * 只做协议解码（不走 `validate`），用来断言**协议自己**把那个字段读成了什么。
     *
     * [AttentionBackupCodec.decode] 返回的是领域状态，已经走过 `toDomainState()`，所以看不到协议枚举
     * 读成了哪个常量。两层都要断言：协议层是容错规则真正生效的地方，领域层是用户最终看到的东西。
     */
    private fun protocolOf(encoded: String): AttentionBackup =
        protocolJson.decodeFromString(encoded)

    /** 四个枚举的未知值都落到清单里显式声明的默认值，而不是抛异常。 */
    @Test
    fun an_unrecognized_enum_value_falls_back_to_the_declared_default() {
        val encoded = backupJson(
            goalStages = """[{"id":"g1","targetId":"t1","cadence":"SPRINTLY","targetMinutes":60,"startDate":"2026-10-15"}]""",
            timeEntries = """[{"id":"e1","planningDate":"2026-10-15","durationMinutes":30,"source":"FROZEN_AT_MIDNIGHT"}]""",
            recurrenceRules = """[{"id":"r1","targetId":"t1","title":"晨间","frequency":"EVERY_FORTNIGHT","startDate":"2026-10-15"}]""",
            futureGoalRules = """[{"id":"f1","targetId":"t1","cadence":"WHENEVER","targetMinutes":60,"effectiveFrom":"2026-10-15"}]""",
            launchDestination = "\"SOME_FUTURE_PAGE\"",
            lastOpenedDestination = "\"SOME_FUTURE_PAGE\"",
        )
        val restored = AttentionBackupCodec.decode(encoded)

        // 协议层：容错规则读成的就是声明过的默认值。
        val protocol = protocolOf(encoded)
        assertEquals(BackupLaunchDestination.AGENDA, protocol.launchDestination)
        assertEquals(BackupLaunchDestination.AGENDA, protocol.lastOpenedDestination)
        assertEquals(BackupTimeEntrySource.MANUAL, protocol.timeEntries.single().source)
        assertEquals(BackupScheduleFrequency.DAILY, protocol.recurrenceRules.single().frequency)
        assertEquals(BackupGoalCadence.DAILY, protocol.futureGoalRules.single().cadence)
        assertEquals(BackupGoalCadence.DAILY, protocol.goalStages.single().cadence)

        // 领域层：用户最终拿到的状态也是同一个默认值。
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
        val encoded = backupJson(
            goalStages = """[{"id":"g1","targetId":"t1","cadence":"MONTHLY","targetMinutes":60,"startDate":"2026-10-15"}]""",
            timeEntries = """[{"id":"e1","planningDate":"2026-10-15","durationMinutes":30,"source":"IMPORT"}]""",
            recurrenceRules = """[{"id":"r1","targetId":"t1","title":"晨间","frequency":"WEEKLY","startDate":"2026-10-15"}]""",
            launchDestination = "\"STATISTICS\"",
            lastOpenedDestination = "\"TARGETS\"",
        )
        val restored = AttentionBackupCodec.decode(encoded)

        // 协议层读出来是什么，写回去就还是什么：容错没有改写任何已知值。
        val protocol = protocolOf(encoded)
        assertEquals(BackupLaunchDestination.STATISTICS, protocol.launchDestination)
        assertEquals(BackupLaunchDestination.TARGETS, protocol.lastOpenedDestination)
        assertEquals(BackupGoalCadence.MONTHLY, protocol.goalStages.single().cadence)
        assertEquals(BackupTimeEntrySource.IMPORT, protocol.timeEntries.single().source)
        assertEquals(BackupScheduleFrequency.WEEKLY, protocol.recurrenceRules.single().frequency)

        assertEquals(LaunchDestination.STATISTICS, restored.launchDestination)
        assertEquals(LaunchDestination.TARGETS, restored.lastOpenedDestination)
        assertEquals(GoalCadence.MONTHLY, restored.goalStages.single().cadence)
        assertEquals(TimeEntrySource.IMPORT, restored.timeEntries.single().source)
        assertEquals(ScheduleFrequency.WEEKLY, restored.recurrenceRules.single().frequency)
    }

    /** 旧入口名仍然折成日程：容错不能把「兼容映射」这条既有性质顶掉。 */
    @Test
    fun legacy_entry_names_still_fold_into_the_schedule_entry() {
        val encoded = backupJson(launchDestination = "\"TODAY\"", lastOpenedDestination = "\"ALL_DATES\"")
        val restored = AttentionBackupCodec.decode(encoded)

        // 协议仍读得出旧名本身（它们没有被删掉），折成日程由兼容映射做。
        val protocol = protocolOf(encoded)
        assertEquals(BackupLaunchDestination.TODAY, protocol.launchDestination)
        assertEquals(BackupLaunchDestination.ALL_DATES, protocol.lastOpenedDestination)
        assertEquals(BackupLaunchDestination.AGENDA, protocol.launchDestination.asAgendaEntry())
        assertEquals(BackupLaunchDestination.AGENDA, protocol.lastOpenedDestination.asAgendaEntry())

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

    /**
     * 备份路径与数据库/设置路径对同一个无法识别的名字必须给出同一个值。
     *
     * 容错规则现在有两份实现：协议侧那一份（`BackupProtocolEnums`）读备份 JSON，领域侧那一份
     * （`:domain` 的 `PersistedEnumSerializer`）读 Room 的枚举列与 DataStore 的启动入口偏好。
     * 两份实现各改各的，最坏情况是同一个名字在设置里容错、在备份里被整份丢掉，或者反过来——
     * 而这类不一致不会在任何一侧的测试里显形，因为每侧都只测自己。
     *
     * 因此这里把四个枚举（启动入口额外算上旧入口名的兼容折法）逐个对照：任一侧改动默认值、
     * 改名、删除或新增，这条都会变红。
     */
    @Test
    fun the_backup_path_and_the_database_path_agree_on_an_unrecognized_name() {
        everyNameBothSidesMightSee().forEach { name ->
            assertEquals(
                "启动入口：备份路径与数据库/设置路径对 \"$name\" 的结论不一致",
                LaunchDestinationSerializer.fromPersistedName(name).name,
                BackupLaunchDestinationSerializer.fromPersistedName(name).name,
            )
            assertEquals(
                "启动入口的旧名兼容折法：\"$name\" 在两条路径上的结论不一致",
                com.attention.domain.launchDestinationFromPersistedName(name).name,
                BackupLaunchDestinationSerializer.fromPersistedName(name).asAgendaEntry().name,
            )
            assertEquals(
                "阶段节奏：备份路径与数据库路径对 \"$name\" 的结论不一致",
                GoalCadenceSerializer.fromPersistedName(name).name,
                BackupGoalCadenceSerializer.fromPersistedName(name).name,
            )
            assertEquals(
                "时间记录来源：备份路径与数据库路径对 \"$name\" 的结论不一致",
                TimeEntrySourceSerializer.fromPersistedName(name).name,
                BackupTimeEntrySourceSerializer.fromPersistedName(name).name,
            )
            assertEquals(
                "重复日程频率：备份路径与数据库路径对 \"$name\" 的结论不一致",
                ScheduleFrequencySerializer.fromPersistedName(name).name,
                BackupScheduleFrequencySerializer.fromPersistedName(name).name,
            )
        }
    }

    /** 旧入口名在两条路径上也必须折成同一个入口，否则旧备份恢复出来的落点会与设置里不一致。 */
    @Test
    fun both_paths_fold_the_legacy_entry_names_the_same_way() {
        listOf("TODAY", "ALL_DATES").forEach { name ->
            assertEquals(
                "旧入口名 \"$name\" 在备份路径上不再折成日程入口",
                "AGENDA",
                BackupLaunchDestinationSerializer.fromPersistedName(name).asAgendaEntry().name,
            )
            assertEquals(
                "旧入口名 \"$name\" 在设置路径上不再折成日程入口",
                "AGENDA",
                com.attention.domain.launchDestinationFromPersistedName(name).name,
            )
        }
    }
}

/**
 * 两侧可能见到的全部名字：**协议侧与领域侧所有枚举常量的并集**，外加几个谁都不认识的样本。
 *
 * 用并集而不是几个固定的陌生名字，是因为固定名单只试得到「改默认值」这一种偏离。试到全部常量名
 * 之后，任何「某个名字在一侧认得、另一侧不认得」的情形都会暴露成结论不一致——而那正是「单侧
 * 新增」「单侧删除」「单侧改名」的后果。
 *
 * 不过这三类偏离今天多半**先被编译器拦住**：协议侧与领域侧都有对这几个枚举的穷举 `when`，改一个
 * 常量名就要同步所有分支，编译不过。对照测试是第二道网，编译器是第一道。这里列全常量名是为了
 * 万一将来某个分支改成非穷举、或者默认值以外的行为出现分叉时，这一层仍然兜得住。
 */
private fun everyNameBothSidesMightSee(): Set<String> = buildSet {
    addAll(LaunchDestination.entries.map { it.name })
    addAll(BackupLaunchDestination.entries.map { it.name })
    addAll(GoalCadence.entries.map { it.name })
    addAll(BackupGoalCadence.entries.map { it.name })
    addAll(TimeEntrySource.entries.map { it.name })
    addAll(BackupTimeEntrySource.entries.map { it.name })
    addAll(ScheduleFrequency.entries.map { it.name })
    addAll(BackupScheduleFrequency.entries.map { it.name })
    // 谁都不认识的样本：空串、纯空白、大小写不符、明显是未来某个版本的名字
    addAll(listOf("", " ", "daily", "SOME_FUTURE_PAGE", "SPRINTLY", "FROZEN_AT_MIDNIGHT"))
}
