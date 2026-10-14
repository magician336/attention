package com.attention.app.seams

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ADR-0005 §1、§2：组合根只做依赖组装、导航与跨页面协调。
 * 页面投影 module 除了 seam 类型（`*Actions`、草稿、`*ScreenState` 投影函数）之外还导出
 * 展示层 helper（`capacityLabel`、`goalProgressLabel`、`summaryLabel`、`label`、
 * `formatAgendaMinute` …）。这些 helper 属于页面展示约定，组合根引用它们就说明
 * 根又学到了页面展示知识——那是页面迁移留下的残留。
 *
 * 这条测试守住该约束：组合根从投影 module 只导入 seam 类型。
 */
class CompositionRootSurfaceTest {
    private val workspaceSource: String =
        File("src/main/java/com/attention/app/Workspace.kt").readText()

    /** 组合根允许从投影 module 导入的东西：动作接口、动作参数草稿、五个页面投影函数。 */
    private val allowedProjectionImports = setOf(
        // 动作接口：页面发出 intent 的唯一出口。
        "AgendaActions",
        "SettingsActions",
        "StatisticsActions",
        "TargetsActions",
        "TodayActions",
        // 动作参数：adapter 把它翻译成领域对象，因此草稿类型必须经过根的 adapter。
        "AgendaRecurrenceDraft",
        "AgendaScheduleDraft",
        "OnboardingScheduleDraft",
        // 五个页面投影函数：根读一次时钟，把状态喂进去。
        "agendaScreenState",
        "settingsScreenState",
        "statisticsScreenState",
        "targetsScreenState",
        "todayScreenState",
        // 草稿到领域规则的翻译入口，只有根侧的 adapter 用得到。
        "toRecurrenceRule",
    )

    @Test
    fun composition_root_imports_only_seam_types_from_projection_module() {
        val imported = Regex("""(?m)^import com\.attention\.workspace\.state\.(\w+)$""")
            .findAll(workspaceSource)
            .map { it.groupValues[1] }
            .toList()

        val unexpected = imported.filterNot { it in allowedProjectionImports }.sorted()
        assertEquals(
            "组合根从投影 module 导入了 seam 之外的东西，它们属于页面展示约定：" +
                "$unexpected",
            emptyList<String>(),
            unexpected,
        )
    }
}
