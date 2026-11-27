package com.attention.app.fuguang

import androidx.compose.ui.graphics.Color
import com.attention.app.fuguang.components.FuguangAdjustmentChange
import com.attention.app.fuguang.components.FuguangDerivedThemes
import com.attention.app.fuguang.components.FuguangThemeName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuguangSpecTokenTest {
    @Test
    fun v11_has_five_themes_and_no_white_card_surface() {
        assertEquals(5, FuguangThemeName.entries.size)
        FuguangThemeName.entries.forEach { theme ->
            val roles = FuguangDerivedThemes.rolesFor(theme)
            assertNotEquals(Color.White, roles.surface)
            assertNotEquals(roles.paper, roles.surface)
        }
    }

    @Test
    fun v11_bubbles_use_independent_derived_roles() {
        FuguangThemeName.entries.forEach { theme ->
            val roles = FuguangDerivedThemes.rolesFor(theme)
            assertNotEquals(roles.bubbleAi, roles.bubbleUser)
            assertNotEquals(roles.bubbleAi, roles.bubbleAiEdge)
            assertNotEquals(roles.bubbleUser, roles.bubbleUserEdge)
        }
    }

    @Test
    fun adjustment_change_has_the_three_v11_semantic_states() {
        assertEquals(
            listOf(
                FuguangAdjustmentChange.ADDED,
                FuguangAdjustmentChange.MOVED,
                FuguangAdjustmentChange.PENDING,
            ),
            FuguangAdjustmentChange.entries.toList(),
        )
        assertTrue(FuguangAdjustmentChange.entries.none { it.name == "ORIGINAL" })
    }
}
