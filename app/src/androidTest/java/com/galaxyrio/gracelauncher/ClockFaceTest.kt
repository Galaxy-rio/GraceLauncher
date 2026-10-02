package com.galaxyrio.gracelauncher

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.data.ClockLayout
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.home.HomeScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ClockFaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun desktopRendersSavedLayoutWeightSizeAndSpacingAndRemainsClickable() {
        var clicks = 0
        var state by mutableStateOf(LauncherUiState(isLoadingApps = false))
        state = state.copy(settings = state.settings.copy(clockStyle = ClockStyle(
            layout = ClockLayout.TwoLines,
            twoLines = ClockStyle.defaults(ClockLayout.TwoLines).copy(weight = 600, size = 60, letterSpacing = 2),
        )))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                HomeScreen(state, 24.dp, {}, {}, { _, _ -> }, {}, { clicks++ })
            }
        }
        fun layout(): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("home_clock").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single()
        }
        val stacked = layout()
        assertEquals(2, stacked.lineCount)
        assertEquals(600, stacked.layoutInput.style.fontWeight!!.weight)
        assertEquals(60.sp, stacked.layoutInput.style.fontSize)
        assertEquals(2.sp, stacked.layoutInput.style.letterSpacing)
        assertFalse(stacked.layoutInput.text.text.contains(':'))
        compose.onNodeWithTag("home_clock").performClick()
        compose.runOnIdle {
            assertEquals(1, clicks)
            val single = ClockStyle().withFace(ClockStyle().face.copy(showColon = true))
            state = state.copy(settings = state.settings.copy(clockStyle = single))
        }
        assertEquals(1, layout().lineCount)
        assertTrue(layout().layoutInput.text.text.contains(':'))
        assertTrue(stacked.size.height > layout().size.height)
    }
}
