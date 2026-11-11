package com.attention.app.data.backup

import kotlinx.serialization.serializer

/** 协议字段是一个对象，还是同一个对象的列表。 */
enum class BackupPayloadContainer { VALUE, LIST }

/**
 * 清单项的类别。
 *
 * [PROTOCOL_RECORD] 是协议自己的数据模型（`Backup*`，见 [BackupProtocolModels]），
 * [PROTOCOL_ENUM] 是协议自己的按名持久化枚举（见 [BackupProtocolEnums]），
 * [PROTOCOL_SCALAR] 是协议自己定义的标量字段。
 *
 * [DOMAIN_ENUM] 目前没有任何载荷占着：备份字段不再直接承载领域枚举。枚举承载点之所以必须是
 * 协议类型而不是领域类型，是因为线上那些枚举名是备份协议的东西，「名字认不出来时读成什么」
 * 这条容错规则也随它走（见 `BackupProtocolEnums`）。留下 [DOMAIN_ENUM] 这一类是为了让
 * 「协议字段又直接引用了领域枚举」这件事在清单里显形，而不是悄悄回到上一轮的过渡状态。
 */
enum class BackupPayloadKind { PROTOCOL_RECORD, PROTOCOL_ENUM, DOMAIN_ENUM, PROTOCOL_SCALAR }

/**
 * 一条「备份协议承载了什么」的清单项。
 *
 * @param field 承载路径。顶层字段就是协议字段名（`targets`），嵌在另一个协议模型里的承载点
 * 写成点路径（`activeTimer.completedSegments`）。
 * @param serialName 该路径上承载的类型的序列化标识，取自类型自己的序列化描述符。
 * 写死成序列化描述符里的名字，是为了让一致性测试能与协议实际形状直接比对。
 * @param wireFields 该类型自己被序列化出去的字段名，逗号分隔。与清单分开的协议标量留空。
 * @param reason 为什么它在备份里——是「没有它就恢复不出用户的东西」的硬理由，
 * 而不是随手带上的字段。
 */
data class BackupPayload(
    val field: String,
    val serialName: String,
    val container: BackupPayloadContainer,
    val kind: BackupPayloadKind,
    val wireFields: String,
    val reason: String,
) {
    /** [wireFields] 的集合形式，供一致性测试与协议描述符比对。 */
    fun wireFieldNames(): Set<String> =
        wireFields.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
}

/**
 * version 1 备份协议承载内容的显式清单。
 *
 * 协议由自己的模型承载（见 [AttentionBackup] 与 [BackupProtocolModels]），领域状态与协议模型之间
 * 隔着 [BackupDomainMapping] 的映射，所以「它到底把什么写进了文件」必须被写下来，而不是靠读
 * 构造函数去推断。本清单逐项列出协议承载的每个类型、每个嵌在里面的承载点，以及协议自己的标量
 * 字段，每项写明它在备份里的理由。
 *
 * 清单与协议实际字段的一致性由 `BackupProtocolPayloadTest` 保证：给协议新增一个字段、把某个
 * 承载点的容器改掉、或新增一个被承载的类型，那条测试都会变红。协议字段与**领域**字段的一一对应
 * 由 `BackupDomainMappingTest` 保证：给领域类型加一个进入备份的字段而忘了映射、或协议凭空多出
 * 一个字段，那条测试会变红。两条护栏互补，谁也替代不了谁。
 *
 * 每一条清单项都在回答「备份里的哪一段对应哪个领域概念」，所以下面的 `reason` 写的是领域侧的
 * 理由，而不是协议字段的理由——「这段内容对应哪个领域类型、哪个字段」由映射层与上面那条
 * 一致性测试保证。
 */
object BackupProtocolPayloads {

    /** 协议自有模型在序列化描述符里的包前缀。承载点是否属于协议，由它判断。 */
    private const val PROTOCOL_PREFIX = "com.attention.app.data.backup."

    /** 协议承载的全部内容。顺序与 [AttentionBackup] 的字段声明顺序一致。 */
    val payloads: List<BackupPayload> = listOf(
        BackupPayload(
            field = "version",
            serialName = serializer<Int>().descriptor.serialName,
            container = BackupPayloadContainer.VALUE,
            kind = BackupPayloadKind.PROTOCOL_SCALAR,
            wireFields = "",
            reason = "协议版本号。解码时必须先读它：版本不被支持就整份拒收，而不是猜着解析。",
        ),
        BackupPayload(
            field = "settings",
            serialName = BackupStoredSettings.serializer().descriptor.serialName,
            container = BackupPayloadContainer.VALUE,
            kind = BackupPayloadKind.PROTOCOL_RECORD,
            wireFields = "planningDayBoundaryMinutes, weekStartDay, dailyCapacityMinutes",
            reason = "规划日界线、周起始日与每日容量是日期统计的口径。换一台设备却不带上它们，" +
                "同一条时间记录会落到不同的规划日里，用户看到的历史因此被改写。",
        ),
        BackupPayload(
            field = "targets",
            serialName = BackupTarget.serializer().descriptor.serialName,
            container = BackupPayloadContainer.LIST,
            kind = BackupPayloadKind.PROTOCOL_RECORD,
            wireFields = "id, parentId, title, note, icon, colorHex, archived, sortOrder, expanded",
            reason = "目标树的节点本身：层级靠 parentId 表达，归档靠 archived 表达。没有它，" +
                "所有时间记录、日程条目与达成里程碑都失去归属，恢复出来是一片没有目标的账。",
        ),
        BackupPayload(
            field = "goalStages",
            serialName = BackupGoalStage.serializer().descriptor.serialName,
            container = BackupPayloadContainer.LIST,
            kind = BackupPayloadKind.PROTOCOL_RECORD,
            wireFields = "id, targetId, cadence, targetMinutes, startDate, dueDate, completed",
            reason = "目标阶段承载目标量与达成状态，是「计划投入」与实际投入对照的另一半。" +
                "只恢复实际投入而不恢复它，缺口就无从计算。",
        ),
        BackupPayload(
            field = "timeEntries",
            serialName = BackupTimeEntry.serializer().descriptor.serialName,
            container = BackupPayloadContainer.LIST,
            kind = BackupPayloadKind.PROTOCOL_RECORD,
            wireFields = "id, planningDate, durationMinutes, targetId, source, occurredAtEpochMillis, " +
                "note, scheduleEntryId",
            reason = "时间记录是实际投入的事实本身，也是投入经验与达成里程碑的唯一来源。" +
                "它才是这份备份存在的理由。",
        ),
        BackupPayload(
            field = "schedules",
            serialName = BackupScheduleEntry.serializer().descriptor.serialName,
            container = BackupPayloadContainer.LIST,
            kind = BackupPayloadKind.PROTOCOL_RECORD,
            wireFields = "id, planningDate, title, startMinute, endMinute, estimatedMinutes, note, " +
                "targetId, completed, reminderEpochMillis, recurrenceRuleId",
            reason = "日程条目是「打算在哪天做什么」，与时间记录是两回事。时间记录可以由日程确认而来，" +
                "但日程本身还带有时间段、预计时长与日程提醒，不能被时间记录替代。",
        ),
        BackupPayload(
            field = "recurrenceRules",
            serialName = BackupRecurrenceRule.serializer().descriptor.serialName,
            container = BackupPayloadContainer.LIST,
            kind = BackupPayloadKind.PROTOCOL_RECORD,
            wireFields = "id, title, startDate, frequency, weekdays, dayOfMonth, untilDate, active, " +
                "estimatedMinutes, targetId, note, reminderMinuteOfDay",
            reason = "重复日程的规则本身。它决定未来还会生成哪些日程条目；丢掉它只会剩下历史发生过的条目，" +
                "用户回来后安排不再延续。",
        ),
        BackupPayload(
            field = "migrations",
            serialName = BackupMigration.serializer().descriptor.serialName,
            container = BackupPayloadContainer.LIST,
            kind = BackupPayloadKind.PROTOCOL_RECORD,
            wireFields = "id, targetId, sourceStageId, minutes, destinationStartDate, destinationEndDate, cancelled",
            reason = "目标迁移把当前周期的剩余目标安排到未来周期。原周期的目标缺口仍然保留，" +
                "所以迁移记录必须跟着走，否则恢复后的缺口与用户的迁移决定对不上。",
        ),
        BackupPayload(
            field = "futureGoalRules",
            serialName = BackupFutureGoalRule.serializer().descriptor.serialName,
            container = BackupPayloadContainer.LIST,
            kind = BackupPayloadKind.PROTOCOL_RECORD,
            wireFields = "id, targetId, stageId, cadence, targetMinutes, effectiveFrom, dueDate",
            reason = "未来目标规则只在指定日期之后生效，不回写已经开始的周期。" +
                "不带它，未来周期的目标量会算错。",
        ),
        BackupPayload(
            field = "periodSnapshots",
            serialName = BackupPeriodSnapshot.serializer().descriptor.serialName,
            container = BackupPayloadContainer.LIST,
            kind = BackupPayloadKind.PROTOCOL_RECORD,
            wireFields = "id, targetId, cadence, periodStart, periodEnd, targetMinutes, actualMinutes, " +
                "gapMinutes, excessMinutes, completed, stageId",
            reason = "周期快照是历史事实的计算依据，按 ADR-0002 保留历史目标引用，删除目标也不删除它。" +
                "少了它，已经结束周期的缺口与达成判定会变成重新计算的另一个值。",
        ),
        BackupPayload(
            field = "targetMoves",
            serialName = BackupTargetMove.serializer().descriptor.serialName,
            container = BackupPayloadContainer.LIST,
            kind = BackupPayloadKind.PROTOCOL_RECORD,
            wireFields = "id, targetId, parentId, effectiveFrom",
            reason = "目标移动从指定日期起改变目标树的父子关系。不带上它，恢复后的目标树会回到移动之前的位置。",
        ),
        BackupPayload(
            field = "milestones",
            serialName = BackupMilestone.serializer().descriptor.serialName,
            container = BackupPayloadContainer.LIST,
            kind = BackupPayloadKind.PROTOCOL_RECORD,
            wireFields = "id, kind, instanceKey, reward, achievedAtEpochMillis",
            reason = "达成里程碑是一次性反馈，靠 instanceKey 去重。它属于用户的历史，不是可以重算的派生值。",
        ),
        BackupPayload(
            field = "experience",
            serialName = serializer<Long>().descriptor.serialName,
            container = BackupPayloadContainer.VALUE,
            kind = BackupPayloadKind.PROTOCOL_SCALAR,
            wireFields = "",
            reason = "投入经验的总量。导入时会被 recalculateExperience() 重新推导，" +
                "写进协议是为了文档自解释，以及让状态指纹不依赖推导时机。",
        ),
        BackupPayload(
            field = "activeTimer",
            serialName = BackupActiveTimer.serializer().descriptor.serialName,
            container = BackupPayloadContainer.VALUE,
            kind = BackupPayloadKind.PROTOCOL_RECORD,
            wireFields = "targetId, startedAtEpochMillis, accumulatedMillis, paused, lastPlanningDate, " +
                "completedSegments, scheduleEntryId",
            reason = "正在进行的计时现场。它对应用户当下正在做的事，随用户数据一起保存；" +
                "丢掉它会把一段已经积累的实际投入清零。",
        ),
        BackupPayload(
            field = "onboardingCompleted",
            serialName = serializer<Boolean>().descriptor.serialName,
            container = BackupPayloadContainer.VALUE,
            kind = BackupPayloadKind.PROTOCOL_SCALAR,
            wireFields = "",
            reason = "引导状态。恢复一份老备份到新设备时，用户不该被重新走一遍引导流程。",
        ),
        BackupPayload(
            field = "launchDestination",
            serialName = BackupLaunchDestination.serializer().descriptor.serialName,
            container = BackupPayloadContainer.VALUE,
            kind = BackupPayloadKind.PROTOCOL_ENUM,
            wireFields = "",
            reason = "用户选定的启动入口。它是用户偏好而不是领域事实，因此与目标、日程等领域记录分开列出。" +
                "它写出去的就是线上名字，所以容错规则由协议自己的枚举承担：名字认不出来时按「日程」读，" +
                "而不是让整份备份被拒。",
        ),
        BackupPayload(
            field = "lastOpenedDestination",
            serialName = BackupLaunchDestination.serializer().descriptor.serialName,
            container = BackupPayloadContainer.VALUE,
            kind = BackupPayloadKind.PROTOCOL_ENUM,
            wireFields = "",
            reason = "上次打开的页面。恢复备份后「上次打开」应当仍指向用户当时离开的地方。" +
                "认不出来的入口名同样按「日程」读；旧入口名（今天页、所有日期）折成日程入口。",
        ),
        BackupPayload(
            field = "notificationsEnabled",
            serialName = serializer<Boolean>().descriptor.serialName,
            container = BackupPayloadContainer.VALUE,
            kind = BackupPayloadKind.PROTOCOL_SCALAR,
            wireFields = "",
            reason = "通知偏好属于应用设置，跟着状态一起恢复，避免恢复后日程提醒静默改变。",
        ),
        // 以下承载点不出现在协议顶层，而是嵌在被承载的协议模型里，
        // 但同样会随父类型被序列化，因此也是协议承载的内容。
        BackupPayload(
            field = "goalStages.cadence",
            serialName = BackupGoalCadence.serializer().descriptor.serialName,
            container = BackupPayloadContainer.VALUE,
            kind = BackupPayloadKind.PROTOCOL_ENUM,
            wireFields = "",
            reason = "目标阶段的周期口径：每日、每周、每月或一次性。同一个目标量在不同周期下不是一个承诺。" +
                "认不出来的口径按「每日」读，宁可多提醒也不要静默漏掉。",
        ),
        BackupPayload(
            field = "futureGoalRules.cadence",
            serialName = BackupGoalCadence.serializer().descriptor.serialName,
            container = BackupPayloadContainer.VALUE,
            kind = BackupPayloadKind.PROTOCOL_ENUM,
            wireFields = "",
            reason = "未来目标规则沿用同一套周期口径。缺了它就无法判断这条未来规则约束的是一天、一周还是一月，" +
                "规则会被静默读成另一个承诺。",
        ),
        BackupPayload(
            field = "periodSnapshots.cadence",
            serialName = BackupGoalCadence.serializer().descriptor.serialName,
            container = BackupPayloadContainer.VALUE,
            kind = BackupPayloadKind.PROTOCOL_ENUM,
            wireFields = "",
            reason = "周期快照必须记住它是按哪个周期口径留存的历史依据。没有它，快照无法与同口径的历史对齐，" +
                "缺口与达成判定会对不上。",
        ),
        BackupPayload(
            field = "timeEntries.source",
            serialName = BackupTimeEntrySource.serializer().descriptor.serialName,
            container = BackupPayloadContainer.VALUE,
            kind = BackupPayloadKind.PROTOCOL_ENUM,
            wireFields = "",
            reason = "时间记录的来源。它决定记录是否具备精确计时时刻，也影响回顾与统计的呈现方式。" +
                "认不出来的来源按「手动」处理：手动是唯一一个不谎报事实的选项。",
        ),
        BackupPayload(
            field = "recurrenceRules.frequency",
            serialName = BackupScheduleFrequency.serializer().descriptor.serialName,
            container = BackupPayloadContainer.VALUE,
            kind = BackupPayloadKind.PROTOCOL_ENUM,
            wireFields = "",
            reason = "重复日程的频率。它与 weekdays、dayOfMonth 一起决定规则如何展开成日程条目。" +
                "认不出来的频率按「每日」处理：不漏掉用户已经答应的那些天，好过静默地少排几天。",
        ),
        BackupPayload(
            field = "activeTimer.completedSegments",
            serialName = BackupTimerSegment.serializer().descriptor.serialName,
            container = BackupPayloadContainer.LIST,
            kind = BackupPayloadKind.PROTOCOL_RECORD,
            wireFields = "startedAtEpochMillis, endedAtEpochMillis",
            reason = "计时器里已经完成的计时片段。累计时长由它们累加而来，恢复时若丢失，" +
                "已经记下的实际投入会被当成零。",
        ),
    )

    /** 某个类型在清单里声明的全部承载点。 */
    fun payloadsOf(serialName: String): List<BackupPayload> = payloads.filter { it.serialName == serialName }

    /** 某个承载类型在清单里的全部路径，例如启动入口与上次打开共用同一个枚举。 */
    fun fieldsOf(serialName: String): Set<String> = payloadsOf(serialName).mapTo(linkedSetOf()) { it.field }

    /** 判断某个序列化标识是否属于备份协议自有的模型。 */
    fun isProtocolType(serialName: String): Boolean = serialName.startsWith(PROTOCOL_PREFIX)
}
