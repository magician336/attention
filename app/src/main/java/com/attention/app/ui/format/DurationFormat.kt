package com.attention.app.ui.format

/**
 * 时长的自然语言表达。页面不再把分钟数和单位直接写进界面。
 *
 * 表达规则来自规格的“时间表达”用户故事：
 * 不足一小时保持简洁，超过一小时显示小时与分钟的组合，零时长有自己的表达而不是空白。
 *
 * 这是纯函数：只依赖入参，不读系统时间、时区或设备状态，因此在任何设备上结果一致。
 */
object DurationFormat {

    private const val MINUTES_PER_HOUR = 60

    /**
     * 把分钟数表达为中文自然语言。
     *
     * 负数按零处理：领域模型不会产生负时长，这里只是保证异常输入不会渲染出怪异的界面文本。
     */
    fun ofMinutes(minutes: Int): String {
        val total = minutes.coerceAtLeast(0)
        val hours = total / MINUTES_PER_HOUR
        val rest = total % MINUTES_PER_HOUR

        return when {
            hours == 0 -> "$rest 分钟"
            rest == 0 -> "$hours 小时"
            else -> "$hours 小时 $rest 分钟"
        }
    }
}