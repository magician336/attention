package com.attention.app.data.backup

import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.FutureGoalRule
import com.attention.domain.GoalCadence
import com.attention.domain.GoalCadenceSerializer
import com.attention.domain.GoalStage
import com.attention.domain.LaunchDestination
import com.attention.domain.LaunchDestinationSerializer
import com.attention.domain.Migration
import com.attention.domain.Milestone
import com.attention.domain.PeriodSnapshot
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.domain.ScheduleFrequencySerializer
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.domain.TargetMove
import com.attention.domain.TimeEntry
import com.attention.domain.TimeEntrySource
import com.attention.domain.TimeEntrySourceSerializer
import com.attention.domain.TimerSegment
import com.attention.domain.recalculateExperience
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 领域类型 ↔ 备份协议模型之间那条映射的完备性护栏。
 *
 * `BackupProtocolPayloadTest` 守的是「协议自己的形状」：协议描述符与那份显式清单是否一致。
 * 它**不会**发现「给领域类型加了一个进入备份的字段，映射里忘了搬」，也不会发现「协议模型凭空
 * 多了一个领域侧没有的字段」——因为在它眼里，协议模型就是权威。这两条测试补上那个缺口：
 *
 * - 领域侧每个进入备份的字段在协议模型里都有对应，协议模型里每个字段在领域侧也有对应
 *   （[every_backed_up_field_has_a_protocol_counterpart_in_both_directions]）；
 * - 这张对照表覆盖的正好是清单声明的全部协议模型，不多不少
 *   （[the_mapping_table_covers_exactly_the_protocol_models_the_manifest_declares]）；
 * - 逐字段的映射函数真的把值搬过去了，一个字段都没漏、没造
 *   （[every_field_value_survives_a_round_trip_through_the_protocol_model]）；
 * - 协议模型相对领域类型唯一的「可选性」差异被钉死，不许悄悄增加
 *   （[the_only_optionality_difference_is_the_row_id_the_protocol_requires]）。
 *
 * 领域侧的可序列化标注已经摘掉（见 ADR 0005「备份协议的边界」），所以这张表用 Kotlin 反射读
 * 两个类型的构造参数来对照字段：构造参数顺序就是声明顺序，`KParameter.isOptional` 就是「这个字段
 * 有没有默认值」。序列化描述符只留给协议侧——协议模型仍然要真的能被编解码，它的那种顺序与可选性
 * 只有描述符说得准。对照时协议枚举按类型名翻译成领域枚举（两侧承载的是同名但不同身份的枚举，
 * 备份协议那一份见 `BackupProtocolEnums`）。
 */
class BackupDomainMappingTest {

    /**
     * 进入备份的每一个领域类型，与承载它的协议模型。
     *
     * 路径写的是协议里的承载点，与 [BackupProtocolPayloads] 的 `field` 一致，因此两张表可以互相
     * 校验，不必靠注释维持一致。
     */
    private val mappedTypes: List<MappedType> = listOf(
        MappedType("settings", StoredSettings::class, BackupStoredSettings::class),
        MappedType("targets", Target::class, BackupTarget::class),
        MappedType("goalStages", GoalStage::class, BackupGoalStage::class),
        MappedType("timeEntries", TimeEntry::class, BackupTimeEntry::class),
        MappedType("schedules", ScheduleEntry::class, BackupScheduleEntry::class),
        MappedType("recurrenceRules", RecurrenceRule::class, BackupRecurrenceRule::class),
        MappedType("migrations", Migration::class, BackupMigration::class),
        MappedType("futureGoalRules", FutureGoalRule::class, BackupFutureGoalRule::class),
        MappedType("periodSnapshots", PeriodSnapshot::class, BackupPeriodSnapshot::class),
        MappedType("targetMoves", TargetMove::class, BackupTargetMove::class),
        MappedType("milestones", Milestone::class, BackupMilestone::class),
        MappedType("activeTimer", ActiveTimer::class, BackupActiveTimer::class),
        MappedType(
            "activeTimer.completedSegments",
            TimerSegment::class,
            BackupTimerSegment::class,
        ),
    )

    @Test
    fun every_backed_up_field_has_a_protocol_counterpart_in_both_directions() {
        assertEquals(
            "对照表漏了一个进入备份的领域类型，或多了一个协议模型",
            mappedTypes.size,
            mappedTypes.map { it.path }.toSet().size,
        )
        mappedTypes.forEach { mapped ->
            assertEquals(
                "${mapped.path} 的领域侧与协议侧字段数量不一致：协议模型凭空多出字段，或漏掉了字段。" +
                    "给领域类型新增一个进入备份的字段时，必须同时加进 ${mapped.protocol.protocolName()}。",
                mapped.domain.shape(),
                mapped.protocol.shape(),
            )
        }
    }

    @Test
    fun the_mapping_table_covers_exactly_the_protocol_models_the_manifest_declares() {
        val declared = BackupProtocolPayloads.payloads
            .filter { it.kind == BackupPayloadKind.PROTOCOL_RECORD }
            .associate { it.serialName to it.field }

        assertEquals(
            "对照表与清单对「备份里有哪些协议模型」的说法不一致：见 BackupProtocolPayloads。",
            declared.toSortedMap(),
            mappedTypes.associate { it.protocol.protocolName() to it.path }.toSortedMap(),
        )
        mappedTypes.forEach { mapped ->
            assertTrue(
                "${mapped.path} 的协议侧类型 ${mapped.protocol.protocolName()} 不在备份协议自己的包里",
                BackupProtocolPayloads.isProtocolType(mapped.protocol.protocolName()),
            )
            assertTrue(
                "${mapped.path} 的领域侧类型 ${mapped.domain.qualifiedName} 不在 :domain 里",
                mapped.domain.qualifiedName!!.startsWith(DOMAIN_PREFIX),
            )
        }
    }

    /**
     * 协议枚举的翻译表必须正好覆盖清单里的全部协议枚举承载类型。
     *
     * 少了某个：那个字段的协议类型与领域类型名字对不上，[every_backed_up_field_has_a_protocol_counterpart]
     * 会变红——但报错说的是「字段对不上」，读的人看不出真正的原因是翻译表没登记这个枚举。
     * 多了某个：翻译表里有一条清单从不使用、也没有对应领域枚举的规则，它悄悄放宽了对照，属于没人要的宽容。
     */
    @Test
    fun the_enum_translation_table_covers_exactly_the_manifests_protocol_enums() {
        val declared = BackupProtocolPayloads.payloads
            .filter { it.kind == BackupPayloadKind.PROTOCOL_ENUM }
            .map { it.serialName }
            .toSortedSet()

        assertEquals(
            "协议枚举翻译表与清单对「备份里有哪些协议枚举」的说法不一致：见 protocolEnumToDomainEnum。",
            protocolEnumToDomainEnum.keys.toSortedSet(),
            declared,
        )
        assertTrue(
            "翻译表里有条目没有对应的领域枚举：${protocolEnumToDomainEnum.filterValues { !it.startsWith(DOMAIN_PREFIX) }.keys}",
            protocolEnumToDomainEnum.values.all { it.startsWith(DOMAIN_PREFIX) },
        )
    }

    /** 每一行清单项的承载路径都必须能在这张对照表里找到对应的领域类型与协议模型。 */
    @Test
    fun every_manifest_path_resolves_to_a_mapped_pair() {
        val paths = mappedTypes.map { it.path }.toSet()
        BackupProtocolPayloads.payloads
            .filter { it.kind == BackupPayloadKind.PROTOCOL_RECORD }
            .forEach { payload ->
                assertTrue(
                    "清单项 ${payload.field} 在领域↔协议对照表里找不到对应关系",
                    payload.field in paths,
                )
            }
    }

    /**
     * 逐字段搬值的证据：把每个字段都设成非默认值，走一遍「领域 → 协议模型 → 领域」。
     *
     * 只比签名不够——映射函数里漏搬一个字段，签名照样对得上。这里用实际取值把每个字段都压到
     * 非默认值上，任何一个字段没被搬过去，round-trip 就不相等。
     */
    @Test
    fun every_field_value_survives_a_round_trip_through_the_protocol_model() {
        val state = fullyPopulatedState()

        val protocol = AttentionBackup.fromDomain(state)
        val restored = protocol.toDomain()

        assertEquals(10_045L, state.recalculateExperience().experience)
        assertEquals(state.recalculateExperience(), restored)
        assertEquals(state, restored)
    }

    /**
     * 协议模型相对领域类型的可选性差异：一处都不允许。
     *
     * 可选性决定缺字段的历史备份读成什么：协议模型一旦在某处悄悄放宽或收紧，缺这一段的历史备份
     * 就会被读成另一个值，而字段名与类型全都对得上，前两条测试看不出来。
     *
     * 这里特别在意行 ID：迁移前协议字段由领域类型承载，`id` 带着 `newId()` 默认值，缺 ID 的行会
     * 被安上一个随机 ID 导入。当时有人提议趁这次迁移把它收紧成「缺 ID 即结构损坏」，被否决了
     * ——那是用户可见的行为变更，超出这次重构的范围，记在 #207 待裁决。协议侧因此自带
     * [newBackupRowId]，行为与迁移前一致，但不再反向依赖领域。
     */
    @Test
    fun the_protocol_is_exactly_as_optional_as_the_domain_on_every_field() {
        val differences = mutableListOf<String>()
        mappedTypes.forEach { mapped ->
            val requiredByProtocol = mapped.protocol.requiredFieldNames()
            mapped.domain.valueParameters()
                .filter { it.isOptional && it.name in requiredByProtocol }
                .forEach { differences += "${mapped.path}.${it.name}" }
        }

        assertEquals(
            "协议模型相对领域类型多出了「必填字段」：这会改变缺字段历史备份的读法。" +
                "确需收紧某处时先确认它不超出本次范围，并同步更新这里的说明。",
            emptySet<String>(),
            differences.toSortedSet(),
        )
    }

    /** 协议默认值必须与领域默认值一致：它们决定缺字段的历史备份读成什么。 */
    @Test
    fun protocol_defaults_read_back_the_same_way_as_domain_defaults() {
        val lenient = Json { ignoreUnknownKeys = true }
        val defaults = lenient.decodeFromString(BackupStoredSettings.serializer(), "{}")

        assertEquals(
            "协议里写死的设置默认值已经和领域默认值不是一回事：旧备份里的空 settings 会被读成另一套口径",
            StoredSettings(),
            defaults.toDomain(),
        )
        assertEquals(
            BackupStoredSettings(),
            defaults,
        )
    }

    /**
     * 缺 ID 的行仍然被导入，并拿到一个非空 ID——与迁移前的行为一致。
     *
     * 这里本来是「缺 ID 视为结构损坏、整份拒收」，那是一处用户可见的行为变更，被否决了（见
     * [the_protocol_is_exactly_as_optional_as_the_domain_on_every_field] 与 #207）。迁移前协议
     * 字段由领域类型承载，缺 ID 的行由领域的 `newId()` 兜底；现在协议自带 [newBackupRowId]，
     * 行为不变，只是不再反向依赖领域。
     *
     * 顺带钉住它生成的确实是 ID 而不是空串——一个空的 ID 会让后续的归属校验以「ID 为空」
     * 的形式失败，那样报错信息会指向错误的理由。
     */
    @Test
    fun a_row_without_an_id_is_imported_with_a_generated_one() {
        val withoutId = """{"version":1,"settings":{},"targets":[{"title":"学习"}],"goalStages":[],""" +
            """"timeEntries":[],"schedules":[],"recurrenceRules":[],"migrations":[],""" +
            """"futureGoalRules":[],"periodSnapshots":[],"targetMoves":[],"milestones":[],""" +
            """"experience":0,"activeTimer":null,"onboardingCompleted":false,""" +
            """"launchDestination":"AGENDA","lastOpenedDestination":"AGENDA","notificationsEnabled":true}"""

        val restored = AttentionBackupCodec.decode(withoutId)

        assertEquals(1, restored.targets.size)
        assertEquals("学习", restored.targets.single().title)
        assertTrue(
            "缺 ID 的行应当被生成一个非空 ID，而不是空串",
            restored.targets.single().id.isNotBlank(),
        )
    }

    /** 每个字段都取非默认值：默认值不能证明字段真的被搬了过去。 */
    private fun fullyPopulatedState(): AttentionState {
        val target = Target(
            id = "target",
            parentId = null,
            title = "学习",
            note = "备注",
            icon = "book",
            colorHex = "#112233",
            archived = false,
            sortOrder = 7,
            expanded = false,
        )
        val rule = RecurrenceRule(
            id = "rule",
            title = "晨间阅读",
            startDate = "2026-10-01",
            frequency = ScheduleFrequency.MONTHLY,
            weekdays = listOf(1, 3, 5),
            dayOfMonth = 12,
            untilDate = "2026-12-31",
            active = false,
            estimatedMinutes = 45,
            targetId = target.id,
            note = "规则备注",
            reminderMinuteOfDay = 390,
        )
        val stage = GoalStage(
            id = "stage",
            targetId = target.id,
            cadence = GoalCadence.MONTHLY,
            targetMinutes = 300,
            startDate = "2026-10-01",
            dueDate = "2026-10-31",
            completed = true,
        )
        val schedule = ScheduleEntry(
            id = "schedule",
            planningDate = "2026-10-02",
            title = "阅读",
            startMinute = 60,
            endMinute = 180,
            estimatedMinutes = 30,
            note = "日程备注",
            targetId = target.id,
            completed = true,
            reminderEpochMillis = 1_700_000_000_000L,
            recurrenceRuleId = rule.id,
        )
        return AttentionState(
            settings = StoredSettings(
                planningDayBoundaryMinutes = 90,
                weekStartDay = 7,
                dailyCapacityMinutes = 420,
            ),
            targets = listOf(target),
            goalStages = listOf(stage),
            timeEntries = listOf(
                TimeEntry(
                    id = "entry",
                    planningDate = "2026-10-02",
                    durationMinutes = 45,
                    targetId = target.id,
                    source = TimeEntrySource.TIMER,
                    occurredAtEpochMillis = 1_700_000_000_000L,
                    note = "记录备注",
                    scheduleEntryId = schedule.id,
                ),
            ),
            schedules = listOf(schedule),
            recurrenceRules = listOf(rule),
            migrations = listOf(
                Migration(
                    id = "migration",
                    targetId = target.id,
                    sourceStageId = stage.id,
                    minutes = 60,
                    destinationStartDate = "2026-11-01",
                    destinationEndDate = "2026-11-07",
                    cancelled = true,
                ),
            ),
            futureGoalRules = listOf(
                FutureGoalRule(
                    id = "future",
                    targetId = target.id,
                    stageId = stage.id,
                    cadence = GoalCadence.ONE_TIME,
                    targetMinutes = 120,
                    effectiveFrom = "2026-11-01",
                    dueDate = "2026-11-30",
                ),
            ),
            periodSnapshots = listOf(
                PeriodSnapshot(
                    id = "snapshot",
                    targetId = target.id,
                    cadence = GoalCadence.WEEKLY,
                    periodStart = "2026-09-28",
                    periodEnd = "2026-10-04",
                    targetMinutes = 300,
                    actualMinutes = 250,
                    gapMinutes = 50,
                    excessMinutes = 0,
                    completed = false,
                    stageId = stage.id,
                ),
            ),
            targetMoves = listOf(
                TargetMove(id = "move", targetId = target.id, parentId = "other-target", effectiveFrom = "2026-11-01"),
            ),
            milestones = listOf(
                Milestone(id = "milestone", kind = "cumulative", instanceKey = "cumulative:100", reward = 10_000L, achievedAtEpochMillis = 1_700_000_000_000L),
            ),
            experience = 10_045L,
            activeTimer = ActiveTimer(
                targetId = target.id,
                startedAtEpochMillis = 1_700_000_000_000L,
                accumulatedMillis = 1_800_000L,
                paused = true,
                lastPlanningDate = "2026-10-02",
                completedSegments = listOf(TimerSegment(1_700_000_000_000L, 1_700_000_060_000L)),
                scheduleEntryId = schedule.id,
            ),
            onboardingCompleted = true,
            launchDestination = LaunchDestination.TARGETS,
            lastOpenedDestination = LaunchDestination.STATISTICS,
            notificationsEnabled = false,
        )
    }
}

/** 一个进入备份的领域类型，与承载它的协议模型。 */
private data class MappedType(
    val path: String,
    val domain: KClass<*>,
    val protocol: KClass<*>,
) {
    init {
        requireNotNull(domain.primaryConstructor) { "$path 的领域侧必须是一个数据类" }
        requireNotNull(protocol.primaryConstructor) { "$path 的协议侧必须是一个数据类" }
    }
}

private const val DOMAIN_PREFIX = "com.attention.domain."
private const val BACKUP_PACKAGE = "com.attention.app.data.backup."

/**
 * 协议枚举 → 它对应的领域枚举，按序列化标识配对。
 *
 * 协议字段承载的是备份协议自己的枚举（[BackupProtocolEnums]），领域字段承载的是领域枚举，两者名字
 * 一样、身份不同。逐字段对照时两侧的类型名要对齐到同一套名字，否则 `cadence`、`source`、
 * `frequency` 这几列永远对不上，而对不上的原因不是真的缺字段。
 *
 * 这张表因此是护栏的一部分而不是装饰：它列出的协议枚举必须正好是清单里 [BackupPayloadKind.PROTOCOL_ENUM]
 * 的那些承载类型（见 [the_enum_translation_table_covers_exactly_the_manifests_protocol_enums]）。
 */
private val protocolEnumToDomainEnum: Map<String, String> = mapOf(
    BackupLaunchDestination.serializer().descriptor.serialName to LaunchDestinationSerializer.descriptor.serialName,
    BackupGoalCadence.serializer().descriptor.serialName to GoalCadenceSerializer.descriptor.serialName,
    BackupTimeEntrySource.serializer().descriptor.serialName to TimeEntrySourceSerializer.descriptor.serialName,
    BackupScheduleFrequency.serializer().descriptor.serialName to ScheduleFrequencySerializer.descriptor.serialName,
)

/** 清单按 [kotlinx.serialization] 的序列化标识登记，所以这里问描述符，不问类型名。 */
@OptIn(InternalSerializationApi::class)
private fun KClass<*>.protocolName(): String = serializer().descriptor.serialName

/**
 * 协议描述符里「没有默认值、必须出现在文档里」的字段名。
 *
 * 只有协议侧用描述符：协议模型真的要编解码 JSON，「这个字段缺了会怎样」只有描述符说得准，
 * 而且缺字段的历史备份读成什么也由它决定。
 */
@OptIn(InternalSerializationApi::class)
private fun SerialDescriptor.requiredFieldNames(): Set<String> =
    (0 until elementsCount).filterNot { isElementOptional(it) }.map { getElementName(it) }.toSet()

/** 协议侧「必须自带」的字段名；领域侧的可选性由 [KParameter.isOptional] 回答，两边不必用同一套手段。 */
@OptIn(InternalSerializationApi::class)
private fun KClass<*>.requiredFieldNames(): Set<String> = serializer().descriptor.requiredFieldNames()

/** 数据类的构造参数，按声明顺序；`$stable` 之类的合成成员不在里面。 */
private fun KClass<*>.valueParameters(): List<KParameter> = requireNotNull(primaryConstructor)
    .parameters.filter { it.kind == KParameter.Kind.VALUE }

/** `名称:类型` 的有序形状，字段名、字段顺序与字段类型都在里面。 */
private fun KClass<*>.shape(): List<String> = valueParameters().map { "${it.name}:${it.type.renderType()}" }

/**
 * 类型名，类型参数一并带出来。
 *
 * 可空性两侧是一致的，所以这里不区分 `String` 与 `String?`：真要多一层「领域可空、协议必填」的语义，
 * [the_only_optionality_difference_is_the_row_id_the_protocol_requires] 会在默认值的差别上抓到。
 *
 * 协议包里自己的类型去掉 `Backup` 前缀再比对，因为两侧承载的是同名但不同身份的类型：枚举见
 * [protocolEnumToDomainEnum]，嵌套的记录（`BackupTimerSegment` 对 `TimerSegment`）在对照表里本来就是
 * 单独的一行，字段只要指向同一对类型即可。记录的身份由 [MappedType] 顶层的清单核对保证。
 */
private fun KType.renderType(): String {
    val classifier = requireNotNull(classifier as? KClass<*>)
    val simpleName = requireNotNull(classifier.simpleName)
    val name = if (classifier.qualifiedName?.startsWith(BACKUP_PACKAGE) == true) {
        simpleName.removePrefix("Backup")
    } else {
        simpleName
    }
    val arguments = arguments.mapNotNull { it.type }.map { it.renderType() }
    return if (arguments.isEmpty()) name else "$name<${arguments.joinToString(", ")}>"
}
