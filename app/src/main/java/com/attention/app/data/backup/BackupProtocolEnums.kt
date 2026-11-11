package com.attention.app.data.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/*
 * version 1 备份协议**自己的**四个按名持久化的枚举，以及它们「名字认不出来时读成什么」的规则。
 *
 * 为什么它们属于协议而不属于领域：备份文档里的枚举是**线上字符串**。`ignoreUnknownKeys` 只放宽
 * 键名，不放宽枚举值——只要某个枚举值在不升版本号的情况下原地漂移（重命名、删除，或者备份来自
 * 一台跑着更新版本的机器），kotlinx 就会抛 `SerializationException`，把这份备份连同里面全部的
 * 目标、日程和时间记录一起挡在门外。「一条时间记录的来源认不出来」不该等价于「我的备份导不进来」。
 * 这条知识描述的是**这份协议怎么读线上名字**，所以它归拥有线上名字的模块，也就是协议自己。
 *
 * 领域侧另有等价的一份（`:domain` 的 `PersistedEnumSerializer`），它服务的是另外两条持久化路径：
 * Room 的枚举列（`BusinessMappers`）与 DataStore 的启动入口偏好（`launchDestinationFromPersistedName`）。
 * 两条路径对同一个名字必须给出同一个答案，否则同一个名字在设置里容错、在备份里就整份丢掉。
 * 这件事不能靠「代码看起来一样」维持，由 `UnknownEnumValueToleranceTest` 里的对照测试钉住：
 * 同一个无法识别的名字，备份路径与数据库/设置路径必须解析成同一个值。
 */

/**
 * 备份协议侧按名持久化枚举的容错协议，这是协议侧唯一的规则来源。
 *
 * 抽象成基类而不是四份实现：容错这件事本身只有一种，四份实现就是同一段知识抄四遍，改一次要拆四次。
 * 四个 `object` 只是 [values] 与 [unknownValueFallback] 两个指针。
 *
 * 描述符的 [SerialKind] 声明为 [SerialKind.ENUM]：协议描述符遍历要能只靠描述符区分「这是一个被
 * 承载的枚举」与「这是一个被承载的标量」（Int、String、Boolean 都是 PRIMITIVE）。否则
 * `BackupProtocolPayloadTest` 会把协议自己的枚举当成标量而漏掉承载点。
 *
 * @param values 该枚举的全部取值，按名匹配用
 * @param unknownValueFallback 名字认不出来时的默认值。**必须为每个枚举显式声明并写明理由**：
 * 不取枚举的第一个常量（那是隐式知识），也不取数据类的字段默认值（那会把语义藏进十几处）。
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
abstract class BackupPersistedEnumSerializer<T : Enum<T>>(
    serialName: String,
    private val values: Array<T>,
    private val unknownValueFallback: T,
) : KSerializer<T> {

    override val descriptor: SerialDescriptor = buildSerialDescriptor(serialName, SerialKind.ENUM)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): T = fromPersistedName(decoder.decodeString())

    /**
     * 名称 → 值的唯一解析入口。备份 JSON 走它；领域侧的同一规则见
     * `:domain` 的 `PersistedEnumSerializer.fromPersistedName`，两者必须给出同一个答案。
     */
    fun fromPersistedName(name: String?): T =
        values.firstOrNull { it.name == name } ?: unknownValueFallback
}

/** 目标阶段的周期口径。未知节奏按天处理：规则本身是「用户答应过的投入」，宁可多提醒也不要静默漏掉。 */
@Serializable(with = BackupGoalCadenceSerializer::class)
enum class BackupGoalCadence { DAILY, WEEKLY, MONTHLY, ONE_TIME }

/** 阶段节奏。默认值理由见 [BackupGoalCadence]。 */
object BackupGoalCadenceSerializer :
    BackupPersistedEnumSerializer<BackupGoalCadence>(
        "com.attention.app.data.backup.BackupGoalCadence",
        enumValues(),
        BackupGoalCadence.DAILY,
    )

/**
 * 时间记录的来源。未知来源按 [BackupTimeEntrySource.MANUAL] 处理：
 * 手动记录是唯一一个不谎报事实的选项——不会把一条来路不明的记录说成「计时器量的」或「导入的」。
 */
@Serializable(with = BackupTimeEntrySourceSerializer::class)
enum class BackupTimeEntrySource { MANUAL, TIMER, SCHEDULE_CONFIRMED, IMPORT }

/** 时间记录来源。默认值理由见 [BackupTimeEntrySource]。 */
object BackupTimeEntrySourceSerializer :
    BackupPersistedEnumSerializer<BackupTimeEntrySource>(
        "com.attention.app.data.backup.BackupTimeEntrySource",
        enumValues(),
        BackupTimeEntrySource.MANUAL,
    )

/** 重复规则的频率。未知频率按天处理：不漏掉用户已经答应的那些天，好过静默地少排几天。 */
@Serializable(with = BackupScheduleFrequencySerializer::class)
enum class BackupScheduleFrequency { DAILY, WEEKLY, MONTHLY }

/** 重复日程频率。默认值理由见 [BackupScheduleFrequency]。 */
object BackupScheduleFrequencySerializer :
    BackupPersistedEnumSerializer<BackupScheduleFrequency>(
        "com.attention.app.data.backup.BackupScheduleFrequency",
        enumValues(),
        BackupScheduleFrequency.DAILY,
    )

/**
 * 启动入口。[AGENDA] 是「日程」的正式语义；[TODAY] 与 [ALL_DATES] 是旧版本持久化的入口值，
 * 它们仍然解码成各自的枚举值，由 [asAgendaEntry] 做兼容映射。
 *
 * 未知入口按 [BackupLaunchDestination.AGENDA] 处理：日程是主入口，认不出上次落在哪儿时回到主入口，
 * 而不是回到一块已经不存在的工作区。
 *
 * 「写什么名字」（这里）与「旧名字还读不读得出来」（[asAgendaEntry]）是两件独立的事。
 */
@Serializable(with = BackupLaunchDestinationSerializer::class)
enum class BackupLaunchDestination { TODAY, TARGETS, ALL_DATES, STATISTICS, LAST_OPENED, AGENDA }

/** 启动入口。默认值理由见 [BackupLaunchDestination]。 */
object BackupLaunchDestinationSerializer :
    BackupPersistedEnumSerializer<BackupLaunchDestination>(
        "com.attention.app.data.backup.BackupLaunchDestination",
        enumValues(),
        BackupLaunchDestination.AGENDA,
    )

/**
 * 旧入口值到「日程」的兼容映射：旧版本持久化的 [BackupLaunchDestination.TODAY] 与
 * [BackupLaunchDestination.ALL_DATES] 读取后等价于日程入口，其余值保持自身。
 *
 * 与 `:domain` 的 `LaunchDestination.asAgendaEntry` 是同一条规则的两份实现（那份服务设置存储与组合根
 * 导航）。备份侧不该反向依赖领域侧的解析入口，但两侧对旧名字的结论必须一致。
 */
fun BackupLaunchDestination.asAgendaEntry(): BackupLaunchDestination = when (this) {
    BackupLaunchDestination.TODAY, BackupLaunchDestination.ALL_DATES -> BackupLaunchDestination.AGENDA
    else -> this
}
