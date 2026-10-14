package com.attention.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 状态在品牌语义色里的角色：状态组件按角色取色，而不是各写一个裸色值。
 */
enum class AttentionStateRole {
    /** 中性：不表达好坏，例如加载中与空数据。 */
    NEUTRAL,

    /** 正向：已达成。 */
    POSITIVE,

    /** 负向：出错。 */
    NEGATIVE,

    /** 说明性：告诉用户发生了什么、需要做什么。 */
    INFORMATIONAL,
}

/**
 * 六种状态的语义定义：文案标签、图标与稳定标识。
 *
 * 颜色从不是唯一通道——每种状态都自带 [label] 与 [icon]，因此界面测试可以在不依赖颜色的
 * 前提下断言状态。标识由状态种类唯一决定，所以同一个状态在任意页面渲染都得到同一个标识，
 * 页面之间不需要各自发明定位方式。
 */
enum class AttentionStateKind(
    /** 状态的语义标签，同时是图标的朗读文本。 */
    val label: String,
    /** 标识末段，必须不含分隔符，才能保证命名空间只有一个前缀。 */
    val slug: String,
    /** 状态的图标，与文案一起构成非颜色通道。 */
    val icon: ImageVector,
    /** 取哪个品牌语义色。 */
    val role: AttentionStateRole,
) {
    /** 加载中：页面正在工作而不是卡住。 */
    LOADING("加载中", "loading", Icons.Filled.HourglassEmpty, AttentionStateRole.NEUTRAL),

    /** 成功：操作已经达成。 */
    SUCCESS("成功", "success", Icons.Filled.CheckCircle, AttentionStateRole.POSITIVE),

    /** 错误：读取或写入失败，附带重试入口。 */
    ERROR("出错了", "error", Icons.Filled.ErrorOutline, AttentionStateRole.NEGATIVE),

    /** 空数据：没有内容，附带明确的下一步。 */
    EMPTY("暂无内容", "empty", Icons.Filled.Inbox, AttentionStateRole.NEUTRAL),

    /** 权限未开启：说明影响与是否必须开启。 */
    PERMISSION_NOT_GRANTED("权限未开启", "permission", Icons.Filled.Lock, AttentionStateRole.INFORMATIONAL),

    /** 本地数据说明：说明数据只保存在本机。 */
    LOCAL_DATA_NOTICE("数据只保存在本地", "local_data", Icons.Filled.PhoneAndroid, AttentionStateRole.INFORMATIONAL),
    ;

    /** 整个状态组件的标识命名空间。 */
    val namespace: String = "attention.state"

    /** 状态容器的标识。 */
    val rootTag: String = "$namespace.$slug"

    /** 状态图标的标识。 */
    val iconTag: String = "$rootTag.icon"

    /** 状态文案的标识。 */
    val labelTag: String = "$rootTag.label"

    /** 补充说明的标识，没有说明时不存在。 */
    val descriptionTag: String = "$rootTag.description"

    /** 状态动作（重试、下一步、跳转权限）的标识，没有动作时不存在。 */
    val actionTag: String = "$rootTag.action"
}
