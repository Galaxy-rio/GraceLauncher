package com.galaxyrio.gracelauncher

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.AlphabetAppearance
import com.galaxyrio.gracelauncher.data.IconColor
import com.galaxyrio.gracelauncher.data.IconShape
import com.galaxyrio.gracelauncher.data.ListAppearance
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AlphabetRail
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlphabetSettingsTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(LauncherUiState())

    @Test fun settingsRoundTripAndLegacyListsGetSafeDefaults() {
        assertEquals(AlphabetAppearance(), ListAppearance.decode(null).alphabet)
        assertEquals(AlphabetAppearance(), ListAppearance.decode("""{"fontSize":22}""").alphabet)
        IconShape.entries.forEach { shape ->
            val alphabet = AlphabetAppearance(fontId = "imported.ttf", fontColor = IconColor(0xFF34ABCD.toInt()),
                fontSize = 24, indicatorShape = shape, pebbleRoundness = 64, squareCornerRadius = 45,
                cookieSides = 9, topPercent = 20, bottomPercent = 80, freeMovement = true)
            val list = ListAppearance(fontSize = 18, fontColor = IconColor(-1), alphabet = alphabet)
            assertEquals(list, ListAppearance.decode(list.encode()))
        }
    }

    @Test fun fourthPersonalizationEntryOpensTheRightSidePreview() {
        showSettings("Themes")
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_font"))
        val appList = compose.onNodeWithTag("settings_list_appearance").fetchSemanticsNode().boundsInRoot
        val alphabet = compose.onNodeWithTag("settings_alphabet").fetchSemanticsNode().boundsInRoot
        val font = compose.onNodeWithTag("settings_font").fetchSemanticsNode().boundsInRoot
        assertTrue(appList.top < alphabet.top && alphabet.top < font.top)
        compose.onNodeWithTag("settings_alphabet").performClick()
        val preview = compose.onNodeWithTag("alphabet_preview").fetchSemanticsNode().boundsInRoot
        val page = compose.onNodeWithTag("alphabet_settings").fetchSemanticsNode().boundsInRoot
        assertEquals(page.right, preview.right, 1f)
        assertTrue(preview.height > page.height * .65f)
        scrollTo("alphabet_free_movement")
        compose.onNodeWithTag("alphabet_preview").assertIsDisplayed()
        assertEquals(preview, compose.onNodeWithTag("alphabet_preview").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun resetColorAndFreeMovementDoNotAlterAppListAppearance() {
        val original = ListAppearance(fontSize = 18, fontColor = IconColor(-1),
            alphabet = AlphabetAppearance(fontSize = 22, topPercent = 60, bottomPercent = 90))
        state = state.copy(settings = state.settings.copy(listAppearance = original))
        showSettings()
        scrollTo("alphabet_font_size_control")
        compose.onNodeWithTag("alphabet_font_size_reset").performClick()
        scrollTo("alphabet_top_control")
        compose.onNodeWithTag("alphabet_top_reset").performClick()
        scrollTo("alphabet_free_movement")
        compose.onNodeWithTag("alphabet_free_movement").performClick()
        scrollTo("alphabet_color")
        compose.onNodeWithTag("alphabet_color").performClick()
        compose.onNodeWithTag("alphabet_preview").assertIsDisplayed()
        compose.onNodeWithTag("icon_designer_hex").performScrollTo().performTextReplacement("34ABCD")
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("alphabet_color").assertIsDisplayed()
        compose.runOnIdle {
            val list = state.settings.listAppearance
            assertEquals(original.copy(alphabet = list.alphabet), list)
            assertEquals(14, list.alphabet.fontSize)
            assertEquals(35, list.alphabet.topPercent)
            assertEquals(90, list.alphabet.bottomPercent)
            assertTrue(list.alphabet.freeMovement)
            assertEquals(IconColor(0xFF34ABCD.toInt()), list.alphabet.fontColor)
        }
    }

    @Test fun bothTouchEdgesFloatReverseAndReturnWithoutMovingTheHitSurface() {
        var selected by mutableStateOf<String?>(null)
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize()) {
                    AlphabetRail(listOf("A", "B", "C", "D", "E"), selected, 240.dp, { selected = it },
                        Modifier.align(Alignment.CenterEnd), includeHome = false, leftTouchEnabled = true,
                        alphabet = AlphabetAppearance(freeMovement = true))
                }
            }
        }
        val initial = compose.onNodeWithTag("alphabet:A").fetchSemanticsNode().boundsInRoot.top
        for (tag in listOf("alphabet_rail", "alphabet_left_touch")) {
            val rail = compose.onNodeWithTag(tag, useUnmergedTree = true)
            val hitArea = rail.fetchSemanticsNode().boundsInRoot
            rail.performTouchInput { down(Offset(centerX, centerY)); moveTo(Offset(centerX, -height / 2f)) }
            compose.runOnIdle { assertEquals("A", selected) }
            assertTrue(compose.onNodeWithTag("alphabet:A").fetchSemanticsNode().boundsInRoot.top < initial)
            assertEquals(hitArea, rail.fetchSemanticsNode().boundsInRoot)
            rail.performTouchInput { moveTo(Offset(centerX, -height / 2f + height / 5f * 1.1f)) }
            compose.runOnIdle { assertEquals("B", selected) }
            rail.performTouchInput { up() }
            compose.waitForIdle()
            assertEquals(initial, compose.onNodeWithTag("alphabet:A").fetchSemanticsNode().boundsInRoot.top, 1f)
        }
    }

    private fun scrollTo(tag: String) = compose.onNodeWithTag("alphabet_controls").performScrollToNode(hasTestTag(tag))
    private fun showSettings(page: String = "Alphabet") {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(updateSettings = { state = state.copy(settings = it(state.settings)) }),
                    onBack = {}, initialPage = page)
            }
        }
    }
}
