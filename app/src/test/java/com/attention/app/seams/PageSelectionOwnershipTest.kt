package com.attention.app.seams

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-0005 §24：`WorkspaceApp` 不承载页面内部的选择状态，选择状态留在对应页面 module 内。
 * 这条测试守住该约束：组合根不得声明页面内部的状态 holder。
 */
class PageSelectionOwnershipTest {
    private val workspaceSource: String =
        File("src/main/java/com/attention/app/Workspace.kt").readText()

    @Test
    fun composition_root_declares_no_page_internal_state_holder() {
        val declaredClasses = Regex("""private class (\w+)""")
            .findAll(workspaceSource)
            .map { it.groupValues[1] }
            .toList()
        assertTrue(
            "组合根声明了页面内部的状态类型：$declaredClasses",
            declaredClasses.none { it.contains("Selection") || it.contains("Holder") || it.contains("Form") },
        )
    }

    @Test
    fun composition_root_keeps_no_nullable_selected_date_state() {
        assertTrue(
            "组合根仍持有页面选择状态 mutableStateOf<LocalDate?>，应改由页面 module 拥有",
            !workspaceSource.contains("mutableStateOf<LocalDate?>"),
        )
    }
}
