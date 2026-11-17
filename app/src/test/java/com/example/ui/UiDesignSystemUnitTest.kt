package com.example.ui

import androidx.compose.ui.unit.dp
import com.example.ui.theme.LucideIcons
import com.example.ui.theme.NexusShapes
import com.example.ui.theme.NexusSpacing
import com.example.ui.theme.NexusThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiDesignSystemUnitTest {

    @Test
    fun lucideIcons_coreNavigationAndActionIconsAreDefinedAt24dp() {
        val icons = listOf(
            LucideIcons.Menu,
            LucideIcons.Edit,
            LucideIcons.Sync,
            LucideIcons.MoreVertical,
            LucideIcons.Copy,
            LucideIcons.ThumbsUp,
            LucideIcons.ThumbsDown,
            LucideIcons.Terminal,
            LucideIcons.Close,
            LucideIcons.Add,
            LucideIcons.AttachFile,
            LucideIcons.FolderOpen,
            LucideIcons.Send,
            LucideIcons.Stop,
            LucideIcons.Github,
            LucideIcons.Heart
        )
        icons.forEach { icon ->
            assertNotNull(icon)
            assertEquals(24.dp, icon.defaultWidth)
            assertEquals(24.dp, icon.defaultHeight)
        }
    }

    @Test
    fun designTokens_preserveLightModeDefaultAndSpacingScale() {
        assertEquals(NexusThemeMode.LIGHT, NexusThemeMode.fromId("unknown"))
        assertEquals(NexusThemeMode.LIGHT, NexusThemeMode.fromId("light"))
        assertEquals(NexusThemeMode.DARK, NexusThemeMode.fromId("dark"))
        assertEquals(NexusThemeMode.SYSTEM, NexusThemeMode.fromId("system"))
        assertTrue(NexusSpacing.minTouchTarget >= 48.dp)
        assertNotNull(NexusShapes.medium)
    }
}
