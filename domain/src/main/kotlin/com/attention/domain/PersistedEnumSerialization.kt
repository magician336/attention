package com.attention.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 被按名持久化的枚举的容错协议，这是它唯一的规则来源。
 *
 * 枚举在 JSON 里是按名称编码的。`ignoreUnknownKeys` 只放宽**键名**，不放宽**枚举值**：只要某个
 * 枚举值在不升版本号的情况下原地漂移（重命名、删除，或者备份来自一台跑着更新版本的机器），
 * kotlinx 就会抛 `SerializationException`，整份备份连同里面全部的目标、日程和时间记录一起被拒。
 * 「一条时间记录的来源认不出来」不该等价于「我的备份导不进来」，所以这里把未知名定义为
 * 「按声明过的默认值读」。
 *
 * 这不是「错误处理器」：上层调用者对这个状态没有任何可执行的分叉——备份要么进来、要么不进来。
 * 把它做成错误路径只会让每个调用者多背一份「这个异常该怎么办」的心智负担，而没有任何一处真的
 * 会做出不同的选择。
 *
 * 抽象成基类而不是四个各写一份：容错这件事本身只有一种，四份实现就是同一段知识抄四遍，
 * 改一次要拆四次。四个 `object` 只是 [values] 与 [unknownValueFallback] 两个指针。
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
     * 名称 → 值的唯一解析入口。DataStore 与备份 JSON 共用它：两条持久化路径对「认不出来的名字」
     * 必须给出同一个答案，否则同一个名字在设置里容错、在备份里就整份丢掉。
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
