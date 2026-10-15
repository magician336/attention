package com.attention.app.data.backup

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 清单与协议实际字段之间的一致性护栏。
 *
 * 这里不信任任何注释或人工阅读：清单条目通过 kotlinx.serialization 的描述符与
 * [AttentionBackup] 协议描述符逐条比对。给领域类型新增一个进入备份的字段、给备份新增一个字段、
 * 把某个承载点从列表改成单值、或新增一个被承载的类型，本测试都会失败。
 */
class BackupProtocolPayloadTest {

    @Test
    fun manifest_lists_exactly_the_carry_points_the_protocol_has() {
        val actual = ProtocolCarryPoints.from(AttentionBackup.serializer().descriptor)
        val declared = BackupProtocolPayloads.payloads
        assertEquals(
            "同一承载点路径被清单声明了多次",
            declared.size,
            declared.map { it.field }.toSet().size,
        )
        assertEquals(
            "备份协议承载内容的清单与协议实际字段不一致：见 BackupProtocolPayloads。" +
                "新增或删除协议字段、领域类型字段、或被承载的类型后，必须同步清单。" +
                diff(
                    actual.render(),
                    declared.map { it.field to CarryPoint(it.serialName, it.container, it.kind) }.render(),
                ),
            actual.render(),
            declared.map { it.field to CarryPoint(it.serialName, it.container, it.kind) }.render(),
        )
    }

    @Test
    fun manifest_lists_exactly_the_fields_each_carried_type_serializes() {
        val actual = ProtocolCarryPoints.from(AttentionBackup.serializer().descriptor)
        val carriedTypes = BackupProtocolPayloads.payloads
            .filter { it.kind == BackupPayloadKind.DOMAIN_RECORD }
            .map { it.serialName }
            .toSet()
        assertEquals(
            "被承载的数据类清单与协议实际存在的数据类不一致：${actual.dataTypes()} vs $carriedTypes",
            actual.dataTypes(),
            carriedTypes,
        )
        carriedTypes.sorted().forEach { serialName ->
            val descriptor = actual.descriptors.getValue(serialName)
            val entries = BackupProtocolPayloads.payloadsOf(serialName)
            assertEquals(
                "$serialName 的清单条目对 wireFields 的说法不一致",
                1,
                entries.map { it.wireFields }.distinct().size,
            )
            val declaredFields = entries.single().wireFieldNames()
            assertEquals(
                "$serialName 的清单字段与协议实际字段不一致：见 BackupProtocolPayloads。" +
                    "给领域类型新增或删除一个进入备份的字段后，必须同步 wireFields。",
                descriptor.elementNames.sorted(),
                declaredFields.sorted(),
            )
        }
    }

    @Test
    fun every_carried_type_states_why_it_is_in_the_backup() {
        BackupProtocolPayloads.payloads.forEach { payload ->
            assertTrue(
                "清单项 ${payload.field} 没有写明它为什么在备份里",
                payload.reason.isNotBlank(),
            )
        }
    }
}

/** 从序列化描述符里读出来的协议实际承载点。 */
internal class ProtocolCarryPoints(
    val carryPoints: Map<String, CarryPoint>,
    val descriptors: Map<String, SerialDescriptor>,
) {
    /** 被承载的数据类的序列化标识。 */
    fun dataTypes(): Set<String> =
        descriptors.filterValues { it.kind == StructureKind.CLASS }.keys.toSortedSet()

    fun render(): List<String> = carryPoints.toList().render()

    companion object {
        private const val DOMAIN_PREFIX = "com.attention.domain."

        /**
         * 从 [root] 开始遍历：顶层字段全部记下（标量也要记，否则新增标量字段不会被发现），
         * 之后只在遇到 `:domain` 类型时继续下钻，记录嵌在领域类型里的承载点。
         */
        fun from(root: SerialDescriptor): ProtocolCarryPoints {
            val carryPoints = linkedMapOf<String, CarryPoint>()
            val descriptors = linkedMapOf<String, SerialDescriptor>()
            val pending = ArrayDeque<Pair<String, SerialDescriptor>>()
            val visited = mutableSetOf<String>()

            fun visit(path: String, field: SerialDescriptor, isProtocolLevel: Boolean) {
                val container = if (field.kind == StructureKind.LIST) {
                    BackupPayloadContainer.LIST
                } else {
                    BackupPayloadContainer.VALUE
                }
                val carried = if (container == BackupPayloadContainer.LIST) {
                    field.getElementDescriptor(0)
                } else {
                    field
                }
                // 可空字段的描述符把 "?" 写进标识里；承载的类型仍然是同一个类型。
                val serialName = carried.serialName.removeSuffix("?")
                if (isProtocolLevel || serialName.startsWith(DOMAIN_PREFIX)) {
                    carryPoints[path] = CarryPoint(serialName, container, kindOf(serialName, carried))
                }
                if (carried.kind == StructureKind.CLASS) {
                    descriptors.putIfAbsent(serialName, carried)
                    pending.addLast(path to carried)
                }
            }

            (0 until root.elementsCount).forEach { index ->
                visit(
                    path = root.getElementName(index),
                    field = root.getElementDescriptor(index),
                    isProtocolLevel = true,
                )
            }
            while (pending.isNotEmpty()) {
                val (path, descriptor) = pending.removeFirst()
                if (!visited.add(path)) continue
                (0 until descriptor.elementsCount).forEach { index ->
                    visit(
                        path = "$path.${descriptor.getElementName(index)}",
                        field = descriptor.getElementDescriptor(index),
                        isProtocolLevel = false,
                    )
                }
            }
            return ProtocolCarryPoints(carryPoints, descriptors)
        }

        private fun kindOf(serialName: String, descriptor: SerialDescriptor): BackupPayloadKind = when {
            !serialName.startsWith(DOMAIN_PREFIX) -> BackupPayloadKind.PROTOCOL_SCALAR
            descriptor.kind == StructureKind.CLASS -> BackupPayloadKind.DOMAIN_RECORD
            else -> BackupPayloadKind.DOMAIN_ENUM
        }
    }
}

/** 一个承载点的类型、容器与类别。 */
internal data class CarryPoint(
    val serialName: String,
    val container: BackupPayloadContainer,
    val kind: BackupPayloadKind,
) {
    override fun toString(): String = "$serialName[$container/$kind]"
}

/** 按路径排序渲染成可比对的文本行，让断言失败时能直接看出差异。 */
private fun List<Pair<String, CarryPoint>>.render(): List<String> =
    sortedBy { it.first }.map { (path, point) -> "$path -> $point" }

/** 渲染两份列表的差异。断言消息里带上真实差异，报告才是可用的。 */
private fun diff(actual: List<String>, declared: List<String>): String {
    val onlyActual = actual.filterNot { it in declared }
    val onlyDeclared = declared.filterNot { it in actual }
    return buildString {
        append("\n  协议有而清单没有：")
        append(onlyActual.ifEmpty { listOf("<无>") })
        append("\n  清单有而协议没有：")
        append(onlyDeclared.ifEmpty { listOf("<无>") })
    }
}
