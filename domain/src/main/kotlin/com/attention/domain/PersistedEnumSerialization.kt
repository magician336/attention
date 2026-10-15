package com.attention.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 被按名持久化的枚举的容错协议，这是它在**领域侧**唯一的规则来源。
 *
 * 谁在用它：Room 的枚举列（`BusinessMappers` 把枚举按 `name` 存进 TEXT 列）与 DataStore 的
 * 启动入口偏好（[launchDestinationFromPersistedName]）。这两条路径都不是备份——备份 JSON 的枚举
 * 由备份协议自己那一份容错规则承担（`app` 的 `BackupProtocolEnums`），因为线上那些名字是协议的。
 *
 * 容错为什么仍然必要：应用升级后存量行还在本地库里，某个枚举值一旦在**没有迁移**的情况下改名或
 * 删除，`valueOf` 会抛 `IllegalArgumentException` 把整库读取打穿，一条行读不出来就等于整个应用打不开。
 * 备份 JSON 同理（`ignoreUnknownKeys` 只放宽键名，不放宽枚举值），只是那份规则归协议所有。
 *
 * 抽象成基类而不是四个各写一份：容错这件事本身只有一种，四份实现就是同一段知识抄四遍，
 * 改一次要拆四次。四个 `object` 只是 [values] 与 [unknownValueFallback] 两个指针。
 *
 * 与协议侧那份的对照由测试钉住：同一个无法识别的名字，备份路径与 Room/设置路径必须解析成同一个值。
 *
 * @param values 该枚举的全部取值，按名匹配用
 * @param unknownValueFallback 名字认不出来时的默认值。**必须为每个枚举显式声明并写明理由**：
 * 不取枚举的第一个常量（那是隐式知识），也不取数据类的字段默认值（那会把语义藏进十几处）。
 */
abstract class PersistedEnumSerializer<T : Enum<T>>(
    serialName: String,
    private val values: Array<T>,
    private val unknownValueFallback: T,
) : KSerializer<T> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): T = fromPersistedName(decoder.decodeString())

    /**
     * 名称 → 值的唯一解析入口。Room 的枚举列与 DataStore 的启动入口偏好共用它。备份 JSON 不走这里：
     * 它用备份协议自己那一份规则，两侧对认不出来的名字必须给出同一个答案，否则同一个名字在设置里
     * 容错、在备份里就整份丢掉。
     */
    fun fromPersistedName(name: String?): T =
        values.firstOrNull { it.name == name } ?: unknownValueFallback
}

/** 阶段节奏。未知节奏按天处理：规则本身是「用户答应过的投入」，宁可多提醒也不要静默漏掉。 */
object GoalCadenceSerializer :
    PersistedEnumSerializer<GoalCadence>("com.attention.domain.GoalCadence", enumValues(), GoalCadence.DAILY)

/**
 * 时间记录来源。未知来源按 [TimeEntrySource.MANUAL] 处理：
 * 手动记录是唯一一个不谎报事实的选项——不会把一条来路不明的记录说成「计时器量的」或「导入的」。
 */
object TimeEntrySourceSerializer :
    PersistedEnumSerializer<TimeEntrySource>(
        "com.attention.domain.TimeEntrySource",
        enumValues(),
        TimeEntrySource.MANUAL,
    )

/** 重复规则频率。未知频率按天处理：不漏掉用户已经答应的那些天，好过静默地少排几天。 */
object ScheduleFrequencySerializer :
    PersistedEnumSerializer<ScheduleFrequency>(
        "com.attention.domain.ScheduleFrequency",
        enumValues(),
        ScheduleFrequency.DAILY,
    )

/**
 * 启动入口。未知入口按 [LaunchDestination.AGENDA] 处理：日程是主入口，认不出上次落在哪儿时
 * 回到主入口，而不是回到一块已经不存在的工作区。
 *
 * 旧入口名（`TODAY`/`ALL_DATES`）**不在**这里折成日程：它们仍然解码成各自的枚举值，由
 * [asAgendaEntry] 做兼容映射。这样「写什么名字」和「旧名字还读不读得出来」是两件独立的事。
 */
object LaunchDestinationSerializer :
    PersistedEnumSerializer<LaunchDestination>(
        "com.attention.domain.LaunchDestination",
        enumValues(),
        LaunchDestination.AGENDA,
    )
